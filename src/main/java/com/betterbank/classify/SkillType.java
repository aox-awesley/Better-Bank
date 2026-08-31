package com.betterbank.classify;

import java.util.Locale;

/**
 * An OSRS skill an item is relevant to.
 *
 * <p>Named {@code SkillType} rather than {@code Skill} so it is never confused with
 * {@code net.runelite.api.Skill}. This package deliberately depends on no game API.
 */
public enum SkillType
{
	ATTACK,
	DEFENCE,
	STRENGTH,
	HITPOINTS,
	RANGED,
	PRAYER,
	MAGIC,
	COOKING,
	WOODCUTTING,
	FLETCHING,
	FISHING,
	FIREMAKING,
	CRAFTING,
	SMITHING,
	MINING,
	HERBLORE,
	AGILITY,
	THIEVING,
	SLAYER,
	FARMING,
	RUNECRAFT,
	HUNTER,
	CONSTRUCTION;

	private final String categoryId = name().toLowerCase(Locale.ROOT);

	/**
	 * The conventional category id a scheme uses for this skill, e.g. {@code "herblore"}.
	 * A scheme is free not to declare it.
	 */
	public String categoryId()
	{
		return categoryId;
	}
}
