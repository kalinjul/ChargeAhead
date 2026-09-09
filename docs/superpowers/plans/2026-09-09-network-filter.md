# Charging Network Filter Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let a driver pick their charging networks from a bundled, curated list and have both data sources (OpenChargeMap, Bnetza) filter *at the source*, so only the selected networks' chargers are ever downloaded.

**Architecture:** A hardcoded `NetworkCatalog` (VehicleCatalog-style) maps each selectable *network* to its OCM operator IDs and Bnetza name keywords. The selected networks thread through the `SiteRepository`/`ChargeSiteSource` interfaces into each source: OCM sends `operatorid=`, Bnetza extends its `where` clause with `UPPER(Betreiber) LIKE`. The tile cache tracks coverage **per network** (`(sourceId, networkKey, tile)`), so adding a network fetches only the missing one and removing fetches nothing. A startup prune drops stale or deselected-network rows. The Networks screen stages selection behind a **Confirm** button.

**Tech Stack:** Kotlin Multiplatform, Room (KMP), Ktor client, kotlinx.serialization, kotlin.test / JUnit on `jvmTest`, Compose (phone UI), MVI ViewModels (see the `viewmodels` skill).

**Spec:** `docs/superpowers/specs/2026-09-09-network-operator-filter-design.md`

## Global Constraints

- **Language/tests:** Kotlin. Unit/integration tests live in `shared/src/jvmTest/kotlin/de/autoapp/shared/...` and run with `./gradlew :shared:jvmTest`. Use `kotlin.test` (`assertEquals`, `assertTrue`, `assertContains`, `runTest`).
- **Build env:** `JAVA_HOME` must point at Android Studio's JBR; run gradle via homebrew bash in sandboxed shells (see the build-env memory). Command: `JAVA_HOME=... ./gradlew :shared:jvmTest --tests '...'`.
- **Commit style (this repo):** `type: subject` — **no ticket**, no `Co-Authored-By` trailer. Lowercase, conversational, ≤70 chars. `feat:` / `fix:` / `chore:` / `test:` / `docs:`.
- **networkKey identity:** the catalog `key` slug (`"enbw"`, `"shell-recharge"`). Stored in `preferredOperators` and in the coverage stamp. Never the display name.
- **No-selection sentinel:** `NetworkCatalog.UNFILTERED = "*"`. Empty selection ⇒ fetch everything, stamp under `"*"`, behave as today.
- **TTL unchanged:** `TiledSiteRepository.DEFAULT_TTL_MILLIS` stays 3 days.
- **Cache migration is destructive:** cache tables are regenerable; bump DB version and use `fallbackToDestructiveMigration()`. No hand-written `ALTER TABLE`.
- **Keep `OperatorKey`.** It is used by `OperatorOptions` and for accent/case folding of keywords. Do not delete it.

---

## Phase 0 — Catalog authoring (data, gated)

### Task 1: Fetch OCM operator reference + POI density sample (throwaway probe)

This task produces **data + a proposed grouping for Raphael to sign off**, not shipped code. The script is throwaway.

**Files:**
- Create (throwaway): `scratchpad/fetch-operators.main.kts` (or a `jvmTest` throwaway — see step 1)
- Output artifact: `docs/superpowers/specs/network-catalog-proposed.md` (the proposed groupings for review)

**Interfaces:**
- Consumes: the OCM API key from `local.properties` (`openChargeMapApiKey`, same key the live contract test reads).
- Produces: a reviewed table of `network key → { display name, operatorIds[], nameKeywords[] }` that Task 2 hardcodes.

