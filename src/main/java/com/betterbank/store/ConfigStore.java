package com.betterbank.store;

import java.util.List;

/**
 * Narrow view of persistent key/value storage.
 *
 * <p>The seam that keeps {@link OverrideStore} unit-testable: the real implementation writes
 * through {@code ConfigManager}, tests use an in-memory fake. Nothing behind this interface
 * touches a game or client type.
 */
public interface ConfigStore
{
	/** @return the stored value, or null. */
	String get(String key);

	void set(String key, String value);

	void unset(String key);

	/** @return every key in this store beginning with {@code prefix}. */
	List<String> keys(String prefix);
}
