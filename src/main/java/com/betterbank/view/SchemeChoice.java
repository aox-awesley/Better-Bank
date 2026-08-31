package com.betterbank.view;

import com.betterbank.classify.BuiltInSchemes;
import com.betterbank.classify.Scheme;

/**
 * The schemes selectable in config. M4 ships the two SPEC §8 names - deliberately the two
 * most different lenses, so the attribute&rarr;category mapping is proved to generalise
 * before the other five are added.
 */
public enum SchemeChoice
{
	SKILLER("Skiller")
		{
			@Override
			public Scheme scheme()
			{
				return SKILLER_SCHEME;
			}
		},
	MERCHANT("Merchant")
		{
			@Override
			public Scheme scheme()
			{
				return MERCHANT_SCHEME;
			}
		};

	// Built once: a Scheme is immutable, and rebuilding it on every bank redraw would be
	// pointless allocation on the client thread.
	private static final Scheme SKILLER_SCHEME = BuiltInSchemes.skiller();
	private static final Scheme MERCHANT_SCHEME = BuiltInSchemes.merchant();

	private final String label;

	SchemeChoice(String label)
	{
		this.label = label;
	}

	public abstract Scheme scheme();

	@Override
	public String toString()
	{
		return label;
	}
}
