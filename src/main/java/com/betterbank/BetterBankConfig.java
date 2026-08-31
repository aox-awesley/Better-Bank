package com.betterbank;

import com.betterbank.view.SchemeChoice;
import com.betterbank.view.SortMode;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup(BetterBankConfig.GROUP)
public interface BetterBankConfig extends Config
{
	String GROUP = "better-bank";

	@ConfigItem(
		keyName = "groupByCategory",
		name = "Group by category",
		description = "Group bank items under category headers instead of the vanilla layout",
		position = 1
	)
	default boolean groupByCategory()
	{
		return false;
	}

	@ConfigItem(
		keyName = "scheme",
		name = "Scheme",
		description = "Which taxonomy to organise the bank by",
		position = 2
	)
	default SchemeChoice scheme()
	{
		return SchemeChoice.SKILLER;
	}

	@ConfigItem(
		keyName = "sortMode",
		name = "Sort within category",
		description = "How items are ordered inside each category."
			+ " Untradeables have no price, so they are kept together at the end of their"
			+ " category rather than sinking to the bottom.",
		position = 3
	)
	default SortMode sortMode()
	{
		return SortMode.VALUE;
	}

	@ConfigItem(
		keyName = "showValueTooltip",
		name = "Value tooltip on hover",
		description = "Show an item's single and full-stack value when you hover it in the bank",
		position = 4
	)
	default boolean showValueTooltip()
	{
		return true;
	}
}
