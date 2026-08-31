package com.betterbank.classify;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * What Better Bank knows about one item, independent of any scheme.
 *
 * <p>This is the single shared data layer described in SPEC §3: there is one
 * item&rarr;attributes table, and every scheme is a different mapping from these
 * attributes to its own categories. Adding a scheme must never mean adding a column here
 * for that scheme alone.
 *
 * <p>Immutable. Contains no game API types so the classifier stays unit-testable with no
 * client running.
 */
public final class ItemAttributes
{
	private final String name;
	private final EquipmentSlot slot;
	private final Set<CombatStyle> styles;
	private final Set<SkillType> skills;
	private final ConsumableClass consumable;
	private final MaterialStage material;
	private final boolean tradeable;
	private final boolean members;
	private final boolean stackable;
	private final boolean noted;
	private final boolean questItem;
	private final boolean clueReward;
	private final boolean achievement;
	private final boolean tool;
	private final boolean rune;
	private final boolean currency;
	private final boolean teleport;
	private final boolean pet;

	private ItemAttributes(Builder b)
	{
		this.name = b.name;
		this.slot = b.slot;
		// NOT an EnumSet: like skills, the ordering here is load-bearing (see styles()).
		this.styles = b.styles.isEmpty()
			? Collections.emptySet()
			: Collections.unmodifiableSet(new LinkedHashSet<>(b.styles));
		// NOT an EnumSet: it would reorder these into ordinal order and the ordering here is
		// load-bearing (see skills()).
		this.skills = b.skills.isEmpty()
			? Collections.emptySet()
			: Collections.unmodifiableSet(new LinkedHashSet<>(b.skills));
		this.consumable = b.consumable;
		this.material = b.material;
		this.tradeable = b.tradeable;
		this.members = b.members;
		this.stackable = b.stackable;
		this.noted = b.noted;
		this.questItem = b.questItem;
		this.clueReward = b.clueReward;
		this.achievement = b.achievement;
		this.tool = b.tool;
		this.rune = b.rune;
		this.currency = b.currency;
		this.teleport = b.teleport;
		this.pet = b.pet;
	}

	/** Human-readable item name. Documentation only - never matched against. */
	public String name()
	{
		return name;
	}

	/** The slot this item equips into, or null if it is not equippable. */
	public EquipmentSlot slot()
	{
		return slot;
	}

	public boolean equippable()
	{
		return slot != null;
	}

	/**
	 * Combat styles this item serves, <b>most dominant first</b>. The ordering is significant:
	 * a scheme that splits gear by style files an item under the first of these it has a
	 * category for, so a staff with a small crush bonus and a large magic bonus is magic gear,
	 * not melee gear.
	 */
	public Set<CombatStyle> styles()
	{
		return styles;
	}

	/** The style this item primarily serves, or null if it serves none. */
	public CombatStyle primaryStyle()
	{
		return styles.isEmpty() ? null : styles.iterator().next();
	}

	/**
	 * The skills this item is relevant to, <b>most relevant first</b>. The ordering is
	 * significant: a skill-oriented scheme files an item under the first of these it has a
	 * category for, so raw shark ({@code FISHING, COOKING}) lands in Fishing rather than
	 * Cooking.
	 */
	public Set<SkillType> skills()
	{
		return skills;
	}

	public ConsumableClass consumable()
	{
		return consumable;
	}

	public MaterialStage material()
	{
		return material;
	}

	public boolean tradeable()
	{
		return tradeable;
	}

	public boolean members()
	{
		return members;
	}

	public boolean stackable()
	{
		return stackable;
	}

	public boolean noted()
	{
		return noted;
	}

	public boolean questItem()
	{
		return questItem;
	}

	public boolean clueReward()
	{
		return clueReward;
	}

	public boolean achievement()
	{
		return achievement;
	}

	public boolean tool()
	{
		return tool;
	}

	public boolean rune()
	{
		return rune;
	}

	public boolean currency()
	{
		return currency;
	}

	public boolean teleport()
	{
		return teleport;
	}

	/** A follower pet. Untradeable, unequippable, and pure collection value. */
	public boolean pet()
	{
		return pet;
	}

	@Override
	public String toString()
	{
		return "ItemAttributes(" + name + ")";
	}

	public static Builder builder(String name)
	{
		return new Builder(name);
	}

	/**
	 * Mutable builder. Defaults match the JSON table's defaults, so an omitted field means
	 * the same thing in both places.
	 */
	public static final class Builder
	{
		private final String name;
		private EquipmentSlot slot;
		private Set<CombatStyle> styles = new LinkedHashSet<>();
		private Set<SkillType> skills = new LinkedHashSet<>();
		private ConsumableClass consumable = ConsumableClass.NONE;
		private MaterialStage material = MaterialStage.NONE;
		private boolean tradeable = true;
		private boolean members = false;
		private boolean stackable = false;
		private boolean noted = false;
		private boolean questItem = false;
		private boolean clueReward = false;
		private boolean achievement = false;
		private boolean tool = false;
		private boolean rune = false;
		private boolean currency = false;
		private boolean teleport = false;
		private boolean pet = false;

		private Builder(String name)
		{
			this.name = name;
		}

		public Builder slot(EquipmentSlot v)
		{
			this.slot = v;
			return this;
		}

		/** Order is significant - most dominant style first. */
		public Builder styles(CombatStyle... v)
		{
			this.styles = new LinkedHashSet<>(Arrays.asList(v));
			return this;
		}

		/** Order is significant - most relevant skill first. */
		public Builder skills(SkillType... v)
		{
			this.skills = new LinkedHashSet<>(Arrays.asList(v));
			return this;
		}

		public Builder consumable(ConsumableClass v)
		{
			this.consumable = v;
			return this;
		}

		public Builder material(MaterialStage v)
		{
			this.material = v;
			return this;
		}

		public Builder tradeable(boolean v)
		{
			this.tradeable = v;
			return this;
		}

		public Builder members(boolean v)
		{
			this.members = v;
			return this;
		}

		public Builder stackable(boolean v)
		{
			this.stackable = v;
			return this;
		}

		public Builder noted(boolean v)
		{
			this.noted = v;
			return this;
		}

		public Builder questItem(boolean v)
		{
			this.questItem = v;
			return this;
		}

		public Builder clueReward(boolean v)
		{
			this.clueReward = v;
			return this;
		}

		public Builder achievement(boolean v)
		{
			this.achievement = v;
			return this;
		}

		public Builder tool(boolean v)
		{
			this.tool = v;
			return this;
		}

		public Builder rune(boolean v)
		{
			this.rune = v;
			return this;
		}

		public Builder currency(boolean v)
		{
			this.currency = v;
			return this;
		}

		public Builder teleport(boolean v)
		{
			this.teleport = v;
			return this;
		}

		public Builder pet(boolean v)
		{
			this.pet = v;
			return this;
		}

		public ItemAttributes build()
		{
			return new ItemAttributes(this);
		}
	}
}
