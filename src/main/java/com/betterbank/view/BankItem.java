package com.betterbank.view;

import net.runelite.api.widgets.Widget;

/**
 * One bank item widget, plus the facts the view needs to group and order it.
 *
 * <p>Snapshotted once per rebuild so sorting never re-reads the client. Holds the item's
 * <i>own</i> widget: the view moves that widget rather than reassigning item ids onto
 * fixed-position widgets, which keeps each widget's container index matching the bank slot
 * it shows. Other plugins rely on that correspondence, and it is what lets this be a pure
 * rendering layer - the widget keeps its native withdraw menu untouched.
 */
public final class BankItem
{
	private final Widget widget;
	private final int canonicalId;
	private final String name;
	private final long stackValue;
	private final int unitPrice;
	private final int quantity;
	private final boolean priced;

	BankItem(Widget widget, int canonicalId, String name, long stackValue, int unitPrice,
		int quantity, boolean priced)
	{
		this.unitPrice = unitPrice;
		this.quantity = quantity;
		this.widget = widget;
		this.canonicalId = canonicalId;
		this.name = name;
		this.stackValue = stackValue;
		this.priced = priced;
	}

	public Widget widget()
	{
		return widget;
	}

	/** Placeholder- and note-resolved item id, so the classifier sees the real item. */
	public int canonicalId()
	{
		return canonicalId;
	}

	public String name()
	{
		return name;
	}

	/** Unit price times quantity, or 0 when the item has no price data. */
	public long stackValue()
	{
		return stackValue;
	}

	/** Price of a single one, or 0 when the item has no price data. */
	public int unitPrice()
	{
		return unitPrice;
	}

	/** How many are in this bank slot. */
	public int quantity()
	{
		return quantity;
	}

	/** False for untradeables and anything else the client has no price for. */
	public boolean priced()
	{
		return priced;
	}
}
