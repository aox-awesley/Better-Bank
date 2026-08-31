# Better Bank — RuneLite Plugin Spec

Working brief for implementation. Read this before writing any code.

---

## 1. What this plugin is

Better Bank gives the OSRS bank an **automatic category system**. Items are
classified into sensible categories out of the box — no manual tagging — and the
bank interface can be re-rendered grouped by those categories with headers.
Players can override any classification and define their own categories.

The differentiator vs. existing plugins: **Bank Tags requires you to hand-tag
every item.** Better Bank ships knowing what a shark is.

---

## 2. Hard constraints — read first

These are not preferences. Violating them means the plugin is rejected from the
Plugin Hub and puts users' accounts at risk.

**The plugin must never cause an action to be sent to the game server.**

Jagex's third-party client guidelines prohibit adding menu entries that cause
server actions, and clients that generate input to the game applet are treated
as botting software.

Concretely, this means:

| Not allowed | Legal equivalent |
| --- | --- |
| Button that reorders the real bank | Re-render the bank view client-side; server state untouched |
| "Withdraw all items for X" | Filter the view to those items, highlight them, user clicks |
| Any automated click, drag, or swap | Nothing. Never synthesize input. |

**Everything Better Bank does is a rendering and bookkeeping layer.** The real
bank on the server is never modified by the plugin. This is also better UX:
sorting is instant, costs no server round-trips, and is reversible.

Secondary constraints:

- Java 11. RuneLite targets it; newer JDKs cause Gradle friction.
- `build=standard` in `runelite-plugin.properties`. No third-party dependencies.
  Non-transitive deps require cryptographic hash verification and significantly
  slow Hub review.
- Bundle resources under `src/main/resources` and read them with
  `getResourceAsStream`, never `getResource`. Deployed plugins run from inside a
  jar and are not unpacked on disk.
- Anything touching the client must run on the client thread.

---

## 3. Architecture

Four modules, deliberately separable so the risky part is isolated.

### 3.1 Categorizer (pure Java, no game API)

Item ID in, category out. No RuneLite types in its signature beyond an item ID
and a small injected metadata interface. **Fully unit-testable with no client
running** — this is where most iteration should happen.

Classification signals, applied in priority order:

1. **User override** — explicit per-item assignment. Always wins.
2. **Curated static table** — bundled JSON mapping item IDs to categories for
   things that cannot be inferred (quest items, skill supplies, clue gear).
   This is the bulk of the real work.
3. **Metadata inference** — equipment slot, tradeability, members status,
   noted/placeholder state, name pattern matching.
4. **Fallback** — `UNCATEGORIZED`.

The static table should be a plain JSON resource so it can be regenerated and
diffed without touching code.

### 3.2 Default taxonomy

Top-level categories, each with optional subcategories:

- **Currency & Valuables** — coins, platinum tokens, tokkul
- **Combat Gear** — Melee / Ranged / Magic, then by equipment slot
- **Ammunition & Runes**
- **Consumables** — Food / Potions
- **Skilling** — subcategory per skill
- **Teleports & Transport**
- **Quest Items**
- **Treasure Trails**
- **Untradeables & Achievement**
- **Uncategorized** — the honest dumping ground; keep it visible so users can
  see what the categorizer missed and report it

Every category is enable/disable-able and reorderable by the user.

### 3.3 View layer (highest risk)

Hooks the bank build script and regroups the item widgets under category
headers. Core Bank Tags and the Hub's Bank Tag Layouts both manipulate the bank
widget tree, so this is possible — but the exact degree of layout control is
**unverified and must be spiked before anything is built on top of it**.

Fallback if re-layout proves too fragile: filter-only mode (hide non-matching
items, one category at a time), which is a proven pattern and still useful.

### 3.4 Override store

Per-item and per-category user edits, persisted via `ConfigManager`. Must
survive client restarts and be scoped per profile. Keep the serialization format
simple and forward-compatible — users will accumulate overrides and you cannot
break them later.

---

## 4. Milestones

**M0 — Environment.** Java 11 JDK, repo generated from
`runelite/example-plugin` template, `./gradlew run` opens a dev client with the
stub plugin visible in the sidebar. Done when you can toggle the stub on and off
in-game.

**M1 — Scaffold.** Rename package and classes, update `pluginMainClass` in
`build.gradle`, fill `runelite-plugin.properties`, set `runeLiteVersion` to
`'latest.release'`. Add a config interface with one dummy toggle and confirm it
renders in the settings panel. No logic yet — this proves the wiring.

**M2 — Spike: bank re-layout.** Timeboxed. Prove you can inject a header widget
and reposition bank item widgets without the interface breaking on scroll,
search, resize, tab switch, or bank close/reopen. **If this fails, stop and
switch to filter-only mode before writing the categorizer.** Do not skip this
milestone or defer it.

**M3 — Categorizer.** Pure logic plus unit tests. Build the static table
incrementally: start with a few hundred common items, not the whole game. Ship a
categorizer that is right about sharks and honest about obscurities.

**M4 — Wire together.** Categorizer drives the view. Sort toggle in config.
Verify against a real bank.

**M5 — Overrides UI.** Right-click an item in the bank to reassign its category;
sidebar panel to manage categories.

**M6 — Harden.** Edge cases below, then README, BSD-2-Clause license, 48x72
`icon.png`, and the Hub manifest PR.

V2 candidates, explicitly out of scope for v1: bank value breakdown, item
finder, search upgrades, Quest Helper integration (needs its own feasibility
research — cross-plugin dependency on the Hub is not a solved problem).

---

## 5. Edge cases to test

These break bank plugins in practice:

- Login / logout / world hop with bank open
- Plugin toggled on and off while bank is open
- Fixed vs. resizable mode, and resizing mid-session
- Bank search active while sorting is on
- Native bank tabs vs. plugin categories interacting
- Placeholders enabled and disabled
- Noted items
- Scrolling far down a large bank
- Empty bank, and a bank with a single item
- Deposit box and other bank-like interfaces that must be left alone

---

## 6. Definition of done for v1

- Real bank state provably never modified — no code path sends a server action
- Categorizer has unit test coverage independent of the game client
- Every edge case above manually verified
- Plugin cleanly unregisters everything in `shutDown()`; toggling off leaves the
  bank exactly as vanilla
- No third-party dependencies; `build=standard`
