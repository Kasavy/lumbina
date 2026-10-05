package dev.lumbina.tv.speech.vosk

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class VoskDefaultLanguagesTest {

    @get:Rule val temp = TemporaryFolder()

    private val store by lazy { VoskModelStore(temp.newFolder("models")) }

    private fun install(language: String) {
        File(store.directory(language), "conf").mkdirs()
        File(store.directory(language), "conf/model.conf").writeText("")
        File(store.directory(language), "am").mkdirs()
        // The store's own rule decides; if it changes, these tests must not quietly test "none".
        assertTrue(store.isInstalled(language))
    }

    @Test
    fun `nothing installed - the device language alone`() {
        assertEquals(listOf("ru"), VoskDefaultLanguages(store) { "ru" }.get())
    }

    @Test
    fun `a device language without a model - English`() {
        assertEquals(listOf("en"), VoskDefaultLanguages(store) { "xx" }.get())
    }

    @Test
    fun `both installed - only the device language`() {
        install("ru")
        install("en")
        assertEquals(listOf("ru"), VoskDefaultLanguages(store) { "ru" }.get())
    }

    @Test
    fun `another language installed - it rather than a download`() {
        install("de")
        assertEquals(listOf("de"), VoskDefaultLanguages(store) { "ru" }.get())
    }
}
