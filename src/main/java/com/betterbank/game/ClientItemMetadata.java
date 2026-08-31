package com.betterbank.game;

import com.betterbank.classify.ItemMetadata;
import com.betterbank.classify.RuntimeItem;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ItemComposition;
import net.runelite.client.game.ItemEquipmentStats;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemStats;

/**
 * Reads runtime item facts out of the running client.
 *
 * <p>The only place in the plugin that turns game API types into the classifier's plain
 * {@link RuntimeItem}. Keeping the conversion here is what lets everything behind
 * {@link ItemMetadata} be unit-tested with no client.
 *
 * <p>Read-only: {@code ItemComposition} and {@code ItemStats} are lookups against data the
 * client already has. Nothing here contacts the server.
 */
@Slf4j
@Singleton
public class ClientItemMetadata implements ItemMetadata
{
	private final ItemManager itemManager;

	@Inject
	ClientItemMetadata(ItemManager itemManager)
	{
		this.itemManager = itemManager;
	}

	@Override
	public RuntimeItem lookup(int itemId)
	{
		final ItemComposition composition;
		try
		{
			composition = itemManager.getItemComposition(itemId);
		}
		catch (RuntimeException ex)
		{
			// An id this client has no definition for.
			log.debug("no composition for item {}", itemId, ex);
			return null;
		}
		if (composition == null)
		{
			return null;
		}

		final RuntimeItem.Builder b = RuntimeItem.builder(composition.getName())
			.tradeable(composition.isTradeable())
			.members(composition.isMembers())
			.stackable(composition.isStackable())
			.noted(composition.getNote() != -1)
			.placeholder(composition.getPlaceholderTemplateId() != -1)
			.storeValue(composition.getPrice())
			.actions(composition.getInventoryActions());

		final ItemStats stats = itemManager.getItemStats(itemId);
		if (stats != null && stats.isEquipable())
		{
			final ItemEquipmentStats equipment = stats.getEquipment();
			if (equipment != null)
			{
				b.slotIdx(equipment.getSlot())
					.attack(equipment.getAstab(), equipment.getAslash(), equipment.getAcrush(),
						equipment.getAmagic(), equipment.getArange())
					.strength(equipment.getStr(), equipment.getRstr(), equipment.getMdmg())
					.defence(equipment.getDstab(), equipment.getDslash(), equipment.getDcrush())
					.prayer(equipment.getPrayer());
			}
		}

		return b.build();
	}
}
