package com.betterbank.classify;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * The five M5 schemes, plus the two categories every scheme carries.
 */
public class M5SchemesTest
{
	/** Classifies a hand-built item by handing the resolver a matching runtime record. */
	private static String classify(Scheme scheme, RuntimeItem item)
	{
		final AttributeResolver r = new AttributeResolver(OverrideTable.empty(),
			new FakeItemMetadata().put(1, item));
		return new Classifier(r, Assignments.none()).classify(scheme, 1).id();
	}

	// ---- universal categories ------------------------------------------------------

	@Test
	public void everySchemeHasPetsAndQuestClutter()
	{
		for (Scheme scheme : BuiltInSchemes.all())
		{
			assertTrue(scheme.id() + " is missing Pets", scheme.hasCategory(BuiltInSchemes.PETS));
			assertTrue(scheme.id() + " is missing quest clutter",
				scheme.hasCategory(BuiltInSchemes.QUEST_CLUTTER));
		}
	}

	@Test
	public void questCategoryDisplaysAsQuestItemsButKeepsItsPersistedId()
	{
		// The id is persisted in user assignments and must not change with the display name.
		assertEquals("quest-items", BuiltInSchemes.QUEST_CLUTTER);
		for (Scheme scheme : BuiltInSchemes.all())
		{
			assertEquals("Quest Items",
				scheme.category(BuiltInSchemes.QUEST_CLUTTER).name());
		}
	}

	@Test
	public void petsWinOverEverythingInEveryScheme()
	{
		// "Pet <x>" is caught by the name rule, so this needs no bundled row.
		final RuntimeItem pet = RuntimeItem.builder("Pet snakeling").tradeable(false).build();
		for (Scheme scheme : BuiltInSchemes.all())
		{
			assertEquals(scheme.id(), BuiltInSchemes.PETS, classify(scheme, pet));
		}
	}

	@Test
	public void questClutterOnlyClaimsWhatNothingElseWants()
	{
		// A key is dead weight and lands in the clutter bucket...
		assertEquals(BuiltInSchemes.QUEST_CLUTTER,
			classify(BuiltInSchemes.merchant(), RuntimeItem.builder("Rusty key").build()));
		// ...but a quest item that is still usable gear files as gear, because the clutter
		// rule runs last.
		assertEquals("armour", classify(BuiltInSchemes.merchant(),
			RuntimeItem.builder("Ghostspeak amulet key").slotIdx(2).defence(1, 1, 1).build()));
	}

	@Test
	public void questingPutsQuestClutterFirst()
	{
		// Same item, opposite precedence: this is the scheme that exists for it.
		assertEquals(BuiltInSchemes.QUEST_CLUTTER, classify(BuiltInSchemes.questing(),
			RuntimeItem.builder("Ghostspeak amulet key").slotIdx(2).defence(1, 1, 1).build()));
	}

	@Test
	public void rustyKeyAndMiningCertificateFinallyLand()
	{
		final Scheme questing = BuiltInSchemes.questing();
		assertEquals(BuiltInSchemes.QUEST_CLUTTER,
			classify(questing, RuntimeItem.builder("Rusty key").build()));
		assertEquals(BuiltInSchemes.QUEST_CLUTTER,
			classify(questing, RuntimeItem.builder("Mining certificate").build()));
	}

	// ---- Ironman -------------------------------------------------------------------

	@Test
	public void ironmanOrganisesByProductionChainNotBySkill()
	{
		final Scheme ironman = BuiltInSchemes.ironman();
		assertEquals("raw-materials", classify(ironman, RuntimeItem.builder("Iron ore").build()));
		assertEquals("intermediates", classify(ironman, RuntimeItem.builder("Iron bar").build()));
		assertEquals("raw-materials", classify(ironman, RuntimeItem.builder("Yew logs").build()));
		// The same three items are three different skills to a skiller.
		final Scheme skiller = BuiltInSchemes.skiller();
		assertEquals("mining", classify(skiller, RuntimeItem.builder("Iron ore").build()));
		assertEquals("smithing", classify(skiller, RuntimeItem.builder("Iron bar").build()));
		assertEquals("woodcutting", classify(skiller, RuntimeItem.builder("Yew logs").build()));
	}

	@Test
	public void ironmanSeparatesHerbloreSecondariesFromOtherRawMaterials()
	{
		// Secondaries are the bottleneck on every potion, so they get their own bucket even
		// though they are RAW like everything else.
		assertEquals("secondaries", classify(BuiltInSchemes.ironman(),
			RuntimeItem.builder("Grimy ranarr weed").build()));
		assertEquals("raw-materials", classify(BuiltInSchemes.ironman(),
			RuntimeItem.builder("Iron ore").build()));
	}

	@Test
	public void ironmanFinishedGoodsIsReachable()
	{
		// Most finished goods are claimed by the specific buckets above it. This is what is
		// left: something made, neither worn nor consumed.
		final ItemAttributes.Builder b = ItemAttributes.builder("Crafted trinket");
		b.material(MaterialStage.FINISHED);
		final Scheme ironman = BuiltInSchemes.ironman();
		String landed = Scheme.UNCATEGORIZED_ID;
		for (Rule rule : ironman.rules())
		{
			for (String proposed : rule.proposals(b.build()))
			{
				if (ironman.hasCategory(proposed))
				{
					landed = proposed;
					break;
				}
			}
			if (!Scheme.UNCATEGORIZED_ID.equals(landed))
			{
				break;
			}
		}
		assertEquals("finished-goods", landed);
	}

