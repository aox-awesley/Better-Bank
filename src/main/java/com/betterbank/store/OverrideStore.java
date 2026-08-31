package com.betterbank.store;

import com.betterbank.classify.Assignments;
import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

/**
 * Persistent user customization (SPEC §7 module 4).
 *
 * <p><b>Per-scheme independence is structural, not a convention.</b> Every key this class
 * writes contains the scheme id, so there is no shared bucket an edit could leak through.
 * Customising Merchant, switching to Skiller and switching back cannot disturb the Merchant
 * edits, because the two schemes never touch the same key.
 *
 * <p><b>Assignments are sharded.</b> A single config value holding a large bank's worth of
 * assignments would be one unbounded string; item ids are spread across
 * {@value #ASSIGNMENT_SHARDS} keys instead, which bounds each value and means writing one
 * assignment rewrites one shard rather than the whole set.
 *
 * <p><b>The schema is versioned from the first write.</b> An unrecognised version is left
 * strictly alone - never parsed, never overwritten - so a downgrade cannot silently destroy
 * edits made by a later build.
 */
@Slf4j
@Singleton
public class OverrideStore implements Assignments
{
	/** Bump only alongside a migration. */
	public static final int SCHEMA_VERSION = 1;

	static final String KEY_VERSION = "storeSchemaVersion";
	static final String ASSIGN_PREFIX = "assign_";
	static final String CATEGORIES_PREFIX = "cats_";

	/**
	 * Number of keys assignments are spread across. Eight keeps each value small for a large
	 * bank while staying few enough to read cheaply.
	 */
	static final int ASSIGNMENT_SHARDS = 8;

	private final ConfigStore config;
	private final Gson gson;

	/** Parsed assignments per scheme, loaded lazily and kept in step with every write. */
	private final Map<String, Map<Integer, String>> assignmentCache = new LinkedHashMap<>();
	private final Map<String, SchemeCustomization> customizationCache = new LinkedHashMap<>();

	@Inject
	public OverrideStore(ConfigStore config, Gson gson)
	{
		this.config = Objects.requireNonNull(config, "config");
		this.gson = Objects.requireNonNull(gson, "gson");
	}

	// ---- schema version ------------------------------------------------------------

	/**
	 * @return true when the stored data is a version this build understands. An empty store
	 * counts as compatible - it becomes version {@value #SCHEMA_VERSION} on first write.
	 */
	public boolean isCompatible()
	{
		final String stored = config.get(KEY_VERSION);
		if (stored == null)
		{
			return true;
		}
		try
		{
			return Integer.parseInt(stored.trim()) <= SCHEMA_VERSION;
		}
		catch (NumberFormatException ex)
		{
			return false;
		}
	}

	private void stampVersion()
	{
		if (config.get(KEY_VERSION) == null)
		{
			config.set(KEY_VERSION, Integer.toString(SCHEMA_VERSION));
		}
	}

	// ---- item assignments ----------------------------------------------------------

	@Override
	public String assignedCategory(String schemeId, int itemId)
	{
		return assignments(schemeId).get(itemId);
	}

	/** @return every assignment in this scheme, item id to category id. */
	public Map<Integer, String> assignments(String schemeId)
	{
		return assignmentCache.computeIfAbsent(schemeId, this::loadAssignments);
	}

	public void assign(String schemeId, int itemId, String categoryId)
	{
		if (!guard())
		{
			return;
		}
		assignments(schemeId).put(itemId, categoryId);
		writeShard(schemeId, shardOf(itemId));
		stampVersion();
	}

	public void clearAssignment(String schemeId, int itemId)
	{
		if (!guard() || assignments(schemeId).remove(itemId) == null)
		{
			return;
		}
		writeShard(schemeId, shardOf(itemId));
	}

	private Map<Integer, String> loadAssignments(String schemeId)
	{
		final Map<Integer, String> out = new LinkedHashMap<>();
		if (!isCompatible())
		{
			return out;
		}
		for (int shard = 0; shard < ASSIGNMENT_SHARDS; shard++)
		{
			final String raw = config.get(assignmentKey(schemeId, shard));
			if (raw == null || raw.isEmpty())
			{
				continue;
			}
			for (String pair : raw.split(";"))
			{
				final int eq = pair.indexOf('=');
				if (eq <= 0)
				{
					continue;
				}
				try
				{
					out.put(Integer.parseInt(pair.substring(0, eq)), pair.substring(eq + 1));
				}
				catch (NumberFormatException ex)
				{
					log.debug("skipping malformed assignment '{}' in {}", pair, schemeId);
				}
			}
		}
		return out;
	}

