package com.betterbank.classify;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class SchemeTest
{
	@Test
	public void everySchemeHasUncategorizedAndItIsLast()
	{
		for (Scheme scheme : BuiltInSchemes.all())
		{
			final List<Category> categories = scheme.categories();
			assertEquals("Uncategorized must be last in " + scheme.id(),
				Scheme.UNCATEGORIZED_ID, categories.get(categories.size() - 1).id());
			assertNotNull(scheme.uncategorized());
		}
	}

	@Test
	public void uncategorizedIsAddedEvenWhenNotDeclared()
	{
		final Scheme scheme = new Scheme("s", "S",
			Collections.singletonList(new Category("a", "A")), Collections.emptyList());
		assertTrue(scheme.hasCategory(Scheme.UNCATEGORIZED_ID));
		assertEquals(2, scheme.categories().size());
	}

	@Test
	public void declaringUncategorizedDoesNotDuplicateIt()
	{
		final Scheme scheme = new Scheme("s", "S",
			Arrays.asList(new Category(Scheme.UNCATEGORIZED_ID, "Mine"), new Category("a", "A")),
			Collections.emptyList());
		assertEquals(2, scheme.categories().size());
		assertEquals(Scheme.UNCATEGORIZED_ID, scheme.categories().get(1).id());
	}

	@Test
	public void duplicateCategoryIsRejected()
	{
		try
		{
			new Scheme("s", "S",
				Arrays.asList(new Category("a", "A"), new Category("a", "Again")),
				Collections.emptyList());
			fail("expected duplicate category ids to be rejected");
		}
		catch (IllegalArgumentException expected)
		{
			assertTrue(expected.getMessage().contains("duplicate"));
		}
	}

	@Test
	public void missingCategoryReadsAsNullNotAnException()
	{
		assertNull(BuiltInSchemes.merchant().category("nope"));
	}

	@Test
	public void categoriesAndRulesAreUnmodifiable()
	{
		try
		{
			BuiltInSchemes.skiller().categories().add(new Category("x", "X"));
			fail("scheme categories must not be mutable by callers");
		}
		catch (UnsupportedOperationException expected)
		{
			// expected
		}
	}

	@Test
	public void builtInSchemeIdsAreUniqueAndResolvable()
	{
		final Set<String> ids = new HashSet<>();
		for (Scheme scheme : BuiltInSchemes.all())
		{
			assertTrue("duplicate scheme id " + scheme.id(), ids.add(scheme.id()));
			assertEquals(scheme.id(), BuiltInSchemes.byId(scheme.id()).id());
		}
		assertNull(BuiltInSchemes.byId("nope"));
	}

	@Test
	public void everyRuleProposalNamesADeclaredCategory()
	{
		// A rule proposing a category the scheme never declares is dead weight, and the
		// classifier would silently skip it. Catch that here instead.
		for (Scheme scheme : BuiltInSchemes.all())
		{
			final List<ItemAttributes> probes = probeItems();
			for (Rule rule : scheme.rules())
			{
				for (ItemAttributes probe : probes)
				{
					for (String proposed : rule.proposals(probe))
					{
						// bySkill legitimately proposes skills a scheme may not carry; every
						// fixed proposal must resolve.
						if (probe.skills().isEmpty() || !isSkillId(proposed))
						{
							assertTrue(scheme.id() + " proposes undeclared category " + proposed,
								scheme.hasCategory(proposed));
						}
					}
				}
			}
		}
	}

	private static boolean isSkillId(String id)
	{
		for (SkillType skill : SkillType.values())
		{
			if (skill.categoryId().equals(id))
			{
				return true;
			}
		}
		return false;
	}

	/** One attribute shape per rule branch the built-in schemes test for. */
	private static List<ItemAttributes> probeItems()
	{
		final List<ItemAttributes> probes = new ArrayList<>();
		probes.add(ItemAttributes.builder("currency").currency(true).build());
		probes.add(ItemAttributes.builder("rune").rune(true).build());
		probes.add(ItemAttributes.builder("tool").tool(true).build());
		probes.add(ItemAttributes.builder("weapon").slot(EquipmentSlot.WEAPON).build());
		probes.add(ItemAttributes.builder("ammo").slot(EquipmentSlot.AMMUNITION).build());
		probes.add(ItemAttributes.builder("armour").slot(EquipmentSlot.BODY).build());
		probes.add(ItemAttributes.builder("food").consumable(ConsumableClass.FOOD).build());
		probes.add(ItemAttributes.builder("potion").consumable(ConsumableClass.POTION).build());
		probes.add(ItemAttributes.builder("raw").material(MaterialStage.RAW).build());
		probes.add(ItemAttributes.builder("intermediate").material(MaterialStage.INTERMEDIATE).build());
		probes.add(ItemAttributes.builder("skilled").skills(SkillType.MINING, SkillType.SMITHING).build());
		return probes;
	}
}
