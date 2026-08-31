# Better Bank — in-game test matrix

Every case from SPEC §9, plus the plugin's own surfaces. **None of this has been verified —
it all needs a running client.** Work through it and note anything that does not match the
expected result.

Launch with `./gradlew run` (Java 11: `sdk use java 11.0.32+1.1-tem`), log in per
[Using Jagex Accounts](https://github.com/runelite/runelite/wiki/Using-Jagex-Accounts), and
enable **Group by category** in the plugin config. Run the client with `--debug` to get the
switcher's position logging.

---

## A. SPEC §9 edge cases

| # | Case | Steps | Expected |
| --- | --- | --- | --- |
| A1 | Login with bank open | Open bank, log out, log back in | Grouped layout returns; no duplicate headers or icons |
| A2 | Logout with bank open | Open bank, log out | No errors in console; nothing left drawn |
| A3 | World hop with bank open | Open bank, hop worlds | Layout rebuilds correctly on the new world |
| A4 | Plugin toggled off with bank open | Open bank, disable plugin in config | Bank returns to **exactly vanilla** — original order, no headers, no scheme icon, scrollbar correct |
| A5 | Plugin toggled on with bank open | With bank open, enable plugin | Grouped layout appears without needing to reopen the bank |
| A6 | Scheme switched with bank open | Click the scheme icon repeatedly | Layout regroups each time; exactly one icon visible throughout; icon never overlaps items |
| A7 | Fixed mode | Set fixed mode, open bank | Icon sits in the left strip beside the grid; headers aligned; scroll reaches the last row |
| A8 | Resizable mode | Set resizable, open bank | As A7 |
| A9 | Resize mid-session | Open bank, resize the window several times | Icon and headers follow; no overlap at any size |
| A10 | Bank search active | Open bank, press the search button, type | Plugin **stands down**: normal ungrouped search results, no headers, no scheme icon |
| A11 | Native bank tabs | Click through the numbered tabs | Each tab groups independently; no leftover headers from the previous tab |
| A12 | Bank Tags tag tab | Install/enable Bank Tags, open a tag tab | Plugin **stands down**: Bank Tags' own layout is untouched |
| A13 | Placeholders on | Enable placeholders, withdraw all of an item | Placeholder classifies as the real item, not as its own thing |
| A14 | Placeholders off | Disable placeholders | No empty slots left floating in the grouped layout |
| A15 | Noted items | Deposit some noted items | Noted item groups with its unnoted form |
| A16 | Scroll a large bank | Open a bank with many items, scroll to the very bottom | Last row fully reachable; headers scroll with content; scheme icon does **not** scroll away |
| A17 | Empty bank | Open a bank with nothing in it | No headers, no errors; scheme icon still usable |
| A18 | Single-item bank | Bank with exactly one item | One header, correct count and value |
| A19 | Deposit box | Open a deposit box | **Completely untouched** — no headers, no icon, no repositioning |
| A20 | Other bank-like interfaces | Open the grand exchange, seed vault, looting bag | Untouched |
| A21 | Item with no price data | Hover an untradeable (Fire cape, quest item) | Tooltip reads "No price data"; item sorts in the untradeable block at the end of its category, not scattered at the bottom |
| A22 | Category with zero items | Switch to a scheme with a category your bank cannot fill (e.g. Collection Log with no pets) | Empty category renders **no header at all** rather than an empty one |
| A23 | Potion storage | Open the potion storage tab | Plugin stands down; potion store renders normally |

## B. Scheme switcher

| # | Case | Expected |
| --- | --- | --- |
| B1 | Position | Icon sits in the empty strip left of the item grid, level with the top of the grid. Never over an item |
| B2 | Exactly one icon | Cycle all seven schemes; only one icon visible at any moment. `--debug` log shows `visibleWidgets=1` every pass |
| B3 | Hover tooltip | Hovering shows `Scheme: <name>` and "Click to switch" |
| B4 | Icon per scheme | Each of the seven shows a distinct sprite (tools / ironman helm / coins / swords / skull / compass / crown) |
| B5 | Persistence | Switch scheme, fully restart the client | Same scheme still active |
| B6 | Right-click reset | Right-click the icon → "Reset scheme" | Chatbox asks Yes/No; "No" changes nothing |
| B7 | Reset with nothing to reset | Right-click reset on an unedited scheme | Chat message says there is nothing to reset |
| B8 | Deferred placement | Open the bank for the very first time after login | Icon appears correctly positioned, never in the middle of the grid |

## C. Right-click assignment

| # | Case | Expected |
| --- | --- | --- |
| C1 | Entry appears | Right-click a bank item | "Assign to category" with a submenu of the active scheme's categories |
| C2 | Assign | Pick a category | Item moves there immediately; **no game action occurs** — nothing withdrawn, no chat, no XP drop |
| C3 | Current marked | Right-click an already-assigned item | Its category is marked "(current)" |
| C4 | Clear | Pick "Clear assignment" | Item returns to where the rules put it |
| C5 | Not on foreign views | Right-click an item during a bank search or on a Bank Tags tag tab | No Better Bank entry appears |
| C6 | Persistence | Assign, restart the client | Assignment still applied |

## D. Per-scheme independence — the important one

| # | Steps | Expected |
| --- | --- | --- |
| D1 | Assign 2–3 items in **Merchant**. Switch to **Skiller**. Assign different items. Switch back to Merchant. | Merchant's assignments are **exactly** as you left them |
| D2 | Rename and reorder categories in Merchant, switch away and back | Edits intact |
| D3 | Reset Skiller | Skiller returns to shipped state; **Merchant untouched** |
| D4 | Restart the client after D1–D3 | All of the above still true |

## E. Sidebar panel

| # | Case | Expected |
| --- | --- | --- |
| E1 | Panel opens | Better Bank icon in the sidebar opens the panel |
| E2 | Scheme picker | Changing it switches the scheme and regroups the bank |
| E3 | Rename | Rename a category; bank header updates |
| E4 | Reorder | ↑ / ↓ move a category; bank order follows; Uncategorized stays last |
| E5 | Hide | Hide a category; it disappears from the bank and its items fall to another category or Uncategorized. It remains listed in the panel so it can be unhidden |
| E6 | Colour | Set a colour; swatch updates |
| E7 | Add category | Add one; it appears, empty, and only receives items you assign by hand |
| E8 | Assigned items list | Expand a category (▶) | Lists items you assigned by hand |
| E9 | Unassign | ✕ on an assigned item | Item returns to rule-based placement |
| E10 | Reset | Reset button asks for confirmation; "No" changes nothing |
| E11 | Export | Copies to clipboard; paste it somewhere and confirm it is JSON |
| E12 | Import round trip | Export, reset the scheme, import | Scheme restored exactly |
| E13 | Import garbage | Copy random text, import | Clear error message; **nothing changes** |
| E14 | Import truncated JSON | Copy half an exported scheme, import | Clear error; nothing partially applied |
| E15 | UI responsiveness | Use the panel while the bank is open | No client freeze or stutter |

## F. Compliance spot-checks

| # | Case | Expected |
| --- | --- | --- |
| F1 | No server actions | Play normally with the plugin on for a session | No unexplained animations, withdrawals, chat, or XP |
| F2 | Real bank untouched | Group the bank, disable the plugin, log out and back in on another client | Bank order on the server is unchanged |
| F3 | Console clean | Watch the `--debug` console through a full session | No exceptions, no `AssertionError` |
| F4 | Toggle stress | Enable/disable the plugin 10× with the bank open | No accumulating widgets; bank still vanilla when off |

---

## Known-unverified

Everything above. In particular these have never run against a real client:

- The scheme switcher's final position — the cauldron-derived path never executed in the last
  session, and the current vertical placement derives from grid bounds that were only ever
  read from one log line.
- The `Metamorphosis` fallback for metamorphic pets — the inventory-option name is assumed.
- Whether the `ICON_*` sprites render legibly at 20×20.
- Any behaviour of the sidebar panel, which has no automated coverage at all.
