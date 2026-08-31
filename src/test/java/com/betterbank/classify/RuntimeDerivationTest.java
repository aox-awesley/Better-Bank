package com.betterbank.classify;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Attributes derived from what the running client knows. Slot indices are the values of
 * {@code net.runelite.api.EquipmentInventorySlot.getSlotIdx()}.
 */
public class RuntimeDerivationTest
{
	private static ItemAttributes derive(RuntimeItem item)
	{
		final ItemAttributes.Builder b = ItemAttributes.builder(item.name());
		RuntimeDerivation.apply(item, b);
		return b.build();
	}

	@Test
	public void everyRealEquipmentSlotMaps()
	{
		assertEquals(EquipmentSlot.HEAD, RuntimeDerivation.slotFor(0));
		assertEquals(EquipmentSlot.CAPE, RuntimeDerivation.slotFor(1));
		assertEquals(EquipmentSlot.NECK, RuntimeDerivation.slotFor(2));
		assertEquals(EquipmentSlot.WEAPON, RuntimeDerivation.slotFor(3));
		assertEquals(EquipmentSlot.BODY, RuntimeDerivation.slotFor(4));
		assertEquals(EquipmentSlot.SHIELD, RuntimeDerivation.slotFor(5));
		assertEquals(EquipmentSlot.LEGS, RuntimeDerivation.slotFor(7));
		assertEquals(EquipmentSlot.HANDS, RuntimeDerivation.slotFor(9));
		assertEquals(EquipmentSlot.FEET, RuntimeDerivation.slotFor(10));
		assertEquals(EquipmentSlot.RING, RuntimeDerivation.slotFor(12));
		assertEquals(EquipmentSlot.AMMUNITION, RuntimeDerivation.slotFor(13));
	}

	@Test
	public void cosmeticAndAbsentSlotsAreNotEquipment()
	{
		assertNull("ARMS is a cosmetic layer", RuntimeDerivation.slotFor(6));
		assertNull("HAIR is a cosmetic layer", RuntimeDerivation.slotFor(8));
		assertNull("JAW is a cosmetic layer", RuntimeDerivation.slotFor(11));
		assertNull(RuntimeDerivation.slotFor(RuntimeItem.NO_SLOT));
	}

	@Test
	public void slotAloneCoversJewelleryAndClothing()
	{
		// The gaps reported in game: no bundled row needed for any of these now.
		assertEquals(EquipmentSlot.NECK,
			derive(RuntimeItem.builder("Amulet of glory").slotIdx(2).build()).slot());
		assertEquals(EquipmentSlot.RING,
			derive(RuntimeItem.builder("Gold bracelet").slotIdx(12).build()).slot());
		assertEquals(EquipmentSlot.HEAD,
			derive(RuntimeItem.builder("Wizard hat").slotIdx(0).build()).slot());
		assertEquals(EquipmentSlot.FEET,
			derive(RuntimeItem.builder("Leather boots").slotIdx(10).build()).slot());
		assertEquals(EquipmentSlot.HANDS,
			derive(RuntimeItem.builder("Leather gloves").slotIdx(9).build()).slot());
	}

	@Test
	public void combatStyleComesFromBonuses()
	{
		assertTrue(derive(RuntimeItem.builder("Rune scimitar").slotIdx(3)
			.attack(0, 45, 0, 0, 0).strength(44, 0, 0f).build())
			.styles().contains(CombatStyle.MELEE));

		assertTrue(derive(RuntimeItem.builder("Magic shortbow").slotIdx(3)
			.attack(0, 0, 0, 0, 69).build())
			.styles().contains(CombatStyle.RANGED));

		assertTrue(derive(RuntimeItem.builder("Staff of fire").slotIdx(3)
			.attack(0, 0, 0, 10, 0).build())
			.styles().contains(CombatStyle.MAGIC));
	}

	// ---- weapon style is decided by attack bonuses, never by defence -------------------

	/** A weapon in the weapon slot (index 3) with the given attack bonuses. */
	private static RuntimeItem weapon(String name, int stab, int slash, int crush, int magic, int ranged)
	{
		return RuntimeItem.builder(name).slotIdx(3)
			.attack(stab, slash, crush, magic, ranged)
			// Real weapons carry defence bonuses too; they must not decide the style.
			.defence(2, 2, 2).build();
	}

	@Test
	public void stavesAreMagicWeaponsNotMeleeWeapons()
	{
		// A staff has a small crush attack bonus and defence bonuses alongside its magic
		// attack. Treating either as a melee signal filed every staff under melee gear.
		for (RuntimeItem staff : new RuntimeItem[]{
			weapon("Staff of air", -1, -1, 3, 10, 0),
			weapon("Ancient staff", 2, 2, 5, 15, 0),
			weapon("Kodai wand", 0, 0, 0, 28, 0),
			weapon("Trident of the seas", 0, 0, 0, 25, 0)})
		{
			final ItemAttributes a = derive(staff);
			assertEquals(staff.name() + " should be a magic weapon",
				CombatStyle.MAGIC, a.primaryStyle());
		}
	}

