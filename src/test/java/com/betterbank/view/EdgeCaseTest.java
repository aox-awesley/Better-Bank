package com.betterbank.view;

import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The SPEC §9 edge cases that can be decided in code rather than only in game.
 */
public class EdgeCaseTest
{
	private static BankItem item(String name, long stackValue, int unit, int qty, boolean priced)
	{
		return new BankItem(null, 1, name, stackValue, unit, qty, priced);
	}

	// ---- item with no price data ---------------------------------------------------

	@Test
	public void anItemWithNoPriceSaysSoRatherThanShowingAConfidentZero()
	{
		final String tooltip = BankTooltipOverlay.describe(item("Fire cape", 0, 0, 1, false));
		assertTrue(tooltip, tooltip.contains("No price data"));
		assertFalse("must not claim a value", tooltip.contains("Each:"));
	}

	@Test
	public void aPricedItemShowsSingleAndStackValue()
	{
		final String tooltip = BankTooltipOverlay.describe(item("Shark", 8_000, 800, 10, true));
		assertTrue(tooltip, tooltip.contains("Each: <col=ffd700>800"));
		assertTrue(tooltip, tooltip.contains("Stack (10)"));
		assertTrue(tooltip, tooltip.contains("8,000"));
	}

	@Test
	public void aSingleItemShowsNoStackLine()
	{
		final String tooltip = BankTooltipOverlay.describe(item("Rune platebody", 38_000, 38_000, 1, true));
		assertTrue(tooltip.contains("Each:"));
		assertFalse("a stack of one is not a stack", tooltip.contains("Stack ("));
	}

	@Test
	public void unpricedItemsDoNotSinkBelowEverythingUnderValueSort()
	{
		final List<BankItem> items = new ArrayList<>();
		items.add(item("Fire cape", 0, 0, 1, false));
		items.add(item("Coins", 1_000_000, 1, 1_000_000, true));
		items.add(item("Bones", 0, 0, 1, false));
		items.sort(SortMode.VALUE.comparator());

		assertEquals("Coins", items.get(0).name());
		// The unpriced pair stays together and alphabetical rather than interleaved at zero.
		assertEquals("Bones", items.get(1).name());
		assertEquals("Fire cape", items.get(2).name());
	}

	// ---- category with zero items, and an empty bank --------------------------------

	@Test
	public void aCategoryWithNoItemsFormatsAsZeroNotAsBlank()
	{
		// An empty category never renders a header, but if the count is ever shown it must
		// read as 0 rather than as an empty string.
		assertEquals("0", ValueFormat.abbreviate(0));
		assertEquals("0", ValueFormat.exact(0));
	}

	@Test
	public void anEmptyBankProducesNoItemsToSortOrValue()
	{
		final List<BankItem> empty = new ArrayList<>();
		empty.sort(SortMode.VALUE.comparator());
		assertTrue(empty.isEmpty());

		long total = 0;
		for (BankItem i : empty)
		{
			total += i.stackValue();
		}
		assertEquals("an empty category totals zero, not a crash", 0, total);
	}
}
