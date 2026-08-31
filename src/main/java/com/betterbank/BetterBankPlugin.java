package com.betterbank;

import com.google.inject.Provides;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;

@Slf4j
@PluginDescriptor(
	name = "Better Bank",
	description = "Automatically categorises your bank and groups it by category",
	tags = {"bank", "category", "categories", "sort", "organise", "organize"}
)
public class BetterBankPlugin extends Plugin
{
	@Inject
	private BetterBankConfig config;

	@Override
	protected void startUp() throws Exception
	{
		log.debug("Better Bank started, groupByCategory={}", config.groupByCategory());
	}

	@Override
	protected void shutDown() throws Exception
	{
		log.debug("Better Bank stopped");
	}

	@Provides
	BetterBankConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(BetterBankConfig.class);
	}
}
