package com.betterbank.classify;

/**
 * Source of runtime item facts.
 *
 * <p>The seam that keeps the classifier testable. The plugin binds this to an implementation
 * reading {@code ItemComposition} and {@code ItemManager.getItemStats}; tests bind a fake.
 * Nothing behind this interface may reference a game API type.
 */
@FunctionalInterface
public interface ItemMetadata
{
	/** @return what the client knows about the item, or null if it knows nothing. */
	RuntimeItem lookup(int itemId);

	/** Knows nothing about anything - the classifier then runs on overrides and names alone. */
	static ItemMetadata empty()
	{
		return itemId -> null;
	}
}
