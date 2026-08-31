# Better Bank

**Pick the taxonomy that matches how you play, switch it whenever, edit it freely.**

Better Bank groups your bank into categories automatically — no hand-tagging — and lets you
switch between several different organising schemes instantly. Every scheme is a fully
editable starting point, not a fixed layout.

Existing plugins each impose a single organisation. Bank Tags requires you to tag every item
by hand. The skill-tabs plugin ships one taxonomy, by skill, which only helps if you're a
skiller. Better Bank ships seven, makes them switchable at any time, and treats all of them
as editable.

## It never touches your real bank

**Better Bank is a rendering layer. It never modifies the real bank, and no code path sends
an action to the game server.**

It reads your bank and repositions the item widgets on your own screen. It does not reorder
the bank, withdraw, deposit, or synthesise any click, drag, or keypress. Switching schemes,
assigning an item to a category, and every other action in the plugin change local
configuration only. The bank as the server sees it is never altered.

The one script the plugin runs is the client's own scrollbar-update routine, used to resize
the scroll region to fit a taller grouped layout. Menu entries the plugin adds use
`MenuAction.RUNELITE`, which RuneLite handles internally and never forwards to the server.

## Schemes

| Scheme | Organising principle |
| --- | --- |
| **Skiller** | By skill — Herblore, Fishing, Mining, Farming, … |
| **Ironman** | By production chain — raw materials, secondaries, intermediates, finished goods |
| **Merchant** | Broad market buckets — Currency, Teleports, Armour, Weapons, Tools, Runes, … |
| **PvMer** | Gear switches by combat style, supplies, ammunition |
| **PKer** | Gear setups, food and brews, teleports first |
| **Questing** | Quest items, teleports, stat boosters |
| **Collection Log** | Untradeables, pets, clue rewards, achievement items |

Every scheme also carries **Pets**, **Quest Items** and **Uncategorized**. Uncategorized is
deliberately visible: it shows you what the data missed, and gives you something concrete to
report.

## Using it

- **Group by category** in the plugin config turns the grouped view on.
- **Switch schemes in the bank** with the icon in the strip to the left of the item grid.
  Hover it to see the current scheme, click to cycle, right-click to reset.
- **Reassign an item** by right-clicking it in the bank → *Assign to category*.
- **Edit a scheme** from the sidebar panel: add, rename, reorder, hide and recolour
  categories, see what you've assigned by hand, and unassign.
- **Share a scheme** with Export / Import in the sidebar. Imports are validated strictly and
  either apply completely or not at all.
- **Reset** returns any scheme to its shipped state. Your edits to other schemes are
  untouched — every scheme is stored independently.

Sorting within a category is by stack value, descending. Untradeables have no price, so they
are kept together at the end of their category rather than sinking to the bottom.

## Screenshots

<!-- TODO: add screenshots before opening the Hub PR.
     Suggested: grouped bank under Skiller; the same bank under Merchant; the sidebar
     category editor; the right-click Assign to category submenu. -->

_Screenshots to be added._

## Where the categories come from

Most item attributes are derived at runtime from the client itself — equipment slot and
combat bonuses from `ItemStats`, food and potions from the item's own inventory options,
tradeable/stackable/noted from the item definition. A small bundled table
(`src/main/resources/com/betterbank/item-attributes.json`) covers only what the client cannot
tell you: skill relevance, production stage, and a handful of genuine exceptions.

### Regenerating the pet list

Pets are the exception to "derive at runtime". Nothing in the client's item data identifies a
pet: there is no flag, the `Metamorphosis` inventory option only covers metamorphic pets, and
the `"Pet …"` name prefix misses every skilling pet (Beaver, Heron, Rocky, Tangleroot). So
pets are a generated block of item ids inside the bundled table.

**That block goes stale whenever Jagex adds a pet.** To regenerate it, extract the pet
constants from the RuneLite API jar and re-emit those rows:

```
javap -classpath <runelite-api.jar> -constants net.runelite.api.gameval.ItemID \
  | grep -oP 'int \K[A-Z0-9_]+ = \d+'
```

Pet constants are those matching `SKILLPET*`, `*_PET`, or `*PET`, excluding the false
positives `CARPET`, `PETITION`, `TRUMPET`, `COMPETITION`, `PETECANDLE`, `PETAL` and
`PETTICOAT`. Each becomes a row of `{"name": "<CONSTANT>", "tradeable": false, "pet": true}`.
The `name` field is documentation only — the client's display name always wins at runtime.

A pet added after the last regeneration falls back to the `"Pet …"` name rule, so boss pets
named that way still work; skilling pets will land in Uncategorized until the block is
refreshed.

## Building

Java 11:

```
sdk use java 11.0.32+1.1-tem
./gradlew build
```

`./gradlew run` launches a development client with the plugin loaded. See
[Using Jagex Accounts](https://github.com/runelite/runelite/wiki/Using-Jagex-Accounts) to log
in to it.

## License

BSD-2-Clause. See [LICENSE](LICENSE).
