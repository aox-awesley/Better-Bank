package com.betterbank.view;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/** Header abbreviation, in the game's own style. */
public class ValueFormatTest
{
	@Test
	public void smallValuesStayExactSoTheyNeverReadAsZero()
	{
		assertEquals("0", ValueFormat.abbreviate(0));
		assertEquals("1", ValueFormat.abbreviate(1));
		assertEquals("999", ValueFormat.abbreviate(999));
		assertEquals("9,999", ValueFormat.abbreviate(9_999));
	}

	@Test
	public void thousandsAbbreviateWithoutADecimal()
	{
		assertEquals("10k", ValueFormat.abbreviate(10_000));
		assertEquals("182k", ValueFormat.abbreviate(182_400));
		assertEquals("999k", ValueFormat.abbreviate(999_999));
	}

	@Test
	public void millionsKeepOneDecimalOnlyWhenItSaysSomething()
	{
		assertEquals("1.2m", ValueFormat.abbreviate(1_234_567));
		assertEquals("1m", ValueFormat.abbreviate(1_000_000));
		assertEquals("15m", ValueFormat.abbreviate(15_000_000));
		assertEquals("999.9m", ValueFormat.abbreviate(999_999_999));
	}

	@Test
	public void billionsAbbreviateToo()
	{
		assertEquals("2.1b", ValueFormat.abbreviate(2_147_483_647L));
		assertEquals("1b", ValueFormat.abbreviate(1_000_000_000L));
	}

	@Test
	public void negativesKeepTheirSign()
	{
		assertEquals("-182k", ValueFormat.abbreviate(-182_400));
	}

	@Test
	public void exactAlwaysUsesThousandsSeparators()
	{
		assertEquals("0", ValueFormat.exact(0));
		assertEquals("800", ValueFormat.exact(800));
		assertEquals("1,234,567", ValueFormat.exact(1_234_567));
	}

	@Test
	public void everySchemeHasADistinctIcon()
	{
		final java.util.Set<Integer> seen = new java.util.HashSet<>();
		for (SchemeChoice choice : SchemeChoice.values())
		{
			assertEquals("sprite id must be set for " + choice, true, choice.spriteId() > 0);
			assertEquals("duplicate icon for " + choice, true, seen.add(choice.spriteId()));
		}
	}
}
