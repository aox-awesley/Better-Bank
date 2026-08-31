package com.betterbank.view;

import com.betterbank.BetterBankConfig;
import com.betterbank.classify.Category;
import com.betterbank.classify.Classifier;
import com.betterbank.classify.Scheme;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.FontID;
import net.runelite.api.ScriptID;
import net.runelite.api.events.ScriptPostFired;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetType;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.bank.BankSearch;
import net.runelite.client.plugins.banktags.tabs.TabInterface;

/**
 * Regroups the bank's item widgets under category headers (SPEC §7 module 3).
 *
 * <p><b>Rendering only.</b> Nothing here sends an action to the game server. The plugin
 * moves widgets, hides widgets, and creates text widgets for headers; it never reorders the
 * real bank, never synthesises input, and never adds a menu entry that would cause a server
 * action. Every item widget keeps its own native withdraw menu, untouched.
 *
 * <p>The approach follows the two shipped Hub plugins surveyed in {@code notes/m2-spike.md}
 * - hook the bank rebuild through a script event, move the existing children with
 * {@code setOriginalX/Y} + {@code revalidate()}, and hide rather than delete.
 */
@Slf4j
@Singleton
public class BankCategoryRenderer
{
	// Bank item grid geometry. These match the values the bank interface itself uses, and are
	// the same constants both reference plugins arrived at independently.
	private static final int ITEMS_PER_ROW = 8;
	private static final int ITEM_WIDTH = 36;
	private static final int ITEM_HEIGHT = 32;
	private static final int COLUMN_WIDTH = 48;
	private static final int ROW_HEIGHT = 36;
	private static final int START_X = 51;

	private static final int HEADER_HEIGHT = 15;
	/** Breathing room under a category's last row, before the next header. */
	private static final int CATEGORY_GAP = 6;
	/** Bank title while no other plugin owns the view. */
	private static final int HEADER_COLOUR = 0xff981f;

	private final Client client;
	private final ClientThread clientThread;
	private final ItemManager itemManager;
	private final BankSearch bankSearch;
	private final TabInterface tabInterface;
	private final BetterBankConfig config;

	/** Set once the attribute table has loaded; null means "not ready, do nothing". */
	private Classifier classifier;

	/**
	 * True while a layout pass is running. Anything we do during a pass that makes the client
	 * rebuild the bank would re-enter {@link #layout()} on top of the pass already in
	 * progress, which corrupts the pass and can recurse; a nested request is dropped instead.
	 */
	private boolean layingOut;

	// Header widgets we created, pooled and reused across rebuilds. There is no API to remove
	// a single child from a widget (only deleteAllChildren, which would take the bank's items
	// with it), so unused headers are hidden and kept for the next build - the same approach
	// bank-templates uses for its overflow slots.
	private final List<Widget> headers = new ArrayList<>();
	private Widget headerParent;

	@Inject
	BankCategoryRenderer(Client client, ClientThread clientThread, ItemManager itemManager,
		BankSearch bankSearch, TabInterface tabInterface, BetterBankConfig config)
	{
		this.client = client;
		this.clientThread = clientThread;
		this.itemManager = itemManager;
		this.bankSearch = bankSearch;
		this.tabInterface = tabInterface;
		this.config = config;
	}

	public void setClassifier(Classifier classifier)
	{
		this.classifier = classifier;
	}

	// ---- lifecycle -----------------------------------------------------------------

	/**
	 * Puts the bank back exactly as vanilla: drop our headers, then ask the client to rebuild
	 * the bank from scratch. The rebuild is what restores item positions - we never recorded
	 * the originals, and we do not need to, because the bank interface rebuilds itself
	 * completely on demand.
	 */
	public void restoreVanilla()
	{
		clientThread.invokeLater(() ->
		{
			hideAllHeaders();
			if (client.getWidget(InterfaceID.Bankmain.ITEMS) != null)
			{
				bankSearch.layoutBank();
			}
		});
	}

	/** Forces a redraw so a config change (scheme, sort, on/off) shows immediately. */
	public void requestRebuild()
	{
		clientThread.invokeLater(() ->
		{
			if (client.getWidget(InterfaceID.Bankmain.ITEMS) != null)
			{
				bankSearch.layoutBank();
			}
		});
	}

	// ---- event entry points --------------------------------------------------------

	/**
	 * The bank has finished rebuilding; lay our grouping over it.
	 *
	 * <p>Called from a {@code priority = -1f} subscriber so it runs after core Bank Tags,
	 * which also rewrites bank item positions - running first would mean being overwritten.
	 *
	 * <p>{@code BANKMAIN_FINISHBUILDING} rather than {@code BANKMAIN_BUILD}: finishbuilding
	 * is what sizes the scrollbar, so laying out after it completes means our scroll height
	 * is the last word instead of being immediately overwritten.
	 */
	public void onScriptPostFired(ScriptPostFired event)
	{
		if (event.getScriptId() != ScriptID.BANKMAIN_FINISHBUILDING)
		{
			return;
		}
		layout();
	}

