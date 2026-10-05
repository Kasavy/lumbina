package dev.lumbina.tv.platform.accessibility

import android.Manifest
import android.app.Application
import android.content.ComponentName
import android.provider.Settings
import dev.lumbina.tv.core.domain.access.AdbState
import dev.lumbina.tv.core.domain.access.LocalAccess
import dev.lumbina.tv.core.domain.access.LocalAdb
import dev.lumbina.tv.core.domain.health.HealAccessibilityService
import dev.lumbina.tv.core.testing.FakeHealLock
import dev.lumbina.tv.core.testing.FakeSecureSettings
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class RecognizerSetupTest {

    private val context: Application = RuntimeEnvironment.getApplication()
    private val ours = ComponentName(context, LumbinaRecognitionService::class.java).flattenToShortString()

    /** Lumbina's adb: carries out what the setup asks, as the shell would. */
    private inner class FakeAdb(var connected: Boolean = true) : LocalAdb {
        val commands = mutableListOf<String>()

        override suspend fun state() = if (connected) AdbState.Connected else AdbState.Off

        override suspend fun connect() = state()

        override suspend fun shell(command: String): String? {
            if (!connected) return null
            commands += command
            when {
                command.startsWith("pm grant") -> shadowOf(context).grantPermissions(Manifest.permission.RECORD_AUDIO)
                command.startsWith("settings put secure $SETTING ") -> recognizer = command.substringAfterLast(' ')
            }
            return ""
        }
    }

    private var recognizer: String?
        get() = Settings.Secure.getString(context.contentResolver, SETTING)
        set(value) {
            Settings.Secure.putString(context.contentResolver, SETTING, value)
        }

    private fun setup(adb: LocalAdb): RecognizerSetup {
        val settings = FakeSecureSettings()
        val heal = HealAccessibilityService(settings, { true }, "x/.Y", FakeHealLock(), 0, 0)
        return RecognizerSetup(context, LocalAccess(adb, settings, heal, context.packageName, "x.Listener")) { _, _ -> }
    }

    @Test
    fun `takes the recognizer once and grants the microphone`() = runBlocking {
        recognizer = OTHER
        val adb = FakeAdb()
        setup(adb).run()
        assertEquals(ours, recognizer)
        assertTrue(adb.commands.any { it.startsWith("pm grant") })
    }

    @Test
    fun `a recognizer the user picks later stays`() = runBlocking {
        recognizer = OTHER
        val adb = FakeAdb()
        setup(adb).run()
        recognizer = OTHER // the user chose another one since

        adb.commands.clear()
        setup(adb).run()
        assertEquals(OTHER, recognizer)
        assertTrue(adb.commands.none { it.startsWith("settings put") })
    }

    @Test
    fun `without adb access nothing is remembered, and the next start tries again`() = runBlocking {
        recognizer = OTHER
        val adb = FakeAdb(connected = false)
        setup(adb).run()
        assertEquals(OTHER, recognizer)

        adb.connected = true
        setup(adb).run()
        assertEquals(ours, recognizer)
    }

    @Test
    fun `a granted microphone is not asked for again`() = runBlocking {
        shadowOf(context).grantPermissions(Manifest.permission.RECORD_AUDIO)
        val adb = FakeAdb()
        setup(adb).run()
        assertTrue(adb.commands.none { it.startsWith("pm grant") })
    }

    private companion object {
        const val SETTING = "voice_recognition_service"
        const val OTHER = "org.futo.voiceinput/.DummyService"
    }
}
