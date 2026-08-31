package com.betterbank;

import com.betterbank.classify.AttributeTable;
import com.betterbank.classify.Assignments;
import com.betterbank.classify.Classifier;
import com.betterbank.view.BankCategoryRenderer;
import com.google.gson.Gson;
import com.google.inject.Provides;
import java.io.IOException;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.events.ScriptPostFired;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDependency;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.banktags.BankTagsPlugin;

/**
 * Better Bank groups the bank into categories client-side.
 *
 * <p>The plugin never causes an action to be sent to the game server: it is a rendering and
 * bookkeeping layer over the bank interface, and the real bank on the server is never
 * modified (SPEC §2).
 *
 * <p>Depends on core Bank Tags only to read {@code TabInterface.getActiveTag()}, which is
 * how the view knows to stand down while a tag tab owns the bank.
 */
@Slf4j
@PluginDependency(BankTagsPlugin.class)
@PluginDescriptor(
	name = "Better Bank",
	description = "Automatically categorises your bank and groups it by category",
	tags = {"bank", "category", "categories", "sort", "organise", "organize"}
)
public class BetterBankPlugin extends Plugin
{
	@Inject
	private BetterBankConfig config;

	@Inject
	private BankCategoryRenderer renderer;

	@Inject
	private Gson gson;

	@Override
	protected void startUp() throws Exception
	{
		// Reads the bundled attribute table out of the plugin jar. One small classpath read,
		// once per enable - not user-disk or network IO.
		final AttributeTable table = AttributeTable.bundled(gson);
		log.debug("Better Bank loaded {} item attribute rows", table.size());

		// Overrides are the store module and arrive with the customization UI; until then the
		// classifier runs on scheme rules and inference alone.
		renderer.setClassifier(new Classifier(table, Assignments.none()));
		renderer.requestRebuild();
	}

	@Override
	protected void shutDown()
	{
		// Leaves the bank exactly vanilla. Never blocks.
		renderer.setClassifier(null);
		renderer.restoreVanilla();
	}

	/**
	 * Runs after core Bank Tags (default priority 0), which rewrites bank item positions for
	 * its own layouts. Running before it would mean being immediately overwritten.
	 */
	@Subscribe(priority = -1f)
	public void onScriptPostFired(ScriptPostFired event)
	{
		renderer.onScriptPostFired(event);
	}

	@Subscribe
	public void onWidgetLoaded(WidgetLoaded event)
	{
		renderer.onWidgetLoaded(event);
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (BetterBankConfig.GROUP.equals(event.getGroup()))
		{
			renderer.requestRebuild();
		}
	}

	@Provides
	BetterBankConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(BetterBankConfig.class);
	}
}
