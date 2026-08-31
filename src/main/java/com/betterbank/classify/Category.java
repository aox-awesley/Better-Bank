package com.betterbank.classify;

import java.util.Objects;

/**
 * One category within a scheme (SPEC §3). Identity is the {@code id}, which is stable and
 * persisted; {@code name} is display text the user may rename freely.
 */
public final class Category
{
	private final String id;
	private final String name;
	private final Integer colourRgb;

	public Category(String id, String name)
	{
		this(id, name, null);
	}

	public Category(String id, String name, Integer colourRgb)
	{
		this.id = Objects.requireNonNull(id, "id");
		this.name = Objects.requireNonNull(name, "name");
		this.colourRgb = colourRgb;
	}

	public String id()
	{
		return id;
	}

	public String name()
	{
		return name;
	}

	/** Optional display colour as 0xRRGGBB, or null to use the default. */
	public Integer colourRgb()
	{
		return colourRgb;
	}

	@Override
	public boolean equals(Object o)
	{
		return o instanceof Category && id.equals(((Category) o).id);
	}

	@Override
	public int hashCode()
	{
		return id.hashCode();
	}

	@Override
	public String toString()
	{
		return "Category(" + id + ")";
	}
}
