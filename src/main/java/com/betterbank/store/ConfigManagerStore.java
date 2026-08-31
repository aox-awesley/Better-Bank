package com.betterbank.store;

import com.betterbank.BetterBankConfig;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.client.config.ConfigManager;

/**
 * {@link ConfigStore} backed by RuneLite's {@code ConfigManager}.
 *
 * <p>Writes land in the user's profile, so customization follows the account and survives a
 * restart. This is the only class in the store package that knows about the client.
 */
@Singleton
public class ConfigManagerStore implements ConfigStore
{
	private final ConfigManager configManager;

	@Inject
	ConfigManagerStore(ConfigManager configManager)
	{
		this.configManager = configManager;
	}

	@Override
	public String get(String key)
	{
		return configManager.getConfiguration(BetterBankConfig.GROUP, key);
	}

	@Override
	public void set(String key, String value)
	{
		configManager.setConfiguration(BetterBankConfig.GROUP, key, value);
	}

	@Override
	public void unset(String key)
	{
		configManager.unsetConfiguration(BetterBankConfig.GROUP, key);
	}

	@Override
	public List<String> keys(String prefix)
	{
		final String qualified = BetterBankConfig.GROUP + "." + prefix;
		final List<String> out = new ArrayList<>();
		for (String key : configManager.getConfigurationKeys(qualified))
		{
			// getConfigurationKeys returns group-qualified keys; callers want the bare key.
			out.add(key.substring(BetterBankConfig.GROUP.length() + 1));
		}
		return out;
	}
}
