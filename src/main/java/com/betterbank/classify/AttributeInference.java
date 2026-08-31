package com.betterbank.classify;

import java.util.ArrayList;
import java.util.List;

/**
 * The generic fallback layer in the resolution order (SPEC §3): user assignment &rarr;
 * scheme rules &rarr; <b>attribute inference</b> &rarr; Uncategorized.
 *
 * <p>Where a scheme's rules are curated and specific, this is scheme-agnostic. It proposes
 * conventional category ids in decreasing order of confidence; {@link Classifier} takes the
 * first one the scheme actually declares. A scheme that declares a category with a
 * conventional id therefore gets sensible behaviour for free, without writing a rule.
 */
public final class AttributeInference
{
	private AttributeInference()
	{
	}

	/** Conventional category ids, most confident first. */
	public static List<String> candidates(ItemAttributes a)
	{
		final List<String> out = new ArrayList<>();
		if (a.currency())
		{
			out.add("currency");
		}
		if (a.rune())
		{
			out.add("runes");
		}
		if (a.teleport())
		{
			out.add("teleports");
		}
		if (a.tool())
		{
			out.add("tools");
		}
		if (a.consumable() == ConsumableClass.FOOD)
		{
			out.add("food");
		}
		if (a.consumable() == ConsumableClass.POTION)
		{
			out.add("potions");
		}
		if (a.consumable() != ConsumableClass.NONE)
		{
			out.add("consumables");
		}
		for (SkillType skill : a.skills())
		{
			out.add(skill.categoryId());
		}
		if (a.clueReward())
		{
			out.add("treasure-trails");
		}
		if (a.questItem())
		{
			out.add("quest-items");
		}
		if (a.achievement())
		{
			out.add("achievements");
		}
		if (a.slot() == EquipmentSlot.WEAPON || a.slot() == EquipmentSlot.AMMUNITION)
		{
			out.add("weapons");
		}
		if (a.equippable())
		{
			out.add("armour");
			out.add("combat-gear");
		}
		if (a.material() == MaterialStage.RAW || a.material() == MaterialStage.INTERMEDIATE)
		{
			out.add("resources");
		}
		return out;
	}
}
