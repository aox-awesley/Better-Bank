package com.betterbank.classify;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A named taxonomy: an ordered set of categories plus the rules that assign items to them
 * (SPEC §3).
 *
 * <p>Schemes never own item data. They are a lens over the one shared
 * {@link AttributeTable}, which is what makes an eighth scheme a small declaration rather
 * than a data-entry project.
 */
public final class Scheme
{
	/** Every scheme has this category, and it is always last. */
	public static final String UNCATEGORIZED_ID = "uncategorized";

	private final String id;
	private final String name;
	private final List<Category> categories;
	private final List<Rule> rules;
	private final Map<String, Category> byId;

	public Scheme(String id, String name, List<Category> categories, List<Rule> rules)
	{
		this.id = Objects.requireNonNull(id, "id");
		this.name = Objects.requireNonNull(name, "name");

		final List<Category> ordered = new ArrayList<>(categories);
		ordered.removeIf(c -> UNCATEGORIZED_ID.equals(c.id()));
		ordered.add(new Category(UNCATEGORIZED_ID, "Uncategorized"));

		final Map<String, Category> index = new LinkedHashMap<>();
		for (Category c : ordered)
		{
			if (index.put(c.id(), c) != null)
			{
				throw new IllegalArgumentException("Scheme " + id + " has duplicate category " + c.id());
			}
		}

		this.categories = Collections.unmodifiableList(ordered);
		this.rules = Collections.unmodifiableList(new ArrayList<>(rules));
		this.byId = Collections.unmodifiableMap(index);
	}

	public String id()
	{
		return id;
	}

	public String name()
	{
		return name;
	}

	/** Categories in display order. Always ends with Uncategorized. */
	public List<Category> categories()
	{
		return categories;
	}

	/** Rules in precedence order - the first applicable proposal wins. */
	public List<Rule> rules()
	{
		return rules;
	}

	public boolean hasCategory(String categoryId)
	{
		return byId.containsKey(categoryId);
	}

	/** @return the category, or null if this scheme does not declare it. */
	public Category category(String categoryId)
	{
		return byId.get(categoryId);
	}

	public Category uncategorized()
	{
		return byId.get(UNCATEGORIZED_ID);
	}

	@Override
	public String toString()
	{
		return "Scheme(" + id + ")";
	}
}
