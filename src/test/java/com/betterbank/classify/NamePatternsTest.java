package com.betterbank.classify;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The name rules, which supply what the client's stats cannot: skill relevance and
 * production stage.
 */
public class NamePatternsTest
{
	private static ItemAttributes of(String name)
	{
		final ItemAttributes.Builder b = ItemAttributes.builder(name);
		NamePatterns.apply(name, b);
		return b.build();
	}

	@Test
	public void productionChainsAreRecognised()
	{
		assertTrue(of("Yew logs").skills().contains(SkillType.WOODCUTTING));
		assertTrue(of("Iron ore").skills().contains(SkillType.MINING));
		assertEquals(MaterialStage.INTERMEDIATE, of("Mithril bar").material());
		assertTrue(of("Pure essence").skills().contains(SkillType.RUNECRAFT));
		assertTrue(of("Ranarr seed").skills().contains(SkillType.FARMING));
		assertTrue(of("Grimy ranarr weed").skills().contains(SkillType.HERBLORE));
		assertTrue(of("Dragon bones").skills().contains(SkillType.PRAYER));
		assertTrue(of("Green dragonhide").skills().contains(SkillType.CRAFTING));
		assertTrue(of("Uncut ruby").skills().contains(SkillType.CRAFTING));
	}

	@Test
	public void runesMatchOnlyAsATrailingWord()
	{
		assertTrue("Nature rune should be a rune", of("Nature rune").rune());
		assertTrue(of("Nature rune").stackable());
		// The bug this guards: "Rune platebody" contains "rune" but is armour.
		assertFalse("Rune platebody must not be a rune", of("Rune platebody").rune());
		assertFalse(of("Rune scimitar").rune());
		assertFalse(of("Runite bar").rune());
	}

	@Test
	public void toolExclusionsHoldForLookalikeWeapons()
	{
		assertTrue(of("Rune axe").tool());
		assertFalse("battleaxe is a weapon, not a woodcutting axe", of("Rune battleaxe").tool());
		assertTrue(of("Rune pickaxe").tool());
		assertTrue(of("Imcando hammer").tool());
		assertFalse("warhammer is a weapon", of("Rune warhammer").tool());
	}

	@Test
	public void potionsMatchByWordOrDoseSuffix()
	{
		assertEquals(ConsumableClass.POTION, of("Prayer potion(4)").consumable());
		assertEquals(ConsumableClass.POTION, of("Saradomin brew(2)").consumable());
		assertEquals(ConsumableClass.POTION, of("Super restore(1)").consumable());
		assertTrue(of("Prayer potion(4)").skills().contains(SkillType.HERBLORE));
	}

	@Test
	public void gapsReportedInGameAreCovered()
	{
		// Items that were previously Uncategorized because nothing bundled described them.
		assertTrue(of("Bronze arrow").styles().contains(CombatStyle.RANGED));
		assertTrue(of("Rusty key").questItem());
		assertTrue(of("Mining certificate").questItem());
		assertTrue(of("Varrock teleport").teleport());
		assertTrue(of("Adamant saw").tool());
		assertTrue(of("Clue scroll (easy)").clueReward());
	}

	@Test
	public void teleportTabsAndScrollsMatchOnTheWordTeleport()
	{
		assertTrue(of("Varrock teleport").teleport());
		assertTrue(of("Teleport to house").teleport());
		assertTrue(of("Ardougne teleport scroll").teleport());
	}

	@Test
	public void teleportJewelleryMatchesEveryChargeVariant()
	{
		// One fragment per family covers base, charged and trimmed variants alike.
		for (String name : new String[]{
			"Amulet of glory", "Amulet of glory(4)", "Amulet of glory (t)",
			"Games necklace(8)", "Ring of dueling(1)", "Skills necklace(4)",
			"Combat bracelet(6)", "Necklace of passage(5)", "Digsite pendant(5)"})
		{
			assertTrue(name + " should be a teleport item", of(name).teleport());
		}
	}

	@Test
	public void teleportJewelleryKeepsNoSkillSoSkillerIsUnaffected()
	{
		// The tab rule sets MAGIC; the jewellery rule must not, or charged glories would move
		// out of a skiller's combat gear.
		assertTrue(of("Amulet of glory(4)").skills().isEmpty());
		assertTrue(of("Varrock teleport").skills().contains(SkillType.MAGIC));
	}

	@Test
	public void chargedJewelleryIsNotMistakenForAPotion()
	{
		// The dose suffix "(4)" is shared between potions and charged jewellery.
		assertEquals(ConsumableClass.NONE, of("Amulet of glory(4)").consumable());
		assertEquals(ConsumableClass.NONE, of("Skills necklace(4)").consumable());
		assertEquals(ConsumableClass.NONE, of("Ring of dueling(8)").consumable());
		assertTrue(of("Amulet of glory(4)").skills().isEmpty());
		// ...and real potions still match.
		assertEquals(ConsumableClass.POTION, of("Prayer potion(4)").consumable());
		assertEquals(ConsumableClass.POTION, of("Saradomin brew(2)").consumable());
	}

	@Test
	public void nonMatchingNameGetsNothing()
	{
		final ItemAttributes plain = of("Gas mask");
		assertTrue(plain.skills().isEmpty());
		assertEquals(MaterialStage.NONE, plain.material());
		assertFalse(plain.tool());
	}

	@Test
	public void everyRuleHasADescriptionForDebugging()
	{
		for (NamePatterns.Rule rule : NamePatterns.RULES)
		{
			assertTrue(rule.description != null && !rule.description.isEmpty());
		}
		assertTrue(NamePatterns.matching("Yew logs").toString().contains("logs"));
	}
}
