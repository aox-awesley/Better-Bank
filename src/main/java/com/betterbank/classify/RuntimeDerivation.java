package com.betterbank.classify;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Derives attributes from what the running client already knows (SPEC §3's attribute list,
 * sourced at runtime instead of by hand).
 *
 * <p>This is the layer that made the bundled table stop needing to cover the game. Equipment
 * slot alone accounts for jewellery, headgear, boots, gloves, ammunition, weapons, shields
 * and robes; combat bonuses account for style; inventory actions account for food and
 * potions. None of it can go stale, because it is read from the client the user is running.
 */
public final class RuntimeDerivation
{
	// Equipment slot indices, from net.runelite.api.EquipmentInventorySlot.getSlotIdx().
	// ARMS (6), HAIR (8) and JAW (11) are cosmetic layers no bank item occupies.
	private static final int SLOT_HEAD = 0;
	private static final int SLOT_CAPE = 1;
	private static final int SLOT_AMULET = 2;
	private static final int SLOT_WEAPON = 3;
	private static final int SLOT_BODY = 4;
	private static final int SLOT_SHIELD = 5;
	private static final int SLOT_LEGS = 7;
	private static final int SLOT_GLOVES = 9;
	private static final int SLOT_BOOTS = 10;
	private static final int SLOT_RING = 12;
	private static final int SLOT_AMMO = 13;

	private RuntimeDerivation()
	{
	}

	/** Applies everything the client can tell us about this item onto {@code out}. */
	public static void apply(RuntimeItem item, ItemAttributes.Builder out)
	{
		out.tradeable(item.tradeable());
		out.members(item.members());
		out.stackable(item.stackable());
		out.noted(item.noted());

		final EquipmentSlot slot = slotFor(item.slotIdx());
		if (slot != null)
		{
			out.slot(slot);
		}

		final CombatStyle[] styles = stylesFor(item);
		if (styles.length > 0)
		{
			out.styles(styles);
		}

		// Metamorphic pets carry a Metamorphosis option. This only covers the metamorphic
		// subset, and is a best-effort fallback behind the bundled pet list - see NamePatterns.
		if (item.hasInventoryAction("Metamorphosis"))
		{
			out.pet(true);
		}

		final ConsumableClass consumable = consumableFor(item);
		if (consumable != ConsumableClass.NONE)
		{
			out.consumable(consumable);
			// Anything you can eat or drink is an end product by definition.
			out.material(MaterialStage.FINISHED);
		}
	}

	/** @return the slot this index means, or null for "not equippable" and cosmetic layers. */
	public static EquipmentSlot slotFor(int slotIdx)
	{
		switch (slotIdx)
		{
			case SLOT_HEAD:
				return EquipmentSlot.HEAD;
			case SLOT_CAPE:
				return EquipmentSlot.CAPE;
			case SLOT_AMULET:
				return EquipmentSlot.NECK;
			case SLOT_WEAPON:
				return EquipmentSlot.WEAPON;
			case SLOT_BODY:
				return EquipmentSlot.BODY;
			case SLOT_SHIELD:
				return EquipmentSlot.SHIELD;
			case SLOT_LEGS:
				return EquipmentSlot.LEGS;
			case SLOT_GLOVES:
				return EquipmentSlot.HANDS;
			case SLOT_BOOTS:
				return EquipmentSlot.FEET;
			case SLOT_RING:
				return EquipmentSlot.RING;
			case SLOT_AMMO:
				return EquipmentSlot.AMMUNITION;
			default:
				return null;
		}
	}

	/**
	 * Combat styles this item serves, most dominant first.
	 *
	 * <p><b>Weapons and armour are read differently, and that distinction is the whole point.</b>
	 *
	 * <p>For a <b>weapon</b>, style is decided by which <i>attack</i> bonus is largest.
	 * Defence is ignored entirely: a staff carries defence bonuses and a small crush attack
	 * bonus alongside its magic attack, and treating either as a melee signal filed every
	 * staff as melee gear. Ranking by attack bonus puts the magic bonus first, which is what
	 * the weapon is for.
	 *
	 * <p>For <b>armour</b>, attack bonuses still rank first when present (a slayer helmet
	 * serves all three styles), but defence is a legitimate weak signal: a platebody has no
	 * attack bonus at all and is unambiguously melee gear. Prayer with nothing else is the
	 * signature of robes.
	 */
	public static CombatStyle[] stylesFor(RuntimeItem item)
	{
		final int melee = Math.max(item.attackStab(),
			Math.max(item.attackSlash(), item.attackCrush()));
		final int ranged = item.attackRanged();
		final int magic = item.attackMagic();

		return item.slotIdx() == SLOT_WEAPON
			? weaponStyles(item, melee, ranged, magic)
			: armourStyles(item, melee, ranged, magic);
	}

