package com.betterbank.store;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** In-memory {@link ConfigStore}, so the store is testable with no client. */
public final class FakeConfigStore implements ConfigStore
{
	private final Map<String, String> values = new LinkedHashMap<>();
	private int writes;

	@Override
	public String get(String key)
	{
		return values.get(key);
	}

	@Override
	public void set(String key, String value)
	{
		writes++;
		values.put(key, value);
	}

	@Override
	public void unset(String key)
	{
		values.remove(key);
	}

	@Override
	public List<String> keys(String prefix)
	{
		final List<String> out = new ArrayList<>();
		for (String key : values.keySet())
		{
			if (key.startsWith(prefix))
			{
				out.add(key);
			}
		}
		return out;
	}

	public Map<String, String> raw()
	{
		return values;
	}

	public int writes()
	{
		return writes;
	}

	/** A fresh store over the same data - as if the client had restarted. */
	public FakeConfigStore reopen()
	{
		final FakeConfigStore next = new FakeConfigStore();
		next.values.putAll(values);
		return next;
	}
}
