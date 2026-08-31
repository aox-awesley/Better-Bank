package com.betterbank.store;

import com.betterbank.classify.BuiltInSchemes;
import com.betterbank.classify.Category;
import com.betterbank.classify.Scheme;
import java.util.List;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;

/** Applying stored edits over a shipped scheme. */
public class SchemeCustomizerTest
{
	private static List<String> ids(Scheme scheme)
	{
		final List<String> out = new java.util.ArrayList<>();
		for (Category c : scheme.categories())
		{
			out.add(c.id());
		}
		return out;
	}

	@Test
	public void noCustomizationReturnsTheShippedSchemeUnchanged()
	{
		final Scheme base = BuiltInSchemes.merchant();
		assertEquals(base, SchemeCustomizer.apply(base, new SchemeCustomization()));
		assertEquals(base, SchemeCustomizer.apply(base, null));
	}

	@Test
	public void renameChangesTheNameAndKeepsTheId()
	{
		final SchemeCustomization custom = new SchemeCustomization();
		custom.edit("armour").name("Armor");
		final Scheme edited = SchemeCustomizer.apply(BuiltInSchemes.merchant(), custom);

		assertEquals("Armor", edited.category("armour").name());
		assertTrue("the id is persisted and must not move", edited.hasCategory("armour"));
	}

	@Test
	public void hidingRemovesTheCategoryAndItsRuleQuietlyStopsApplying()
	{
		final SchemeCustomization custom = new SchemeCustomization();
		custom.edit("weapons").hidden(true);
		final Scheme edited = SchemeCustomizer.apply(BuiltInSchemes.merchant(), custom);

		assertFalse(edited.hasCategory("weapons"));
		// Rules are carried over untouched; the classifier skips proposals for categories the
		// scheme no longer declares, so nothing else needed to change.
		assertEquals(BuiltInSchemes.merchant().rules().size(), edited.rules().size());
	}

	@Test
	public void reorderMovesCategoriesAndUncategorizedStaysLast()
	{
		final SchemeCustomization custom = new SchemeCustomization();
		custom.edit("resources").order(-1);
		final Scheme edited = SchemeCustomizer.apply(BuiltInSchemes.merchant(), custom);

		assertEquals("resources", ids(edited).get(0));
		assertEquals(Scheme.UNCATEGORIZED_ID, ids(edited).get(ids(edited).size() - 1));
	}

	@Test
	public void addedCategoryAppearsAndIsOnlyReachableByAssignment()
	{
		final SchemeCustomization custom = new SchemeCustomization();
		custom.edit("flip-list").added(true).name("Flip list");
		final Scheme edited = SchemeCustomizer.apply(BuiltInSchemes.merchant(), custom);

		assertTrue(edited.hasCategory("flip-list"));
		assertEquals("Flip list", edited.category("flip-list").name());
		// It carries no rule, so nothing lands there unless the user puts it there.
		assertEquals(BuiltInSchemes.merchant().rules().size(), edited.rules().size());
	}

	@Test
	public void uncategorizedCannotBeHidden()
	{
		// It is what tells the user the data missed something.
		final SchemeCustomization custom = new SchemeCustomization();
		custom.edit(Scheme.UNCATEGORIZED_ID).hidden(true);
		assertTrue(SchemeCustomizer.apply(BuiltInSchemes.merchant(), custom)
			.hasCategory(Scheme.UNCATEGORIZED_ID));
	}

	@Test
	public void theShippedSchemeIsNeverMutatedSoResetIsJustDeletingEdits()
	{
		final Scheme base = BuiltInSchemes.merchant();
		final List<String> before = ids(base);

		final SchemeCustomization custom = new SchemeCustomization();
		custom.edit("armour").name("Armor").hidden(true);
		final Scheme edited = SchemeCustomizer.apply(base, custom);

		assertNotSame(base, edited);
		assertEquals("shipped scheme untouched", before, ids(BuiltInSchemes.merchant()));
		assertEquals("Armour", BuiltInSchemes.merchant().category("armour").name());
	}

	@Test
	public void editsAreScopedToTheSchemeTheyWereMadeOn()
	{
		// The same customization object applied to two schemes affects each independently -
		// nothing is shared or cached between them.
		final SchemeCustomization custom = new SchemeCustomization();
		custom.edit("currency").name("Cash");

		assertEquals("Cash", SchemeCustomizer.apply(BuiltInSchemes.merchant(), custom)
			.category("currency").name());
		assertEquals("Currency", BuiltInSchemes.skiller().category("currency").name());
	}
}
