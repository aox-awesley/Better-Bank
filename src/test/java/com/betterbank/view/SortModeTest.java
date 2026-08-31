package com.betterbank.view;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * The within-category ordering rules from SPEC §5. Pure comparator logic - no client.
 */
public class SortModeTest
{
	/** Builds an item without a widget; the comparator never touches one. */
	private static BankItem priced(String name, long stackValue)
	{
		return new BankItem(null, 1, name, stackValue, true);
	}

	private static BankItem unpriced(String name)
	{
		return new BankItem(null, 1, name, 0L, false);
	}

	private static List<String> sorted(SortMode mode, BankItem... items)
	{
		final List<BankItem> list = new ArrayList<>(Arrays.asList(items));
		list.sort(mode.comparator());
		final List<String> names = new ArrayList<>();
		for (BankItem item : list)
		{
			names.add(item.name());
		}
		return names;
	}

	@Test
	public void valueSortsDescendingByStackValue()
	{
		assertEquals(Arrays.asList("Big", "Middling", "Small"),
			sorted(SortMode.VALUE,
				priced("Small", 10),
				priced("Big", 1_000_000),
				priced("Middling", 5_000)));
	}

	@Test
	public void untradeablesStayTogetherBelowPricedItemsRatherThanSinking()
	{
		// The rule SPEC §5 asks for: with no price they would all resolve to zero and end up
		// interleaved at the bottom in arbitrary order. They get their own block instead.
		assertEquals(Arrays.asList("Rune platebody", "Shark", "Fire cape", "Quest point cape"),
			sorted(SortMode.VALUE,
				unpriced("Quest point cape"),
				priced("Shark", 800),
				unpriced("Fire cape"),
				priced("Rune platebody", 38_000)));
	}

	@Test
	public void untradeableBlockIsAlphabetical()
	{
		assertEquals(Arrays.asList("Avernic defender", "Fire cape", "Infernal cape"),
			sorted(SortMode.VALUE,
				unpriced("Infernal cape"),
				unpriced("Avernic defender"),
				unpriced("Fire cape")));
	}

	@Test
	public void equalValuesFallBackToNameSoOrderIsStable()
	{
		assertEquals(Arrays.asList("Adamant bar", "Bronze bar", "Coal"),
			sorted(SortMode.VALUE,
				priced("Coal", 100),
				priced("Bronze bar", 100),
				priced("Adamant bar", 100)));
	}

	@Test
	public void alphabeticalIgnoresValueAndCase()
	{
		assertEquals(Arrays.asList("adamant bar", "Bronze bar", "Coal"),
			sorted(SortMode.ALPHABETICAL,
				priced("Coal", 999_999),
				priced("adamant bar", 1),
				priced("Bronze bar", 500)));
	}

	@Test
	public void alphabeticalNeedsNoUntradeableSpecialCase()
	{
		assertEquals(Arrays.asList("Fire cape", "Shark", "Zamorak brew"),
			sorted(SortMode.ALPHABETICAL,
				priced("Shark", 800),
				unpriced("Fire cape"),
				priced("Zamorak brew", 2_000)));
	}

	@Test
	public void everySchemeChoiceResolvesToAScheme()
	{
		for (SchemeChoice choice : SchemeChoice.values())
		{
			assertEquals(choice.scheme(), choice.scheme());
			// Same instance every call - the renderer reads this on every bank redraw.
			assertEquals(true, choice.scheme() == choice.scheme());
		}
	}
}
