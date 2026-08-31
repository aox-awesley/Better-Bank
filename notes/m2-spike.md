# M2 spike research — how two shipping plugins reposition bank item widgets

Prior-art analysis of two RuneLite plugins that re-layout the bank client-side, read
end-to-end on their rendering paths:

- `~/bank-tag-custom-layouts` — "Bank Tag Layouts" (BTL), plugin hub
- `~/bank-templates` — "Bank Templates" (BT), plugin hub

Both are pure rendering layers: neither sends an action to the game server. This is
research for SPEC §3.3 / M2, not a design decision.

---

## Bank Tag Layouts

### Rebuild signal

Two script hooks, both at `@Subscribe(priority = -1f)` so they run *after* core Bank Tags:

- **`ScriptPreFired(BANKMAIN_FINISHBUILDING)`** — only to fix the scroll height (below).
- **`ScriptPostFired(BANKMAIN_BUILD)`** — the actual apply, calling
  `applyCustomBankTagItemPositions(false, false)`.

Supporting events: `WidgetLoaded(InterfaceID.BANK)` / `WidgetClosed` register and tear
down mouse/key listeners and null out its created buttons; `ClientTick` for menu entries;
`ConfigChanged` / `ProfileChanged`. Out-of-band redraws are forced with
`bankSearch.layoutBank()` (core `BankSearch`, injectable because the plugin declares
`@PluginDependency(BankTagsPlugin.class)`).

### Obtaining the item widgets

```
client.getWidget(ComponentID.BANK_ITEM_CONTAINER).getDynamicChildren()
    filter: !isHidden() && getItemId() >= 0
```

### What it mutates

Position and visibility only:

- `setOriginalX(getXForIndex(i))` / `setOriginalY(getYForIndex(i))`, then `revalidate()`.
  Fixed 8-wide grid: `x = (i % 8) * 48 + 51`, `y = (i / 8) * 36`.
- `setHidden(true)` + `revalidate()` for widgets not participating in the layout.
- `setOnDragCompleteListener` per item, for drag-to-reposition.
- Scroll height: during the `BANKMAIN_FINISHBUILDING` pre-fire it overwrites
  `client.getIntStack()[size - 9]` — the height argument still sitting on the stack. For
  changes outside a rebuild it calls `container.setScrollHeight(h)` followed by
  `client.runScript(ScriptID.UPDATE_SCROLLBAR, BANK_SCROLLBAR, BANK_ITEM_CONTAINER, scroll)`.
- Its own preview buttons are real widgets:
  `BANK_CONTENT_CONTAINER.createChild(-1, WidgetType.GRAPHIC)`.

