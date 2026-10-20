package dev.lumbina.tv.platform.tvprovider

import org.junit.Assert.assertEquals
import org.junit.Test

class CompanionLogLinesTest {

    @Test
    fun `the log is counted by app and action, its last refusals kept whole`() {
        val log = """
            10-18 22:50:25.601 ru.vokino.web insert channel → 1 Популярное
            10-18 22:50:25.700 ru.vokino.web insert preview_program → 2 Фильм
            10-18 22:50:25.701 ru.vokino.web insert preview_program → 3 Другой
            10-18 22:50:26.000 ru.vokino.web delete content://android.media.tv/preview_program?channel=1 → 2
            10-18 22:50:26.100 uid:10123 delete content://android.media.tv/preview_program/7 → 1
            10-18 22:50:27.000 com.vk.tv insert refused: another app's channel
            10-18 22:50:28.000 com.vk.tv unsupported selection: browsable IN (1)
        """.trimIndent()
        assertEquals(
            listOf(
                "its log as far as given, app by app:",
                "2 ru.vokino.web insert preview_program",
                "1 ru.vokino.web insert channel",
                "1 ru.vokino.web delete content://android.media.tv/preview_program",
                "1 uid:10123 delete content://android.media.tv/preview_program",
                "1 com.vk.tv insert refused",
                "1 com.vk.tv unsupported selection",
                "last refusals:",
                "10-18 22:50:27.000 com.vk.tv insert refused: another app's channel",
                "10-18 22:50:28.000 com.vk.tv unsupported selection: browsable IN (1)",
            ),
            companionLogLines(log),
        )
    }

    @Test
    fun `a title with the word in it is no refusal, and only the last thirty are kept`() {
        val titled = "10-18 22:50:25.601 ru.app insert preview_program → 2 Refused entry"
        val refusals = (1..31).map { "10-18 22:50:26.000 com.vk.tv update refused: another app's channel $it" }
        val lines = companionLogLines((listOf(titled) + refusals).joinToString("\n"))
        assertEquals(refusals.takeLast(30), lines.dropWhile { it != "last refusals:" }.drop(1))
    }

    @Test
    fun `an empty log says so`() {
        assertEquals(listOf("its log: empty"), companionLogLines("\n"))
    }
}
