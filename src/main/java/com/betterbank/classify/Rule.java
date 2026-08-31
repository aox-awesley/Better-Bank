package com.betterbank.classify;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;

/**
 * One matching rule belonging to a scheme.
 *
 * <p>A rule <i>proposes</i> categories; the scheme disposes. {@link Classifier} takes the
 * first proposal naming a category the scheme actually declares, so a user who deletes a
 * category does not break the rules that referenced it, and one rule set can be shared
 * between schemes declaring different subsets.
 */
@FunctionalInterface
public interface Rule
{
	/**
	 * @return proposed category ids, most preferred first; empty if this rule does not apply.
	 */
	List<String> proposals(ItemAttributes attributes);

	/** A rule proposing a fixed category whenever {@code test} passes. */
	static Rule when(Predicate<ItemAttributes> test, String categoryId)
	{
		final List<String> single = Collections.singletonList(categoryId);
		return a -> test.test(a) ? single : Collections.emptyList();
	}

	/**
	 * A rule proposing one category per skill the item is relevant to, in
	 * {@link ItemAttributes#skills()} order - so a skill-oriented scheme files raw shark
	 * ({@code FISHING, COOKING}) under Fishing, but still catches an item whose primary
	 * skill it has no category for.
	 */
	static Rule bySkill()
	{
		return a ->
		{
			if (a.skills().isEmpty())
			{
				return Collections.emptyList();
			}
			final List<String> out = new ArrayList<>(a.skills().size());
			for (SkillType skill : a.skills())
			{
				out.add(skill.categoryId());
			}
			return out;
		};
	}
}
