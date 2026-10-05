package dev.lumbina.tv.core.domain.backdrop

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ScreensaverCommandsTest {

    @Test
    fun `the commands name the screensaver and turn the system's screensaver on`() {
        assertEquals(
            listOf(
                "adb shell settings put secure screensaver_components dev.lumbina.tv/.Dream",
                "adb shell settings put secure screensaver_enabled 1",
            ),
            screensaverCommands("dev.lumbina.tv/.Dream"),
        )
    }
}
