package com.betterbank.classify;

import java.util.Objects;

/**
 * Resolves {@code (scheme, itemId)} to a category (SPEC §7 module 2).
 *
 * <p>Pure Java: no game API, no I/O, no client. Everything it needs arrives through the
 * constructor, so the whole of Better Bank's classification behaviour is testable with no
 * client running.
 *
 * <p>Resolution order is SPEC §3's, exactly:
 * user assignment &rarr; scheme rules &rarr; attribute inference &rarr; Uncategorized.
 */
public final class Classifier
{
	private final AttributeTable table;
	private final Assignments assignments;

	public Classifier(AttributeTable table, Assignments assignments)
	{
		this.table = Objects.requireNonNull(table, "table");
		this.assignments = Objects.requireNonNull(assignments, "assignments");
	}

	/** Convenience for callers that do not care which stage decided. */
	public Category classify(Scheme scheme, int itemId)
	{
		return explain(scheme, itemId).category();
	}

	/** Classifies an item and reports which stage of the resolution order decided it. */
	public Classification explain(Scheme scheme, int itemId)
	{
		Objects.requireNonNull(scheme, "scheme");

		// 1. A user assignment always wins - but only while the category still exists. If the
		// user deleted the category the override named, fall through rather than dangle.
		final String assigned = assignments.assignedCategory(scheme.id(), itemId);
		if (assigned != null && scheme.hasCategory(assigned))
		{
			return new Classification(scheme.category(assigned), Classification.Source.ASSIGNMENT);
		}

		// An item the table does not cover has no attributes to reason about. Uncategorized is
		// the honest answer, and it is what tells us the table needs another entry.
		final ItemAttributes attributes = table.get(itemId);
		if (attributes == null)
		{
			return new Classification(scheme.uncategorized(), Classification.Source.FALLBACK);
		}

		// 2. The scheme's own rules, in precedence order.
		for (Rule rule : scheme.rules())
		{
			for (String proposed : rule.proposals(attributes))
			{
				if (scheme.hasCategory(proposed))
				{
					return new Classification(scheme.category(proposed), Classification.Source.SCHEME_RULE);
				}
			}
		}

		// 3. The scheme-agnostic attribute fallback.
		for (String candidate : AttributeInference.candidates(attributes))
		{
			if (scheme.hasCategory(candidate))
			{
				return new Classification(scheme.category(candidate), Classification.Source.INFERENCE);
			}
		}

		// 4. Uncategorized, kept visible on purpose.
		return new Classification(scheme.uncategorized(), Classification.Source.FALLBACK);
	}
}
