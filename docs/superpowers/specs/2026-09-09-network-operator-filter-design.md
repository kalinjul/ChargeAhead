# Charging network filter with source-side filtering

**Date:** 2026-09-09
**Status:** Design, approved (interview 2026-09-09)

## Problem

A driver wants to declare *their* charging networks up front — tick EnBW and
Ionity because they hold subscriptions there — on the very first launch,
before the app has loaded any data, and then never touch the filter again.

Today the Networks screen can't support that:

- The pick-list is derived live from the chargers in your *current
  surroundings*. On a fresh install it's empty, and it only ever reflects the
  patch of map you're looking at — so there's nothing to tick on day one.
- A network like Shell or E.ON appears as a dozen separate rows (one per
  country subsidiary), so a single "I use Shell" is impossible to express.
- Every charger in an area is downloaded and then filtered on-device, so a
  two-network subscriber still pays for ~2.5 MB of chargers they'll never see.

## What we learned from the data (OCM, live, 2026-09-09)

- The OCM operator reference list is **997 rows of `(ID, name)` with no
  geography** — every operator's address is null. So the operator list alone
  can never tell a German network from an American one.
- Every charging site (POI) carries a stable `OperatorID`. POI operator names
  match the reference list's names exactly (2427/2427 in the sample, zero
  mismatches). So the IDs are a clean, exact join key — no fuzzy matching
  needed for OCM. (OCM already *sends* `OperatorID` on the POI; the app's DTO
  just doesn't deserialize it yet.)
- The genuinely fragmented brands are the pan-European ones: Shell Recharge
  (~12 country IDs), E.ON (~7), Tesla (2–3). EnBW is a single ID (86).
- OCM's POI query accepts an `operatorid=` filter and returns **exactly** those
  operators. For EnBW+Ionity around Munich that was 15 chargers / **74 KB**
  versus 500 chargers / **2.5 MB** unfiltered — a ~97% saving.
- The app has a **second** source besides OCM: **Bnetza** (Bundesnetzagentur,
  the German federal charging registry). Germany-only, authoritative, but
  operator is **free text with no IDs**, so it can only be filtered by name.

## Design

### The operator catalog (bundled, hardcoded)

A curated, hardcoded `OperatorCatalog` in Kotlin source — the same shape as the
existing `VehicleCatalog`: a shipped, maintained list, present on first launch,
offline, no fetch required. Updating it needs an app release, which is fine —
charging networks don't churn weekly and the grouping is curated by hand anyway.

The catalog is **not per-country**. Because the 997 operators have no
geography, a country picker can't be populated from them; the earlier
per-country design is dropped. The catalog is a flat curated list of the
common, selectable networks, with the fragmented brands' IDs merged into one
row each.

One row per *selectable network*:

- `key` — a stable slug (`"enbw"`, `"shell-recharge"`, `"ionity"`). This is the
  identity threaded through preferences and the cache; it never changes on a
  display rename.
- `name` — canonical display name ("EnBW", "Shell Recharge", "Ionity").
- `operatorIds` — the set of OCM operator IDs that are this network
  (`shell-recharge -> [156, 157, 47, 3392, 59, …]`). The fragmented brands
  group many; EnBW is a singleton `[86]`.
- `nameKeywords` — keywords for matching no-ID chargers, i.e. Bnetza
  (`["enbw"]`, `["ionity"]`).

Authored from the OCM reference list plus a curated grouping of the multi-ID
brands, ranked by real charger density (so the picker leads with the networks a
driver is likely to hold). Each grouped brand gets a test asserting its ID set
(E.ON, Shell, Tesla, EnBW, Ionity). Long-tail operators are simply not in the
catalog — the picker is a curated common set, not an exhaustive 997.

### The picker (Networks screen)

- Shows the whole catalog — a flat curated list. No country dimension, no
  device-region detection.
- Selection is **staged**: ticking a network builds a pending set in the
  ViewModel and does **not** touch settings or the map yet.
- A **"Confirm filters"** button commits the pending set. It is enabled only
  when the pending set differs from the committed one, signalling "unsaved
  changes". Committing is the single moment the filter becomes real.

This reverses today's apply-on-toggle behaviour — deliberately, so that a
filter change reads as the significant, map-reshuffling action it is.

### Fetching (on confirm, and on normal map movement)

The committed network set drives what we request:

- **OpenChargeMap** — pass the union of the selected networks' `operatorIds`
  as `operatorid=`. Only those chargers are downloaded.
- **Bnetza** — has no IDs, but it's an ArcGIS service with a SQL-like `where`
  clause. Extend the current `Status='In Betrieb'` with a name filter on the
  `Betreiber` field — `UPPER(Betreiber) LIKE '%<KEYWORD>%'` OR-ed over the
  selected networks' `nameKeywords` — so Bnetza filters **at the source too**,
  not by downloading all of Germany. `UPPER()` for case-insensitivity;
  keywords are our own list but still quote-escaped. Free-text names mean
  `LIKE '%ENBW%'` catches every "EnBW … GmbH" variant, which is the intent.
- **No selection** — behave as today: fetch everything, filter nothing.
- On **Confirm**, refetch the **currently rendered map area** with the new
  set; the map visibly reloads.

### Matching / display rule

A charger is shown iff it matches a selected network by **ID** (OCM,
`operatorId ∈ network.operatorIds`) or by **name keyword** (Bnetza,
`UPPER(operator)` contains a selected network's keyword). With both sources
filtered at the source, a charger matching neither never arrives — consistent
with the rule. The on-device `allows()` check keeps this rule for the merge and
for any already-cached rows.

The trade-off: because non-matches aren't downloaded, we can't directly count
how many we're hiding. If we ever want that number (to judge whether keyword
lists are too narrow), it takes an **occasional unfiltered probe** to compare
counts — not built now.

### Caching

The on-device tile cache currently assumes "I fetched *everything* in this
area." Source-side filtering breaks that, so coverage is tracked **per
network**: the "fetched here" stamp becomes `(sourceId, networkKey, tile)`
instead of `(sourceId, tile)`. `networkKey` is the catalog `key`. The
unfiltered case (no selection) stamps under a sentinel key `"*"` so today's
behaviour keeps working without colliding with per-network stamps. An area is
covered for the current selection when every selected network is stamped and
fresh on every tile in view.

This makes filter changes incremental instead of a wipe:

- **Adding a network** (EnBW → EnBW+Ionity): EnBW's stamps stand, so EnBW
  never refetches. Only networks not yet stamped on the visible tiles are
  fetched. For OCM that's a **single** `operatorid=` call for the union of the
  missing networks (comma list), after which each fetched network's tiles are
  stamped. Bnetza likewise fetches only the missing networks' keywords.
- **Removing a network**: nothing fetches and nothing invalidates mid-session —
  the query stops selecting it. Its data is reclaimed by the next startup prune
  (below), so within a session re-adding is instant; re-adding after a restart
  refetches.

Costs, both small: the coverage table grows to ~tiles × selected-networks rows
(still kB, and only for networks actually picked); and a Bnetza station whose
free-text name matches two selected networks' keywords is stamped under **all**
matched networks. The existing 3-day tile TTL applies per `(networkKey, tile)`.
No whole-cache wipe on filter change.

### Cache eviction (size)

Freshness (the 3-day window) only decides what may be *served*; it never
deletes, so without eviction the charger cache grows monotonically as the
driver covers ground. A prune runs **on app startup** and deletes any charger
row and coverage stamp that is *either*:

- **stale** — older than the freshness window (refetched anyway if revisited),
  *or*
- **orphaned by selection** — its `networkKey` is not in the current selection
  (can't be shown, so dead weight).

Both classes are unshowable, so deletion loses nothing functional. The cache
settles at "fresh chargers, for currently-selected networks, near where the
driver has recently been." This also subsumes filter-change cleanup: a
deselected network's data disappears at the next startup, gently, with no
mid-use wipe.

Running on startup (not per toggle) avoids thrashing while the driver edits the
selection. Deletes are cheap `DELETE … WHERE` statements; charger rows carry a
`fetchedAtMillis` so age pruning needs no tile math.

Deliberately **not** built yet: a hard size/row cap with LRU eviction. Because
filtering means we store only the driver's own networks, growth is slow (low
tens of MB even for heavy use); add a cap only if real measurements demand it.

The TTL stays at **3 days** (`DEFAULT_TTL_MILLIS`), unchanged. It now does
double duty — servable-freshness and prune-retention — which is acceptable at
this value.

## Components touched

- **`OperatorCatalog`**: new bundled, hardcoded Kotlin list (authored from OCM
  data), read statically. Follows the `VehicleCatalog` precedent. No Room
  table, no seeding, no country field.
- **`OcmPoi` / mapping**: **add** `OperatorID` to the DTO (currently not
  deserialized) and carry it onto `ChargeSite`.
- **`OpenChargeMapSource.query`**: accept an optional operator-ID set and pass
  `operatorid=`.
- **`BnetzaSource`**: extend the `where` clause with a `UPPER(Betreiber) LIKE`
  keyword filter (quote-escaped) when a set is active.
- **`TiledSiteRepository` / `TileCoverageEntity`**: add `networkKey` to the
  coverage key so coverage is per network; the covered-check ANDs over the
  selected networks; adding a network fetches only the missing ones, removing
  fetches nothing.
- **`MergingSiteRepository`**: thread the selected set to each source.
- **`NetworkPreferences`**: `preferredOperators` becomes canonical catalog
  `key`s, not name-normalized keys. `allows()` / resolution maps a site to a
  network via ID (OCM) or keyword (Bnetza).
- **`NetworksViewModel`**: reads the bundled catalog (whole list), holds the
  pending selection, exposes the Confirm action and its enabled state. No
  longer derives the pick-list from surrounding sites.
- **Startup prune**: a maintenance step hung at the pipeline entry
  (`ChargeStopsFeatureFactory.create`, after the DB is created) that deletes
  stale or unselected-network charger rows and coverage stamps.
- **Room migration** v1 → v2, **destructive**: the cache tables are pure
  regenerable cache and only just moved to Room, so bump the version with
  `fallbackToDestructiveMigration` (drop + recreate) rather than hand-written
  `ALTER TABLE`. New shape: `networkKey` on tile coverage; `operatorId` +
  `fetchedAtMillis` on `chargeSite`. The cache refills on next map view.

`OperatorKey`'s algorithmic normalization is superseded on the keyword path by
the catalog's `nameKeywords`; keep it only where still referenced, otherwise
retire it.

## Testing

- Unit: catalog ID grouping per brand (E.ON, Shell, Tesla, EnBW, Ionity);
  keyword matching incl. accent/case folding; the "matches neither → hidden"
  rule; "no selection → everything".
- Repository: coverage key varies with the network set; adding a network
  fetches only the missing ones and stamps them; removing fetches nothing;
  stale-on-error behaviour preserved.
- Source: OCM query emits `operatorid=` for the selected set; Bnetza keeps only
  keyword matches.
- ViewModel: staging does not touch settings; Confirm commits and enables only
  on a real change.
- A live contract test (opt-in, like the existing OCM one) asserting
  `operatorid=` still filters server-side.

## Deliberately out of scope

- A per-country picker / device-region detection (the 997 have no geography, so
  it can't be populated; dropped).
- Fetching/caching the full 997 for *names* (POIs already carry canonical
  names; the curated catalog covers the picker).
- "Remember networks as you drive" / continent accumulation.
- Active prefetch of a continent's chargers.
- A hard size cap with LRU eviction.

## Honest caveat

This is not "every network, complete, on first launch" — that dataset doesn't
exist in usable form. It's "a curated, bundled list of the common networks,
pickable immediately, that filters both sources correctly and downloads only
what you'll see."
