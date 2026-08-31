package com.betterbank.view;

import java.util.Comparator;

/**
 * How items are ordered inside a category (SPEC §5).
 *
 * <p>The comparator is the seam: alternative modes (quantity, recently added) are a new
 * constant here and nothing else.
 *
 * <p><b>Untradeables get an explicit rule.</b> They have no price, so under a pure
 * descending-value sort every one of them would resolve to zero and sink to the bottom of
 * every category - which is exactly where a Fire cape or a quest item is least useful. They
 * are instead kept in their own block, ordered alphabetically, below the priced items.
 */
public enum SortMode
{
	/**
	 * Descending stack value, then untradeables alphabetically in their own block.
	 *
	 * <p>This is <i>live</i>: it re-sorts on every bank rebuild, so items move as prices
	 * move. Pinned ordering (sort once, then hold position, so muscle memory survives) needs
	 * somewhere to persist the frozen order, which is the store module - it is not part of
	 * this milestone. Live is the honest default until then: it is always correct, where a
	 * pinned order that cannot be saved would silently reset every login.
	 */
	VALUE("Stack value")
		{
			@Override
			public Comparator<BankItem> comparator()
			{
				return Comparator
					// false (priced) sorts before true (unpriced), keeping untradeables together.
					.comparing((BankItem i) -> !i.priced())
					.thenComparing(Comparator.comparingLong(BankItem::stackValue).reversed())
					.thenComparing(BankItem::name, String.CASE_INSENSITIVE_ORDER);
			}
		},

	/** Alphabetical by item name. Untradeables need no special case here. */
	ALPHABETICAL("Name")
		{
			@Override
			public Comparator<BankItem> comparator()
			{
				return Comparator.comparing(BankItem::name, String.CASE_INSENSITIVE_ORDER);
			}
		};

	private final String label;

	SortMode(String label)
	{
		this.label = label;
	}

	public abstract Comparator<BankItem> comparator();

	@Override
	public String toString()
	{
		return label;
	}
}
