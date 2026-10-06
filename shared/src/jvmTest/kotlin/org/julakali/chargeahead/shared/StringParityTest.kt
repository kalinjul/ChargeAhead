package org.julakali.chargeahead.shared

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/** Compose Resources has no MissingTranslation lint; this is it. */
class StringParityTest {

    private fun file(dir: String) = File("src/commonMain/composeResources/$dir/strings.xml")

    /** Key → its quantities with their sorted placeholders, e.g. "other[%1$d]". */
    private fun load(dir: String): Map<String, Set<String>> {
        val root = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file(dir)).documentElement
        val entries = mutableMapOf<String, Set<String>>()
        val children = root.childNodes
        for (i in 0 until children.length) {
            val node = children.item(i) as? Element ?: continue
            val texts = when (node.tagName) {
                "string" -> listOf("" to node.textContent)
                "plurals" -> (0 until node.childNodes.length)
                    .mapNotNull { node.childNodes.item(it) as? Element }
                    .map { it.getAttribute("quantity") to it.textContent }
                else -> continue
            }
            entries[node.getAttribute("name")] = texts.map { (quantity, text) ->
                quantity + PLACEHOLDER.findAll(text).map { it.value }.sorted().toList()
            }.toSet()
        }
        return entries
    }

    @Test
    fun `english and german have the same keys, quantities and placeholders`() {
        assertEquals(load("values"), load("values-de"))
    }

    @Test
    fun `no android escapes, compose resources would show them literally`() {
        for (dir in listOf("values", "values-de")) {
            val text = file(dir).readText()
            assertFalse("%%" in text, dir)
            assertFalse("\\'" in text, dir)
        }
    }

    @Test
    fun `the resource folders are exactly the supported languages`() {
        val folders = File("src/commonMain/composeResources").listFiles()!!.map { it.name }.filter { it.startsWith("values") }
        assertEquals(UI_LANGUAGES, folders.map { it.removePrefix("values").removePrefix("-").ifEmpty { "en" } }.toSet())
    }

    private companion object {
        val PLACEHOLDER = Regex("%\\d\\$[sd]")
    }
}
