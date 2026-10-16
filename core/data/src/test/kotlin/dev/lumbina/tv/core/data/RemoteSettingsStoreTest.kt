package dev.lumbina.tv.core.data

import dev.lumbina.tv.core.domain.app.AppId
import dev.lumbina.tv.core.domain.remote.KeyBinding
import dev.lumbina.tv.core.domain.remote.KeyGesture
import dev.lumbina.tv.core.domain.remote.RemoteAction
import dev.lumbina.tv.core.domain.remote.RemoteKey
import dev.lumbina.tv.core.domain.remote.RemoteSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteSettingsStoreTest {
    private val red = RemoteKey.ofCode(183)

    @Test
    fun `bindings, names and the other home screen read back as they were written`() {
        val settings = RemoteSettings(
            bindings = listOf(
                KeyBinding(red, KeyGesture.ShortPress, RemoteAction.OpenApp(AppId("tv.cinema", "tv.cinema.Main"))),
                KeyBinding(red, KeyGesture.LongPress, RemoteAction.Nothing),
                KeyBinding(RemoteKey.Home, KeyGesture.DoubleTap, action = null),
                KeyBinding(RemoteKey.Home, KeyGesture.LongPress, RemoteAction.MediaPlayPause),
            ),
            keyNames = mapOf(red to "Red"),
            lumbinaOnly = setOf(red),
            everywhere = setOf(RemoteKey.Menu),
            otherHome = AppId("tv.launcher", "tv.launcher.Home"),
        )
        assertEquals(settings, settings.toStored().toDomain())
    }

    @Test
    fun `the old switch, off, is read as a double press on Home left to the device`() {
        val read = StoredRemoteSettings(doubleHomeRecents = false).toDomain()
        assertEquals(listOf(KeyBinding(RemoteKey.Home, KeyGesture.DoubleTap, action = null)), read.bindings)
        assertFalse(read.doubleHomeRecents)
        assertTrue(StoredRemoteSettings().toDomain().bindings.isEmpty())
    }

    @Test
    fun `a binding this build does not know is dropped, not read as left to the device`() {
        val stored = StoredRemoteSettings(
            bindings = listOf(
                StoredKeyBinding("key:183", "ShortPress", "teleport"),
                StoredKeyBinding("key:183", "Wave", "nothing"),
                StoredKeyBinding("key:184", "ShortPress", "nothing"),
            ),
        )
        assertEquals(
            listOf(KeyBinding(RemoteKey.ofCode(184), KeyGesture.ShortPress, RemoteAction.Nothing)),
            stored.toDomain().bindings,
        )
    }
}
