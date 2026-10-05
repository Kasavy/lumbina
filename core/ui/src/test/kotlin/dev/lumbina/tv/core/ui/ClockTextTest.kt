package dev.lumbina.tv.core.ui

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class ClockTextTest {

    @Test
    fun `a date starts with a capital whatever the language writes inside a sentence`() {
        assertEquals("Воскресенье, 4 октября", "воскресенье, 4 октября".asHeading(Locale.forLanguageTag("ru")))
        assertEquals("Sunday, October 4", "Sunday, October 4".asHeading(Locale.ENGLISH))
        assertEquals("4 октября", "4 октября".asHeading(Locale.forLanguageTag("ru")))
    }
}
