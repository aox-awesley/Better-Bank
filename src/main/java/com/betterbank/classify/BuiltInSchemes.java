package com.betterbank.classify;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * The schemes that ship with the plugin.
 *
 * <p>M3 builds the two SPEC §8 names as maximally different lenses over the same data:
 * {@link #skiller()} organises by skill, {@link #merchant()} by coarse market bucket. Every
 * item in the table therefore has two correct answers, which is the whole point of the
 * attribute table - a shark is a Cooking product to a skiller and a consumable to a
 * flipper, and neither view needed its own data.
 *
 * <p>The remaining five schemes are declarations like these, not new data.
 */
public final class BuiltInSchemes
{
	public static final String SKILLER_ID = "skiller";
	public static final String MERCHANT_ID = "merchant";

	private BuiltInSchemes()
	{
	}

	public static List<Scheme> all()
	{
		return Arrays.asList(skiller(), merchant());
	}

	/** @return the scheme with this id, or null. */
	public static Scheme byId(String id)
	{
		for (Scheme s : all())
		{
			if (s.id().equals(id))
			{
				return s;
			}
		}
		return null;
	}

	/**
	 * By skill. A skiller's bank is organised around what they are training, so an item goes
	 * under its most relevant skill before anything else - cooked shark is Cooking, raw shark
	 * is Fishing.
	 */
	public static Scheme skiller()
	{
		final List<Category> categories = new ArrayList<>();
		categories.add(new Category("currency", "Currency"));
		for (SkillType skill : new SkillType[]{
			SkillType.MINING, SkillType.SMITHING, SkillType.FISHING, SkillType.COOKING,
			SkillType.WOODCUTTING, SkillType.FIREMAKING, SkillType.FLETCHING, SkillType.FARMING,
			SkillType.HERBLORE, SkillType.CRAFTING, SkillType.RUNECRAFT, SkillType.PRAYER,
			SkillType.MAGIC})
		{
			categories.add(new Category(skill.categoryId(), displayName(skill)));
		}
		categories.add(new Category("combat-gear", "Combat gear"));
		categories.add(new Category("consumables", "Consumables"));

		final List<Rule> rules = Arrays.asList(
			Rule.when(ItemAttributes::currency, "currency"),
			// Skill relevance first: this is what makes the scheme "by skill" rather than
			// "by item type with skills as a fallback".
			Rule.bySkill(),
			Rule.when(ItemAttributes::equippable, "combat-gear"),
			Rule.when(a -> a.consumable() != ConsumableClass.NONE, "consumables")
		);

		return new Scheme(SKILLER_ID, "Skiller", categories, rules);
	}

	/**
	 * Broad market buckets. Deliberately coarse (SPEC §4): a flipper's bank is mostly coins
	 * plus a rotating handful of held items, and fine-grained taxonomy adds nothing.
	 */
	public static Scheme merchant()
	{
		final List<Category> categories = Arrays.asList(
			new Category("currency", "Currency"),
			new Category("armour", "Armour"),
			new Category("weapons", "Weapons"),
			new Category("tools", "Tools"),
			new Category("runes", "Runes"),
			new Category("consumables", "Consumables"),
			new Category("resources", "Resources")
		);

		final List<Rule> rules = Arrays.asList(
			Rule.when(ItemAttributes::currency, "currency"),
			Rule.when(ItemAttributes::rune, "runes"),
			// Tools before weapons on purpose: a dragon pickaxe equips in the weapon slot but
			// a merchant thinks of it as a tool.
			Rule.when(ItemAttributes::tool, "tools"),
			Rule.when(a -> a.slot() == EquipmentSlot.WEAPON || a.slot() == EquipmentSlot.AMMUNITION,
				"weapons"),
			Rule.when(ItemAttributes::equippable, "armour"),
			Rule.when(a -> a.consumable() != ConsumableClass.NONE, "consumables"),
			Rule.when(a -> a.material() == MaterialStage.RAW
				|| a.material() == MaterialStage.INTERMEDIATE, "resources")
		);

		return new Scheme(MERCHANT_ID, "Merchant", categories, rules);
	}

	private static String displayName(SkillType skill)
	{
		final String lower = skill.categoryId();
		return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
	}
}
