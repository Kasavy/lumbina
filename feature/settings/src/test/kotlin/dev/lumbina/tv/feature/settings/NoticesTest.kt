package dev.lumbina.tv.feature.settings

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NoticesTest {

    @Test
    fun `reads the entries of the list, each with the license written under it`() {
        val notice = """
            Lumbina

            Third-party components:

            - One, Two
              Apache License 2.0 — Someone
            - Three (inside Two)
              BSD 3-Clause License — Someone else;
              the text is below

            Photos:
            - not.jpg
        """.trimIndent()
        assertEquals(
            listOf(
                Component("One, Two", "Apache License 2.0 — Someone"),
                Component("Three (inside Two)", "BSD 3-Clause License — Someone else; the text is below"),
            ),
            thirdPartyComponents(notice),
        )
    }

    @Test
    fun `a text without the list has no entries`() {
        assertEquals(emptyList<Component>(), thirdPartyComponents("Lumbina\n- not an entry"))
    }

    @Test
    fun `the repository's NOTICE is read, every entry with a license`() {
        val components = thirdPartyComponents(File("../../NOTICE").readText())
        assertTrue(components.size >= 10)
        assertTrue(components.all { it.name.isNotBlank() && it.license.isNotBlank() })
    }
}