	// ---- PvMer / PKer --------------------------------------------------------------

	@Test
	public void stavesAndBowsFileUnderTheirOwnStyleInEveryCombatScheme()
	{
		final RuntimeItem staff = RuntimeItem.builder("Staff of air").slotIdx(3)
			.attack(-1, -1, 3, 10, 0).defence(2, 2, 2).build();
		final RuntimeItem bow = RuntimeItem.builder("Magic shortbow").slotIdx(3)
			.attack(0, 0, 0, 0, 69).defence(0, 0, 0).build();
		for (Scheme scheme : new Scheme[]{BuiltInSchemes.pvmer(), BuiltInSchemes.pker()})
		{
			assertEquals(scheme.id() + " staff", "magic-gear", classify(scheme, staff));
			assertEquals(scheme.id() + " bow", "ranged-gear", classify(scheme, bow));
		}
	}

	@Test
	public void gearIsSplitByCombatStyle()
	{
		final Scheme pvmer = BuiltInSchemes.pvmer();
		assertEquals("melee-gear", classify(pvmer, RuntimeItem.builder("Bandos chestplate")
			.slotIdx(4).defence(50, 50, 50).build()));
		assertEquals("ranged-gear", classify(pvmer, RuntimeItem.builder("Armadyl chestplate")
			.slotIdx(4).attack(0, 0, 0, 0, 20).build()));
		assertEquals("magic-gear", classify(pvmer, RuntimeItem.builder("Ahrim's robetop")
			.slotIdx(4).attack(0, 0, 0, 15, 0).build()));
		assertEquals("other-gear", classify(pvmer, RuntimeItem.builder("Chef's hat")
			.slotIdx(0).build()));
	}

	@Test
	public void combatSchemesPutTeleportsAheadOfGear()
	{
		// Teleport jewellery is worn, so the gear rules would claim it if teleports did not
		// run first. Both combat schemes order it this way: to a PKer it is the escape, to a
		// PvMer it is how you get to the boss - either way it is travel, not a gear switch.
		final RuntimeItem glory =
			RuntimeItem.builder("Amulet of glory(4)").slotIdx(2).defence(3, 3, 3).build();
		assertEquals("teleports", classify(BuiltInSchemes.pker(), glory));
		assertEquals("teleports", classify(BuiltInSchemes.pvmer(), glory));
		// A combat amulet with no teleport still files as gear.
		assertEquals("melee-gear", classify(BuiltInSchemes.pker(),
			RuntimeItem.builder("Amulet of fury").slotIdx(2).attack(10, 10, 10, 0, 0).build()));
	}

	@Test
	public void combatSchemesHaveSomewhereToPutSkillingTools()
	{
		// 30 tools sitting in Uncategorized was a rule gap, not missing data.
		for (Scheme scheme : new Scheme[]{BuiltInSchemes.pvmer(), BuiltInSchemes.pker()})
		{
			assertEquals(scheme.id(), "tools",
				classify(scheme, RuntimeItem.builder("Tinderbox").build()));
		}
	}

	// ---- Collection Log ------------------------------------------------------------

	@Test
	public void collectionLogSeparatesPetsFromUntradeables()
	{
		final Scheme log = BuiltInSchemes.collectionLog();
		// A pet is untradeable and would match Untradeables, but the universal pets rule runs
		// before every scheme's own rules, so Pets always wins.
		assertEquals(BuiltInSchemes.PETS,
			classify(log, RuntimeItem.builder("Pet snakeling").tradeable(false).build()));
		assertEquals("untradeables",
			classify(log, RuntimeItem.builder("Barrows gloves").tradeable(false).build()));
	}

	@Test
	public void collectionLogBucketsTheRestRatherThanDumpingIt()
	{
		// The scheme is inherently narrow; without a catch-all most of a bank would read as
		// Uncategorized, which looks broken rather than "not collection log".
		assertEquals("other-items", classify(BuiltInSchemes.collectionLog(),
			RuntimeItem.builder("Shark").actions("Eat").build()));
	}

	@Test
	public void merchantLeadsWithCurrencyThenTeleports()
	{
		final java.util.List<Category> categories = BuiltInSchemes.merchant().categories();
		assertEquals("currency", categories.get(0).id());
		assertEquals("teleports", categories.get(1).id());
		// The rest keep their previous relative order.
		assertEquals("armour", categories.get(2).id());
		assertEquals("weapons", categories.get(3).id());
		assertEquals("tools", categories.get(4).id());
		assertEquals("runes", categories.get(5).id());
		assertEquals("consumables", categories.get(6).id());
		assertEquals("resources", categories.get(7).id());
	}

	@Test
	public void merchantHasNoStatBoostersCategory()
	{
		// Pinned deliberately: the reorder asked for Stat boosters third, and this scheme has
		// no such category. Nothing was invented to fill the slot.
		assertEquals(false, BuiltInSchemes.merchant().hasCategory("stat-boosters"));
		assertEquals(true, BuiltInSchemes.questing().hasCategory("stat-boosters"));
	}

	@Test
	public void allSevenSchemesAreDistinctAndResolvable()
	{
		assertEquals(7, BuiltInSchemes.all().size());
		for (Scheme scheme : BuiltInSchemes.all())
		{
			assertEquals(scheme.id(), BuiltInSchemes.byId(scheme.id()).id());
		}
	}
}
