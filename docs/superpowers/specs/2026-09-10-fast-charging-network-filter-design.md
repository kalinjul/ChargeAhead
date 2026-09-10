# Fast-charging network filter + slow mode — design (2026-09-10)

Turns the operator picker into a fast-charging-only list driven by a power
tier, and moves everything below 50 kW into a single opt-in "slow mode"
toggle. Four tasks, in the order they were given:

1. **Operator-Liste nur auf Schnellladenetze beziehen.**
2. **kW-Auswahl nur direkt bei den Ladenetzen anzeigen** (alles andere macht
   keinen Sinn).
3. **kW-Auswahl an reale Säulenstärken anpassen** — Tiers bleiben 50 / 150 / 300
   (100 explizit *nicht*, gibt es real kaum; 11 fällt raus, siehe Slow-Mode).
4. **Slow-Mode direkt in den Drawer** — ein Schalter, der nur Lader < 50 kW zeigt.

## Background — what exists today

- Networks (`NetworkCatalog.Network`: `key`/`name`/`operatorIds`/`nameKeywords`)
  carry **no power information**. Power lives per connector/site
  (`ChargeSpeed`, `MIN_DC_POWER_KW = 50`).
- Power is picked in the drawer as **segments** `11 / 50 / 150 / 300`
  (`DrawerContent.PowerSegments`), driving `ChargeFilters.minPowerKw`
  (default 150). Only `minPowerKw` re-runs the map query.
- Operator picker: `NetworkSettingsScreen` → `NetworksViewModel` → the full
  327-network catalog, with a browse-mode toggle and search. Selection is
  staged and confirmed ("Filter übernehmen"); `NetworkPreferences.allowsSite`
  enforces it when `isActive`.

## The data — classifying networks by power

Each network gets a maintained power ceiling, so we can tell a Schnellladenetz
from an 11/22 kW AC network.

- Add `maxPowerKw: Double?` to `Network` — the highest power observed across
  that network's OCM sample. `isSlow = maxPowerKw != null && maxPowerKw < 50`.
- **Source:** the same OCM POI sample that produced the density numbers, field
  `Connections[].PowerKW`. No new data feed.
- **Curation:** the 37 hand-maintained networks classified by hand; the ~290
  auto-generated singletons computed from OCM. OCM power is user-contributed
  and patchy, so a mislabeled DC entry on a genuinely-slow network can slip
  through — hence the hand pass on the curated head.
- **Sign-off gate:** the network→power table is signed off before any of the
  code below lands, exactly like the original catalog authoring.

## Task 3 — kW tiers

Power selector offers **50 / 150 / 300**. `100` dropped (barely exists in
reality), `11` dropped (below-50 is now the slow mode, not a normal tier).

## Tasks 1 + 2 — selector and list live together on the network screen

The kW selector moves **out of the drawer onto `NetworkSettingsScreen`**, at
the top. Below it, the operator list shows **only networks whose
`maxPowerKw >= selected tier`** — which is fast networks by construction, so
task 1 falls out of task 2. The map's `minPowerKw` follows the selected tier.

Drawer "Netze" therefore opens one screen where power tier and the matching
operators sit together; the drawer no longer carries `PowerSegments`.

**Trade-off (accepted):** min-power is now one tap deeper than today's drawer
segments. Explicitly requested ("only makes sense with the Netze").

## Task 4 — slow mode in the drawer

A single toggle, **off by default**: *"Langsam-Lader anzeigen (< 50 kW)"*.

- **Off (default):** below-50 kW is hidden; map behaves as the fast filter
  above (tier + operator selection).
- **On:** *forget all filter criteria* — ignore the kW tier and the operator
  selection, map shows **only < 50 kW chargers from all networks**.

Scope: slow mode is a **map/browse** state only. Trip planning and Charge-Now
stay fast (they need DC); slow mode does not relax their `MIN_DC_POWER_KW`
floor.

## State & wiring

- `ChargeFilters` gains `slowMode: Boolean = false` (persisted, drives the map
  query alongside `minPowerKw`).
- Map query (`HomeViewModel`): `slowMode` on → query `maxPowerKw < 50`, bypass
  `allowsSite` and `minPowerKw`; off → today's behavior.
- `NetworksViewModel`: filter the presented catalog by
  `maxPowerKw >= filters.minPowerKw`; keep search and staged selection.
- Trip planning / Charge-Now: unchanged (fast only).

## Files (expected)

- `shared/.../domain/NetworkCatalog.kt` — `maxPowerKw` on `Network`, classify all rows
- `shared/.../domain/ChargeFilters.kt` — `slowMode`, tier list 50/150/300
- `shared/.../ui/NetworksViewModel.kt` — filter list by tier
- `androidApp/.../phone/NetworkSettingsScreen.kt` — kW tier selector on top
- `androidApp/.../phone/DrawerContent.kt` — drop `PowerSegments`, add slow toggle
- `shared/.../ui/HomeViewModel.kt` — map query honors slow mode

## Open / to confirm

- Slow-mode label wording ("Langsam-Lader anzeigen (< 50 kW)" vs "11/22 kW Netze").
- Whether the kW tier selector reuses the existing segment component or a new one.
