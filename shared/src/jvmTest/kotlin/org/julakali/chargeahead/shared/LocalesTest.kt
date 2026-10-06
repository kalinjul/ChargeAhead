package org.julakali.chargeahead.shared

import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

class LocalesTest {
    private val saved = Locale.getDefault()

    @AfterTest
    fun restore() = Locale.setDefault(saved)

    @Test
    fun `a supported language keeps its own notation and tag`() {
        Locale.setDefault(Locale.GERMANY)
        assertEquals("8,4", formatDecimal(8.4, 1))
        assertEquals("de-DE", currentLanguageTag())
    }

    @Test
    fun `an unsupported language gets english numbers to go with the english text`() {
        Locale.setDefault(Locale.forLanguageTag("ar-EG"))
        assertEquals("8.4", formatDecimal(8.4, 1))
        assertEquals("en", currentLanguageTag())
    }
}