	@Test
	public void bowsAndCrossbowsAreRangedWeapons()
	{
		for (RuntimeItem bow : new RuntimeItem[]{
			weapon("Shortbow", 0, 0, 0, 0, 8),
			weapon("Magic shortbow", 0, 0, 0, 0, 69),
			weapon("Rune crossbow", 0, 0, 0, 0, 90),
			weapon("Toxic blowpipe", 0, 0, 0, 0, 30)})
		{
			final ItemAttributes a = derive(bow);
			assertEquals(bow.name() + " should be a ranged weapon",
				CombatStyle.RANGED, a.primaryStyle());
		}
	}

	@Test
	public void meleeWeaponsAreStillMelee()
	{
		assertEquals(CombatStyle.MELEE, derive(weapon("Rune scimitar", 0, 45, 0, 0, 0)).primaryStyle());
		assertEquals(CombatStyle.MELEE, derive(weapon("Abyssal whip", 0, 82, 0, 0, 0)).primaryStyle());
	}

	@Test
	public void defenceNeverDecidesAWeaponsStyle()
	{
		// Heavy defence, modest magic attack, zero melee attack: still a magic weapon.
		final ItemAttributes a = derive(RuntimeItem.builder("Defensive staff").slotIdx(3)
			.attack(0, 0, 0, 5, 0).defence(60, 60, 60).build());
		assertEquals(CombatStyle.MAGIC, a.primaryStyle());
	}

	@Test
	public void aWeaponWithNoBonusesAtAllIsMelee()
	{
		assertEquals(CombatStyle.MELEE,
			derive(RuntimeItem.builder("Novelty weapon").slotIdx(3).build()).primaryStyle());
	}

	@Test
	public void aStaffWithMoreMeleeThanMagicIsStillAMagicWeapon()
	{
		// Mystic fire staff: +12 crush against +10 magic. Largest-bonus-wins would call this
		// melee; nobody uses a mystic staff as a club. Its secondary melee use is still listed.
		final ItemAttributes a = derive(weapon("Mystic fire staff", 10, 3, 12, 10, 0));
		assertEquals(CombatStyle.MAGIC, a.primaryStyle());
		assertTrue(a.styles().contains(CombatStyle.MELEE));
	}

	@Test
	public void aRangedWeaponWithMeleeBonusesIsStillRanged()
	{
		final ItemAttributes a = derive(weapon("Hybrid bow", 0, 20, 0, 0, 55));
		assertEquals(CombatStyle.RANGED, a.primaryStyle());
		assertTrue(a.styles().contains(CombatStyle.MELEE));
	}

	@Test
	public void anItemCanServeSeveralStyles()
	{
		final ItemAttributes helm = derive(RuntimeItem.builder("Slayer helmet").slotIdx(0)
			.attack(0, 0, 0, 3, 3).strength(1, 0, 0f).build());
		assertTrue(helm.styles().contains(CombatStyle.MELEE));
		assertTrue(helm.styles().contains(CombatStyle.RANGED));
		assertTrue(helm.styles().contains(CombatStyle.MAGIC));
	}

	@Test
	public void prayerBonusWithNoOffenceSignalsRobes()
	{
		// Monk robes have prayer and nothing else. Without this they would have no style at all.
		final ItemAttributes robes = derive(
			RuntimeItem.builder("Monk's robe top").slotIdx(4).prayer(6).build());
		assertTrue(robes.styles().contains(CombatStyle.MAGIC));
	}

	@Test
	public void prayerOnMeleeArmourDoesNotMakeItMagic()
	{
		final ItemAttributes proselyte = derive(RuntimeItem.builder("Proselyte hauberk")
			.slotIdx(4).attack(0, 0, 0, -5, -5).defence(53, 51, 47).prayer(8).build());
		// Real armour defence stats, so this is melee gear that happens to have prayer - the
		// robes rule must not claim it.
		assertTrue(proselyte.styles().contains(CombatStyle.MELEE));
		assertFalse(proselyte.styles().contains(CombatStyle.MAGIC));
	}

	@Test
	public void consumablesComeFromInventoryActions()
	{
		assertEquals(ConsumableClass.FOOD, derive(
			RuntimeItem.builder("Cake").actions("Eat", null, null, null, "Drop").build()).consumable());
		assertEquals(ConsumableClass.POTION, derive(
			RuntimeItem.builder("Prayer potion(4)").actions("Drink", null).build()).consumable());
		assertEquals(ConsumableClass.FOOD, derive(
			RuntimeItem.builder("Dwarven stout").actions("Eat").build()).consumable());
		assertEquals(ConsumableClass.NONE, derive(
			RuntimeItem.builder("Iron bar").actions("Drop").build()).consumable());
	}

	@Test
	public void consumablesAreMarkedFinished()
	{
		assertEquals(MaterialStage.FINISHED, derive(
			RuntimeItem.builder("Shark").actions("Eat").build()).material());
	}

	@Test
	public void definitionFlagsCarryThrough()
	{
		final ItemAttributes a = derive(RuntimeItem.builder("Nature rune")
			.tradeable(true).members(false).stackable(true).noted(false).build());
		assertTrue(a.tradeable());
		assertTrue(a.stackable());
	}
}
