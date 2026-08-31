package com.betterbank.classify;

import com.google.gson.Gson;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * The resolution order from SPEC §3, and the behaviour it produces, over the real bundled
 * override table plus a fake client.
 */
public class ClassifierTest
{
	private AttributeResolver resolver;
	private Scheme skiller;
	private Scheme merchant;

	/**
	 * A stand-in client. Names match the real item definitions; slots and bonuses are
	 * supplied for the equipment cases, which is exactly what the runtime layer reads.
	 */
	private static FakeItemMetadata client()
	{
		return new FakeItemMetadata()
			.named(385, "Shark")
			.named(383, "Raw shark")
			.named(995, "Coins")
			.named(13204, "Platinum token")
			.named(561, "Nature rune")
			.named(440, "Iron ore")
			.named(2351, "Iron bar")
			.named(1521, "Oak logs")
			.named(536, "Dragon bones")
			.named(7936, "Pure essence")
			.named(207, "Grimy ranarr weed")
			.named(5295, "Ranarr seed")
			.named(2434, "Prayer potion(4)")
			.named(2677, "Clue scroll (easy)")
			.named(11920, "Dragon pickaxe")
			.named(1275, "Rune pickaxe")
			.put(1333, RuntimeItem.builder("Rune scimitar").slotIdx(3)
				.attack(0, 45, 0, 0, 0).strength(44, 0, 0f).build())
			.put(1127, RuntimeItem.builder("Rune platebody").slotIdx(4).build())
			.put(892, RuntimeItem.builder("Rune arrow").slotIdx(13).stackable(true)
				.strength(0, 49, 0f).build())
			.named(8007, "Varrock teleport")
			.put(1712, RuntimeItem.builder("Amulet of glory(4)").slotIdx(2)
				.attack(10, 10, 10, 10, 10).defence(3, 3, 3).build())
			.put(3853, RuntimeItem.builder("Games necklace(8)").slotIdx(2).build())
			.put(2552, RuntimeItem.builder("Ring of dueling(8)").slotIdx(12).build());
	}

	@Before
	public void setUp() throws IOException
	{
		resolver = new AttributeResolver(OverrideTable.bundled(new Gson()), client());
		skiller = BuiltInSchemes.skiller();
		merchant = BuiltInSchemes.merchant();
	}

	private Classifier plain()
	{
		return new Classifier(resolver, Assignments.none());
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
		final Classification result = new Classifier(resolver, overrides).explain(merchant, 385);
		assertEquals("resources", result.category().id());
		assertEquals(Classification.Source.ASSIGNMENT, result.source());
	}

	@Test
	public void assignmentIsScopedToItsOwnScheme()
	{
		final Assignments overrides = new Assignments.InMemory()
			.put(BuiltInSchemes.MERCHANT_ID, 385, "resources");
		final Classifier classifier = new Classifier(resolver, overrides);
		assertEquals("resources", classifier.classify(merchant, 385).id());
		assertEquals("cooking", classifier.classify(skiller, 385).id());
	}

	@Test
	public void assignmentToADeletedCategoryFallsThroughRatherThanDangling()
	{
		final Assignments overrides = new Assignments.InMemory()
			.put(BuiltInSchemes.MERCHANT_ID, 385, "a-category-the-user-deleted");
		final Classification result = new Classifier(resolver, overrides).explain(merchant, 385);
		assertEquals("consumables", result.category().id());
		assertEquals(Classification.Source.SCHEME_RULE, result.source());
	}

	@Test
	public void inferenceFillsInForASchemeWithNoRulesOfItsOwn()
	{
		final Scheme minimal = new Scheme("minimal", "Minimal",
			Arrays.asList(new Category("consumables", "Consumables"), new Category("runes", "Runes")),
			Collections.emptyList());
		final Classification shark = plain().explain(minimal, 385);
		assertEquals("consumables", shark.category().id());
		assertEquals(Classification.Source.INFERENCE, shark.source());
		assertEquals("runes", plain().classify(minimal, 561).id());
	}

	@Test
	public void itemTheClientDoesNotKnowIsUncategorized()
	{
		final Classification result = plain().explain(skiller, Integer.MAX_VALUE);
		assertEquals(Scheme.UNCATEGORIZED_ID, result.category().id());
		assertEquals(Classification.Source.FALLBACK, result.source());
	}

	// ---- behaviour preserved across the architecture change ------------------------

	@Test
	public void sharkIsCookingToASkillerAndAConsumableToAMerchant()
	{
		assertEquals("cooking", category(skiller, 385));
		assertEquals("consumables", category(merchant, 385));
	}

	@Test
	public void rawSharkIsFishingNotCooking()
	{
		assertEquals("fishing", category(skiller, 383));
	}

	@Test
	public void dragonPickaxeIsMiningToASkillerAndAToolToAMerchant()
	{
		// Now driven by the "pickaxe" name rule rather than a bundled row.
		assertEquals("mining", category(skiller, 11920));
		assertEquals("tools", category(merchant, 11920));
	}

	@Test
	public void toolRuleOutranksWeaponSlotForMerchant()
	{
		assertEquals("tools", category(merchant, 1275));
		assertEquals("weapons", category(merchant, 1333));
	}

	@Test
	public void equipmentIsClassifiedFromTheRuntimeSlotAlone()
	{
		// No bundled row for either of these any more.
		assertEquals("armour", category(merchant, 1127));
		assertEquals("combat-gear", category(skiller, 1127));
		assertEquals("weapons", category(merchant, 892));
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
	public void barsLogsBonesAndEssenceKeepTheirSkills()
	{
		assertEquals("smithing", category(skiller, 2351));
		assertEquals("woodcutting", category(skiller, 1521));
		assertEquals("prayer", category(skiller, 536));
		assertEquals("runecraft", category(skiller, 7936));
	}

	@Test
	public void merchantFilesTeleportsUnderTeleports()
	{
		assertEquals("teleports", category(merchant, 8007));
		assertEquals("teleports", category(merchant, 1712));
		assertEquals("teleports", category(merchant, 3853));
		assertEquals("teleports", category(merchant, 2552));
	}

	@Test
	public void teleportRuleOutranksArmourForWornTeleportJewellery()
	{
		// Glory is worn in the amulet slot, so the armour rule would claim it first if the
		// teleport rule were not ahead of it.
		assertEquals("teleports", category(merchant, 1712));
	}

	@Test
	public void skillerIsUnchangedByTheTeleportsAddition()
	{
		// Skiller declares no teleports category, and the jewellery rule sets no skill.
		assertEquals("combat-gear", category(skiller, 1712));
		assertEquals("combat-gear", category(skiller, 3853));
		assertEquals("magic", category(skiller, 8007));
	}

	@Test
	public void skillerConsumablesCatchesAConsumableWithNoSkillRelevance()
	{
		final AttributeResolver synthetic = new AttributeResolver(OverrideTable.empty(),
			new FakeItemMetadata().put(9_000_001,
				RuntimeItem.builder("Purchased snack").actions("Eat").build()));
		final Classification result =
			new Classifier(synthetic, Assignments.none()).explain(skiller, 9_000_001);
		assertEquals("consumables", result.category().id());
		assertEquals(Classification.Source.SCHEME_RULE, result.source());
	}
}
