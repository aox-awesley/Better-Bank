package com.betterbank.classify;

import com.google.gson.Gson;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * The resolution order from SPEC §3, and the behaviour that order produces.
 */
public class ClassifierTest
{
	private AttributeTable table;
	private Scheme skiller;
	private Scheme merchant;

	@Before
	public void setUp() throws IOException
	{
		table = AttributeTable.bundled(new Gson());
		skiller = BuiltInSchemes.skiller();
		merchant = BuiltInSchemes.merchant();
	}

	private Classifier plain()
	{
		return new Classifier(table, Assignments.none());
	}

	private String category(Scheme scheme, int itemId)
	{
		return plain().classify(scheme, itemId).id();
	}

	// ---- resolution order ----------------------------------------------------------

	@Test
	public void userAssignmentBeatsSchemeRules()
	{
		final Assignments overrides = new Assignments.InMemory()
			.put(BuiltInSchemes.MERCHANT_ID, 385, "resources");
		final Classification result = new Classifier(table, overrides).explain(merchant, 385);

		assertEquals("resources", result.category().id());
		assertEquals(Classification.Source.ASSIGNMENT, result.source());
	}

	@Test
	public void assignmentIsScopedToItsOwnScheme()
	{
		final Assignments overrides = new Assignments.InMemory()
			.put(BuiltInSchemes.MERCHANT_ID, 385, "resources");
		final Classifier classifier = new Classifier(table, overrides);

		assertEquals("resources", classifier.classify(merchant, 385).id());
		// The same override must not leak into another scheme's view of the same item.
		assertEquals("cooking", classifier.classify(skiller, 385).id());
	}

	@Test
	public void assignmentToADeletedCategoryFallsThroughRatherThanDangling()
	{
		final Assignments overrides = new Assignments.InMemory()
			.put(BuiltInSchemes.MERCHANT_ID, 385, "a-category-the-user-deleted");
		final Classification result = new Classifier(table, overrides).explain(merchant, 385);

		assertEquals("consumables", result.category().id());
		assertEquals(Classification.Source.SCHEME_RULE, result.source());
	}

	@Test
	public void schemeRulesBeatInference()
	{
		assertEquals(Classification.Source.SCHEME_RULE, plain().explain(merchant, 995).source());
	}

	@Test
	public void inferenceFillsInForASchemeWithNoRulesOfItsOwn()
	{
		// A scheme that declares conventional category ids but writes no rules still
		// classifies, which is what keeps an eighth scheme a declaration rather than a
		// data project.
		final Scheme minimal = new Scheme("minimal", "Minimal",
			Arrays.asList(new Category("consumables", "Consumables"),
				new Category("runes", "Runes")),
			Collections.emptyList());

		final Classification shark = plain().explain(minimal, 385);
		assertEquals("consumables", shark.category().id());
		assertEquals(Classification.Source.INFERENCE, shark.source());
		assertEquals("runes", plain().classify(minimal, 561).id());
	}

	@Test
	public void unknownItemIsUncategorizedNotGuessed()
	{
		final Classification result = plain().explain(skiller, Integer.MAX_VALUE);
		assertEquals(Scheme.UNCATEGORIZED_ID, result.category().id());
		assertEquals(Classification.Source.FALLBACK, result.source());
	}

	@Test
	public void knownItemWithNoMatchingCategoryIsUncategorized()
	{
		// A clue scroll is in the table, but Merchant is deliberately coarse and has no
		// bucket for it. Saying so is the point of keeping Uncategorized visible.
		assertEquals(Scheme.UNCATEGORIZED_ID, category(merchant, 2677));
	}

	// ---- right about sharks --------------------------------------------------------

	@Test
	public void sharkIsCookingToASkillerAndAConsumableToAMerchant()
	{
		assertEquals("cooking", category(skiller, 385));
		assertEquals("consumables", category(merchant, 385));
	}

	@Test
	public void rawSharkIsFishingNotCooking()
	{
		// Ordered skills, most relevant first: catching it is the point, cooking it is next.
		assertEquals("fishing", category(skiller, 383));
	}

	// ---- the two lenses disagree, using one table ----------------------------------

	@Test
	public void dragonPickaxeIsMiningToASkillerAndAToolToAMerchant()
	{
		assertEquals("mining", category(skiller, 11920));
		assertEquals("tools", category(merchant, 11920));
	}

	@Test
	public void toolRuleOutranksWeaponSlotForMerchant()
	{
		// A pickaxe equips in the weapon slot. If the weapon rule ran first it would land in
		// Weapons, which is wrong for this scheme - rule order is load-bearing.
		assertEquals("tools", category(merchant, 1275));
		assertEquals("weapons", category(merchant, 1333));
	}

	@Test
	public void oresAreMiningToASkillerAndResourcesToAMerchant()
	{
		assertEquals("mining", category(skiller, 440));
		assertEquals("resources", category(merchant, 440));
	}

	@Test
	public void runesAreMagicToASkillerAndRunesToAMerchant()
	{
		assertEquals("magic", category(skiller, 561));
		assertEquals("runes", category(merchant, 561));
	}

	@Test
	public void coinsAreCurrencyInBothSchemes()
	{
		assertEquals("currency", category(skiller, 995));
		assertEquals("currency", category(merchant, 995));
		assertEquals("currency", category(merchant, 13204));
	}

	@Test
	public void nonSkillGearFallsToCombatGearForASkiller()
	{
		assertEquals("combat-gear", category(skiller, 1127));
		assertEquals("armour", category(merchant, 1127));
	}

	@Test
	public void grimyHerbIsHerbloreAndSeedIsFarming()
	{
		assertEquals("herblore", category(skiller, 207));
		assertEquals("farming", category(skiller, 5295));
	}

	@Test
	public void potionsAreHerbloreToASkillerAndConsumablesToAMerchant()
	{
		assertEquals("herblore", category(skiller, 2434));
		assertEquals("consumables", category(merchant, 2434));
	}

	@Test
	public void ammunitionIsWeaponsForAMerchant()
	{
		assertEquals("weapons", category(merchant, 892));
	}

	@Test
	public void barsAreSmithingAndLogsAreWoodcutting()
	{
		assertEquals("smithing", category(skiller, 2351));
		assertEquals("woodcutting", category(skiller, 1521));
	}

	@Test
	public void skillerConsumablesCatchesAConsumableWithNoSkillRelevance()
	{
		// Every consumable currently in the table is also a skill product, so under a
		// by-skill lens this category holds nothing today. It is not dead weight: it is where
		// a consumable with no skill attribute lands, instead of Uncategorized. Proven with a
		// synthetic item rather than by inventing a table row for it.
		final AttributeTable synthetic = AttributeTable.of(Collections.singletonMap(
			9_000_001, ItemAttributes.builder("Purchased snack")
				.consumable(ConsumableClass.FOOD)
				.material(MaterialStage.FINISHED)
				.build()));

		final Classification result =
			new Classifier(synthetic, Assignments.none()).explain(skiller, 9_000_001);
		assertEquals("consumables", result.category().id());
		assertEquals(Classification.Source.SCHEME_RULE, result.source());
	}

	@Test
	public void bonesArePrayerAndEssenceIsRunecraft()
	{
		assertEquals("prayer", category(skiller, 536));
		assertEquals("runecraft", category(skiller, 7936));
	}
}
