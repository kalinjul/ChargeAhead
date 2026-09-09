package de.autoapp.shared.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NetworkCatalogTest {

    @Test fun enbw_is_a_single_id() {
        val enbw = NetworkCatalog.byKey("enbw")!!
        assertEquals(setOf(86L), enbw.operatorIds)
    }

    @Test fun ionity_is_a_single_id() {
        val ionity = NetworkCatalog.byKey("ionity")!!
        assertEquals(setOf(3299L), ionity.operatorIds)
    }

    @Test fun shell_groups_its_country_ids() {
        val shell = NetworkCatalog.byKey("shell-recharge")!!
        assertEquals(setOf(47L, 156L, 157L, 3392L, 3508L, 3709L, 3964L), shell.operatorIds)
    }

    @Test fun tesla_groups_both_ids() {
        val tesla = NetworkCatalog.byKey("tesla")!!
        assertEquals(setOf(23L, 3534L), tesla.operatorIds)
    }

    @Test fun totalenergies_groups_its_country_ids() {
        val total = NetworkCatalog.byKey("totalenergies")!!
        assertEquals(setOf(25L, 3447L, 3571L), total.operatorIds)
    }

    @Test fun eon_drive_groups_its_cluster_ids() {
        val eon = NetworkCatalog.byKey("eon-drive")!!
        assertEquals(setOf(46L, 3251L, 3359L, 3403L, 3422L, 3814L), eon.operatorIds)
    }

    @Test fun all_has_327_networks() {
        assertEquals(327, NetworkCatalog.all.size)
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

    @Test fun keywords_are_unique() {
        val seen = mutableMapOf<String, String>()
        for (n in NetworkCatalog.all) {
            for (kw in n.nameKeywords) {
                assertNull(seen.put(kw, n.key), "keyword '$kw' shared by ${n.key} and ${seen[kw]}")
            }
        }
    }

    @Test fun resolve_matches_keyword_as_whole_word() {
        // "MER GmbH" → folded "mer gmbh"; keyword "mer" is a whole word at the start
        val site = ChargeSite(
            id = "x", name = "n", operator = "MER GmbH",
            position = LatLon(0.0, 0.0), connectors = emptyList(), operatorId = null,
        )
        assertEquals("mer", NetworkCatalog.resolve(site))
    }

    @Test fun resolve_ignores_substring_inside_a_word() {
        // "Neon Ladestationen" — "eon" (E.ON keyword) appears only inside "neon", not as a whole word
        val site = ChargeSite(
            id = "x", name = "n", operator = "Neon Ladestationen",
            position = LatLon(0.0, 0.0), connectors = emptyList(), operatorId = null,
        )
        assertNull(NetworkCatalog.resolve(site))
    }
}
