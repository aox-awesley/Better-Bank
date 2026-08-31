package com.betterbank;

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
		description = "Group bank items under category headers instead of the vanilla layout"
	)
	default boolean groupByCategory()
	{
		return false;
	}
}
