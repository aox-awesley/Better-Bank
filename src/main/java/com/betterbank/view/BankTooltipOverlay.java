package com.betterbank.view;

import com.betterbank.BetterBankConfig;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.Point;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.tooltip.Tooltip;
import net.runelite.client.ui.overlay.tooltip.TooltipManager;

/**
 * Hover tooltips for the bank: an item's single and full-stack value, and the name of the
 * active scheme when the cursor is over the scheme switcher.
 *
 * <p>The switcher is an icon with no label, so this is what tells the user which scheme they
 * are on - a compass for Questing or a crown for Collection Log is not self-evident, and the
 * tooltip carries that meaning without spending any of the left strip on text.
 *
 * <p><b>Rendered, not a menu entry.</b> This draws through {@link TooltipManager} - the same
 * mechanism core RuneLite uses for its own hover readouts. Nothing is added to any game
 * widget's menu and nothing reaches the server, which is what SPEC §2 requires.
 */
@Singleton
public class BankTooltipOverlay extends Overlay
{
	private final Client client;
	private final TooltipManager tooltipManager;
	private final BankCategoryRenderer renderer;
	private final BetterBankConfig config;

	@Inject
	BankTooltipOverlay(Client client, TooltipManager tooltipManager,
		BankCategoryRenderer renderer, BetterBankConfig config)
	{
		this.client = client;
		this.tooltipManager = tooltipManager;
		this.renderer = renderer;
		this.config = config;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_WIDGETS);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		final Point mouse = client.getMouseCanvasPosition();
		if (mouse == null)
		{
			return null;
		}

		// The switcher sits outside the item grid, so this is checked before the grid bounds
		// early-out below - and it is not gated on the value-tooltip setting, because naming
		// the scheme is the icon's only label.
		final Rectangle switcher = renderer.switcherBounds();
		if (switcher != null && switcher.contains(mouse.getX(), mouse.getY()))
		{
			tooltipManager.add(new Tooltip("Scheme: <col=ffffff>" + config.scheme() + "</col>"
				+ "</br><col=b0b0b0>Click to switch</col>"));
			return null;
		}

		if (!config.showValueTooltip())
		{
			return null;
		}

		// Only over an open bank, and only over items this plugin actually laid out - which
		// also keeps the hit test bounded to the visible bank rather than every widget.
		final Widget container = client.getWidget(InterfaceID.Bankmain.ITEMS);
		if (container == null || container.isHidden())
		{
			return null;
		}

		final List<BankItem> items = renderer.placedItems();
		if (items.isEmpty())
		{
			return null;
		}
		// Cheap rejection before the per-item scan: if the cursor is not over the grid at all,
		// there is nothing to find.
		final Rectangle grid = container.getBounds();
		if (grid == null || !grid.contains(mouse.getX(), mouse.getY()))
		{
			return null;
		}

		final BankItem hovered = itemAt(items, mouse);
		if (hovered == null)
		{
			return null;
		}

		tooltipManager.add(new Tooltip(describe(hovered)));
		return null;
	}

	private static BankItem itemAt(List<BankItem> items, Point mouse)
	{
		for (BankItem item : items)
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

	/**
	 * Single and full-stack value. Untradeables have no price, and saying so is more useful
	 * than showing a confident zero.
	 */
	static String describe(BankItem item)
	{
		final StringBuilder out = new StringBuilder();
		out.append("<col=ff9040>").append(item.name()).append("</col>");
		if (!item.priced())
		{
			out.append("</br>No price data");
			return out.toString();
		}

		final int quantity = item.quantity();
		out.append("</br>Each: <col=ffd700>").append(ValueFormat.exact(item.unitPrice()))
			.append("</col>");
		if (quantity > 1)
		{
			out.append("</br>Stack (").append(ValueFormat.exact(quantity)).append("): ")
				.append("<col=ffd700>").append(ValueFormat.exact(item.stackValue())).append("</col>");
		}
		return out.toString();
	}
}
