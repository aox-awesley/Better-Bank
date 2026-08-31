package com.betterbank.view;

import com.betterbank.BetterBankConfig;
import com.betterbank.classify.Category;
import com.betterbank.classify.Scheme;
import com.betterbank.store.OverrideStore;
import com.betterbank.store.SchemeCustomizer;
import java.awt.Rectangle;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.Menu;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.Point;
import net.runelite.api.events.MenuOpened;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.util.ColorUtil;

/**
 * Right-click "Assign to category" on a bank item.
 *
 * <p><b>Why this is permitted, and how it was checked.</b> Jagex's guidelines prohibit menu
 * entries that cause an action to be sent to the server. Entries added here use
 * {@link MenuAction#RUNELITE}, whose opcode (68) sits in RuneLite's own synthesized range
 * (59-73) rather than the game's menu opcodes - the client handles it internally and never
 * forwards it. bank-tag-custom-layouts, which is merged on the Hub, adds
 * {@code MenuAction.RUNELITE} entries to bank items in exactly this way for its
 * "Remove-layout" and "Duplicate-item" options.
 *
 * <p>The click handler writes one config value and asks for a redraw. It runs no script,
 * builds no packet, and touches no game widget's existing entries.
 */
@Singleton
public class BankAssignmentMenu
{
	private static final int MENU_COLOUR = 0xff9040;

	private final Client client;
	private final BankCategoryRenderer renderer;
	private final OverrideStore store;
	private final BetterBankConfig config;

	@Inject
	BankAssignmentMenu(Client client, BankCategoryRenderer renderer, OverrideStore store,
		BetterBankConfig config)
	{
		this.client = client;
		this.renderer = renderer;
		this.store = store;
		this.config = config;
	}

	public void onMenuOpened(MenuOpened event)
	{
		if (!config.groupByCategory())
		{
			return;
		}

		// Only over an item this plugin actually laid out - which also means we are not on a
		// tag tab, a search, or any other view we stand down for.
		final BankItem hovered = hoveredItem();
		if (hovered == null)
		{
			return;
		}

		final Scheme scheme = activeScheme();
		final Menu menu = client.getMenu();

		final MenuEntry parent = menu.createMenuEntry(-1)
			.setOption("Assign to category")
			.setTarget(ColorUtil.wrapWithColorTag(hovered.name(), new java.awt.Color(MENU_COLOUR)))
			.setType(MenuAction.RUNELITE);

		final Menu submenu = parent.createSubMenu();
		final String current = store.assignedCategory(scheme.id(), hovered.canonicalId());

		for (Category category : scheme.categories())
		{
			final boolean isCurrent = category.id().equals(current);
			submenu.createMenuEntry(-1)
				.setOption(isCurrent ? category.name() + " <col=808080>(current)</col>" : category.name())
				.setType(MenuAction.RUNELITE)
				.onClick(e -> assign(scheme.id(), hovered.canonicalId(), category.id()));
		}

		if (current != null)
		{
			submenu.createMenuEntry(-1)
				.setOption("<col=ff8080>Clear assignment</col>")
				.setType(MenuAction.RUNELITE)
				.onClick(e -> clear(scheme.id(), hovered.canonicalId()));
		}
	}

	/** Writes the override and redraws. Nothing else happens. */
	private void assign(String schemeId, int itemId, String categoryId)
	{
		store.assign(schemeId, itemId, categoryId);
		renderer.requestRebuild();
	}

	private void clear(String schemeId, int itemId)
	{
		store.clearAssignment(schemeId, itemId);
		renderer.requestRebuild();
	}

	/** The active scheme with the user's category edits applied. */
	private Scheme activeScheme()
	{
		final Scheme base = config.scheme().scheme();
		return SchemeCustomizer.apply(base, store.customization(base.id()));
	}

	private BankItem hoveredItem()
	{
		final Widget container = client.getWidget(InterfaceID.Bankmain.ITEMS);
		if (container == null || container.isHidden())
		{
			return null;
		}
		final Point mouse = client.getMouseCanvasPosition();
		if (mouse == null)
		{
			return null;
		}
		for (BankItem item : renderer.placedItems())
		{
			final Widget widget = item.widget();
			if (widget == null || widget.isHidden())
			{
				continue;
			}
			final Rectangle bounds = widget.getBounds();
			if (bounds != null && bounds.contains(mouse.getX(), mouse.getY()))
			{
				return item;
			}
		}
		return null;
	}
}
