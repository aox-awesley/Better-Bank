# Better Bank — RuneLite Plugin Spec (v2)

Supersedes v1. Working brief for implementation — read before writing any code.

---

## 1. What this plugin is

Better Bank lets you view your bank organised by a taxonomy that matches how you
actually play, switch between taxonomies instantly, and edit any of them freely.

**The one-sentence positioning** (use it in the README and the Hub PR):

> Pick the taxonomy that matches how you play, switch it whenever, edit it
> freely.

Existing plugins each impose a single fixed organisation. Bank Tags requires
hand-tagging every item. The skill-tabs plugin ships one taxonomy — by skill —
which is only useful if you're a skiller. Better Bank ships several, makes them
switchable at any time, and treats all of them as fully editable starting
points.

---

## 2. Hard constraints — read first

**The plugin must never cause an action to be sent to the game server.**

Jagex's third-party guidelines prohibit adding menu entries that cause server
actions, and clients that generate input to the game applet are treated as
botting software.

Everything here is a **rendering layer**. The real bank on the server is never
modified. No reordering, no withdrawing, no synthesised clicks or drags.

Both reference implementations confirm this is the accepted technique — read
the bank-templates README, which states plainly that it only reads the bank and
repositions item widgets client-side.

Build constraints:

- Java 11 via SDKMAN (`sdk use java 11.0.32+1.1-tem`). Not 25.
- `build=standard`, no third-party dependencies. Non-transitive deps require
  hash verification and significantly slow Hub review.
- Bundle resources under `src/main/resources`, read via `getResourceAsStream` —
  deployed plugins run from inside a jar and are not unpacked.
- Anything touching the client runs on the client thread.

---

## 3. Core model

Four concepts. Getting these right makes everything else straightforward.

### Scheme

A named taxonomy: an ordered set of categories plus the rules that assign items
to them. The user picks an active scheme; switching is instant and
**non-destructive** — every scheme retains its own edits.

Built-in schemes ship editable. Editing one does not lock the user out of
returning to its shipped state; keep a reset path.

### Category

Belongs to a scheme. Has a name, display order, optional colour, and matching
rules. Users can add, remove, rename, and reorder categories within any scheme.

### Attribute table (the key design decision)

**Do not build one item→category table per scheme.** That is seven
data-entry projects that drift out of sync.

Build **one item→attributes table**, bundled as JSON. Attributes include:

- equipment slot and whether equippable
- combat style relevance (melee / ranged / magic)
- skill relevance (which skills the item is used for)
- tradeable, members, stackable, noted
- quest item, clue reward, untradeable/achievement
- consumable class (food, potion)
- raw material vs. intermediate vs. finished product

Each scheme is then a **mapping from attributes to categories**. Skiller maps
skill attributes to skill categories. Merchant maps equipment-slot attributes to
broad "Armour" / "Weapons" buckets. Same data, different lens.

Adding an eighth scheme becomes a config file, not a data project. This is the
single highest-leverage decision in the plugin.

### Assignment

An explicit user override: this item goes in this category, in this scheme.
Always wins over rules.

**Resolution order:** user assignment → scheme rules → attribute inference →
`Uncategorized`.

Keep `Uncategorized` visible. It shows users what the data misses and gives them
something concrete to report.

---

## 4. Built-in schemes

| Scheme | Organising principle |
| --- | --- |
| **Skiller** | By skill — Herblore, Fishing, Mining, Farming, etc. |
| **Ironman** | By production chain — raw materials, secondaries, intermediates, finished goods |
| **PvMer** | Gear switches, supplies, boss-specific loadouts |
| **PKer** | Risk tiers, gear setups, food and brews, teleports |
| **Merchant** | Broad buckets — Armour, Weapons, Tools, Runes, Consumables, Resources |
| **Questing** | Quest items, teleports, stat boosters, key items |
| **Collection log** | Untradeables, pets, clue rewards, achievement items |

**Ironman is likely the strongest.** Ironmen can't buy anything, so raw
materials and intermediate products matter in a way they don't for mains, and no
existing bank tool is built for accounts that don't use the GE.

**Merchant is deliberately coarse.** A flipper's bank is mostly coins plus a
rotating handful of held items; fine-grained taxonomy adds nothing. Broad
buckets only.

---

## 5. Sorting within a category

Default: **descending by stack value** (quantity × unit price, via the client's
item price data — no network dependency).

