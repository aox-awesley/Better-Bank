package com.betterbank.classify;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Attributes inferred from the item's name, for the things stats cannot tell you.
 *
 * <p>The client knows an item's slot and bonuses, but nothing about <i>what skill it is
 * for</i> or <i>where it sits in a production chain</i>. A rune bar and a rune platebody are
 * both untradeable-free, unequippable-or-not metal objects to the item definition; only the
 * name says one is a Smithing intermediate.
 *
 * <p>This is the lowest-precedence layer: anything the bundled overrides or the runtime
 * derivation has an opinion on wins over it. Every rule lives in {@link #RULES} - one list,
 * in priority order, each entry a matcher plus what it means.
 *
 * <p><b>Ordering is load-bearing.</b> Rules are applied in list order and each one only
 * fills fields still unset, so put the specific before the general: "Grimy guam leaf" must
 * be caught by the grimy rule before anything else claims it.
 */
public final class NamePatterns
{
	private NamePatterns()
	{
	}

	/** One name rule: what it matches, and what that implies. */
	static final class Rule
	{
		final String description;
		final Predicate<String> matches;
		final Consumer<ItemAttributes.Builder> apply;

		Rule(String description, Predicate<String> matches, Consumer<ItemAttributes.Builder> apply)
		{
			this.description = description;
			this.matches = matches;
			this.apply = apply;
		}
	}

	private static Rule rule(String description, Predicate<String> matches,
		Consumer<ItemAttributes.Builder> apply)
	{
		return new Rule(description, matches, apply);
	}

	/** True when the name ends with {@code word}, as a whole word. */
	private static Predicate<String> endsWith(String word)
	{
		return n -> n.equals(word) || n.endsWith(" " + word);
	}

	/**
	 * True when any whole word of the name is {@code word}.
	 *
	 * <p>Splits on non-letters rather than spaces, so a charge or dose suffix counts as a word
	 * boundary: "Skills necklace(4)" contains the word "necklace". Splitting on spaces alone
	 * missed those, and a plain substring test would find "ring" inside "Herring".
	 */
	private static Predicate<String> hasWord(String word)
	{
		return n ->
		{
			for (String token : n.split("[^a-z']+"))
			{
				if (token.equals(word))
				{
					return true;
				}
			}
			return false;
		};
	}

	private static Predicate<String> startsWith(String prefix)
	{
		return n -> n.startsWith(prefix);
	}

	private static Predicate<String> anyOf(List<Predicate<String>> parts)
	{
		return n ->
		{
			for (Predicate<String> p : parts)
			{
				if (p.test(n))
				{
					return true;
				}
			}
			return false;
		};
	}

	@SafeVarargs
	private static Predicate<String> any(Predicate<String>... parts)
	{
		return anyOf(Arrays.asList(parts));
	}

	/**
	 * Teleport jewellery. None of these names contains "teleport", and the client exposes no
	 * "has a teleport" flag, so a name list is the only signal available.
	 *
	 * <p>Matched as a fragment so every charge variant is covered by one entry - "Amulet of
	 * glory", "Amulet of glory(4)" and "Amulet of glory (t)" all hit the same rule. Doing this
	 * with bundled rows would mean one row per charge per item, and several of these families
	 * do not use predictable item-id constant names.
	 */
	private static final List<String> TELEPORT_JEWELLERY = Collections.unmodifiableList(Arrays.asList(
		"amulet of glory", "games necklace", "ring of dueling", "skills necklace",
		"combat bracelet", "necklace of passage", "digsite pendant", "burning amulet",
		"slayer ring"));

	/**
	 * Worn-jewellery words. Charged jewellery carries the same "(4)" suffix as a potion dose,
	 * so the dose rule has to exclude these or every Amulet of glory(4) reads as a potion.
	 */
	private static final List<String> JEWELLERY_WORDS = Collections.unmodifiableList(Arrays.asList(
		"amulet", "necklace", "ring", "bracelet", "pendant", "medallion", "sceptre"));

	private static boolean isJewellery(String n)
	{
		for (String word : JEWELLERY_WORDS)
		{
			if (hasWord(word).test(n))
			{
				return true;
			}
		}
		return false;
	}

	static final List<Rule> RULES = Collections.unmodifiableList(Arrays.asList(

		// --- production chains -------------------------------------------------------
		rule("logs -> Woodcutting/Firemaking/Fletching, raw",
			any(endsWith("logs"), endsWith("log")),
			b -> b.skills(SkillType.WOODCUTTING, SkillType.FIREMAKING, SkillType.FLETCHING)
				.material(MaterialStage.RAW)),

		rule("ore -> Mining/Smithing, raw",
			endsWith("ore"),
			b -> b.skills(SkillType.MINING, SkillType.SMITHING).material(MaterialStage.RAW)),

		rule("bar -> Smithing, intermediate",
			endsWith("bar"),
			b -> b.skills(SkillType.SMITHING).material(MaterialStage.INTERMEDIATE)),

		rule("essence -> Runecraft, raw",
			endsWith("essence"),
			b -> b.skills(SkillType.RUNECRAFT).material(MaterialStage.RAW)),

		rule("seed / sapling -> Farming, raw",
			any(endsWith("seed"), endsWith("seeds"), endsWith("sapling"), endsWith("seedling")),
			b -> b.skills(SkillType.FARMING).material(MaterialStage.RAW)),

		rule("grimy herb -> Herblore/Farming, raw",
			startsWith("grimy "),
			b -> b.skills(SkillType.HERBLORE, SkillType.FARMING).material(MaterialStage.RAW)),

		rule("bones / ashes -> Prayer, raw",
			any(endsWith("bones"), endsWith("ashes")),
			b -> b.skills(SkillType.PRAYER).material(MaterialStage.RAW)),

		// Plain suffix, not a whole word: "Green dragonhide" and "Cowhide" are one word.
		rule("hide / leather -> Crafting",
			any(n -> n.endsWith("hide"), n -> n.endsWith("hides"), n -> n.endsWith("leather")),
			b -> b.skills(SkillType.CRAFTING).material(MaterialStage.RAW)),

		rule("uncut gem -> Crafting/Mining, raw",
			startsWith("uncut "),
			b -> b.skills(SkillType.CRAFTING, SkillType.MINING).material(MaterialStage.RAW)),

		// --- runes and ammunition ----------------------------------------------------
		// "... rune" as a whole trailing word, so "Rune platebody" is untouched.
		rule("rune -> stackable Magic/Runecraft rune",
			endsWith("rune"),
			b -> b.rune(true).stackable(true)
				.skills(SkillType.MAGIC, SkillType.RUNECRAFT)
				.material(MaterialStage.FINISHED)),

		rule("arrow / bolt / dart / javelin -> ammunition",
			any(endsWith("arrow"), endsWith("arrows"), endsWith("bolt"), endsWith("bolts"),
				endsWith("dart"), endsWith("darts"), endsWith("javelin"), endsWith("javelins")),
			b -> b.styles(CombatStyle.RANGED).material(MaterialStage.FINISHED)),

		// --- consumables -------------------------------------------------------------
		// The runtime layer catches these by their Eat/Drink action; these rules add the
		// Herblore relevance the action cannot imply, and cover an unrecognised item id.
		// The dose suffix must not fire on charged jewellery: "Amulet of glory(4)" is not a
		// potion, and reading it as one filed every charged glory under Herblore.
		rule("potion / brew / dose suffix -> Herblore consumable",
			n -> !isJewellery(n)
				&& (hasWord("potion").test(n) || hasWord("brew").test(n)
					|| hasWord("potions").test(n)
					|| n.endsWith("(1)") || n.endsWith("(2)")
					|| n.endsWith("(3)") || n.endsWith("(4)")),
			b -> b.consumable(ConsumableClass.POTION).skills(SkillType.HERBLORE)
				.material(MaterialStage.FINISHED)),

		// --- tools -------------------------------------------------------------------
		// Exclusions matter: "battleaxe"/"throwing axe" are weapons, "warhammer" is a weapon.
		rule("axe -> Woodcutting tool",
			n -> endsWith("axe").test(n) && !n.contains("battleaxe") && !n.contains("throwing")
				&& !n.contains("thrownaxe") && !n.contains("pickaxe"),
			b -> b.tool(true).skills(SkillType.WOODCUTTING)),

		rule("pickaxe -> Mining tool",
			endsWith("pickaxe"),
			b -> b.tool(true).skills(SkillType.MINING)),

		rule("harpoon / fishing rod / net / lobster pot -> Fishing tool",
			any(endsWith("harpoon"), endsWith("rod"), endsWith("net"), endsWith("pot")),
			b -> b.tool(true).skills(SkillType.FISHING)),

		rule("tinderbox -> Firemaking tool",
			endsWith("tinderbox"),
			b -> b.tool(true).skills(SkillType.FIREMAKING)),

		rule("saw / chisel / needle / glassblowing pipe -> Crafting tool",
			any(endsWith("saw"), endsWith("chisel"), endsWith("needle"), endsWith("pipe")),
			b -> b.tool(true).skills(SkillType.CRAFTING)),

		rule("hammer -> Smithing tool",
			n -> endsWith("hammer").test(n) && !n.contains("warhammer"),
			b -> b.tool(true).skills(SkillType.SMITHING)),

		rule("rake / secateurs / dibber / watering can -> Farming tool",
			any(endsWith("rake"), endsWith("secateurs"), endsWith("dibber"),
				endsWith("can"), endsWith("spade")),
			b -> b.tool(true).skills(SkillType.FARMING)),

		rule("pestle and mortar / vial -> Herblore tool",
			any(endsWith("mortar"), endsWith("vial"), startsWith("vial")),
			b -> b.tool(true).skills(SkillType.HERBLORE)),

		// Trailing word only. "Bucket of sand" and "Jug of wine" are contents, not the
		// empty container, and belong to whatever chain their contents belong to.
		rule("bucket / jug / bowl -> generic tool",
			any(endsWith("bucket"), endsWith("jug"), endsWith("bowl")),
			b -> b.tool(true)),

		// --- transport and oddments --------------------------------------------------
		rule("teleport -> teleport item",
			any(hasWord("teleport"), endsWith("tablet")),
			b -> b.teleport(true).skills(SkillType.MAGIC).material(MaterialStage.FINISHED)),

		// Teleport jewellery is worn gear that also teleports. Deliberately sets no skill:
		// adding MAGIC here would move every charged glory out of a skiller's combat gear.
		rule("teleport jewellery -> teleport item",
			n ->
			{
				for (String fragment : TELEPORT_JEWELLERY)
				{
					if (n.contains(fragment))
					{
						return true;
					}
				}
				return false;
			},
			b -> b.teleport(true)),

		rule("key -> quest/key item",
			any(endsWith("key"), endsWith("keys")),
			b -> b.questItem(true)),

		rule("certificate -> quest item",
			any(endsWith("certificate"), endsWith("certificates")),
			b -> b.questItem(true)),

		// Catches the boss pets literally named "Pet <boss>". It does NOT catch skilling pets
		// (Beaver, Heron, Rocky, Tangleroot) or most metamorphic pets, whose display names
		// share nothing - those come from the generated pet list in the override table.
		rule("\"Pet <x>\" name prefix -> pet",
			startsWith("pet "),
			b -> b.pet(true).tradeable(false)),

		rule("clue scroll / casket -> treasure trails",
			any(startsWith("clue scroll"), startsWith("casket"), startsWith("reward casket")),
			b -> b.clueReward(true))
	));

	/**
	 * Applies every matching rule onto {@code out}, in list order.
	 *
	 * <p>Rules do not clobber each other's fields in practice because each targets a
	 * different production chain, but order is still fixed so behaviour is deterministic and
	 * testable.
	 */
	public static void apply(String name, ItemAttributes.Builder out)
	{
		if (name == null || name.isEmpty())
		{
			return;
		}
		final String n = name.toLowerCase(Locale.ROOT).trim();
		for (Rule rule : RULES)
		{
			if (rule.matches.test(n))
			{
				rule.apply.accept(out);
			}
		}
	}

	/** Which rules match a name. Exposed so tests and debugging can name the rule. */
	public static List<String> matching(String name)
	{
		final List<String> out = new ArrayList<>();
		if (name == null)
		{
			return out;
		}
		final String n = name.toLowerCase(Locale.ROOT).trim();
		for (Rule rule : RULES)
		{
			if (rule.matches.test(n))
			{
				out.add(rule.description);
			}
		}
		return out;
	}
}
