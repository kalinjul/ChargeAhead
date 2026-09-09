# Per-country charging network filter with source-side filtering

**Date:** 2026-09-09
**Status:** Design, pending review

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
- Every charging site (POI) carries a stable `OperatorID` **and** its own
  country/continent. POI operator names match the reference list's names
  exactly (2427/2427 in the sample, zero mismatches). So the IDs are a clean,
  exact join key — no fuzzy matching needed for OCM.
- The genuinely fragmented brands are the pan-European ones: Shell Recharge
  (~12 country IDs), E.ON (~7), Tesla (2–3). EnBW is a single ID (86).
- OCM's POI query accepts an `operatorid=` filter and returns **exactly** those
  operators. For EnBW+Ionity around Munich that was 15 chargers / **74 KB**
  versus 500 chargers / **2.5 MB** unfiltered — a ~97% saving.
- The app has a **second** source besides OCM: **Bnetza** (Bundesnetzagentur,
  the German federal charging registry). Germany-only, authoritative, but
  operator is **free text with no IDs**, so it can only be filtered by name.

## Design

### The operator table (bundled, on-device)

A curated table shipped with the app so it's present on first launch, offline,
with no fetch required. One row per *selectable network*:

- `name` — canonical display name ("EnBW", "Shell Recharge", "Ionity").
- `operatorIds` — the set of OCM operator IDs that are this network
  (`shell -> [156,157,47,3392,59,…]`). The big brands group many; every
  long-tail operator is its own singleton row, so all 997 are representable and
  nothing is silently unfilterable.
- `nameKeywords` — keywords for matching no-ID chargers ("enbw", "ionity").
- `countries` — ISO country codes where this network is common, for the
  per-country picker. Pan-European networks are tagged in all their countries.

Authored from the OCM reference list (the 997) plus a curated grouping of the
multi-ID brands and per-country ranking from real charger density. Each
grouped brand gets a test asserting its ID set. A background refresh of the raw
operator list can add new singletons over time; the *grouping* stays curated.

### The picker (Networks screen)

- Shows the networks whose `countries` include the user's current country.
- Country is taken from the **device region setting** (no location permission,
  available instantly on first launch) and is manually overridable.
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
  `LIKE '%enbw%'` catches every "EnBW … GmbH" variant, which is the intent.
- **No selection** — behave as today: fetch everything, filter nothing.
- On **Confirm**, refetch the **currently rendered map area** with the new
  set; the map visibly reloads.

### Matching / display rule

A charger is shown iff it matches a selected network by **ID** (OCM) or by
**name keyword** (Bnetza). With both sources filtered at the source, a charger
matching neither never arrives — consistent with the rule. The trade-off:
because non-matches aren't downloaded, we can't directly count how many we're
hiding. If we want that number (to judge whether keyword lists are too narrow),
it takes an **occasional unfiltered probe** to compare counts — not a
per-request log.

### Caching

The on-device tile cache currently assumes "I fetched *everything* in this
area." Source-side filtering breaks that, so coverage is tracked **per
network**: the "fetched here" stamp becomes `(source, networkKey, tile)`
instead of `(source, tile)`. An area is covered for the current selection when
every selected network is stamped and fresh on every tile in view.

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
free-text name matches two selected networks' keywords needs a rule (assign to
all matches). The existing 3-day tile TTL applies per `(network, tile)`. No
whole-cache wipe on filter change.

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

## Components touched

- **Operator table**: new bundled data (authored) + a Room table to hold it,
  plus a small repository/catalog to read it by country. Follows the
  `VehicleCatalog` precedent (a maintained, shipped list).
- **`OcmPoi` / mapping**: keep `OperatorID` (already received, currently
  dropped).
- **`OpenChargeMapSource.query`**: accept an optional operator-ID set and pass
  `operatorid=`.
- **`BnetzaSource`**: extend the `where` clause with a `UPPER(Betreiber) LIKE`
  keyword filter (quote-escaped) when a set is active.
- **`TiledSiteRepository` / `TileCoverageEntity`**: add `networkKey` to the
  coverage key so coverage is per network; the covered-check ANDs over the
  selected networks; adding a network fetches only the missing ones, removing
  fetches nothing.
- **`MergingSiteRepository`**: thread the selected set to each source.
- **`NetworkPreferences`**: `preferredOperators` becomes canonical network keys
  from the table, not name-normalized keys. `allows()` / resolution maps a site
  to a network via ID (OCM) or keyword (Bnetza).
- **`NetworksViewModel`**: reads the bundled catalog (by country), holds the
  pending selection, exposes the Confirm action and its enabled state.
- **Startup prune**: a maintenance step (run where the pipeline starts) that
  deletes stale or unselected-network charger rows and coverage stamps.
- **Room migration** to the next version: the new operator table, `networkKey`
  on tile coverage, and `operatorId` + `fetchedAtMillis` on `chargeSite`.

`OperatorKey`'s algorithmic normalization stays for the keyword/no-ID path but
is no longer the source of the pick-list.

## Testing

- Unit: table lookups by country; ID grouping per brand (E.ON, Shell, Tesla,
  EnBW, Ionity); keyword matching incl. accent/case folding; the "matches
  neither → hidden" rule; "no selection → everything".
- Repository: cache key varies with the network set; changing the set
  invalidates coverage and refetches; stale-on-error behaviour preserved.
- Source: OCM query emits `operatorid=` for the selected set; Bnetza keeps only
  keyword matches.
- ViewModel: staging does not touch settings; Confirm commits and enables only
  on a real change.
- A live contract test (opt-in, like the existing OCM one) asserting
  `operatorid=` still filters server-side.

## Deliberately out of scope

- Fetching/caching the full 997 for *names* (POIs already carry canonical
  names; the bundled table covers the picker).
- "Remember networks as you drive" / continent accumulation (solved the wrong
  problem — day-one selection needs a bundled list, not accrued data).
- Active prefetch of a continent's chargers.

## Honest caveat

This is not "every network in your country, complete, on first launch" — that
dataset doesn't exist in usable form. It's "a curated, bundled list of the
common networks for your country, pickable immediately, that filters both
sources correctly and downloads only what you'll see."