	/**
	 * The bank interface was (re)loaded. Any children we created belong to the previous
	 * widget instance and must not be reused.
	 */
	public void onWidgetLoaded(WidgetLoaded event)
	{
		if (event.getGroupId() == InterfaceID.BANKMAIN)
		{
			headers.clear();
			headerParent = null;
		}
	}

	// ---- layout --------------------------------------------------------------------

	private void layout()
	{
		if (layingOut)
		{
			return;
		}

		final Widget container = client.getWidget(InterfaceID.Bankmain.ITEMS);
		if (container == null)
		{
			return;
		}

		layingOut = true;
		try
		{
			layoutPass(container);
		}
		finally
		{
			layingOut = false;
		}
	}

	/** One layout pass. Only ever called through {@link #layout()}, which guards re-entry. */
	private void layoutPass(Widget container)
	{
		if (!shouldApply())
		{
			// Stand down. No restore is needed: this runs at the end of the bank's own rebuild,
			// so the items are already sitting at their vanilla positions.
			hideAllHeaders();
			return;
		}

		final Scheme scheme = config.scheme().scheme();
		final List<BankItem> items = collectItems(container);
		if (items.isEmpty())
		{
			// An empty bank still needs our headers gone.
			hideAllHeaders();
			return;
		}

		final Map<String, List<BankItem>> grouped = group(scheme, items);
		final int contentHeight = place(container, scheme, grouped);

		resizeBankScrollRegion(container, contentHeight);
	}

	/**
	 * True when it is ours to draw. We stand down whenever another plugin or another bank
	 * mode owns the view, rather than fighting it.
	 */
	private boolean shouldApply()
	{
		if (classifier == null || !config.groupByCategory())
		{
			return false;
		}
		// Potion storage swaps the bank for a different container entirely.
		if (client.getVarbitValue(VarbitID.BANK_CURRENTTAB) == POTION_STORAGE_TAB)
		{
			return false;
		}
		// A Bank Tags tag tab is core Bank Tags' own layout - leave it alone.
		if (tabInterface.getActiveTag() != null)
		{
			return false;
		}
		// A search is a filtered view the user asked for; grouping it would fight the filter.
		return !isSearching();
	}

	private static final int POTION_STORAGE_TAB = 15;

	/**
	 * Whether a bank search is active.
	 *
	 * <p>Read from the title text because there is no API that exposes it. The bank sets the
	 * title to "Showing items: ..." while searching, and that string is the only signal
	 * available to an external plugin - the same workaround bank-templates uses. It is
	 * language-dependent and could break on a client update; the cost of it being wrong is
	 * that we group a filtered view, not that anything breaks.
	 */
	private boolean isSearching()
	{
		final Widget title = client.getWidget(InterfaceID.Bankmain.TITLE);
		return title != null && title.getText() != null
			&& title.getText().startsWith("Showing items");
	}

	/**
	 * Every item widget currently in the bank container.
	 *
	 * <p>Hidden widgets and negative item ids are skipped: the bank keeps spare slot widgets
	 * around, and they are not items.
	 */
	private List<BankItem> collectItems(Widget container)
	{
		final Widget[] children = container.getDynamicChildren();
		if (children == null)
		{
			return Collections.emptyList();
		}

		final Set<Widget> ours = Collections.newSetFromMap(new IdentityHashMap<>());
		ours.addAll(headers);

		final List<BankItem> items = new ArrayList<>(children.length);
		for (Widget child : children)
		{
			if (child == null || ours.contains(child))
			{
				continue;
			}
			if (child.isHidden() || child.getItemId() < 0)
			{
				// Not an item: a spare slot, or one of the bank's own tab separator lines.
				// Hide the separators so they do not float loose inside our grouping.
				if (child.getItemId() < 0 && !child.isHidden())
				{
					child.setHidden(true);
					child.revalidate();
				}
				continue;
			}

			// Resolve placeholders and notes so the classifier and the price lookup both see
			// the real item rather than its stand-in.
			final int canonical = itemManager.canonicalize(child.getItemId());
			final int unitPrice = itemManager.getItemPrice(canonical);
			final int quantity = child.getItemQuantity();
			final boolean priced = unitPrice > 0;
			final long stackValue = priced ? (long) unitPrice * Math.max(quantity, 0) : 0L;

			String name = "";
			try
			{
				name = itemManager.getItemComposition(canonical).getName();
			}
			catch (RuntimeException ex)
			{
				// An id the client has no definition for should not take the whole layout down.
				log.debug("no composition for item {}", canonical, ex);
			}

			items.add(new BankItem(child, canonical, name == null ? "" : name, stackValue, priced));
		}
		return items;
	}

	/** Buckets items by category id, preserving the scheme's declared category order. */
	private Map<String, List<BankItem>> group(Scheme scheme, List<BankItem> items)
	{
		final Map<String, List<BankItem>> grouped = new LinkedHashMap<>();
		for (Category category : scheme.categories())
		{
			grouped.put(category.id(), new ArrayList<>());
		}
		for (BankItem item : items)
		{
			final Category category = classifier.classify(scheme, item.canonicalId());
			grouped.get(category.id()).add(item);
		}
		return grouped;
	}

