package com.betterbank.classify;

import com.google.gson.Gson;
import java.io.IOException;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Integrity of the bundled override table. It is hand-maintained, so a bad row should fail
 * the build rather than quietly misfile an item in someone's bank.
 */
public class BundledDataTest
{
	private OverrideTable table;

	@Before
	public void setUp() throws IOException
	{
		table = OverrideTable.bundled(new Gson());
	}

	@Test
	public void tableLoadsAndParsesEveryRow()
	{
		// fromJson validates each row eagerly, so loading at all is the assertion.
		assertTrue(table.size() > 0);
	}

	@Test
	public void everyRowHasAName()
	{
		for (int itemId : table.itemIds())
		{
			final String name = table.nameFor(itemId);
			assertTrue("item " + itemId + " has no name", name != null && !name.trim().isEmpty());
		}
	}

	@Test
	public void itemIdsArePositive()
	{
		for (int itemId : table.itemIds())
		{
			assertTrue("negative item id " + itemId, itemId >= 0);
		}
	}

	@Test
	public void itIsAnExceptionListNotACatalogue()
	{
		// The whole point of deriving at runtime. Pets are excluded from the count: they are a
		// generated block, not hand-curation, and they exist because no runtime signal
		// identifies a pet (see NamePatterns and the M5 report). Guarding the hand-maintained
		// remainder is what actually catches the table drifting back into being a catalogue.
		final AttributeResolver resolver = new AttributeResolver(table, ItemMetadata.empty());
		int handMaintained = 0;
		for (int itemId : table.itemIds())
		{
			final ItemAttributes a = resolver.resolve(itemId);
			if (a != null && !a.pet())
			{
				handMaintained++;
			}
		}
		assertTrue("hand-maintained overrides have grown to " + handMaintained
			+ " rows - that is a catalogue, not an exception list", handMaintained <= 200);
	}

	@Test
	public void everyPetRowIsMarkedAsAPetAndUntradeable()
	{
		final AttributeResolver resolver = new AttributeResolver(table, ItemMetadata.empty());
		int pets = 0;
		for (int itemId : table.itemIds())
		{
			final ItemAttributes a = resolver.resolve(itemId);
			if (a != null && a.pet())
			{
				pets++;
				assertTrue("pet " + itemId + " should be untradeable", !a.tradeable());
			}
		}
		assertTrue("expected the generated pet block, found " + pets, pets > 100);
	}

	@Test
	public void everyOverrideAppliesCleanlyToEverySchemeWithNoClient()
	{
		// Overrides alone, no runtime data: nothing may throw, and every item must land in a
		// category the scheme declares.
		final AttributeResolver resolver = new AttributeResolver(table, ItemMetadata.empty());
		final Classifier classifier = new Classifier(resolver, Assignments.none());
		for (Scheme scheme : BuiltInSchemes.all())
		{
			for (int itemId : table.itemIds())
			{
				final Category category = classifier.classify(scheme, itemId);
				assertNotNull(category);
				assertTrue(scheme.id() + " returned undeclared category " + category.id(),
					scheme.hasCategory(category.id()));
			}
		}
	}

	@Test
	public void overriddenItemsAreNotLeftUncategorized()
	{
		// A row exists precisely because derivation gets the item wrong. If the row still
		// leaves it Uncategorized, the row is not doing its job.
		//
		// Treasure-trail, quest and achievement rows are exempt: Skiller and Merchant have no
		// bucket for them by design (Merchant is deliberately coarse), so Uncategorized is the
		// correct answer until the Collection log and Questing schemes land.
		final AttributeResolver resolver = new AttributeResolver(table, ItemMetadata.empty());
		final Classifier classifier = new Classifier(resolver, Assignments.none());
		final StringBuilder bad = new StringBuilder();
		final AttributeResolver exemptCheck = new AttributeResolver(table, ItemMetadata.empty());
		for (int itemId : table.itemIds())
		{
			final ItemAttributes a = exemptCheck.resolve(itemId);
			if (a != null && (a.clueReward() || a.questItem() || a.achievement()))
			{
				continue;
			}
			boolean placed = false;
			for (Scheme scheme : BuiltInSchemes.all())
			{
				if (!Scheme.UNCATEGORIZED_ID.equals(classifier.classify(scheme, itemId).id()))
				{
					placed = true;
					break;
				}
			}
			if (!placed)
			{
				bad.append(table.nameFor(itemId)).append(' ');
			}
		}
		assertTrue("overrides that classify nowhere in any scheme: " + bad, bad.length() == 0);
	}
}
