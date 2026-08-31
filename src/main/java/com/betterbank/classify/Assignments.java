package com.betterbank.classify;

import java.util.HashMap;
import java.util.Map;

/**
 * User overrides: "this item goes in this category, in this scheme" (SPEC §3). Always wins
 * over rules.
 *
 * <p>The persisted, ConfigManager-backed implementation is the store module and lands with
 * the customization UI. This interface is what the classifier needs, and nothing more.
 */
public interface Assignments
{
	/** @return the assigned category id, or null if the user has not overridden this item. */
	String assignedCategory(String schemeId, int itemId);

	/** An empty set of overrides. */
	static Assignments none()
	{
		return (schemeId, itemId) -> null;
	}

	/** Simple in-memory overrides, for tests and for staging edits before they are saved. */
	final class InMemory implements Assignments
	{
		private final Map<String, String> byKey = new HashMap<>();

		public InMemory put(String schemeId, int itemId, String categoryId)
		{
			byKey.put(key(schemeId, itemId), categoryId);
			return this;
		}

		public void remove(String schemeId, int itemId)
		{
			byKey.remove(key(schemeId, itemId));
		}

		@Override
		public String assignedCategory(String schemeId, int itemId)
		{
			return byKey.get(key(schemeId, itemId));
		}

		private static String key(String schemeId, int itemId)
		{
			return schemeId + ':' + itemId;
		}
	}
}
