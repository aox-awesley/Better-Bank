package com.betterbank.classify;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The bundled override table: hand-maintained attributes for the items the client and the
 * name rules get wrong or cannot know.
 *
 * <p>This used to be an attempt at an item&rarr;attributes table for the whole game, which
 * does not scale - OSRS has tens of thousands of items and this file was carrying a few
 * hundred. Attributes now come from {@link RuntimeDerivation} and {@link NamePatterns}; what
 * is left here is only the genuine exceptions, and it wins over both.
 *
 * <p>Loaded through {@code getResourceAsStream}, never {@code getResource} - a deployed
 * plugin runs from inside a jar and is not unpacked.
 */
public final class OverrideTable
{
	public static final String RESOURCE_PATH = "/com/betterbank/item-attributes.json";

	/** The only format version this build understands. Bump alongside a migration. */
	public static final int SUPPORTED_FORMAT_VERSION = 1;

	private final Map<Integer, ItemDto> byItemId;

	private OverrideTable(Map<Integer, ItemDto> byItemId)
	{
		this.byItemId = Collections.unmodifiableMap(byItemId);
	}

	public static OverrideTable bundled(Gson gson) throws IOException
	{
		try (InputStream in = OverrideTable.class.getResourceAsStream(RESOURCE_PATH))
		{
			if (in == null)
			{
				throw new IOException("Bundled override table missing: " + RESOURCE_PATH);
			}
			return fromJson(gson, in);
		}
	}

	public static OverrideTable fromJson(Gson gson, InputStream in) throws IOException
	{
		try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8))
		{
			final FileDto dto = gson.fromJson(reader, FileDto.class);
			if (dto == null)
			{
				throw new IOException("Override table is empty");
			}
			if (dto.formatVersion != SUPPORTED_FORMAT_VERSION)
			{
				throw new IOException("Unsupported override table formatVersion "
					+ dto.formatVersion + " (expected " + SUPPORTED_FORMAT_VERSION + ")");
			}
			if (dto.items == null)
			{
				throw new IOException("Override table has no \"items\"");
			}

			final Map<Integer, ItemDto> parsed = new LinkedHashMap<>(dto.items.size());
			for (Map.Entry<String, ItemDto> e : dto.items.entrySet())
			{
				final int itemId;
				try
				{
					itemId = Integer.parseInt(e.getKey());
				}
				catch (NumberFormatException nfe)
				{
					throw new IOException("Override table key is not an item id: " + e.getKey());
				}
				// Validate eagerly so a typo fails the load rather than one silent bad item.
				e.getValue().validate(itemId);
				parsed.put(itemId, e.getValue());
			}
			return new OverrideTable(parsed);
		}
		catch (JsonParseException ex)
		{
			throw new IOException("Malformed override table", ex);
		}
	}

	/** An empty table, for tests that want derivation only. */
	public static OverrideTable empty()
	{
		return new OverrideTable(Collections.emptyMap());
	}

	public boolean contains(int itemId)
	{
		return byItemId.containsKey(itemId);
	}

	public int size()
	{
		return byItemId.size();
	}

	public Set<Integer> itemIds()
	{
		return byItemId.keySet();
	}

	/** The override's name for this item, or null. Used only when the client has none. */
	public String nameFor(int itemId)
	{
		final ItemDto dto = byItemId.get(itemId);
		return dto == null ? null : dto.name;
	}

	/**
	 * Applies this item's overrides onto {@code out}. Only fields the JSON actually specifies
	 * are written, so an override can correct one attribute without restating the rest.
	 */
	public void applyOnto(int itemId, ItemAttributes.Builder out)
	{
		final ItemDto dto = byItemId.get(itemId);
		if (dto != null)
		{
			dto.applyOnto(out, itemId);
		}
	}

	// ---- JSON shape ----------------------------------------------------------------

	private static final class FileDto
	{
		int formatVersion;
		Map<String, ItemDto> items;
	}

	/**
	 * Mirrors one entry. Every field is boxed so an absent field is distinguishable from a
	 * present-but-default one - that is what lets an override be partial.
	 */
	private static final class ItemDto
	{
		String name;
		String slot;
		List<String> styles;
		List<String> skills;
		String consumable;
		String material;
		Boolean tradeable;
		Boolean members;
		Boolean stackable;
		Boolean noted;
		Boolean questItem;
		Boolean clueReward;
		Boolean achievement;
		Boolean tool;
		Boolean rune;
		Boolean currency;
		Boolean teleport;
		Boolean pet;

		void validate(int itemId)
		{
			applyOnto(ItemAttributes.builder(""), itemId);
		}

		void applyOnto(ItemAttributes.Builder b, int itemId)
		{
			if (slot != null)
			{
				b.slot(parse(EquipmentSlot.class, slot, itemId, "slot"));
			}
			if (styles != null && !styles.isEmpty())
			{
				final CombatStyle[] v = new CombatStyle[styles.size()];
				for (int i = 0; i < v.length; i++)
				{
					v[i] = parse(CombatStyle.class, styles.get(i), itemId, "styles");
				}
				b.styles(v);
			}
			if (skills != null && !skills.isEmpty())
			{
				final SkillType[] v = new SkillType[skills.size()];
				for (int i = 0; i < v.length; i++)
				{
					v[i] = parse(SkillType.class, skills.get(i), itemId, "skills");
				}
				b.skills(v);
			}
			if (consumable != null)
			{
				b.consumable(parse(ConsumableClass.class, consumable, itemId, "consumable"));
			}
			if (material != null)
			{
				b.material(parse(MaterialStage.class, material, itemId, "material"));
			}
			if (tradeable != null)
			{
				b.tradeable(tradeable);
			}
			if (members != null)
			{
				b.members(members);
			}
			if (stackable != null)
			{
				b.stackable(stackable);
			}
			if (noted != null)
			{
				b.noted(noted);
			}
			if (questItem != null)
			{
				b.questItem(questItem);
			}
			if (clueReward != null)
			{
				b.clueReward(clueReward);
			}
			if (achievement != null)
			{
				b.achievement(achievement);
			}
			if (tool != null)
			{
				b.tool(tool);
			}
			if (rune != null)
			{
				b.rune(rune);
			}
			if (currency != null)
			{
				b.currency(currency);
			}
			if (teleport != null)
			{
				b.teleport(teleport);
			}
			if (pet != null)
			{
				b.pet(pet);
			}
		}

		/**
		 * Fails loudly on an unrecognised value. Gson yields null for an unknown enum
		 * constant, which would turn a typo in the data into a quietly wrong item.
		 */
		private static <E extends Enum<E>> E parse(Class<E> type, String value, int itemId, String field)
		{
			try
			{
				return Enum.valueOf(type, value.toUpperCase(Locale.ROOT));
			}
			catch (IllegalArgumentException ex)
			{
				throw new JsonParseException("Item " + itemId + " has unknown " + field
					+ " \"" + value + "\"");
			}
		}
	}
}