	/**
	 * Positions headers and items, top to bottom, in the scheme's category order.
	 * Categories holding nothing are skipped entirely rather than rendering an empty header.
	 *
	 * @return the total content height, for the scroll region
	 */
	private int place(Widget container, Scheme scheme, Map<String, List<BankItem>> grouped)
	{
		final int headerWidth = Math.max(container.getWidth() - START_X, ITEM_WIDTH);
		int headerIndex = 0;
		int y = 0;

		for (Category category : scheme.categories())
		{
			final List<BankItem> items = grouped.get(category.id());
			if (items == null || items.isEmpty())
			{
				continue;
			}

			items.sort(config.sortMode().comparator());

			placeHeader(container, headerIndex++, category, items.size(), y, headerWidth);
			y += HEADER_HEIGHT;

			for (int i = 0; i < items.size(); i++)
			{
				final Widget widget = items.get(i).widget();
				widget.setOriginalX((i % ITEMS_PER_ROW) * COLUMN_WIDTH + START_X);
				widget.setOriginalY(y + (i / ITEMS_PER_ROW) * ROW_HEIGHT);
				widget.revalidate();
			}

			final int rows = (items.size() + ITEMS_PER_ROW - 1) / ITEMS_PER_ROW;
			y += rows * ROW_HEIGHT + CATEGORY_GAP;
		}

		// Headers left over from a previous, longer layout.
		for (int i = headerIndex; i < headers.size(); i++)
		{
			headers.get(i).setHidden(true);
		}

		return y;
	}

	private void placeHeader(Widget container, int index, Category category, int count, int y, int width)
	{
		if (container != headerParent)
		{
			// A different container instance - our old children belong to the old one.
			headers.clear();
			headerParent = container;
		}

		Widget header;
		if (index < headers.size())
		{
			header = headers.get(index);
		}
		else
		{
			header = container.createChild(-1, WidgetType.TEXT);
			headers.add(header);
		}

		header.setText(category.name() + "  (" + count + ")");
		header.setFontId(FontID.BOLD_12);
		header.setTextColor(HEADER_COLOUR);
		header.setTextShadowed(true);
		header.setOriginalX(START_X);
		header.setOriginalY(y);
		header.setOriginalWidth(width);
		header.setOriginalHeight(HEADER_HEIGHT);
		header.setHidden(false);
		header.revalidate();
	}

	private void hideAllHeaders()
	{
		for (Widget header : headers)
		{
			if (header != null)
			{
				header.setHidden(true);
			}
		}
	}

	// ---- the scrollbar workaround --------------------------------------------------

	/**
	 * Resizes the bank's scroll region so a grouped layout taller than the default view can
	 * be scrolled to the bottom.
	 *
	 * <p><b>There is no API for this, and this is the most fragile code in the plugin.</b>
	 * Two undocumented behaviours are relied on:
	 *
	 * <ol>
	 *   <li>{@code setScrollHeight} alone does nothing visible - the client only recomputes
	 *       the scrollbar's geometry when the {@code UPDATE_SCROLLBAR} script runs, so it has
	 *       to be invoked by hand afterwards.</li>
	 *   <li>{@code UPDATE_SCROLLBAR} takes the scrollbar component, the scrolled component
	 *       and a scroll position as raw arguments, an ordering that is not part of any
	 *       published interface.</li>
	 * </ol>
	 *
	 * <p>Both reference plugins on the Hub do exactly this, which is the only reason to
	 * believe it is stable. It may break on a client update, and if it does, the symptom
	 * will be a bank that cannot scroll to its last row rather than anything harmful.
	 * Everything else in this class uses supported API; keep the breakage surface here.
	 */
	private void resizeBankScrollRegion(Widget container, int contentHeight)
	{
		// Takes effect immediately; it is only the scrollbar geometry that needs the script.
		container.setScrollHeight(contentHeight);

		// Keep the existing scroll position, but never leave the view parked past the end of
		// a layout that just got shorter.
		final int maxScroll = Math.max(contentHeight - container.getHeight(), 0);
		final int scrollY = Math.min(container.getScrollY(), maxScroll);

		// MUST be deferred. This method is reached from inside a ScriptPostFired callback -
		// that is, while a script is still on the stack - and the client asserts that scripts
		// are not reentrant: calling runScript here throws AssertionError and freezes the
		// client. invokeLater runs it on the next client tick, once the current script has
		// finished. bank-tag-custom-layouts defers it the same way for the same reason.
		clientThread.invokeLater(() ->
		{
			// The bank may have been closed in the meantime.
			if (client.getWidget(InterfaceID.Bankmain.ITEMS) == null)
			{
				return;
			}
			client.runScript(ScriptID.UPDATE_SCROLLBAR,
				InterfaceID.Bankmain.SCROLLBAR,
				InterfaceID.Bankmain.ITEMS,
				scrollY);
		});
	}
}