	private void writeShard(String schemeId, int shard)
	{
		final StringBuilder out = new StringBuilder();
		for (Map.Entry<Integer, String> e : assignments(schemeId).entrySet())
		{
			if (shardOf(e.getKey()) != shard)
			{
				continue;
			}
			if (out.length() > 0)
			{
				out.append(';');
			}
			out.append(e.getKey()).append('=').append(e.getValue());
		}

		final String key = assignmentKey(schemeId, shard);
		if (out.length() == 0)
		{
			config.unset(key);
		}
		else
		{
			config.set(key, out.toString());
		}
	}

	private static int shardOf(int itemId)
	{
		return Math.floorMod(itemId, ASSIGNMENT_SHARDS);
	}

	static String assignmentKey(String schemeId, int shard)
	{
		return ASSIGN_PREFIX + schemeId + "_" + shard;
	}

	// ---- category customization ----------------------------------------------------

	public SchemeCustomization customization(String schemeId)
	{
		return customizationCache.computeIfAbsent(schemeId, this::loadCustomization);
	}

	public void saveCustomization(String schemeId, SchemeCustomization customization)
	{
		if (!guard())
		{
			return;
		}
		customizationCache.put(schemeId, customization);
		final String key = CATEGORIES_PREFIX + schemeId;
		if (customization == null || customization.isEmpty())
		{
			config.unset(key);
		}
		else
		{
			config.set(key, gson.toJson(customization));
			stampVersion();
		}
	}

	private SchemeCustomization loadCustomization(String schemeId)
	{
		if (!isCompatible())
		{
			return new SchemeCustomization();
		}
		final String raw = config.get(CATEGORIES_PREFIX + schemeId);
		if (raw == null || raw.isEmpty())
		{
			return new SchemeCustomization();
		}
		try
		{
			final SchemeCustomization parsed = gson.fromJson(raw, SchemeCustomization.class);
			return parsed == null ? new SchemeCustomization() : parsed;
		}
		catch (JsonParseException ex)
		{
			log.warn("unreadable category customization for {}; ignoring it", schemeId, ex);
			return new SchemeCustomization();
		}
	}

	// ---- reset ---------------------------------------------------------------------

	/**
	 * Returns one scheme to its shipped state, leaving every other scheme untouched.
	 *
	 * <p>Only keys carrying this scheme's id are removed, which is the same property that
	 * makes per-scheme editing independent in the first place.
	 */
	public void reset(String schemeId)
	{
		for (int shard = 0; shard < ASSIGNMENT_SHARDS; shard++)
		{
			config.unset(assignmentKey(schemeId, shard));
		}
		config.unset(CATEGORIES_PREFIX + schemeId);
		assignmentCache.remove(schemeId);
		customizationCache.remove(schemeId);
	}

	/** True when this scheme has any customization at all. */
	public boolean isCustomized(String schemeId)
	{
		return !assignments(schemeId).isEmpty() || !customization(schemeId).isEmpty();
	}

	/** Drops parsed state so the next read comes from storage. */
	public void invalidate()
	{
		assignmentCache.clear();
		customizationCache.clear();
	}

	/** Scheme ids that have any stored data, for diagnostics. */
	public List<String> customizedSchemes()
	{
		final List<String> out = new ArrayList<>();
		for (String key : config.keys(CATEGORIES_PREFIX))
		{
			out.add(key.substring(CATEGORIES_PREFIX.length()));
		}
		return out;
	}

	/**
	 * Refuses to write over data written by a newer build. Losing months of edits to a
	 * silent format mismatch is the one failure this store must not have.
	 */
	private boolean guard()
	{
		if (isCompatible())
		{
			return true;
		}
		log.warn("Better Bank override store is version {} which this build does not"
			+ " understand; refusing to write", config.get(KEY_VERSION));
		return false;
	}
}
