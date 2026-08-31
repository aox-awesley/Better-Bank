package com.betterbank.view;

/**
 * Abbreviates coin values the way the game does - 182k, 1.2m - so a category header stays
 * readable when a bucket holds a few hundred million.
 */
public final class ValueFormat
{
	private static final long THOUSAND = 1_000L;
	private static final long MILLION = 1_000_000L;
	private static final long BILLION = 1_000_000_000L;

	private ValueFormat()
	{
	}

	/**
	 * @return an abbreviated value, e.g. {@code 0}, {@code 9,999}, {@code 182k}, {@code 1.2m}.
	 * Small values stay exact because rounding them to "0k" would read as broken.
	 */
	public static String abbreviate(long value)
	{
		if (value < 0)
		{
			return "-" + abbreviate(-value);
		}
		if (value < 10 * THOUSAND)
		{
			return withCommas(value);
		}
		if (value < MILLION)
		{
			return (value / THOUSAND) + "k";
		}
		if (value < BILLION)
		{
			return trimTrailingZero(value, MILLION) + "m";
		}
		return trimTrailingZero(value, BILLION) + "b";
	}

	/**
	 * One decimal place, but only when it carries information: 1.2m, and 15m rather than
	 * 15.0m.
	 */
	private static String trimTrailingZero(long value, long unit)
	{
		final long whole = value / unit;
		final long tenths = (value % unit) * 10 / unit;
		return tenths == 0 ? Long.toString(whole) : whole + "." + tenths;
	}

	private static String withCommas(long value)
	{
		final String digits = Long.toString(value);
		final StringBuilder out = new StringBuilder(digits.length() + 3);
		for (int i = 0; i < digits.length(); i++)
		{
			if (i > 0 && (digits.length() - i) % 3 == 0)
			{
				out.append(',');
			}
			out.append(digits.charAt(i));
		}
		return out.toString();
	}

	/** Full value with thousands separators, for tooltips where precision is the point. */
	public static String exact(long value)
	{
		return value < 0 ? "-" + withCommas(-value) : withCommas(value);
	}
}