- [ ] **Step 1: Write a throwaway fetch probe.** Easiest path is a `jvmTest` you run opt-in (mirrors `OpenChargeMapLiveContractTest`'s gating). Fetch the operator reference list:

```kotlin
// GET https://api.openchargemap.io/v3/referencedata/?key=KEY  → operators[]  (id, title)
// GET https://api.openchargemap.io/v3/poi/?key=KEY&countrycode=DE&maxresults=5000&compact=true
//     → group by OperatorID, count. Repeat for a few countries (DE, FR, NL, GB, AT, ...).
```

Print two things: (a) the full `(id, title)` reference list, (b) per-country `OperatorID → count` sorted desc.

- [ ] **Step 2: Cluster the multi-ID brands.** From the reference titles, group the fragmented pan-European brands by name stem. Known from the spec's data findings: Shell Recharge (~12 IDs), E.ON (~7), Tesla (2–3), Ionity (may be 1–few), EnBW (single, id 86). Verify each cluster's IDs against the printed reference list — do not guess IDs.

- [ ] **Step 3: Rank per density.** Using the per-country counts, take the networks that actually carry meaningful charger density (the ones a driver plausibly subscribes to). Long-tail singletons are **excluded** from the catalog (they aren't in the picker — spec's honest caveat).

- [ ] **Step 4: Write the proposal.** Produce `docs/superpowers/specs/network-catalog-proposed.md`: one row per network — `key`, `name`, `operatorIds`, `nameKeywords`, and a one-line note on why grouped/why included. Keywords are lowercase name stems for Bnetza (`enbw`, `ionity`, `eon`, `aral`, `shell`, `tesla`, ...).

- [ ] **Step 5: STOP for sign-off.** This is the review gate agreed in the interview. Present the proposal to Raphael. Do **not** proceed to Task 2 until the groupings are approved. Delete the throwaway probe once the data is captured in the proposal doc.

---

### Task 2: `NetworkCatalog` and `Network` type

**Files:**
- Create: `shared/src/commonMain/kotlin/de/autoapp/shared/domain/NetworkCatalog.kt`
- Test: `shared/src/jvmTest/kotlin/de/autoapp/shared/domain/NetworkCatalogTest.kt`

**Interfaces:**
- Consumes: the approved proposal from Task 1.
- Produces:
  - `data class Network(val key: String, val name: String, val operatorIds: Set<Long>, val nameKeywords: Set<String>)`
  - `object NetworkCatalog { const val UNFILTERED = "*"; val all: List<Network>; fun byKey(key: String): Network?; fun selection(keys: Set<String>): List<Network>; fun resolve(site: ChargeSite): String? }`
  - `resolve(site)` returns a network `key` or null: match `site.operatorId` against `operatorIds` first; else fold `site.operator` and test each network's `nameKeywords` with `contains`.

- [ ] **Step 1: Write the failing tests** (`NetworkCatalogTest.kt`):

```kotlin
package de.autoapp.shared.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NetworkCatalogTest {

    @Test fun enbw_is_a_single_id() {
        val enbw = NetworkCatalog.byKey("enbw")!!
        assertEquals(setOf(86L), enbw.operatorIds)
    }

    @Test fun shell_groups_its_country_ids() {
        val shell = NetworkCatalog.byKey("shell-recharge")!!
        // Exact set asserted from the approved proposal (Task 1). Example shape:
        assertTrue(shell.operatorIds.containsAll(setOf(156L, 157L)))
        assertTrue(shell.operatorIds.size >= 2)
    }

    @Test fun resolve_by_operator_id_wins() {
        val site = ChargeSite(
            id = "x", name = "n", operator = "Some GmbH",
            position = LatLon(0.0, 0.0), connectors = emptyList(), operatorId = 86L,
        )
        assertEquals("enbw", NetworkCatalog.resolve(site))
    }

    @Test fun resolve_by_keyword_when_no_id() {
        val site = ChargeSite(
            id = "x", name = "n", operator = "EnBW mobility+ GmbH",
            position = LatLon(0.0, 0.0), connectors = emptyList(), operatorId = null,
        )
        assertEquals("enbw", NetworkCatalog.resolve(site))
    }

    @Test fun resolve_folds_accents_and_case() {
        val site = ChargeSite(
            id = "x", name = "n", operator = "IONITY GMBH",
            position = LatLon(0.0, 0.0), connectors = emptyList(), operatorId = null,
        )
        assertEquals("ionity", NetworkCatalog.resolve(site))
    }

    @Test fun resolve_unknown_is_null() {
        val site = ChargeSite(
            id = "x", name = "n", operator = "Nobody",
            position = LatLon(0.0, 0.0), connectors = emptyList(), operatorId = 999999L,
        )
        assertNull(NetworkCatalog.resolve(site))
    }

    @Test fun selection_maps_keys_to_networks_and_ignores_unknown() {
        val sel = NetworkCatalog.selection(setOf("enbw", "not-a-network"))
        assertEquals(listOf("enbw"), sel.map { it.key })
    }
}
```

> NOTE: `ChargeSite` gains `operatorId` in Task 3. If executing tasks in order, this test file will not compile until Task 3 adds the field. Either do Task 3 first, or write these tests referencing `operatorId` and accept the compile failure until Task 3 lands (TDD-red across two tasks). Recommended: **do Task 3 before Task 2's test run.**

- [ ] **Step 2: Run the tests to verify they fail.**
Run: `./gradlew :shared:jvmTest --tests '*NetworkCatalogTest'`
Expected: FAIL (unresolved `NetworkCatalog`).

- [ ] **Step 3: Write `NetworkCatalog.kt`** using the VehicleCatalog pattern (a hardcoded `all` list). Fill `all` from the **approved** proposal:

```kotlin
package de.autoapp.shared.domain

data class Network(
    val key: String,
    val name: String,
    val operatorIds: Set<Long>,
    val nameKeywords: Set<String>,
)

/**
 * The curated, shipped list of selectable charging networks — bundled so the
 * picker is populated on first launch, offline. Mirrors [VehicleCatalog]: a
 * maintained in-source list, updated by app release.
 *
 * Not the same as [de.autoapp.shared.data.OperatorCatalog], which reads
 * operator names already in the local cache.
 */
object NetworkCatalog {

    /** Coverage/selection sentinel: no filter, fetch everything (as today). */
    const val UNFILTERED = "*"

    val all: List<Network> = listOf(
        Network("enbw", "EnBW", setOf(86L), setOf("enbw")),
        Network("ionity", "Ionity", setOf(/* from proposal */), setOf("ionity")),
        Network("shell-recharge", "Shell Recharge", setOf(/* from proposal */), setOf("shell")),
        // ... the full approved list
    )

    private val byKey: Map<String, Network> = all.associateBy { it.key }
    private val byOperatorId: Map<Long, Network> =
        all.flatMap { n -> n.operatorIds.map { it to n } }.toMap()

    fun byKey(key: String): Network? = byKey[key]

    fun selection(keys: Set<String>): List<Network> = all.filter { it.key in keys }

    /** The network a site belongs to: by OCM operator id first, else by folded keyword. */
    fun resolve(site: ChargeSite): String? {
        site.operatorId?.let { id -> byOperatorId[id]?.let { return it.key } }
        val name = site.operator ?: return null
        val folded = OperatorKey.folded(name)
        return all.firstOrNull { n -> n.nameKeywords.any { folded.contains(it) } }?.key
    }
}
```

- [ ] **Step 4: Add the grouping tests' exact ID sets** from the approved proposal (replace the `/* from proposal */` placeholders and tighten the Shell assertion to the exact set). Every grouped brand (EnBW, Ionity, Shell, E.ON, Tesla) gets an exact-set assertion.

- [ ] **Step 5: Run the tests to verify they pass.**
Run: `./gradlew :shared:jvmTest --tests '*NetworkCatalogTest'`
Expected: PASS.

- [ ] **Step 6: Commit.**
```bash
git add shared/src/commonMain/kotlin/de/autoapp/shared/domain/NetworkCatalog.kt \
        shared/src/jvmTest/kotlin/de/autoapp/shared/domain/NetworkCatalogTest.kt
git commit -m "feat: a bundled catalog of networks, so day-one has something to tick"
```

---

## Phase 1 — Carry OperatorID from OCM

### Task 3: Deserialize and carry `operatorId`

**Files:**
- Modify: `shared/src/commonMain/kotlin/de/autoapp/shared/domain/Model.kt` (add field to `ChargeSite`)
- Modify: `shared/src/commonMain/kotlin/de/autoapp/shared/data/OpenChargeMapDto.kt` (add `OperatorID` to `OcmPoi`)
- Modify: `shared/src/commonMain/kotlin/de/autoapp/shared/data/OpenChargeMapSource.kt` (`toChargeSite` maps it)
- Test: `shared/src/jvmTest/kotlin/de/autoapp/shared/data/OpenChargeMapSourceTest.kt` (add a case)

**Interfaces:**
- Produces: `ChargeSite.operatorId: Long?` (default `null`); OCM sites carry the id, others leave it null.

- [ ] **Step 1: Add the failing test** to `OpenChargeMapSourceTest.kt` — assert a mocked POI with `"OperatorID": 86` maps to `operatorId == 86L`. Follow the existing MockEngine pattern in that file (copy an existing test's scaffolding; do not invent a new harness).

```kotlin
@Test fun carries_operator_id() = runTest {
    val json = """[{"ID":1,"OperatorID":86,"OperatorInfo":{"Title":"EnBW"},
        "AddressInfo":{"Latitude":48.1,"Longitude":11.5},"Connections":[]}]"""
    val source = OpenChargeMapSource(mockClient(json), apiKey = "k")
    val sites = source.query(SectorArea.circle(LatLon(48.1, 11.5), 5.0))
    assertEquals(86L, sites.single().operatorId)
}
```

- [ ] **Step 2: Run to verify it fails.**
Run: `./gradlew :shared:jvmTest --tests '*OpenChargeMapSourceTest'`
Expected: FAIL (`operatorId` unresolved / null).

- [ ] **Step 3: Implement.**
  - `Model.kt`: add `val operatorId: Long? = null,` to `ChargeSite` (place after `operator`).
  - `OpenChargeMapDto.kt`: add `@SerialName("OperatorID") val operatorId: Long? = null` to `OcmPoi`.
  - `OpenChargeMapSource.kt` `toChargeSite`: pass `operatorId = poi.operatorId`.

- [ ] **Step 4: Run to verify it passes** (and the whole OCM test class still passes).
Run: `./gradlew :shared:jvmTest --tests '*OpenChargeMapSourceTest'`
Expected: PASS.

- [ ] **Step 5: Commit.**
```bash
git add shared/src/commonMain/kotlin/de/autoapp/shared/domain/Model.kt \
        shared/src/commonMain/kotlin/de/autoapp/shared/data/OpenChargeMapDto.kt \
        shared/src/commonMain/kotlin/de/autoapp/shared/data/OpenChargeMapSource.kt \
        shared/src/jvmTest/kotlin/de/autoapp/shared/data/OpenChargeMapSourceTest.kt
git commit -m "feat: keep the OperatorID OCM was sending all along"
```

---

## Phase 2 — Thread the selection to the sources

### Task 4: Add a `networks` parameter to the source/repository ports

**Files:**
- Modify: `shared/src/commonMain/kotlin/de/autoapp/shared/domain/Ports.kt`
- Modify implementations: `OpenChargeMapSource.kt`, `BnetzaSource.kt`, `DemoSiteSource` (wherever it lives), `TiledSiteRepository.kt`, `MergingSiteRepository.kt`, and any others implementing these interfaces.

**Interfaces:**
- Produces (new signatures, default `emptyList()` so existing call sites and tests keep compiling):
  - `suspend fun ChargeSiteSource.query(area: SearchArea, networks: List<Network> = emptyList()): List<ChargeSite>`
  - `suspend fun SiteRepository.sitesIn(area: SearchArea, networks: List<Network> = emptyList()): List<ChargeSite>`
- Semantics this task only *plumbs* the parameter; sources ignore it until Tasks 5–6, the repo until Task 8. Behavior is unchanged after this task (proven by existing tests still passing).

- [ ] **Step 1: Change the interfaces** in `Ports.kt`:

```kotlin
import de.autoapp.shared.domain.Network   // same package; no import needed if co-located

interface ChargeSiteSource {
    val id: String
    suspend fun query(area: SearchArea, networks: List<Network> = emptyList()): List<ChargeSite>
}

interface SiteRepository {
    suspend fun sitesIn(area: SearchArea, networks: List<Network> = emptyList()): List<ChargeSite>
    suspend fun invalidate() {}
}
```

- [ ] **Step 2: Update every implementer's signature** to match (accept `networks` and, for now, ignore it — pass it down where a wrapper calls another):
  - `OpenChargeMapSource.query(area, networks)` — accept, ignore for now.
  - `BnetzaSource.query(area, networks)` — accept, ignore for now.
  - `DemoSiteSource.query(area, networks)` — accept, ignore.
  - `TiledSiteRepository.sitesIn(area, networks)` — accept; pass `networks` through to `source.query(fetchArea, networks)` in `fetchAndStore`.
  - `MergingSiteRepository.sitesIn(area, networks)` — accept; pass `networks` to each `repository.sitesIn(area, networks)`.

- [ ] **Step 3: Run the full shared test suite to verify nothing broke.**
Run: `./gradlew :shared:jvmTest`
Expected: PASS (behavior unchanged; defaults keep old call sites working).

- [ ] **Step 4: Commit.**
```bash
git add -A
git commit -m "feat: give the sources a way to hear which networks the driver wants"
```

---

### Task 5: OpenChargeMap emits `operatorid=`

**Files:**
- Modify: `shared/src/commonMain/kotlin/de/autoapp/shared/data/OpenChargeMapSource.kt`
- Test: `shared/src/jvmTest/kotlin/de/autoapp/shared/data/OpenChargeMapSourceTest.kt`

**Interfaces:**
- Consumes: `networks: List<Network>` from Task 4.
- Produces: when `networks` is non-empty, the request carries `operatorid=<sorted,comma-joined union of operatorIds>`; when empty, no such parameter (unchanged).

- [ ] **Step 1: Write the failing test.** Capture the outgoing request URL via MockEngine (inspect `request.url.parameters`), assert `operatorid` equals `"86,156,157"` for a selection of EnBW + Shell (use the catalog's real ids), and assert the parameter is **absent** when `networks` is empty.

```kotlin
@Test fun sends_operatorid_for_selected_networks() = runTest {
    var seen: String? = null
    val client = mockClientCapturing(body = "[]") { req -> seen = req.url.parameters["operatorid"] }
    val source = OpenChargeMapSource(client, apiKey = "k")
    val enbw = NetworkCatalog.byKey("enbw")!!
    source.query(SectorArea.circle(LatLon(48.1, 11.5), 5.0), networks = listOf(enbw))
    assertEquals("86", seen)
}

@Test fun omits_operatorid_when_unfiltered() = runTest {
    var seen: String? = "unset"
    val client = mockClientCapturing(body = "[]") { req -> seen = req.url.parameters["operatorid"] }
    OpenChargeMapSource(client, "k").query(SectorArea.circle(LatLon(48.1, 11.5), 5.0))
    assertNull(seen)
}
```
(If the file lacks a capturing MockEngine helper, add a small one next to the existing `mockClient`.)

- [ ] **Step 2: Run to verify it fails.**
Run: `./gradlew :shared:jvmTest --tests '*OpenChargeMapSourceTest'`
Expected: FAIL (`operatorid` not sent).

- [ ] **Step 3: Implement.** In `queryCircle` (and any per-section query for polylines), thread `networks` down and add:

```kotlin
if (networks.isNotEmpty()) {
    val ids = networks.flatMap { it.operatorIds }.toSortedSet().joinToString(",")
    parameter("operatorid", ids)
}
```
Thread `networks` from `query(area, networks)` into `queryCircle`/section helpers (add the parameter to those private functions).

- [ ] **Step 4: Run to verify it passes** (whole OCM class).
Run: `./gradlew :shared:jvmTest --tests '*OpenChargeMapSourceTest'`
Expected: PASS.

- [ ] **Step 5: Commit.**
```bash
git add shared/src/commonMain/kotlin/de/autoapp/shared/data/OpenChargeMapSource.kt \
        shared/src/jvmTest/kotlin/de/autoapp/shared/data/OpenChargeMapSourceTest.kt
git commit -m "feat: OCM only sends back the operators you asked for"
```

---

### Task 6: Bnetza filters by `UPPER(Betreiber) LIKE`

**Files:**
- Modify: `shared/src/commonMain/kotlin/de/autoapp/shared/data/BnetzaSource.kt`
- Test: `shared/src/jvmTest/kotlin/de/autoapp/shared/data/BnetzaSourceTest.kt` (create if absent; otherwise extend)

**Interfaces:**
- Consumes: `networks: List<Network>` from Task 4.
- Produces: when `networks` non-empty, the `where` parameter becomes
  `Status='In Betrieb' AND (UPPER(Betreiber) LIKE '%ENBW%' OR UPPER(Betreiber) LIKE '%IONITY%')`
  — keywords uppercased and single-quote-escaped (`'` → `''`), OR-ed over the selection's `nameKeywords`. Empty selection ⇒ unchanged `Status='In Betrieb'`.

- [ ] **Step 1: Write the failing test.** Capture the `where` parameter via MockEngine; assert exact string for an EnBW+Ionity selection and the unchanged base clause for an empty selection.

```kotlin
@Test fun builds_where_with_keyword_likes() = runTest {
    var where: String? = null
    val client = mockClientCapturing(body = bnetzaEmptyPage()) { req -> where = req.url.parameters["where"] }
    val src = BnetzaSource(client)
    val sel = NetworkCatalog.selection(setOf("enbw", "ionity"))
    src.query(SectorArea.circle(LatLon(48.1, 11.5), 5.0), networks = sel)
    assertEquals(
        "Status='In Betrieb' AND (UPPER(Betreiber) LIKE '%ENBW%' OR UPPER(Betreiber) LIKE '%IONITY%')",
        where,
    )
}

@Test fun base_clause_when_unfiltered() = runTest {
    var where: String? = null
    val client = mockClientCapturing(body = bnetzaEmptyPage()) { req -> where = req.url.parameters["where"] }
    BnetzaSource(client).query(SectorArea.circle(LatLon(48.1, 11.5), 5.0))
    assertEquals("Status='In Betrieb'", where)
}
```
(`bnetzaEmptyPage()` = a minimal valid ArcGIS JSON page with zero features so pagination stops after one call; model it on the shapes `BnetzaSource.fetchPage` already parses.)

- [ ] **Step 2: Run to verify it fails.**
Run: `./gradlew :shared:jvmTest --tests '*BnetzaSourceTest'`
Expected: FAIL.

- [ ] **Step 3: Implement.** Replace the hardcoded `where` in `fetchPage` with a built clause, threading `networks` down from `query`:

```kotlin
private fun whereClause(networks: List<Network>): String {
    val base = "Status='In Betrieb'"
    if (networks.isEmpty()) return base
    val likes = networks
        .flatMap { it.nameKeywords }
        .map { "UPPER(Betreiber) LIKE '%${it.uppercase().replace("'", "''")}%'" }
    return "$base AND (${likes.joinToString(" OR ")})"
}
```
Keyword ordering must be deterministic (catalog order → flatMap preserves it) so the test's exact string holds.

- [ ] **Step 4: Run to verify it passes.**
Run: `./gradlew :shared:jvmTest --tests '*BnetzaSourceTest'`
Expected: PASS.

- [ ] **Step 5: Commit.**
```bash
git add shared/src/commonMain/kotlin/de/autoapp/shared/data/BnetzaSource.kt \
        shared/src/jvmTest/kotlin/de/autoapp/shared/data/BnetzaSourceTest.kt
git commit -m "feat: bnetza stops shipping all of germany, filters by name at the source"
```

---

## Phase 3 — Per-network tile cache

### Task 7: Schema v2 — entities, DAO, destructive migration

**Files:**
- Modify: `shared/src/commonMain/kotlin/de/autoapp/shared/db/ChargeSiteDatabase.kt` (entities, DAO, `@Database version`)
- Modify: `shared/src/commonMain/kotlin/de/autoapp/shared/db/DatabaseFactory.kt` (`fallbackToDestructiveMigration`)
- Test: `shared/src/jvmTest/kotlin/de/autoapp/shared/db/ChargeSiteDaoTest.kt` (create if absent; jvm uses `Room.inMemoryDatabaseBuilder`)

**Interfaces:**
- Produces:
  - `TileCoverageEntity(sourceId, networkKey, tileLat, tileLon, fetchedAtMillis)` — primary key now `["sourceId", "networkKey", "tileLat", "tileLon"]`.
  - `ChargeSiteEntity` gains `operatorId: Long?` and `fetchedAtMillis: Long`.
  - DAO gains: `freshTileCount(..., networkKey)`, `pruneStaleCoverage(olderThanMillis)`, `pruneCoverageNotIn(keys)`, `pruneStaleSites(olderThanMillis)`, plus the site upsert carrying the new columns.

- [ ] **Step 1: Write the failing DAO test** — build an in-memory DB, insert coverage for `(ocm, "enbw", tile, now)`, assert `freshTileCount` counts it only when querying `networkKey = "enbw"`, not `"ionity"`:

```kotlin
@Test fun coverage_is_per_network() = runTest {
    val db = Room.inMemoryDatabaseBuilder<ChargeSiteDatabase>()
        .setDriver(BundledSQLiteDriver()).setQueryCoroutineContext(Dispatchers.IO).build()
    val dao = db.chargeSites()
    dao.markTilesFetched(listOf(TileCoverageEntity("ocm", "enbw", 481, 115, 1_000L)))
    assertEquals(1L, dao.freshTileCount("ocm", "enbw", 481, 481, 115, 115, 0L))
    assertEquals(0L, dao.freshTileCount("ocm", "ionity", 481, 481, 115, 115, 0L))
}
```

- [ ] **Step 2: Run to verify it fails.**
Run: `./gradlew :shared:jvmTest --tests '*ChargeSiteDaoTest'`
Expected: FAIL (compile: `networkKey` unknown / arity mismatch).

- [ ] **Step 3: Implement the schema.**
  - `TileCoverageEntity`:
```kotlin
@Entity(tableName = "tileCoverage", primaryKeys = ["sourceId", "networkKey", "tileLat", "tileLon"])
data class TileCoverageEntity(
    val sourceId: String,
    val networkKey: String,
    val tileLat: Long,
    val tileLon: Long,
    val fetchedAtMillis: Long,
)
```
  - `ChargeSiteEntity`: add `val operatorId: Long? = null,` and `val fetchedAtMillis: Long,`.
  - `freshTileCount`: add `AND networkKey = :networkKey` and the `networkKey: String` param.
  - New DAO methods:
```kotlin
@Query("DELETE FROM tileCoverage WHERE fetchedAtMillis <= :olderThanMillis")
suspend fun pruneStaleCoverage(olderThanMillis: Long)

@Query("DELETE FROM tileCoverage WHERE networkKey NOT IN (:keys)")
suspend fun pruneCoverageNotIn(keys: List<String>)

@Query("DELETE FROM chargeSite WHERE fetchedAtMillis <= :olderThanMillis")
suspend fun pruneStaleSites(olderThanMillis: Long)
```
  - `@Database(..., version = 2, exportSchema = false)`.
  - In `DatabaseFactory.kt` `createChargeSiteDatabase`, add `.fallbackToDestructiveMigration()` before `.build()`.

- [ ] **Step 4: Update `ChargeSiteEntity.toDomain` / `toEntity`** (wherever the mapping lives — the `.encode()`/`toDomain` helpers in `TiledSiteRepository` or a mapper file) so the new columns round-trip: carry `operatorId` onto `ChargeSite.operatorId`, and set `fetchedAtMillis` on write (Task 8 supplies the value).

- [ ] **Step 5: Run to verify it passes.**
Run: `./gradlew :shared:jvmTest --tests '*ChargeSiteDaoTest'`
Expected: PASS.

- [ ] **Step 6: Commit.**
```bash
git add -A
git commit -m "feat: the tile cache remembers coverage per network, and per fetch time"
```

---

### Task 8: `TiledSiteRepository` — per-network coverage and incremental fetch

**Files:**
- Modify: `shared/src/commonMain/kotlin/de/autoapp/shared/data/TiledSiteRepository.kt`
- Test: `shared/src/jvmTest/kotlin/de/autoapp/shared/data/TiledSiteRepositoryTest.kt` (create if absent)

**Interfaces:**
- Consumes: `networks` param (Task 4), per-network DAO (Task 7), `NetworkCatalog.UNFILTERED`.
- Produces behavior:
  - Empty `networks` ⇒ coverage keyed `"*"`; identical to today.
  - Non-empty ⇒ covered iff **every** selected network is fresh on **every** tile in the box. Missing networks (not fully covered) are fetched in **one** `source.query(area, missing)` call; each *requested* network's tiles are stamped (whether or not it returned rows). Already-covered networks are not refetched.
  - A fetched OCM site is stamped only under networks it was requested for; a Bnetza site whose folded name matches several requested networks' keywords stamps under **all** matched requested networks.

- [ ] **Step 1: Write the failing tests** using a fake `ChargeSiteSource` that records the `networks` it was queried with:

```kotlin
class RecordingSource(override val id: String = "ocm") : ChargeSiteSource {
    val calls = mutableListOf<List<Network>>()
    var toReturn: List<ChargeSite> = emptyList()
    override suspend fun query(area: SearchArea, networks: List<Network>): List<ChargeSite> {
        calls += networks; return toReturn
    }
}

@Test fun adding_a_network_fetches_only_the_missing_one() = runTest {
    val src = RecordingSource()
    val repo = TiledSiteRepository(src, db, FixedTime(1000L))
    val enbw = NetworkCatalog.byKey("enbw")!!
    val ionity = NetworkCatalog.byKey("ionity")!!
    val area = SectorArea.circle(LatLon(48.1, 11.5), 1.0)

    repo.sitesIn(area, listOf(enbw))                 // fetches EnBW
    repo.sitesIn(area, listOf(enbw, ionity))         // must fetch ONLY Ionity
    assertEquals(listOf("enbw"), src.calls[0].map { it.key })
    assertEquals(listOf("ionity"), src.calls[1].map { it.key })
}

@Test fun removing_a_network_fetches_nothing() = runTest {
    val src = RecordingSource()
    val repo = TiledSiteRepository(src, db, FixedTime(1000L))
    val enbw = NetworkCatalog.byKey("enbw")!!; val ionity = NetworkCatalog.byKey("ionity")!!
    val area = SectorArea.circle(LatLon(48.1, 11.5), 1.0)
    repo.sitesIn(area, listOf(enbw, ionity))
    val before = src.calls.size
    repo.sitesIn(area, listOf(enbw))                 // subset already covered
    assertEquals(before, src.calls.size)
}

@Test fun unfiltered_uses_sentinel_and_behaves_as_before() = runTest {
    val src = RecordingSource(); val repo = TiledSiteRepository(src, db, FixedTime(1000L))
    val area = SectorArea.circle(LatLon(48.1, 11.5), 1.0)
    repo.sitesIn(area)                               // first: fetch
    repo.sitesIn(area)                               // second: covered, no fetch
    assertEquals(1, src.calls.size)
    assertEquals(emptyList(), src.calls[0])          // queried unfiltered
}
```

- [ ] **Step 2: Run to verify they fail.**
Run: `./gradlew :shared:jvmTest --tests '*TiledSiteRepositoryTest'`
Expected: FAIL.

- [ ] **Step 3: Implement per-network coverage.** Rework `sitesIn`/`isCovered`/`fetchAndStore`:

```kotlin
override suspend fun sitesIn(area: SearchArea, networks: List<Network>): List<ChargeSite> = mutex.withLock {
    val keys = if (networks.isEmpty()) listOf(NetworkCatalog.UNFILTERED) else networks.map { it.key }
    val missing = keys.filterNot { isCovered(area, it) }
    if (missing.isNotEmpty()) {
        val toFetch = if (networks.isEmpty()) emptyList()
                      else networks.filter { it.key in missing }
        val failure = runCatching { fetchAndStore(area, toFetch, missing) }.exceptionOrNull()
        if (failure != null) {
            logWarning("Source '${source.id}' did not respond", failure)
            val stored = readStored(area)
            if (stored.isEmpty()) throw failure
            return@withLock stored
        }
    }
    readStored(area)
}

private suspend fun isCovered(area: SearchArea, networkKey: String): Boolean {
    val range = Tiles.rangeOf(area.boundingBox)
    val fresh = dao.freshTileCount(
        sourceId = source.id, networkKey = networkKey,
        minTileLat = range.minTileLat.toLong(), maxTileLat = range.maxTileLat.toLong(),
        minTileLon = range.minTileLon.toLong(), maxTileLon = range.maxTileLon.toLong(),
        notOlderThanMillis = time.nowMillis() - ttlMillis,
    )
    return fresh >= range.count.toLong()
}

// stampKeys = the network keys we REQUESTED (missing ones), stamped on every covered tile.
private suspend fun fetchAndStore(area: SearchArea, networks: List<Network>, stampKeys: List<String>) {
    val fetchArea = area.prefetchArea(prefetchMarginKm)
    val sites = source.query(fetchArea, networks)
    val now = time.nowMillis()
    val tiles = Tiles.covering(fetchArea.boundingBox)
    dao.recordFetch(
        sites = sites.map { it.toEntity(source.id, now) },
        tiles = stampKeys.flatMap { key ->
            tiles.map { TileCoverageEntity(source.id, key, it.lat.toLong(), it.lon.toLong(), now) }
        },
    )
}
```
Notes:
- `isCovered` is now per-network; the covered-check ANDs by virtue of `missing` collecting every not-yet-covered key.
- Site rows carry `operatorId` + `fetchedAtMillis = now` via `toEntity` (Task 7 mapper).
- `readStored` is unchanged (returns all cached sites in box; downstream resolve/filter narrows to selection).

- [ ] **Step 4: Run to verify they pass.**
Run: `./gradlew :shared:jvmTest --tests '*TiledSiteRepositoryTest'`
Expected: PASS. Also run the whole suite to confirm no regressions: `./gradlew :shared:jvmTest`.

- [ ] **Step 5: Commit.**
```bash
git add -A
git commit -m "feat: adding a network fetches only the new one, removing fetches nothing"
```

---

### Task 9: `MergingSiteRepository` threads the selection (verify)

**Files:**
- Modify (if not already covered by Task 4): `shared/src/commonMain/kotlin/de/autoapp/shared/data/MergingSiteRepository.kt`
- Test: `shared/src/jvmTest/kotlin/de/autoapp/shared/data/MergingSiteRepositoryTest.kt` (add a case)

**Interfaces:**
- Produces: `sitesIn(area, networks)` passes the same `networks` to every wrapped repository; merge behavior unchanged.

- [ ] **Step 1: Write the failing test** — two `RecordingSource`-backed `TiledSiteRepository`s behind the merger; assert both saw the same `networks` list.

- [ ] **Step 2: Run to verify it fails** (only if Task 4 left it un-threaded).
Run: `./gradlew :shared:jvmTest --tests '*MergingSiteRepositoryTest'`

- [ ] **Step 3: Implement** — ensure `repositories.map { async { it.sitesIn(area, networks) } }`.

- [ ] **Step 4: Run to verify it passes.**
Run: `./gradlew :shared:jvmTest --tests '*MergingSiteRepositoryTest'`
Expected: PASS.

- [ ] **Step 5: Commit.**
```bash
git add -A
git commit -m "test: the merger hands the same networks to every source"
```

---

### Task 10: Startup prune

**Files:**
- Create: `shared/src/commonMain/kotlin/de/autoapp/shared/data/CachePrune.kt`
- Modify: `shared/src/commonMain/kotlin/de/autoapp/shared/ChargeStopsFeatureFactory.kt`
- Test: `shared/src/jvmTest/kotlin/de/autoapp/shared/data/CachePruneTest.kt`

**Interfaces:**
- Produces: `suspend fun pruneCache(db: ChargeSiteDatabase, selectedKeys: Set<String>, now: Long, ttlMillis: Long)` — deletes stale coverage/sites (older than `now - ttlMillis`) and coverage whose `networkKey` is neither in `selectedKeys` nor the sentinel `"*"`.

- [ ] **Step 1: Write the failing test** — seed coverage rows: one fresh+selected, one fresh+deselected, one stale+selected. After prune with `selectedKeys={"enbw"}`, only the fresh+selected `enbw` row (and `"*"` if present) survives:

```kotlin
@Test fun prune_drops_stale_and_deselected() = runTest {
    val dao = db.chargeSites()
    dao.markTilesFetched(listOf(
        TileCoverageEntity("ocm", "enbw", 1, 1, 10_000L),      // fresh, selected → keep
        TileCoverageEntity("ocm", "ionity", 2, 2, 10_000L),    // fresh, deselected → drop
        TileCoverageEntity("ocm", "enbw", 3, 3, 1L),           // stale → drop
    ))
    pruneCache(db, selectedKeys = setOf("enbw"), now = 10_000L, ttlMillis = 5_000L)
    assertEquals(1L, dao.freshTileCount("ocm", "enbw", 1, 1, 1, 1, 0L))
    assertEquals(0L, dao.freshTileCount("ocm", "ionity", 2, 2, 2, 2, 0L))
    assertEquals(0L, dao.freshTileCount("ocm", "enbw", 3, 3, 3, 3, 0L))
}
```

- [ ] **Step 2: Run to verify it fails.**
Run: `./gradlew :shared:jvmTest --tests '*CachePruneTest'`
Expected: FAIL.

- [ ] **Step 3: Implement `CachePrune.kt`:**
```kotlin
suspend fun pruneCache(db: ChargeSiteDatabase, selectedKeys: Set<String>, now: Long, ttlMillis: Long) {
    val dao = db.chargeSites()
    val keep = selectedKeys + NetworkCatalog.UNFILTERED
    dao.pruneStaleCoverage(now - ttlMillis)
    dao.pruneStaleSites(now - ttlMillis)
    dao.pruneCoverageNotIn(keep.toList())
}
```

- [ ] **Step 4: Wire it into the factory.** In `ChargeStopsFeatureFactory.create`, after `val database = createChargeSiteDatabase(databaseFactory)`, launch a prune with the current selection. Because `create` is not `suspend`, run it on a scope (mirror how the feature launches startup work) — read `settingsStore.networks.first().preferredOperators` for the selected keys, use `timeProvider.nowMillis()` and `TiledSiteRepository.DEFAULT_TTL_MILLIS`. Fire-and-forget on a background scope so startup isn't blocked.

- [ ] **Step 5: Run to verify it passes.**
Run: `./gradlew :shared:jvmTest --tests '*CachePruneTest'`
Expected: PASS.

- [ ] **Step 6: Commit.**
```bash
git add -A
git commit -m "feat: startup quietly evicts stale and deselected chargers"
```

---

## Phase 4 — Resolution and downstream filter

### Task 11: Filter via `NetworkCatalog.resolve` (network keys)

**Files:**
- Modify: `shared/src/commonMain/kotlin/de/autoapp/shared/domain/NetworkPreferences.kt` (or the planner filter call site)
- Modify: `ChargeStopPlanner` (wherever `networks.allows(site.operator)` is called) and any other `allows(...)` call sites (`TripPlanner`, `ChargeNowRanker` per the earlier survey).
- Test: `shared/src/jvmTest/kotlin/de/autoapp/shared/domain/NetworkFilterTest.kt`

**Interfaces:**
- Produces: a single resolution predicate used everywhere a site is filtered by network:
  `fun NetworkPreferences.allowsSite(site: ChargeSite): Boolean` — `if (!isActive) true else NetworkCatalog.resolve(site) in preferredOperators`.
- `preferredOperators` now holds catalog **keys**. The old `allows(operator: String?)` (OperatorKey-based) is replaced at call sites; remove it once no caller remains (grep to confirm).

- [ ] **Step 1: Write the failing test:**
```kotlin
@Test fun allows_only_selected_networks_by_id_or_keyword() {
    val prefs = NetworkPreferences(onlyPreferred = true, preferredOperators = setOf("enbw"))
    val byId = ChargeSite("a","EnBW",  operator="whatever", position=LatLon(0.0,0.0),
        connectors=emptyList(), operatorId=86L)
    val byName = ChargeSite("b","x", operator="EnBW mobility+ GmbH", position=LatLon(0.0,0.0),
        connectors=emptyList(), operatorId=null)
    val other = ChargeSite("c","x", operator="Aral pulse", position=LatLon(0.0,0.0),
        connectors=emptyList(), operatorId=null)
    assertTrue(prefs.allowsSite(byId))
    assertTrue(prefs.allowsSite(byName))
    assertFalse(prefs.allowsSite(other))
}

@Test fun inactive_allows_everything() {
    val prefs = NetworkPreferences(onlyPreferred = false, preferredOperators = setOf("enbw"))
    assertTrue(prefs.allowsSite(ChargeSite("c","x",operator="Aral",position=LatLon(0.0,0.0),
        connectors=emptyList(),operatorId=null)))
}
```

- [ ] **Step 2: Run to verify it fails.**
Run: `./gradlew :shared:jvmTest --tests '*NetworkFilterTest'`
Expected: FAIL.

- [ ] **Step 3: Implement `allowsSite`** in `NetworkPreferences.kt`:
```kotlin
fun allowsSite(site: ChargeSite): Boolean {
    if (!isActive) return true
    return NetworkCatalog.resolve(site) in preferredOperators
}
```
Then update `ChargeStopPlanner` and every other `allows(site.operator)` call site to `allowsSite(site)`. Grep: `rg "\.allows\("` — convert each, then delete the now-unused `allows(operator: String?)`.

- [ ] **Step 4: Run to verify it passes**, and the full suite (planner tests may reference the old signature — fix them to the new one).
Run: `./gradlew :shared:jvmTest`
Expected: PASS.

- [ ] **Step 5: Commit.**
```bash
git add -A
git commit -m "feat: a site belongs to a network by id or keyword, not a normalized name"
```

---

### Task 12: Thread the selection into the live `sitesIn` call

**Files:**
- Modify: `shared/src/commonMain/kotlin/de/autoapp/shared/ChargeStopsFeature.kt` (`recompute`)

**Interfaces:**
- Consumes: `networks` (the `NetworkPreferences` already collected into the feature) and `NetworkCatalog.selection`.
- Produces: `repository.sitesIn(area, selection)` where `selection = if (networks.isActive) NetworkCatalog.selection(networks.preferredOperators) else emptyList()`. On a committed filter change the existing `settingsStore.networks.collect { … recomputeLatest() }` path already re-runs `recompute`, so the map reloads with the new set.

- [ ] **Step 1: Implement** in `recompute`:
```kotlin
val selection = if (networks.isActive) NetworkCatalog.selection(networks.preferredOperators) else emptyList()
val sites = repository.sitesIn(area, selection)
```
Leave the downstream `ChargeStopPlanner.plan(networks = networks, …)` in place — it now uses `allowsSite` (Task 11) and is a correct no-op filter over the already-source-filtered sites (belt and braces for cached leftovers).

- [ ] **Step 2: Build + run the full suite.**
Run: `./gradlew :shared:jvmTest`
Expected: PASS.

- [ ] **Step 3: Commit.**
```bash
git add shared/src/commonMain/kotlin/de/autoapp/shared/ChargeStopsFeature.kt
git commit -m "feat: the map asks only for the networks the driver committed to"
```

---

## Phase 5 — Picker: staged selection + Confirm

> Use the `viewmodels` skill for these two tasks — it documents this repo's MVI ViewModel/UiState/stateless-screen pattern.

### Task 13: `NetworksViewModel` reads the catalog, stages, confirms

**Files:**
- Modify: `shared/src/commonMain/kotlin/de/autoapp/shared/ui/NetworksViewModel.kt`
- Test: `shared/src/jvmTest/kotlin/de/autoapp/shared/ui/NetworksViewModelTest.kt` (create if absent; follow existing VM tests for the fake `SettingsStore`)

**Interfaces:**
- Produces new `NetworksUiState` shape:
  - `networks: List<Network>` — the full catalog (`NetworkCatalog.all`).
  - `pending: Set<String>` — staged selection (network keys).
  - `committed: Set<String>` — the persisted `preferredOperators`.
  - `canConfirm: Boolean` — `pending != committed`.
  - (Keep `search`/`query`/`loading` as needed; the picker list is now catalog-derived, filtered by `OperatorKey.folded` search over `Network.name`.)
- New actions: `onNetworkToggled(key)` mutates `pending` **only**; `onConfirm()` calls `settings.setNetworks(NetworkPreferences(onlyPreferred = true, preferredOperators = pending))`.

- [ ] **Step 1: Write the failing tests:**
```kotlin
@Test fun toggling_stages_without_touching_settings() = runTest {
    val settings = FakeSettingsStore()
    val vm = NetworksViewModel(settings, feature)
    vm.onNetworkToggled("enbw")
    assertEquals(setOf("enbw"), vm.uiState.value.pending)
    assertTrue(settings.saved.isEmpty())          // nothing persisted yet
    assertTrue(vm.uiState.value.canConfirm)
}

@Test fun confirm_commits_and_disables_until_next_change() = runTest {
    val settings = FakeSettingsStore()
    val vm = NetworksViewModel(settings, feature)
    vm.onNetworkToggled("enbw")
    vm.onConfirm()
    assertEquals(setOf("enbw"), settings.saved.single().preferredOperators)
    assertFalse(vm.uiState.value.canConfirm)       // pending == committed now
}
```

- [ ] **Step 2: Run to verify they fail.**
Run: `./gradlew :shared:jvmTest --tests '*NetworksViewModelTest'`
Expected: FAIL.

- [ ] **Step 3: Rewrite the ViewModel.** Replace the `feature.state.availableOperators`-driven picker and the persist-on-toggle `onOperatorToggled`/`update` path with:
  - a `MutableStateFlow<Set<String>>` `pending`, seeded from `settings.networks.first().preferredOperators`;
  - `committed` from `settings.networks`;
  - `onNetworkToggled(key)` toggles `pending`;
  - `onConfirm()` persists `pending`;
  - `uiState` combines catalog + search + pending + committed.
  Drop `onAllShownSelected`/`onNoShownSelected`/`onOnlyPreferredChanged` if the redesigned screen no longer uses them (confirm against the screen in Task 14; keep any the screen still calls).

- [ ] **Step 4: Run to verify they pass.**
Run: `./gradlew :shared:jvmTest --tests '*NetworksViewModelTest'`
Expected: PASS.

- [ ] **Step 5: Commit.**
```bash
git add -A
git commit -m "feat: the networks picker stages your ticks behind a confirm"
```

---

### Task 14: Networks screen — catalog list + Confirm button

**Files:**
- Modify: the Networks Compose screen (find it: `rg -l "NetworksViewModel|NetworksUiState" --glob '*.kt'` under `composeApp`/`androidMain`/`commonMain` UI) and its `Route` composable.

**Interfaces:**
- Consumes: `NetworksUiState` (Task 13) + `onNetworkToggled` / `onConfirm`.
- Produces: a list of the catalog's networks with a checkbox each (checked = `key in pending`), and a **Confirm filters** button enabled iff `canConfirm`. No per-toggle side effects.

- [ ] **Step 1: Read the current screen** to match its Compose idioms (the stateless screen behind a `Route`, per the `viewmodels` skill). Note the existing list-row and search composables to reuse.

- [ ] **Step 2: Replace the pick-list source** — render `state.networks` (filtered by `state.search`) instead of `shown`/`available`. Row checkbox reflects `key in state.pending`, taps call `onNetworkToggled(key)`.

- [ ] **Step 3: Add the Confirm button:**
```kotlin
Button(onClick = onConfirm, enabled = state.canConfirm) {
    Text("Confirm filters")
}
```
Place it as a pinned footer (the commit is the significant action). Wire `onConfirm` through the `Route` composable to `viewModel.onConfirm()`.

- [ ] **Step 4: Build the app.**
Run: `./gradlew :composeApp:assembleDebug` (or the app module's debug assemble).
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Manual check — use the `app-laufen-lassen` skill.** Launch on the emulator: open Networks, tick EnBW + Ionity, confirm the button enables, tap Confirm, verify the map reloads and only those networks' chargers show. Screenshot for the record.

- [ ] **Step 6: Commit.**
```bash
git add -A
git commit -m "feat: a confirm button that means it, on the networks screen"
```

---

## Phase 6 — Server contract

### Task 15: Live contract test for `operatorid=`

**Files:**
- Modify: `shared/src/jvmTest/kotlin/de/autoapp/shared/data/OpenChargeMapLiveContractTest.kt`

**Interfaces:**
- Consumes: the opt-in gate already there (`OCM_LIVE=1` + key from `local.properties`).

- [ ] **Step 1: Add an opt-in test** asserting a live `operatorid=86` query around Munich returns only EnBW sites (every returned site's `operatorId == 86L`, and count is far below an unfiltered query). Follow the file's existing `enabled`-guard pattern so it no-ops without `OCM_LIVE=1`.

```kotlin
@Test fun operatorid_filters_server_side() = runTest {
    if (!enabled) return@runTest
    val source = OpenChargeMapSource(liveClient(), apiKey!!)
    val enbw = NetworkCatalog.byKey("enbw")!!
    val sites = source.query(SectorArea.circle(LatLon(48.137, 11.575), 25.0), networks = listOf(enbw))
    assertTrue(sites.isNotEmpty())
    assertTrue(sites.all { it.operatorId == 86L })
}
```

- [ ] **Step 2: Run it opt-in to confirm the contract holds.**
Run: `OCM_LIVE=1 ./gradlew :shared:jvmTest --tests '*OpenChargeMapLiveContractTest'`
Expected: PASS (and it no-ops in normal CI runs).

- [ ] **Step 3: Commit.**
```bash
git add shared/src/jvmTest/kotlin/de/autoapp/shared/data/OpenChargeMapLiveContractTest.kt
git commit -m "test: assert OCM still filters operatorid server-side, opt-in"
```

---

## Final verification

- [ ] Run the whole shared suite green: `./gradlew :shared:jvmTest`.
- [ ] Build the app: debug assemble succeeds.
- [ ] Manual smoke via `app-laufen-lassen`: fresh install → Networks has a populated list day one → tick two networks → Confirm → map shows only those → relaunch → still filtered (prune kept selected, dropped the rest).
- [ ] Confirm the ~97% bandwidth win is real: filtered vs. unfiltered request size around a dense city (the live contract test's counts are a proxy).

---

## Self-review notes (coverage against the spec)

- Bundled catalog → Task 2. OperatorID plumbing → Task 3. OCM `operatorid=` → Task 5. Bnetza `LIKE` → Task 6. Per-network coverage + incremental add/remove → Tasks 7–8. Sentinel `"*"` → Tasks 7–8, 10. Startup prune (stale OR deselected) → Task 10. Resolution by id/keyword + hide-on-no-match → Tasks 2, 11. No-selection→everything → Tasks 5, 6, 8. Staged selection + Confirm → Tasks 13–14. Destructive migration → Task 7. Live contract → Task 15.
- **Deferred/known cross-task compile edge:** Task 2's tests reference `ChargeSite.operatorId` (added in Task 3). Do Task 3 first, or accept a two-task red. Called out in Task 2.
- **Out of scope (spec):** per-country picker, LRU size cap, unfiltered-probe hidden-count, full-997 name cache — none are tasks here, by design.
