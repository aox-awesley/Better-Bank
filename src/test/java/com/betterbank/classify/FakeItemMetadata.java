package com.betterbank.classify;

import java.util.HashMap;
import java.util.Map;

/**
 * Test double for {@link ItemMetadata}. This is the whole point of the seam: the classifier
 * can be driven through every runtime case without a client.
 */
public final class FakeItemMetadata implements ItemMetadata
{
	private final Map<Integer, RuntimeItem> items = new HashMap<>();

	public FakeItemMetadata put(int itemId, RuntimeItem item)
	{
		items.put(itemId, item);
		return this;
	}

	/** An item the client knows only by name - no stats, not equippable. */
	public FakeItemMetadata named(int itemId, String name)
	{
		return put(itemId, RuntimeItem.builder(name).build());
	}

	/** An equippable item in {@code slotIdx} with no bonuses worth speaking of. */
	public FakeItemMetadata equipment(int itemId, String name, int slotIdx)
	{
		return put(itemId, RuntimeItem.builder(name).slotIdx(slotIdx).build());
	}

	@Override
	public RuntimeItem lookup(int itemId)
	{
		return items.get(itemId);
	}
}
