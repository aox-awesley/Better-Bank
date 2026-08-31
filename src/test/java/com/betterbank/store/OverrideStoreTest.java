package com.betterbank.store;

import com.google.gson.Gson;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class OverrideStoreTest
{
	private FakeConfigStore config;
	private OverrideStore store;

	@Before
	public void setUp()
	{
		config = new FakeConfigStore();
		store = new OverrideStore(config, new Gson());
	}

	// ---- per-scheme independence ---------------------------------------------------

	@Test
	public void editingOneSchemeLeavesAnotherUntouched()
	{
		store.assign("merchant", 385, "resources");
		store.assign("skiller", 385, "consumables");

		assertEquals("resources", store.assignedCategory("merchant", 385));
		assertEquals("consumables", store.assignedCategory("skiller", 385));
	}

	@Test
	public void switchingAwayAndBackKeepsTheFirstSchemesEdits()
	{
		// The exact round trip that a shared override set would break: customise Merchant,
		// switch to Skiller and customise that, switch back.
		store.assign("merchant", 385, "resources");
		store.assign("merchant", 995, "resources");

		store.assign("skiller", 385, "mining");
		store.clearAssignment("skiller", 385);
		store.assign("skiller", 1127, "crafting");

		assertEquals("resources", store.assignedCategory("merchant", 385));
		assertEquals("resources", store.assignedCategory("merchant", 995));
		assertEquals(2, store.assignments("merchant").size());
		assertEquals(1, store.assignments("skiller").size());
	}

	@Test
	public void independenceSurvivesARestart()
	{
		store.assign("merchant", 385, "resources");
		store.assign("skiller", 385, "cooking");

		final OverrideStore reopened = new OverrideStore(config.reopen(), new Gson());
		assertEquals("resources", reopened.assignedCategory("merchant", 385));
		assertEquals("cooking", reopened.assignedCategory("skiller", 385));
	}

	@Test
	public void everyStoredKeyCarriesItsSchemeId()
	{
		// This is why independence holds: there is no shared bucket to leak through.
		store.assign("merchant", 385, "resources");
		store.saveCustomization("skiller", customizationRenaming("mining", "Rocks"));

		for (Map.Entry<String, String> e : config.raw().entrySet())
		{
			if (OverrideStore.KEY_VERSION.equals(e.getKey()))
			{
				continue;
			}
			assertTrue("key without a scheme id: " + e.getKey(),
				e.getKey().contains("merchant") || e.getKey().contains("skiller"));
		}
	}

	@Test
	public void resettingOneSchemeLeavesTheOthersAlone()
	{
		store.assign("merchant", 385, "resources");
		store.assign("skiller", 385, "cooking");
		store.saveCustomization("merchant", customizationRenaming("armour", "Armor"));

		store.reset("merchant");

		assertNull(store.assignedCategory("merchant", 385));
		assertTrue(store.customization("merchant").isEmpty());
		assertEquals("skiller keeps its edits", "cooking", store.assignedCategory("skiller", 385));
	}

	// ---- assignments ---------------------------------------------------------------

	@Test
	public void assignmentsRoundTrip()
	{
		store.assign("merchant", 385, "consumables");
		store.assign("merchant", -1, "currency");
		store.assign("merchant", Integer.MAX_VALUE, "resources");

		final OverrideStore reopened = new OverrideStore(config.reopen(), new Gson());
		assertEquals("consumables", reopened.assignedCategory("merchant", 385));
		assertEquals("currency", reopened.assignedCategory("merchant", -1));
		assertEquals("resources", reopened.assignedCategory("merchant", Integer.MAX_VALUE));
	}

	@Test
	public void clearingAnAssignmentRemovesIt()
	{
		store.assign("merchant", 385, "consumables");
		store.clearAssignment("merchant", 385);
		assertNull(store.assignedCategory("merchant", 385));

		final OverrideStore reopened = new OverrideStore(config.reopen(), new Gson());
		assertNull(reopened.assignedCategory("merchant", 385));
	}

	@Test
	public void assignmentsAreShardedSoNoSingleValueGrowsUnbounded()
	{
		for (int itemId = 0; itemId < 2_000; itemId++)
		{
			store.assign("merchant", itemId, "resources");
		}
		assertEquals(2_000, store.assignments("merchant").size());

		int populated = 0;
		int longest = 0;
		for (int shard = 0; shard < OverrideStore.ASSIGNMENT_SHARDS; shard++)
		{
			final String value = config.get(OverrideStore.assignmentKey("merchant", shard));
			if (value != null)
			{
				populated++;
				longest = Math.max(longest, value.length());
			}
		}
		assertEquals("every shard should carry a share", OverrideStore.ASSIGNMENT_SHARDS, populated);

		// A single key holds roughly 1/8 of the data rather than all of it.
		final int oneKeyWouldBe = 2_000 * "1999=resources;".length();
		assertTrue("shard of " + longest + " should be far below " + oneKeyWouldBe,
			longest < oneKeyWouldBe / 4);
	}

	@Test
	public void writingOneAssignmentTouchesOneShard()
	{
		for (int itemId = 0; itemId < 200; itemId++)
		{
			store.assign("merchant", itemId, "resources");
		}
		final int before = config.writes();
		store.assign("merchant", 8_000, "currency");
		// One shard value plus nothing else; the version stamp is already written.
		assertEquals(1, config.writes() - before);
	}

	@Test
	public void malformedStoredDataIsSkippedNotFatal()
	{
		config.set(OverrideStore.assignmentKey("merchant", 1), "notanumber=x;385=consumables;=y;9");
		final OverrideStore reopened = new OverrideStore(config, new Gson());
		assertEquals("consumables", reopened.assignedCategory("merchant", 385));
	}

	// ---- schema version ------------------------------------------------------------

	@Test
	public void versionIsStampedOnTheFirstWrite()
	{
		assertNull(config.get(OverrideStore.KEY_VERSION));
		store.assign("merchant", 385, "resources");
		assertEquals(Integer.toString(OverrideStore.SCHEMA_VERSION),
			config.get(OverrideStore.KEY_VERSION));
	}

	@Test
	public void aNewerSchemaIsNeitherReadNorOverwritten()
	{
		// A downgrade must not silently destroy edits made by a later build.
		config.set(OverrideStore.KEY_VERSION, "999");
		config.set(OverrideStore.assignmentKey("merchant", 1), "385=somethingfuturistic");

		final OverrideStore newer = new OverrideStore(config, new Gson());
		assertFalse(newer.isCompatible());
		assertNull("must not parse unknown data", newer.assignedCategory("merchant", 385));

		newer.assign("merchant", 385, "resources");
		assertEquals("must not overwrite it either", "385=somethingfuturistic",
			config.get(OverrideStore.assignmentKey("merchant", 1)));
	}

	@Test
	public void unreadableVersionIsTreatedAsIncompatible()
	{
		config.set(OverrideStore.KEY_VERSION, "banana");
		assertFalse(new OverrideStore(config, new Gson()).isCompatible());
	}

	// ---- category customization ----------------------------------------------------

	@Test
	public void categoryCustomizationRoundTrips()
	{
		final SchemeCustomization custom = new SchemeCustomization();
		custom.edit("armour").name("Armor").order(0);
		custom.edit("weapons").hidden(true);
		custom.edit("my-stuff").added(true).name("My stuff").order(9);
		store.saveCustomization("merchant", custom);

		final SchemeCustomization loaded =
			new OverrideStore(config.reopen(), new Gson()).customization("merchant");
		assertEquals("Armor", loaded.byId().get("armour").name());
		assertTrue(loaded.byId().get("weapons").hidden());
		assertTrue(loaded.byId().get("my-stuff").added());
		assertEquals(Integer.valueOf(9), loaded.byId().get("my-stuff").order());
	}

	@Test
	public void unsetFieldsStayUnsetSoShippedValuesKeepShowingThrough()
	{
		final SchemeCustomization custom = new SchemeCustomization();
		custom.edit("armour").name("Armor");
		store.saveCustomization("merchant", custom);

		final CategoryEdit loaded =
			new OverrideStore(config.reopen(), new Gson()).customization("merchant").byId().get("armour");
		assertEquals("Armor", loaded.name());
		assertNull("order was never set", loaded.order());
		assertNull("colour was never set", loaded.colourRgb());
		assertFalse(loaded.hidden());
	}

	@Test
	public void isCustomizedReflectsBothKindsOfEdit()
	{
		assertFalse(store.isCustomized("merchant"));
		store.assign("merchant", 385, "resources");
		assertTrue(store.isCustomized("merchant"));

		store.reset("merchant");
		assertFalse(store.isCustomized("merchant"));

		store.saveCustomization("merchant", customizationRenaming("armour", "Armor"));
		assertTrue(store.isCustomized("merchant"));
	}

	@Test
	public void emptyCustomizationIsRemovedRatherThanStoredAsNoise()
	{
		store.saveCustomization("merchant", customizationRenaming("armour", "Armor"));
		assertNotNull(config.get(OverrideStore.CATEGORIES_PREFIX + "merchant"));
		store.saveCustomization("merchant", new SchemeCustomization());
		assertNull(config.get(OverrideStore.CATEGORIES_PREFIX + "merchant"));
	}

	private static SchemeCustomization customizationRenaming(String categoryId, String name)
	{
		final SchemeCustomization custom = new SchemeCustomization();
		custom.edit(categoryId).name(name);
		return custom;
	}
}