	/**
	 * Weapon style, from attack bonuses only. Defence never counts on a weapon.
	 *
	 * <p>A positive <b>magic</b> attack bonus leads, then a positive <b>ranged</b> one, and
	 * melee is what is left. This is deliberately not pure "largest bonus wins": a mystic
	 * fire staff has +12 crush against +10 magic, and a staff of the dead more melee still,
	 * but nobody uses either as a club. Melee bonuses on a magic or ranged weapon are
	 * incidental; a positive magic or ranged attack bonus is a statement of intent.
	 *
	 * <p>The other styles are still reported after the leader, ranked by bonus, so a hybrid
	 * keeps its secondary use.
	 */
	private static CombatStyle[] weaponStyles(RuntimeItem item, int melee, int ranged, int magic)
	{
		final List<CombatStyle> ranked = rankByBonus(melee, ranged, magic);
		if (!ranked.isEmpty())
		{
			if (magic > 0)
			{
				promote(ranked, CombatStyle.MAGIC);
			}
			else if (ranged > 0)
			{
				promote(ranked, CombatStyle.RANGED);
			}
			return ranked.toArray(new CombatStyle[0]);
		}

		// No positive attack bonus anywhere - fall back to the damage-side bonuses, then to
		// melee, since something in the weapon slot with no bonuses at all is swung.
		if (item.rangedStrength() > 0)
		{
			return new CombatStyle[]{CombatStyle.RANGED};
		}
		if (item.magicDamage() > 0f)
		{
			return new CombatStyle[]{CombatStyle.MAGIC};
		}
		return new CombatStyle[]{CombatStyle.MELEE};
	}

	/** Attack bonuses first; defence and prayer only when there is no attack signal at all. */
	private static CombatStyle[] armourStyles(RuntimeItem item, int melee, int ranged, int magic)
	{
		final List<CombatStyle> styles = rankByBonus(melee, ranged, magic);

		if (item.magicDamage() > 0f && !styles.contains(CombatStyle.MAGIC))
		{
			styles.add(CombatStyle.MAGIC);
		}
		if (item.rangedStrength() > 0 && !styles.contains(CombatStyle.RANGED))
		{
			styles.add(CombatStyle.RANGED);
		}
		if (item.strength() > 0 && !styles.contains(CombatStyle.MELEE))
		{
			styles.add(CombatStyle.MELEE);
		}

		if (styles.isEmpty())
		{
			// Weak signals, armour only.
			if (item.defenceStab() > 0 || item.defenceSlash() > 0 || item.defenceCrush() > 0)
			{
				styles.add(CombatStyle.MELEE);
			}
			else if (item.prayer() > 0)
			{
				// Prayer and nothing else: monk robes, priest gowns, god vestments.
				styles.add(CombatStyle.MAGIC);
			}
		}
		return styles.toArray(new CombatStyle[0]);
	}

	/** Moves {@code style} to the front, keeping the rest in their existing order. */
	private static void promote(List<CombatStyle> styles, CombatStyle style)
	{
		if (styles.remove(style))
		{
			styles.add(0, style);
		}
	}

	/** The styles with a positive bonus, largest bonus first. Ties favour melee, then ranged. */
	private static List<CombatStyle> rankByBonus(int melee, int ranged, int magic)
	{
		final List<CombatStyle> out = new ArrayList<>(3);
		if (melee > 0)
		{
			out.add(CombatStyle.MELEE);
		}
		if (ranged > 0)
		{
			out.add(CombatStyle.RANGED);
		}
		if (magic > 0)
		{
			out.add(CombatStyle.MAGIC);
		}
		out.sort(Comparator.comparingInt((CombatStyle s) ->
		{
			switch (s)
			{
				case MELEE:
					return melee;
				case RANGED:
					return ranged;
				default:
					return magic;
			}
		}).reversed());
		return out;
	}

	/**
	 * Food and potions, read from the item's own inventory menu options. More reliable than
	 * any name rule: every edible thing in the game has an Eat or Drink option, whatever it
	 * happens to be called.
	 */
	public static ConsumableClass consumableFor(RuntimeItem item)
	{
		if (item.hasInventoryAction("Eat"))
		{
			return ConsumableClass.FOOD;
		}
		if (item.hasInventoryAction("Drink"))
		{
			return ConsumableClass.POTION;
		}
		return ConsumableClass.NONE;
	}
}
