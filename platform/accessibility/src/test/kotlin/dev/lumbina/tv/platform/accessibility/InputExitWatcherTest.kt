package dev.lumbina.tv.platform.accessibility

import android.view.KeyEvent
import android.view.accessibility.AccessibilityNodeInfo
import dev.lumbina.tv.core.common.LumbinaLog
import dev.lumbina.tv.core.domain.device.InputSource
import dev.lumbina.tv.core.domain.device.InputSources
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class InputExitWatcherTest {

    private val scope = TestScope()
    private var opened = 0
    private var backToAndroid = 0
    private var front: AccessibilityNodeInfo? = null

    private val inputs = object : InputSources {
        override suspend fun showAndroid(): Boolean = true.also { backToAndroid++ }
        override suspend fun list(): List<InputSource> = emptyList()
        override suspend fun current(): String? = null
        override suspend fun open(id: String): Boolean = true
        override fun showsInputs(packageName: String): Boolean = packageName == TV
    }

    private val watcher = InputExitWatcher(
        inputs = inputs,
        isHomeScreen = { packageName, _ -> packageName == FIRMWARE_HOME },
        own = OWN,
        front = { front },
        open = { opened++ },
        log = LumbinaLog { _, _ -> },
    ).apply { start(scope) }

    private fun screen(packageName: String, focusable: Boolean = false) = AccessibilityNodeInfo.obtain().apply {
        this.packageName = packageName
        isFocusable = focusable
        isVisibleToUser = true
    }

    private fun back(action: Int) = watcher.onKey(KeyEvent(action, KeyEvent.KEYCODE_BACK))

    @Test
    fun `Back on the bare input screen is taken, and Android's picture comes back with Lumbina`() {
        watcher.onWindow(TV, "$TV.LiveTv")
        front = screen(TV)
        assertTrue(back(KeyEvent.ACTION_DOWN))
        assertTrue(back(KeyEvent.ACTION_UP))
        scope.runCurrent()
        assertEquals(1, backToAndroid)
        assertEquals(1, opened)
    }

    @Test
    fun `Back over a menu of the input screen stays with it`() {
        watcher.onWindow(TV, "$TV.LiveTv")
        front = screen(TV, focusable = true)
        assertFalse(back(KeyEvent.ACTION_DOWN))
        assertFalse(back(KeyEvent.ACTION_UP))
    }

    @Test
    fun `Back elsewhere is not even looked at`() {
        front = screen(TV)
        assertFalse(back(KeyEvent.ACTION_DOWN))
        watcher.onWindow(TV, "$TV.LiveTv")
        watcher.onWindow(OWN, "$OWN.MainActivity")
        assertFalse(back(KeyEvent.ACTION_DOWN))
    }

    @Test
    fun `the device's home right after the input gets Lumbina over it, not after Lumbina's own quick menu choice`() {
        watcher.onWindow(TV, "$TV.LiveTv")
        // The quick menu over the input is a window of no activity: the input is still what is left.
        watcher.onWindow(OWN, "android.widget.FrameLayout")
        watcher.onWindow(FIRMWARE_HOME, "$FIRMWARE_HOME.Launcher")
        assertEquals(1, opened)
        watcher.onWindow(TV, "$TV.LiveTv")
        watcher.leftByUser()
        watcher.onWindow(FIRMWARE_HOME, "$FIRMWARE_HOME.Launcher")
        assertEquals(1, opened)
    }

    private companion object {
        const val TV = "tv.inputs"
        const val OWN = "dev.lumbina.tv"
        const val FIRMWARE_HOME = "tv.home"
    }
}
