package com.betterbank;

import com.betterbank.classify.AttributeResolver;
import com.betterbank.classify.Classifier;
import com.betterbank.classify.ItemMetadata;
import com.betterbank.classify.OverrideTable;
import com.betterbank.game.ClientItemMetadata;
import com.betterbank.store.ConfigManagerStore;
import com.betterbank.store.ConfigStore;
import com.betterbank.store.OverrideStore;
import com.betterbank.view.BankAssignmentMenu;
import com.betterbank.view.BankCategoryRenderer;
import com.betterbank.view.BankTooltipOverlay;
import com.google.gson.Gson;
import com.google.inject.Provides;
import java.io.IOException;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.events.MenuOpened;
import net.runelite.api.events.ScriptPostFired;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDependency;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;
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
	private BankTooltipOverlay tooltipOverlay;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private Gson gson;

	@Inject
	private ItemMetadata itemMetadata;

	@Inject
	private OverrideStore overrideStore;

	@Inject
	private BankAssignmentMenu assignmentMenu;

	@Override
	protected void startUp() throws Exception
	{
		// Reads the bundled override table out of the plugin jar. One small classpath read,
		// once per enable - not user-disk or network IO. Attributes for the rest of the game
		// are derived from the client at lookup time.
		final OverrideTable overrides = OverrideTable.bundled(gson);
		log.debug("Better Bank loaded {} item overrides", overrides.size());

		if (!overrideStore.isCompatible())
		{
			// Written by a newer build. Read nothing and write nothing rather than risk
			// destroying edits we cannot understand.
			log.warn("Better Bank customization was saved by a newer version; running without it");
		}

		final AttributeResolver resolver = new AttributeResolver(overrides, itemMetadata);
		renderer.setClassifier(new Classifier(resolver, overrideStore));
		overlayManager.add(tooltipOverlay);
		renderer.requestRebuild();
	}

	@Override
	protected void shutDown()
	{
		// Leaves the bank exactly vanilla. Never blocks.
		overlayManager.remove(tooltipOverlay);
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

	/**
	 * Right-click assignment. The entries are {@code MenuAction.RUNELITE}, which the client
	 * handles internally and never forwards to the server.
	 */
	@Subscribe
	public void onMenuOpened(MenuOpened event)
	{
		assignmentMenu.onMenuOpened(event);
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

	/** Binds the classifier's runtime-data seam to the client-backed implementation. */
	@Provides
	ItemMetadata provideItemMetadata(ClientItemMetadata clientItemMetadata)
	{
		return clientItemMetadata;
	}

	/** Binds the store's persistence seam to the ConfigManager-backed implementation. */
	@Provides
	ConfigStore provideConfigStore(ConfigManagerStore configManagerStore)
	{
		return configManagerStore;
	}

	@Provides
	BetterBankConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(BetterBankConfig.class);
	}
}
