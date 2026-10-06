package org.julakali.chargeahead.shared

import java.io.File
import java.util.Locale
import org.julakali.chargeahead.shared.resources.Res
import org.julakali.chargeahead.shared.resources.allStringResources
import org.julakali.chargeahead.shared.resources.cn_title
import org.julakali.chargeahead.shared.resources.trip_summary_stops
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TextsTest {
    private val saved = Locale.getDefault()

    @AfterTest
    fun restore() = Locale.setDefault(saved)

    @Test
    fun `texts follow the default locale`() {
        Locale.setDefault(Locale.GERMANY)
        assertEquals("Jetzt laden", Texts.string(Res.string.cn_title))
        Locale.setDefault(Locale.US)
        assertEquals("Charge now", Texts.string(Res.string.cn_title))
    }

    @Test
    fun `a language without its own file falls back to english`() {
        Locale.setDefault(Locale.FRANCE)
        assertEquals("Charge now", Texts.string(Res.string.cn_title))
    }

    @Test
    fun `plurals pick the quantity`() {
        Locale.setDefault(Locale.GERMANY)
        assertEquals("1 Stopp", Texts.plural(Res.plurals.trip_summary_stops, 1, 1))
        assertEquals("3 Stopps", Texts.plural(Res.plurals.trip_summary_stops, 3, 3))
    }

    @Test
    fun `swift looks texts up by key`() {
        Locale.setDefault(Locale.US)
        assertEquals("Charge now", Texts.byKey("cn_title"))
    }

    @Test
    fun `every key swift asks for exists`() {
        val swiftKeys = File("../iosApp/ChargeAhead").walk().filter { it.extension == "swift" }
            .flatMap { file -> Regex("""localized\(\s*"([a-z_0-9]+)"""").findAll(file.readText()).map { it.groupValues[1] } }
            .toSet()
        assertTrue(swiftKeys.size > 20, "found only $swiftKeys")
        val missing = swiftKeys - Res.allStringResources.keys
        assertTrue(missing.isEmpty(), "Swift asks for $missing")
    }
}
