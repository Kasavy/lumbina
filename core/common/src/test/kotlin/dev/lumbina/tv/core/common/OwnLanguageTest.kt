package dev.lumbina.tv.core.common

import java.io.File
import java.nio.file.Files
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class OwnLanguageTest {

    private val dir: File = Files.createTempDirectory("own-language").toFile()

    @Test
    fun `nothing chosen is the device's language`() {
        assertNull(OwnLanguage.read(dir))
    }

    @Test
    fun `a chosen language is read back, and the device's again once cleared`() {
        assertTrue(OwnLanguage.write(dir, "ru"))
        assertEquals("ru", OwnLanguage.read(dir))
        assertTrue(OwnLanguage.write(dir, "en"))
        assertEquals("en", OwnLanguage.read(dir))
        assertTrue(OwnLanguage.write(dir, null))
        assertNull(OwnLanguage.read(dir))
        assertTrue(OwnLanguage.write(dir, null))
    }

    @Test
    fun `a blank file is no choice`() {
        File(dir, OwnLanguage.FILE).writeText("  \n")
        assertNull(OwnLanguage.read(dir))
    }
}