Two decisions this forces:

**Live vs. pinned.** Prices move, so items move. Muscle memory is a large part
of why people like fixed bank layouts — sharks being reliably *there* has real
value. Offer both modes:

- *Live* — re-sorts as prices change
- *Pinned* — sorts by value once, then holds position until the user re-sorts

Pick a default deliberately and document why.

**Untradeables have no price.** They resolve to zero and would all sink to the
bottom of every category. Give them an explicit rule — sort them alphabetically
in their own block, or by a fallback — rather than letting the default swallow
them.

Alternative sort modes (alphabetical, quantity, recently added) are cheap once
the sort is pluggable. Build the seam; ship value and alphabetical.

---

## 6. Customization UI

**Note:** the Plugin Hub is a distribution directory, not a settings surface.
All customization lives in the plugin's own config panel and sidebar UI.

Required:

- Scheme picker — switch active scheme in one click
- Category editor — add, remove, rename, reorder, recolour
- Item assignment — right-click an item in the bank to reassign it
- Reset — return a built-in scheme to its shipped state
- Import/export — schemes as text, so users can share them

Import/export is worth building early. It turns one user's careful taxonomy into
something the whole community can use, and it's the cheapest growth mechanism a
Hub plugin has.

---

## 7. Architecture

Four modules, deliberately separable.

1. **Attribute data** — bundled JSON, item → attributes. Pure data.
2. **Classifier** — `(scheme, itemId) → category`. Pure Java, no game API, fully
   unit-testable with no client running. Most iteration happens here.
3. **View layer** — hooks the bank rebuild, regroups item widgets under category
   headers, applies sort. Highest risk. See M2.
4. **Store** — schemes, categories, assignments, active scheme. Persisted via
   ConfigManager. Version the format from the first write; users will accumulate
   months of edits and you cannot break them later.

---

## 8. Milestones

**M0 — Environment.** ✅ Done. Java 11, dev client builds and runs.

**M1 — Scaffold.** ✅ Done. Plugin registers, config group persists.

**M2 — Spike: bank re-layout.** Timeboxed. Prove you can inject a header widget
and reposition bank item widgets without breaking on scroll, search, resize, tab
switch, or bank close/reopen.

Start from the reference implementations rather than from scratch —
`bank-tag-custom-layouts` and `bank-templates` both do this and are merged on
the Hub. Hub review threads on bank plugins point at the relevant API surface:
`TabInterface.getActiveTag`, a low-priority subscriber on `ScriptPostFired`, and
`bankSearch.layoutBank`.

**If re-layout proves too fragile, fall back to filter-only mode** (show one
category at a time) before building anything on top. Decide this before M3.

**M3 — Attribute table and classifier.** Pure logic plus unit tests. Build the
table incrementally — a few hundred common items first, not the whole game. Ship
a classifier that is right about sharks and honest about obscurities.

**M4 — Two schemes end to end.** Skiller and Merchant — maximally different, so
they prove the attribute→category mapping actually generalises. Wire the
classifier to the view. Add value sorting.

**M5 — Remaining schemes.** Ironman, PvMer, PKer, Questing, Collection log. If
M4's design is right these are config, not code. If they aren't, the mapping
layer needs rework — better to learn it here than at M7.

**M6 — Customization UI.** Scheme picker, category editor, right-click
assignment, reset, import/export.

**M7 — Harden and publish.** Edge cases below, README with the positioning
statement, BSD-2-Clause license, 48x72 `icon.png`, Hub manifest PR.

---

## 9. Edge cases to test

- Login, logout, world hop with bank open
- Plugin toggled on/off while bank is open
- Scheme switched while bank is open
- Fixed vs. resizable mode; resizing mid-session
- Bank search active while grouping is on
- Native bank tabs and Bank Tags tag tabs interacting with the plugin view
- Placeholders on and off
- Noted items
- Scrolling far down a large bank
- Empty bank; single-item bank
- Deposit box and other bank-like interfaces — must be left alone
- Item with no price data
- Category containing zero owned items

---

## 10. Definition of done for v1

- No code path sends a server action
- Classifier unit-tested independent of the game client
- Every edge case above manually verified
- Scheme switching is non-destructive — edits survive a round trip
- Store format versioned
- Clean `shutDown()` — toggling off leaves the bank exactly vanilla
- No third-party dependencies; `build=standard`
