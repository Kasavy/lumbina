package dev.lumbina.tv.core.domain.access

import dev.lumbina.tv.core.testing.FakeNotificationListeners
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class NotificationGrantTest {

    private val ours = "dev.lumbina.tv/dev.lumbina.tv.Listener"

    @Test
    fun `from Android 8_1 it is the shell's command`() {
        assertEquals("cmd notification allow_listener $ours", NotificationGrant.ByCommand(ours).command())
    }

    @Test
    fun `the listener goes into the setting, the others kept`() {
        val listeners = FakeNotificationListeners("tv.rec/.Notifications")
        NotificationGrant.BySetting(ours, listeners).byItself()
        assertEquals("tv.rec/.Notifications:$ours", listeners.value)
    }

    @Test
    fun `a listed listener is left as it is, and the shell is given nothing`() {
        val listeners = FakeNotificationListeners("$ours:tv.rec/.Notifications")
        val grant = NotificationGrant.BySetting(ours, listeners)
        grant.byItself()
        assertEquals("$ours:tv.rec/.Notifications", listeners.value)
        assertNull(grant.command())
    }

    @Test
    fun `an old name of Lumbina's listener is put right, not kept beside`() {
        val listeners = FakeNotificationListeners("dev.lumbina.tv/.OldListener:tv.rec/.Notifications")
        NotificationGrant.BySetting(ours, listeners).byItself()
        assertEquals("tv.rec/.Notifications:$ours", listeners.value)
    }

    @Test
    fun `an empty or missing list gets the listener alone`() {
        listOf("", null).forEach { start ->
            val listeners = FakeNotificationListeners(start)
            NotificationGrant.BySetting(ours, listeners).byItself()
            assertEquals(ours, listeners.value)
        }
    }

    @Test
    fun `the shell gets the list quoted, so an inner class's name keeps its dollar`() {
        val listeners = FakeNotificationListeners("tv.rec/.Service\$Listener", canWrite = false)
        assertEquals(
            "settings put secure enabled_notification_listeners 'tv.rec/.Service\$Listener:$ours'",
            NotificationGrant.BySetting(ours, listeners).command(),
        )
    }

    @Test
    fun `from a computer a quoted command goes whole in double quotes`() {
        assertEquals(
            "adb shell \"settings put secure x 'a/.B\$C'\"",
            ComputerSetup.adbShell("settings put secure x 'a/.B\$C'"),
        )
        assertEquals("adb shell pm grant a b", ComputerSetup.adbShell("pm grant a b"))
    }

    @Test
    fun `without the permission Lumbina writes nothing itself`() {
        val listeners = FakeNotificationListeners("tv.rec/.Notifications", canWrite = false)
        NotificationGrant.BySetting(ours, listeners).byItself()
        assertEquals("tv.rec/.Notifications", listeners.value)
    }
}
