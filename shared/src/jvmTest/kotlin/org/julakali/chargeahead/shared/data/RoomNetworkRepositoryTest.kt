package org.julakali.chargeahead.shared.data

import org.julakali.chargeahead.shared.db.DatabaseFactory
import org.julakali.chargeahead.shared.db.createChargeSiteDatabase
import org.julakali.chargeahead.shared.domain.Network
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

class RoomNetworkRepositoryTest {

    private var listed: List<Network> = emptyList()
    private val repository = RoomNetworkRepository({ listed }, createChargeSiteDatabase(DatabaseFactory()))

    @Test
    fun `the backend's order becomes the rank`() = runBlocking {
        listed = listOf(Network("enbw", "EnBW"), Network("kaufland", "Kaufland"))

        repository.refresh()

        assertEquals(
            setOf(Network("enbw", "EnBW", rank = 0), Network("kaufland", "Kaufland", rank = 1)),
            repository.networks.first().toSet(),
        )
    }

    @Test
    fun `a network that dropped off the list keeps its name, unranked`() = runBlocking {
        listed = listOf(Network("enbw", "EnBW"), Network("ladenetz", "ladenetz.de"))
        repository.refresh()
        listed = listOf(Network("kaufland", "Kaufland"), Network("enbw", "EnBW"))

        repository.refresh()

        assertEquals(
            setOf(
                Network("kaufland", "Kaufland", rank = 0),
                Network("enbw", "EnBW", rank = 1),
                Network("ladenetz", "ladenetz.de", rank = null),
            ),
            repository.networks.first().toSet(),
        )
    }

    @Test
    fun `an empty answer keeps the list from before`() = runBlocking {
        listed = listOf(Network("enbw", "EnBW"))
        repository.refresh()
        listed = emptyList()

        repository.refresh()

        assertEquals(listOf(Network("enbw", "EnBW", rank = 0)), repository.networks.first())
    }
}
