package com.betterbank.store;

/**
 * One user edit to a category within a scheme (SPEC §6: add, remove, rename, reorder,
 * recolour).
 *
 * <p>Every field is nullable and means "no opinion", so an edit that only renames a category
 * does not also freeze its position or colour. That is what lets a shipped scheme keep
 * evolving underneath a user's edits.
 */
public final class CategoryEdit
{
	private String id;
	private String name;
	private Integer order;
	private Boolean hidden;
	private Integer colourRgb;
	/** True for a category the user created, which has no shipped counterpart. */
	private Boolean added;

	CategoryEdit()
	{
	}

	public CategoryEdit(String id)
	{
		this.id = id;
	}

	public String id()
	{
		return id;
	}

	public String name()
	{
		return name;
	}

	public CategoryEdit name(String v)
	{
		this.name = v;
		return this;
	}

	public Integer order()
	{
		return order;
	}

	public CategoryEdit order(Integer v)
	{
		this.order = v;
		return this;
	}

	public boolean hidden()
	{
		return Boolean.TRUE.equals(hidden);
	}

	public CategoryEdit hidden(boolean v)
	{
		this.hidden = v;
		return this;
	}

	public Integer colourRgb()
	{
		return colourRgb;
	}

	public CategoryEdit colourRgb(Integer v)
	{
		this.colourRgb = v;
		return this;
	}

	public boolean added()
	{
		return Boolean.TRUE.equals(added);
	}

	public CategoryEdit added(boolean v)
	{
		this.added = v;
		return this;
	}
}
