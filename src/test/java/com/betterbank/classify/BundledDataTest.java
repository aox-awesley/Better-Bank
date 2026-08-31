package com.betterbank.classify;

import com.google.gson.Gson;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertTrue;

/**
 * Integrity checks over the bundled table itself. These guard the data, not the code - the
 * table is going to be edited by hand for a long time, and a bad row should fail the build
 * rather than quietly misfile an item in someone's bank.
 */
public class BundledDataTest
{
	private AttributeTable table;

	@Before
	public void setUp() throws IOException
	{
		table = AttributeTable.bundled(new Gson());
	}

	@Test
	public void everyItemHasAName()
	{
		final List<Integer> bad = new ArrayList<>();
		for (int itemId : table.itemIds())
		{
			final String name = table.get(itemId).name();
			if (name == null || name.trim().isEmpty())
			{
				bad.add(itemId);
			}
		}
		assertTrue("items missing a name: " + bad, bad.isEmpty());
	}

	@Test
	public void itemIdsArePositive()
	{
		final List<Integer> bad = new ArrayList<>();
		for (int itemId : table.itemIds())
		{
			// Cannonball is item id 2; nothing legitimate is negative.
			if (itemId < 0)
			{
				bad.add(itemId);
			}
		}
		assertTrue("negative item ids: " + bad, bad.isEmpty());
	}

	@Test
	public void everyItemClassifiesInEveryBuiltInScheme()
	{
		// Not an assertion that every answer is *good* - only that nothing throws and every
		// item resolves to a category the scheme declares.
		final Classifier classifier = new Classifier(table, Assignments.none());
		for (Scheme scheme : BuiltInSchemes.all())
		{
			for (int itemId : table.itemIds())
			{
				final Category category = classifier.classify(scheme, itemId);
				assertTrue(scheme.id() + " returned undeclared category " + category.id()
					+ " for item " + itemId, scheme.hasCategory(category.id()));
			}
		}
	}

	@Test
	public void mostItemsAreClassifiedInBothSchemes()
	{
		// Honest about obscurities, but the table exists to cover the common case: if a
		// change pushes a lot of items into Uncategorized, that is a regression worth failing.
		for (Scheme scheme : BuiltInSchemes.all())
		{
			final Classifier classifier = new Classifier(table, Assignments.none());
			final List<String> uncategorized = new ArrayList<>();
			for (int itemId : table.itemIds())
			{
				if (Scheme.UNCATEGORIZED_ID.equals(classifier.classify(scheme, itemId).id()))
				{
					uncategorized.add(table.get(itemId).name());
				}
			}
			final double ratio = uncategorized.size() / (double) table.size();
			assertTrue(scheme.id() + " leaves " + uncategorized.size() + "/" + table.size()
				+ " uncategorized: " + uncategorized, ratio < 0.05);
		}
	}

	@Test
	public void consumablesAreMarkedFinished()
	{
		final List<String> bad = new ArrayList<>();
		for (int itemId : table.itemIds())
		{
			final ItemAttributes a = table.get(itemId);
			if (a.consumable() != ConsumableClass.NONE && a.material() != MaterialStage.FINISHED)
			{
				bad.add(a.name());
			}
		}
		assertTrue("consumables should be FINISHED products: " + bad, bad.isEmpty());
	}

	@Test
	public void untradeablesAreNotMarkedAsMarketResources()
	{
		final List<String> bad = new ArrayList<>();
		for (int itemId : table.itemIds())
		{
			final ItemAttributes a = table.get(itemId);
			if (!a.tradeable() && a.material() == MaterialStage.RAW)
			{
				bad.add(a.name());
			}
		}
		assertTrue("untradeable raw materials look like a data mistake: " + bad, bad.isEmpty());
	}
}
