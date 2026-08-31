package com.betterbank.store;

import com.betterbank.classify.Category;
import com.betterbank.classify.Scheme;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Applies a user's {@link SchemeCustomization} to a shipped scheme, producing the scheme the
 * bank is actually drawn from.
 *
 * <p>The shipped scheme is never mutated - it is rebuilt from it every time - which is what
 * makes "reset to shipped state" a matter of deleting stored edits rather than reconstructing
 * anything.
 *
 * <p>Rules are carried across untouched. A rule proposing a category the user hid simply
 * finds no such category and is skipped, because rules propose and the scheme disposes.
 */
public final class SchemeCustomizer
{
	private SchemeCustomizer()
	{
	}

	public static Scheme apply(Scheme base, SchemeCustomization customization)
	{
		if (customization == null || customization.isEmpty())
		{
			return base;
		}

		final Map<String, CategoryEdit> edits = customization.byId();
		final List<Ordered> out = new ArrayList<>();

		int position = 0;
		for (Category category : base.categories())
		{
			// Uncategorized is re-appended by the Scheme constructor and is not the user's to
			// hide - it is what tells them the data missed something.
			if (Scheme.UNCATEGORIZED_ID.equals(category.id()))
			{
				continue;
			}
			final CategoryEdit edit = edits.get(category.id());
			if (edit != null && edit.hidden())
			{
				continue;
			}
			out.add(new Ordered(applyEdit(category, edit),
				edit != null && edit.order() != null ? edit.order() : position));
			position++;
		}

		// Categories the user created have no shipped counterpart; they carry no rules and are
		// reachable only through an explicit assignment.
		for (CategoryEdit edit : customization.categories())
		{
			if (!edit.added() || edit.id() == null || edit.hidden()
				|| base.hasCategory(edit.id()))
			{
				continue;
			}
			final String name = edit.name() == null ? edit.id() : edit.name();
			out.add(new Ordered(new Category(edit.id(), name, edit.colourRgb()),
				edit.order() != null ? edit.order() : position++));
		}

		out.sort(Comparator.comparingInt(o -> o.order));

		final List<Category> categories = new ArrayList<>(out.size());
		for (Ordered o : out)
		{
			categories.add(o.category);
		}
		return new Scheme(base.id(), base.name(), categories, base.rules());
	}

	private static Category applyEdit(Category category, CategoryEdit edit)
	{
		if (edit == null)
		{
			return category;
		}
		final String name = edit.name() == null ? category.name() : edit.name();
		final Integer colour = edit.colourRgb() == null ? category.colourRgb() : edit.colourRgb();
		return new Category(category.id(), name, colour);
	}

	private static final class Ordered
	{
		final Category category;
		final int order;

		Ordered(Category category, int order)
		{
			this.category = category;
			this.order = order;
		}
	}
}
