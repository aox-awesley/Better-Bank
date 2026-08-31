package com.betterbank.store;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Every category edit a user has made to one scheme. Serialized as one JSON value. */
public final class SchemeCustomization
{
	private List<CategoryEdit> categories = new ArrayList<>();

	public List<CategoryEdit> categories()
	{
		if (categories == null)
		{
			categories = new ArrayList<>();
		}
		return categories;
	}

	public boolean isEmpty()
	{
		return categories().isEmpty();
	}

	/** @return the edit for this category id, creating one if absent. */
	public CategoryEdit edit(String categoryId)
	{
		for (CategoryEdit edit : categories())
		{
			if (categoryId.equals(edit.id()))
			{
				return edit;
			}
		}
		final CategoryEdit created = new CategoryEdit(categoryId);
		categories().add(created);
		return created;
	}

	/** @return edits keyed by category id, in stored order. */
	public Map<String, CategoryEdit> byId()
	{
		final Map<String, CategoryEdit> out = new LinkedHashMap<>();
		for (CategoryEdit edit : categories())
		{
			if (edit.id() != null)
			{
				out.put(edit.id(), edit);
			}
		}
		return out;
	}
}
