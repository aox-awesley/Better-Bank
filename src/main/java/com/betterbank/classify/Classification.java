package com.betterbank.classify;

/**
 * The result of classifying one item, including <i>why</i> it landed where it did.
 *
 * <p>The source matters beyond debugging: the UI needs to show a user whether an item is
 * where it is because they put it there or because a rule did, and "why is this here?" is
 * the question an Uncategorized bucket exists to provoke.
 */
public final class Classification
{
	public enum Source
	{
		/** An explicit user override. */
		ASSIGNMENT,
		/** One of the active scheme's own rules. */
		SCHEME_RULE,
		/** The scheme-agnostic attribute fallback. */
		INFERENCE,
		/** Nothing matched, or the item is not in the attribute table. */
		FALLBACK
	}

	private final Category category;
	private final Source source;

	Classification(Category category, Source source)
	{
		this.category = category;
		this.source = source;
	}

	public Category category()
	{
		return category;
	}

	public Source source()
	{
		return source;
	}

	@Override
	public String toString()
	{
		return "Classification(" + category.id() + " via " + source + ")";
	}
}
