package com.betterbank.classify;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * The schemes that ship with the plugin (SPEC §4).
 *
 * <p>Every one of these is a declaration over the shared attribute layers - no scheme owns
 * item data, and none of them needed new derivation logic. That is the property SPEC §3 is
 * built to protect: a shark is a Cooking product to a skiller, a consumable to a flipper, a
 * finished good to an ironman and a supply to a PKer, from one set of attributes.
 */
public final class BuiltInSchemes
{
	public static final String SKILLER_ID = "skiller";
	public static final String MERCHANT_ID = "merchant";
	public static final String IRONMAN_ID = "ironman";
	public static final String PVMER_ID = "pvmer";
	public static final String PKER_ID = "pker";
	public static final String QUESTING_ID = "questing";
	public static final String COLLECTION_LOG_ID = "collection-log";

	/** Category ids every scheme carries. */
	public static final String PETS = "pets";
	public static final String QUEST_CLUTTER = "quest-items";

	private BuiltInSchemes()
	{
	}

	public static List<Scheme> all()
	{
		return Arrays.asList(skiller(), ironman(), merchant(), pvmer(), pker(), questing(),
			collectionLog());
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

	// ---- universal categories ------------------------------------------------------

	/**
	 * Categories every scheme gets, present and future. Added here rather than repeated in
	 * each scheme so a new scheme cannot forget them.
	 *
	 * <p>The quest bucket keeps the id {@code quest-items} - it is persisted and must not
	 * change - while displaying as "Quest Items".
	 */
	private static List<Category> universalCategories()
	{
		return Arrays.asList(
			new Category(PETS, "Pets"),
			new Category(QUEST_CLUTTER, "Quest Items"));
	}

	/**
	 * Assembles a scheme with the universal categories and rules in place.
	 *
	 * <p>Rule order is deliberate. Pets run <b>first</b>: a pet is unambiguous and nothing
	 * else should claim it. Quest clutter runs <b>last</b>: an item that is still useful gear
	 * or a teleport should file as that, and only fall into the clutter bucket when nothing
	 * else wants it.
	 */
	private static Scheme scheme(String id, String name, List<Category> categories, List<Rule> rules)
	{
		final List<Category> allCategories = new ArrayList<>(categories);
		allCategories.addAll(universalCategories());

		final List<Rule> allRules = new ArrayList<>();
		allRules.add(Rule.when(ItemAttributes::pet, PETS));
		allRules.addAll(rules);
		allRules.add(Rule.when(ItemAttributes::questItem, QUEST_CLUTTER));

		return new Scheme(id, name, allCategories, allRules);
	}

	// ---- shared rule fragments -----------------------------------------------------

	private static Rule currency()
	{
		return Rule.when(ItemAttributes::currency, "currency");
	}

	private static Rule teleports()
	{
		return Rule.when(ItemAttributes::teleport, "teleports");
	}

	private static Rule runes()
	{
		return Rule.when(ItemAttributes::rune, "runes");
	}

	private static Rule ammunition()
	{
		return Rule.when(a -> a.slot() == EquipmentSlot.AMMUNITION, "ammunition");
	}

	private static Rule consumables(String categoryId)
	{
		return Rule.when(a -> a.consumable() != ConsumableClass.NONE, categoryId);
	}

	/**
	 * Gear split by combat style.
	 *
	 * <p>Proposals follow the item's own {@link ItemAttributes#styles()} order, which is
	 * ranked by dominance - so a hybrid lands under the style it mainly serves rather than
	 * under whichever style this rule happened to test first. A fixed melee-first ordering
	 * here is what filed every magic staff as melee gear.
	 */
	private static Rule gearByStyle()
	{
		return a ->
		{
			if (!a.equippable())
			{
				return Collections.emptyList();
			}
			final List<String> out = new ArrayList<>(a.styles().size() + 1);
			for (CombatStyle style : a.styles())
			{
				out.add(styleCategory(style));
			}
			// Equippable with no combat bonuses at all: skilling outfits, cosmetics, chef's hat.
			out.add("other-gear");
			return out;
		};
	}

	private static String styleCategory(CombatStyle style)
	{
		switch (style)
		{
			case RANGED:
				return "ranged-gear";
			case MAGIC:
				return "magic-gear";
			default:
				return "melee-gear";
		}
	}

	private static List<Category> gearCategories()
	{
		return Arrays.asList(
			new Category("melee-gear", "Melee gear"),
			new Category("ranged-gear", "Ranged gear"),
			new Category("magic-gear", "Magic gear"),
			new Category("other-gear", "Other gear"));
	}

	private static String displayName(SkillType skill)
	{
		final String lower = skill.categoryId();
		return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
	}

	// ---- Skiller -------------------------------------------------------------------

	/**
	 * By skill. An item goes under its most relevant skill before anything else - cooked shark
	 * is Cooking, raw shark is Fishing.
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

		return scheme(SKILLER_ID, "Skiller", categories, Arrays.asList(
			currency(),
			Rule.bySkill(),
			Rule.when(ItemAttributes::equippable, "combat-gear"),
			consumables("consumables")));
	}

	// ---- Ironman -------------------------------------------------------------------

	/**
	 * By production chain, not by skill (SPEC §4). An ironman cannot buy anything, so what
	 * matters is how far along the chain a thing is: what you gathered, what feeds a recipe,
	 * and what is ready to use.
	 */
	public static Scheme ironman()
	{
		final List<Category> categories = Arrays.asList(
			new Category("currency", "Currency"),
			new Category("raw-materials", "Raw materials"),
			new Category("secondaries", "Secondaries"),
			new Category("intermediates", "Intermediates"),
			new Category("consumables", "Consumables"),
			new Category("combat-gear", "Combat gear"),
			new Category("tools", "Tools"),
			new Category("runes", "Runes"),
			new Category("teleports", "Teleports"),
			new Category("finished-goods", "Finished goods"));

		return scheme(IRONMAN_ID, "Ironman", categories, Arrays.asList(
			currency(),
			runes(),
			teleports(),
			Rule.when(ItemAttributes::tool, "tools"),
			// Herblore secondaries are raw materials, but an ironman thinks of them as their
			// own problem - they are the bottleneck on every potion.
			Rule.when(a -> a.material() == MaterialStage.RAW
				&& a.skills().contains(SkillType.HERBLORE), "secondaries"),
			Rule.when(a -> a.material() == MaterialStage.RAW, "raw-materials"),
			Rule.when(a -> a.material() == MaterialStage.INTERMEDIATE, "intermediates"),
			consumables("consumables"),
			Rule.when(ItemAttributes::equippable, "combat-gear"),
			// The tail of the chain. Most finished goods are claimed by the specific buckets
			// above (a cooked shark is a consumable, a platebody is gear), so this holds what
			// an ironman made that is neither worn nor consumed.
			Rule.when(a -> a.material() == MaterialStage.FINISHED, "finished-goods")));
	}

	// ---- Merchant ------------------------------------------------------------------

	/** Broad market buckets. Deliberately coarse (SPEC §4). */
	public static Scheme merchant()
	{
		// Display order: Currency, then Teleports, then the rest as they were. There is no
		// Stat boosters category in this scheme - see the report; nothing was invented for it.
		final List<Category> categories = Arrays.asList(
			new Category("currency", "Currency"),
			new Category("teleports", "Teleports"),
			new Category("armour", "Armour"),
			new Category("weapons", "Weapons"),
			new Category("tools", "Tools"),
			new Category("runes", "Runes"),
			new Category("consumables", "Consumables"),
			new Category("resources", "Resources"));

		return scheme(MERCHANT_ID, "Merchant", categories, Arrays.asList(
			currency(),
			runes(),
			teleports(),
			Rule.when(ItemAttributes::tool, "tools"),
			Rule.when(a -> a.slot() == EquipmentSlot.WEAPON || a.slot() == EquipmentSlot.AMMUNITION,
				"weapons"),
			Rule.when(ItemAttributes::equippable, "armour"),
			consumables("consumables"),
			Rule.when(a -> a.material() == MaterialStage.RAW
				|| a.material() == MaterialStage.INTERMEDIATE, "resources")));
	}

	// ---- PvMer ---------------------------------------------------------------------

	/**
	 * Gear switches and supplies (SPEC §4).
	 *
	 * <p><b>Boss-specific loadouts are not built.</b> Nothing in the attribute layer says
	 * which boss an item is for, and deriving it is not possible - it would take a curated
	 * item-to-boss table, which is the data project SPEC §3 exists to avoid. See the M5 report.
	 */
	public static Scheme pvmer()
	{
		final List<Category> categories = new ArrayList<>();
		categories.add(new Category("currency", "Currency"));
		categories.addAll(gearCategories());
		categories.add(new Category("ammunition", "Ammunition"));
		categories.add(new Category("runes", "Runes"));
		categories.add(new Category("supplies", "Supplies"));
		categories.add(new Category("teleports", "Teleports"));
		categories.add(new Category("tools", "Tools"));
		categories.add(new Category("resources", "Resources"));

		final List<Rule> rules = new ArrayList<>();
		rules.add(currency());
		rules.add(ammunition());
		rules.add(runes());
		rules.add(teleports());
		rules.add(consumables("supplies"));
		rules.add(Rule.when(ItemAttributes::tool, "tools"));
		rules.add(gearByStyle());
		rules.add(Rule.when(a -> a.material() == MaterialStage.RAW
			|| a.material() == MaterialStage.INTERMEDIATE, "resources"));
		return scheme(PVMER_ID, "PvMer", categories, rules);
	}

	// ---- PKer ----------------------------------------------------------------------

	/**
	 * Gear setups, food and brews, teleports (SPEC §4).
	 *
	 * <p><b>Risk tiers are not built.</b> Ranking by what an item is worth to lose needs live
	 * market prices, and the classifier has none - it is pure and takes no price source. Price
	 * does exist in the view layer, where it drives sorting, so this is a plumbing decision
	 * rather than a data gap. See the M5 report.
	 */
	public static Scheme pker()
	{
		final List<Category> categories = new ArrayList<>();
		categories.add(new Category("currency", "Currency"));
		categories.addAll(gearCategories());
		categories.add(new Category("ammunition", "Ammunition"));
		categories.add(new Category("runes", "Runes"));
		categories.add(new Category("food-and-brews", "Food & brews"));
		categories.add(new Category("teleports", "Teleports"));
		categories.add(new Category("tools", "Tools"));
		categories.add(new Category("resources", "Resources"));

		final List<Rule> rules = new ArrayList<>();
		rules.add(currency());
		// Teleports outrank gear, as in PvMer: most teleports are worn jewellery, and the gear
		// rules would otherwise claim every glory and games necklace as an amulet switch.
		rules.add(teleports());
		rules.add(ammunition());
		rules.add(runes());
		rules.add(consumables("food-and-brews"));
		rules.add(Rule.when(ItemAttributes::tool, "tools"));
		rules.add(gearByStyle());
		rules.add(Rule.when(a -> a.material() == MaterialStage.RAW
			|| a.material() == MaterialStage.INTERMEDIATE, "resources"));
		return scheme(PKER_ID, "PKer", categories, rules);
	}

	// ---- Questing ------------------------------------------------------------------

	/**
	 * Quest items, teleports, stat boosters, key items (SPEC §4).
	 *
	 * <p>This is where "Rusty key" and "Mining certificate" finally land: the name rules
	 * already mark them {@code questItem}, and this is the first scheme with a bucket for it.
	 *
	 * <p>SPEC lists "quest items" and "key items" separately. Nothing distinguishes them -
	 * both are undroppable quest baggage - so they share one bucket rather than inventing an
	 * attribute to split them.
	 */
	public static Scheme questing()
	{
		final List<Category> categories = Arrays.asList(
			new Category("currency", "Currency"),
			new Category("teleports", "Teleports"),
			new Category("stat-boosters", "Stat boosters"),
			new Category("consumables", "Food"),
			new Category("combat-gear", "Combat gear"),
			new Category("tools", "Tools"),
			new Category("runes", "Runes"),
			new Category("resources", "Resources"));

		return scheme(QUESTING_ID, "Questing", categories, Arrays.asList(
			currency(),
			// Quest clutter first in this scheme only: it is the reason the scheme exists.
			Rule.when(ItemAttributes::questItem, QUEST_CLUTTER),
			teleports(),
			Rule.when(a -> a.consumable() == ConsumableClass.POTION, "stat-boosters"),
			consumables("consumables"),
			runes(),
			Rule.when(ItemAttributes::tool, "tools"),
			Rule.when(ItemAttributes::equippable, "combat-gear"),
			Rule.when(a -> a.material() == MaterialStage.RAW
				|| a.material() == MaterialStage.INTERMEDIATE, "resources")));
	}

	// ---- Collection Log ------------------------------------------------------------

	/**
	 * Untradeables, pets, clue rewards, achievement items (SPEC §4).
	 *
	 * <p><b>Interaction with Pets:</b> a pet is untradeable and would match Untradeables, but
	 * the universal pets rule runs before every scheme's own rules, so pets always land in
	 * Pets. Collection Log's Untradeables bucket is therefore "untradeable, and not a pet, a
	 * clue reward or an achievement item" - which is what makes the four buckets readable
	 * side by side instead of one swallowing the others.
	 *
	 * <p>The scheme is inherently narrow - most of a bank is ordinary tradeable goods - so it
	 * ends with an explicit catch-all rather than dropping the majority of the bank into
	 * Uncategorized, which would read as broken rather than as "not collection log".
	 */
	public static Scheme collectionLog()
	{
		final List<Category> categories = Arrays.asList(
			new Category("clue-rewards", "Clue rewards"),
			new Category("achievements", "Achievement items"),
			new Category("untradeables", "Untradeables"),
			new Category("other-items", "Everything else"));

		return scheme(COLLECTION_LOG_ID, "Collection Log", categories, Arrays.asList(
			Rule.when(ItemAttributes::clueReward, "clue-rewards"),
			Rule.when(ItemAttributes::achievement, "achievements"),
			Rule.when(a -> !a.tradeable(), "untradeables"),
			Rule.when(a -> true, "other-items")));
	}
}