It never touches item ids, names, quantities, or actions. Items it cannot back with a real
widget (layout placeholders for items you don't own) are **drawn in Graphics2D** by
`FakeItemOverlay`, an `Overlay` using `drawAfterLayer(BANK_ITEM_CONTAINER)` and
`OverlayLayer.MANUAL`.

### Order of operations

1. Clear `fakeItems` and `indexToWidget`; resolve the active tag to a `LayoutableThing`.
2. Load the saved layout from config; bail if none.
3. Collect visible item widgets.
4. `cleanItemsNotInBankTag` — prune layout entries whose item is no longer tagged
   (skipped while showing a preview).
5. `assignItemPositions`: variant-matching pass, then non-variant pass, producing
   `index -> Widget`.
6. `moveDuplicateItem`, `updateFakeItems`.
7. Attach drag-complete listeners.
8. `setItemPositions`: hide every unlisted child **first**, then set X/Y + `revalidate()`
   on the listed ones.
9. Resize the scrollbar if the computed height changed.
10. Save the layout back to config.

---

## Bank Templates

### Rebuild signal

All on `BANKMAIN_FINISHBUILDING`, at three different priorities:

- **`ScriptPreFired` @ `priority = 1f`** — in a *separate class* (`BankValuePreRenderer`),
  pre-renders out-of-range "virtual" tabs before core Bank sums widget values for the title.
- **`ScriptPreFired` @ `priority = -1f`** — the main render, after Bank Tags has set its title.
- **`ScriptPostFired`** — tab icons, slot counter, title text. Must be post-fire or the
  build clobbers them.

Plus `WidgetLoaded(InterfaceID.BANKMAIN)` (restore virtual tab, force rebuild),
`ItemContainerChanged(BANK)`, `MenuOptionClicked`, `MenuOpened`, `ClientTick`. Same
`bankSearch.layoutBank()` primitive for forced rebuilds.

### Obtaining the item widgets

Walks `client.getWidget(InterfaceID.Bankmain.ITEMS).getChild(i)` from 0, stopping when
`c == null || c.getOriginalHeight() != 32`.

Crucially it reads the item id **from `client.getItemContainer(InventoryID.BANK).getItems()[i]`**,
using child index as bank slot — *not* `widget.getItemId()`, which can be stale from its own
previous render pass. Empty slots are collected into a `Deque` of reusable "spares".

### What it mutates

Much more per widget: `setItemId`, `setName`, `clearActions`, `setItemQuantity`,
`setItemQuantityMode`, `setOpacity`, `setOnDragListener(null)`, `setOnDragCompleteListener`,
plus a hand-rebuilt withdraw menu — `setAction(0..10, ...)` derived from the
`BANK_QUANTITY_TYPE` and `BANK_REQUESTEDQUANTITY` varbits.

Position via the same `setOriginalX` / `setOriginalY` / `revalidate()`, on a
template-defined column count with divider gaps folded into `rowY()`.

It also creates **virtual widgets** (`container.createChild(-1, WidgetType.GRAPHIC)`) for
template slots past the bank's real capacity, tracked in a list and reused, discarded when
the container identity changes. And it repositions the bank's own divider lines, identified
by `getSpriteId() != -1 && originalHeight in 1..4`.

Scroll: the same `intStack[size - 9]` slot, but only ever *grows* it.

### Order of operations

1. `resetWidgets()` — restore any widget whose original W/H it padded, back to 36x32.
2. Sweep children: hide all, build `itemId -> Widget` from container slots, collect spares.
3. Three matcher passes, template item to concrete bank item: **exact, then placeholder,
   then variant**.
4. Per template slot: draw using the matched item's **own** widget where possible
   (preserving child index), else a spare or a virtual widget.
5. Pad the final row so appended items start on a fresh row.
6. Append the player's non-template items below the layout.
7. Hide leftover virtual slots.
8. Reposition divider lines.
9. Grow scroll height on the int stack.

---

## Common to both — likely essential

1. **Script events are the only viable trigger.** Both hook
   `BANKMAIN_BUILD` / `BANKMAIN_FINISHBUILDING`. Neither uses game ticks or item-container
   events to drive layout — the bank rebuilds constantly and stomps anything you did.
2. **`priority = -1f` to run after core Bank Tags.** Both arrived here independently; BTL's
   README documents it as a workaround for Bank Tags' "Remove tab separators" re-collapsing
   layout gaps after their pass.
3. **Movement is `setOriginalX` + `setOriginalY` + `revalidate()`** on the item container's
   existing children. Neither reorders the children list — that is not the API.
4. **Hide, never delete.** Non-participating widgets get `setHidden(true)`; the next native
   build restores them.
5. **Scroll height by overwriting `intStack[size - 9]`** during the pre-fire. Both landed on
   the identical magic offset.
6. **`bankSearch.layoutBank()` as the "redraw now" primitive** — on startUp, shutDown,
   config change, and bank open.
7. **Undo is a rebuild with your state cleared**, not a manual restore. Both `shutDown()`
   this way.
8. **Detect when another plugin owns the bank and stand down entirely.**
9. **Placeholders and item variants are a first-class matching problem**, not an
   afterthought — both have dedicated multi-pass matching.
10. **Same geometry constants:** item 36x32, `startX = 51`, column pitch 48, row pitch 36.

## Differing — likely preference

| | Bank Tag Layouts | Bank Templates |
|---|---|---|
| Apply hook | `PostFired(BANKMAIN_BUILD)` | `PreFired(BANKMAIN_FINISHBUILDING)` |
| Widget handling | Move only; item id / actions untouched | Rewrites id, name, qty, opacity, full action list |
| Withdraw correctness | Free — widget keeps its native identity | Must repair: `remapWithdraw` rewrites `param0` to the real bank slot at click time |
| Unowned items | Graphics2D overlay, manual hit-testing (`getIndexForMousePosition`) and manual menu entries | Real created widgets — clickable, but you own their entire action list |
| Filtered-state detection | `tabInterface.getActiveTag()` via `@PluginDependency(BankTagsPlugin.class)` | Parses the bank title against a title allow-list |
| Chrome | Left alone | Rewrites divider lines, slot counter, tab icons, title |
| Grid width | Hardcoded 8 | From the template |

The overlay-vs-real-widget choice is the deepest fork. BTL's conservatism means it never has
to think about withdraw menus; BT bought fillers, inline unowned placeholders and
over-capacity slots, and paid for them with `remapWithdraw`, the placeholder-vs-real-item bug
(issue #8), and the stale-item-id bug.

---

## Edge cases explicitly handled

### Bank Tag Layouts

- Potion storage tab — bails on `CURRENT_BANK_TAB == 15`
- Bank Tags "Remove tab separators" collapsing gaps after their pass — priority `-1f`
- Inventory Setups' bank title — must run after it; regex-parses
  `Inventory Setup <col=ff0000>...`
- **Profile switch masquerading as a tag rename** — compares
  `configManager.getProfile().getId()` first, or users lose layouts wholesale
- Item dragged above the bank disappearing (README v1.1)
- Anti-drag plugin — reimplements its timing by reading the `antiDrag` config group
  (`dragDelay`, `onShiftOnly`, `disableOnCtrl`)
- Scroll height changing *outside* a rebuild (item moved to a new last row) — explicit
  resize + `UPDATE_SCROLLBAR`
- Excess empty scroll height in laid-out tabs (v1.2.1)
- Duplicate item ids in the container — dedupe before variant assignment
- Its created buttons vanishing when the bank widget reloads — null on `WidgetLoaded`,
  recreate
- Preview mode must not prune layout entries

### Bank Templates

- Real game placeholder rendering as withdrawable (issue #8) — test
  `getPlaceholderTemplateId() != -1`; never trust `count()`
- **Stale widget item id** turning an owned item into a placeholder on the next pass — read
  from the ItemContainer by child index
- Tag tab / search / Inventory Setups / Quest Helper owning the bank — bail *before even*
  `resetWidgets()`
- Title value suffix `(1,567,565,294)` appended post-build — strip trailing parenthetical
  before matching
- Virtual tab reset to all-items on reopen — re-set varbit; and issue #39, don't do it when
  the layout is off
- First bank open not picking up the layout — force `layoutBank()` on `WidgetLoaded`
- Bank container instance changing on reopen — drop stale virtual widget refs
- Post-drag click withdrawing the dragged item — let the native drag run, intercept only
  the drop
- Template larger than bank capacity — virtual widgets
- Menu entries piling up while the cursor sits still — `menuHasOption` guard

Neither handles fixed-vs-resizable mode explicitly — both work off the item container's own
coordinates, which is presumably why. Bank Templates has no potion-storage case; it falls
out of the title allow-list.

---

## Workarounds hinting at API limitations

1. **No API to set bank scroll height.** Both reach into `client.getIntStack()` at
   `size - 9` mid-script — raw coupling to a game script's argument order. The single most
   fragile thing in either plugin.
2. **`setScrollHeight()` alone doesn't take effect** — needs a raw
   `runScript(UPDATE_SCROLLBAR, ...)` with component IDs.
3. **No API for "does another plugin own the bank right now?"** BT parses a user-visible
   display string with an allow-list, explicitly noting the Bank Tags service *isn't
   injectable from an external plugin*. Breaks on any title change.
4. **`BankTagsService` / `TabInterface` require `@PluginDependency` on a core plugin.** BTL
   takes the dependency; BT refuses the coupling and pays in string parsing. Two plugins,
   opposite answers, same missing API.
5. **No API for "the widget showing bank slot i".** Both rely on child index == bank slot,
   an undocumented invariant. BT documents that Inventory Setups depends on it too, and
   preserves it deliberately by moving each item's *own* widget rather than reassigning ids.
6. **No way to identify item widgets** — BT terminates its child sweep on
   `getOriginalHeight() != 32`.
7. **No way to identify divider lines** — "has a sprite, height 1 to 4".
8. **A created widget can't behave like a real bank item.** BT hand-rebuilds eleven withdraw
   actions from varbits *and* still needs click-time `param0` repair. BTL avoids the whole
   class of problem by never creating one — which is exactly why it needs an overlay plus
   manual hit-testing plus synthetic menu entries.
9. **EventBus requires the subscriber method be named `onScriptPreFired`.** One class can't
   hold two priorities, so BT has an entire extra class (`BankValuePreRenderer`) solely to
   get a second one.
10. **`UsedToBeReflection`** — the class name is the tell. BTL used reflection into Bank Tags
    internals; the legal fix was duplicating Bank Tags' config key format (`item_` prefix,
    its config group) and reading it directly. Still coupling, just permitted coupling.
11. **No way to query another plugin's effective state** — BTL reads the `antiDrag` config
    group directly to replicate its drag timing.

---

## Bearing on M2

The spike's riskiest assumption isn't repositioning — both plugins prove that works with
three calls. It's the scroll height, where the only known technique is an undocumented stack
offset, and the withdraw path, where creating your own widgets costs correctness that moving
existing ones gives for free.

If Better Bank's category headers can be drawn as an overlay while every *item* stays on its
own native widget, it avoids most of what bit Bank Templates.
