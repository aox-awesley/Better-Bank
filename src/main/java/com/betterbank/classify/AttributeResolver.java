package com.betterbank.classify;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Builds an item's attributes by layering three sources (SPEC §3's one item&rarr;attributes
 * layer, now derived rather than hand-written).
 *
 * <p>Lowest precedence first, each layer overwriting only the fields it has an opinion on:
 *
 * <ol>
 *   <li>{@link NamePatterns} - skill relevance and production stage, which no API exposes</li>
 *   <li>{@link RuntimeDerivation} - slot, combat style, consumable class, tradeable and
 *       friends, read from the client the user is running</li>
 *   <li>{@link OverrideTable} - the bundled exceptions, which win over both</li>
 * </ol>
 *
 * <p>Results are cached: item attributes do not change while the client is running, and this
 * is called once per item per bank rebuild on the client thread.
 */
public final class AttributeResolver
{
	private final OverrideTable overrides;
	private final ItemMetadata metadata;
	private final Map<Integer, ItemAttributes> cache = new HashMap<>();

	public AttributeResolver(OverrideTable overrides, ItemMetadata metadata)
	{
		this.overrides = Objects.requireNonNull(overrides, "overrides");
		this.metadata = Objects.requireNonNull(metadata, "metadata");
	}

	/**
	 * @return the item's attributes, or null when nothing at all is known about it - no
	 * override, no client definition, and therefore not even a name to match rules against.
	 */
	public ItemAttributes resolve(int itemId)
	{
		if (cache.containsKey(itemId))
		{
			return cache.get(itemId);
		}
		final ItemAttributes resolved = build(itemId);
		cache.put(itemId, resolved);
		return resolved;
	}

	private ItemAttributes build(int itemId)
	{
		final RuntimeItem runtime = metadata.lookup(itemId);
		final String overrideName = overrides.nameFor(itemId);

		String name = runtime != null ? runtime.name() : null;
		if (name == null || name.isEmpty())
		{
			name = overrideName;
		}
		if (name == null && runtime == null && !overrides.contains(itemId))
		{
			// Genuinely unknown: no client definition and no override.
			return null;
		}

		final ItemAttributes.Builder b = ItemAttributes.builder(name == null ? "" : name);
		NamePatterns.apply(name, b);
		if (runtime != null)
		{
			RuntimeDerivation.apply(runtime, b);
		}
		overrides.applyOnto(itemId, b);
		return b.build();
	}

	/**
	 * Drops the cache.
	 *
	 * <p>Needed in practice, not just in theory: {@code ItemManager} fetches equipment stats
	 * over HTTP after startup, so an item looked up before they arrive resolves with no slot
	 * and no combat style. Without this it would stay wrong for the rest of the session.
	 */
	public void invalidate()
	{
		cache.clear();
	}

	public OverrideTable overrides()
	{
		return overrides;
	}
}
