package com.betterbank.classify;

/**
 * Everything the running client knows about one item, snapshotted into a plain value object.
 *
 * <p>Deliberately free of game API types so {@link Classifier} and everything it depends on
 * stays unit-testable with no client running. The real implementation reads
 * {@code ItemComposition} and {@code ItemManager.getItemStats}; tests build these by hand.
 */
public final class RuntimeItem
{
	/** Equipment slot index the client uses; -1 when the item is not equippable. */
	public static final int NO_SLOT = -1;

	private final String name;
	private final boolean tradeable;
	private final boolean members;
	private final boolean stackable;
	private final boolean noted;
	private final boolean placeholder;
	private final int storeValue;
	private final int slotIdx;
	private final int attackMagic;
	private final int attackRanged;
	private final int attackStab;
	private final int attackSlash;
	private final int attackCrush;
	private final int strength;
	private final int rangedStrength;
	private final float magicDamage;
	private final int defenceStab;
	private final int defenceSlash;
	private final int defenceCrush;
	private final int prayer;
	private final String[] inventoryActions;

	private RuntimeItem(Builder b)
	{
		this.name = b.name == null ? "" : b.name;
		this.tradeable = b.tradeable;
		this.members = b.members;
		this.stackable = b.stackable;
		this.noted = b.noted;
		this.placeholder = b.placeholder;
		this.storeValue = b.storeValue;
		this.slotIdx = b.slotIdx;
		this.attackMagic = b.attackMagic;
		this.attackRanged = b.attackRanged;
		this.attackStab = b.attackStab;
		this.attackSlash = b.attackSlash;
		this.attackCrush = b.attackCrush;
		this.strength = b.strength;
		this.rangedStrength = b.rangedStrength;
		this.magicDamage = b.magicDamage;
		this.defenceStab = b.defenceStab;
		this.defenceSlash = b.defenceSlash;
		this.defenceCrush = b.defenceCrush;
		this.prayer = b.prayer;
		this.inventoryActions = b.inventoryActions == null ? new String[0] : b.inventoryActions;
	}

	public String name()
	{
		return name;
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

	public boolean placeholder()
	{
		return placeholder;
	}

	/** The shop value from the item definition. Present even for untradeables. */
	public int storeValue()
	{
		return storeValue;
	}

	public boolean equippable()
	{
		return slotIdx != NO_SLOT;
	}

	public int slotIdx()
	{
		return slotIdx;
	}

	public int attackMagic()
	{
		return attackMagic;
	}

	public int attackRanged()
	{
		return attackRanged;
	}

	public int attackStab()
	{
		return attackStab;
	}

	public int attackSlash()
	{
		return attackSlash;
	}

	public int attackCrush()
	{
		return attackCrush;
	}

	public int strength()
	{
		return strength;
	}

	public int rangedStrength()
	{
		return rangedStrength;
	}

	public float magicDamage()
	{
		return magicDamage;
	}

	public int defenceStab()
	{
		return defenceStab;
	}

	public int defenceSlash()
	{
		return defenceSlash;
	}

	public int defenceCrush()
	{
		return defenceCrush;
	}

	public int prayer()
	{
		return prayer;
	}

	/** Inventory menu options, e.g. {@code Eat}, {@code Drink}, {@code Bury}. Never null. */
	public String[] inventoryActions()
	{
		return inventoryActions;
	}

	public boolean hasInventoryAction(String action)
	{
		for (String a : inventoryActions)
		{
			if (action.equalsIgnoreCase(a))
			{
				return true;
			}
		}
		return false;
	}

	public static Builder builder(String name)
	{
		return new Builder(name);
	}

	public static final class Builder
	{
		private final String name;
		private boolean tradeable = true;
		private boolean members;
		private boolean stackable;
		private boolean noted;
		private boolean placeholder;
		private int storeValue;
		private int slotIdx = NO_SLOT;
		private int attackMagic;
		private int attackRanged;
		private int attackStab;
		private int attackSlash;
		private int attackCrush;
		private int strength;
		private int rangedStrength;
		private float magicDamage;
		private int defenceStab;
		private int defenceSlash;
		private int defenceCrush;
		private int prayer;
		private String[] inventoryActions;

		private Builder(String name)
		{
			this.name = name;
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

		public Builder placeholder(boolean v)
		{
			this.placeholder = v;
			return this;
		}

		public Builder storeValue(int v)
		{
			this.storeValue = v;
			return this;
		}

		public Builder slotIdx(int v)
		{
			this.slotIdx = v;
			return this;
		}

		public Builder attack(int stab, int slash, int crush, int magic, int ranged)
		{
			this.attackStab = stab;
			this.attackSlash = slash;
			this.attackCrush = crush;
			this.attackMagic = magic;
			this.attackRanged = ranged;
			return this;
		}

		public Builder strength(int melee, int ranged, float magic)
		{
			this.strength = melee;
			this.rangedStrength = ranged;
			this.magicDamage = magic;
			return this;
		}

		/** Melee defence bonuses - the signature of armour that has no attack bonus at all. */
		public Builder defence(int stab, int slash, int crush)
		{
			this.defenceStab = stab;
			this.defenceSlash = slash;
			this.defenceCrush = crush;
			return this;
		}

		public Builder prayer(int v)
		{
			this.prayer = v;
			return this;
		}

		public Builder actions(String... v)
		{
			this.inventoryActions = v;
			return this;
		}

		public RuntimeItem build()
		{
			return new RuntimeItem(this);
		}
	}
}
