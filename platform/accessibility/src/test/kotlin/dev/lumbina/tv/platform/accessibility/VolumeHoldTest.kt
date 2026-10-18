package dev.lumbina.tv.platform.accessibility

import android.content.Intent
import android.media.AudioManager
import android.view.accessibility.AccessibilityEvent
import dev.lumbina.tv.core.common.LumbinaLog
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.shadows.ShadowLooper

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class VolumeHoldTest {

    private val context = RuntimeEnvironment.getApplication()

    /** Whether the service takes the keys, as [VolumeHold] last set it; true as the service starts. */
    private var filtering = true
    private val changes = mutableListOf<Boolean>()

    private val log = LumbinaLog { _, _ -> }

    private fun TestScope.hold(enabled: Boolean = true) = VolumeHold(
        context,
        this,
        log,
        filterKeys = {
            filtering = it
            changes += it
        },
        timing = VolumeHold.Timing(freeMillis = 5_000, longestMillis = 12_000, ownMillis = 1_500, checkMillis = 250),
        now = { testScheduler.currentTime },
    ).apply {
        start()
        this.enabled = enabled
    }

    private fun bar(packageName: String = "com.android.systemui", className: String = AOSP_BAR) =
        AccessibilityEvent.obtain(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED).apply {
            this.packageName = packageName
            this.className = className
        }

    private fun broadcast(stream: Int) {
        context.sendBroadcast(
            Intent("android.media.VOLUME_CHANGED_ACTION")
                .setPackage(context.packageName)
                .putExtra("android.media.EXTRA_VOLUME_STREAM_TYPE", stream),
        )
        ShadowLooper.idleMainLooper()
    }

    @Test
    fun `the volume bar lets the buttons go, and they are taken back after a while`() = runTest {
        val hold = hold()
        hold.onAccessibilityEvent(bar())
        assertEquals(false, filtering)
        advanceTimeBy(4_999)
        assertEquals(false, filtering)
        advanceTimeBy(2)
        assertEquals(true, filtering)
        hold.stop()
    }

    @Test
    fun `each change counts the time again`() = runTest {
        val hold = hold()
        hold.onAccessibilityEvent(bar())
        advanceTimeBy(4_000)
        hold.onAccessibilityEvent(bar())
        advanceTimeBy(4_000)
        assertEquals(false, filtering)
        advanceTimeBy(1_001)
        assertEquals(true, filtering)
        assertEquals(listOf(false, true), changes)
        hold.stop()
    }

    @Test
    fun `changes without end are cut off, and the next ones wait`() = runTest {
        val hold = hold()
        repeat(5) {
            hold.onAccessibilityEvent(bar())
            advanceTimeBy(3_000)
        }
        // Let go at 0, taken back at 12 s however often the volume changed.
        assertEquals(true, filtering)
        hold.onAccessibilityEvent(bar())
        runCurrent()
        assertEquals(true, filtering)
        advanceTimeBy(5_000)
        hold.onAccessibilityEvent(bar())
        assertEquals(false, filtering)
        hold.stop()
    }

    @Test
    fun `switched off, nothing is let go, and switching off takes the buttons back`() = runTest {
        val off = hold(enabled = false)
        off.onAccessibilityEvent(bar())
        assertEquals(true, filtering)
        off.enabled = true
        off.onAccessibilityEvent(bar())
        assertEquals(false, filtering)
        off.enabled = false
        assertEquals(true, filtering)
        off.stop()
    }

    @Test
    fun `Lumbina's own change of the volume lets nothing go`() = runTest {
        val hold = hold()
        hold.ownChange()
        advanceTimeBy(300)
        hold.onAccessibilityEvent(bar())
        assertEquals(true, filtering)
        advanceTimeBy(1_500)
        hold.onAccessibilityEvent(bar())
        assertEquals(false, filtering)
        hold.stop()
    }

    @Test
    fun `a screen of the service's own keeps the buttons, and takes them back when it shows`() = runTest {
        val hold = hold()
        var screen = true
        hold.busy = { screen }
        hold.onAccessibilityEvent(bar())
        assertEquals(true, filtering)
        screen = false
        hold.onAccessibilityEvent(bar())
        assertEquals(false, filtering)
        screen = true
        advanceTimeBy(251)
        assertEquals(true, filtering)
        hold.stop()
    }

    @Test
    fun `only the volume bar of others counts, and only the music stream`() = runTest {
        val hold = hold()
        hold.onAccessibilityEvent(bar(packageName = context.packageName, className = "x.systemui.volume.Panel"))
        hold.onAccessibilityEvent(bar(className = "com.android.systemui.recents.RecentsActivity"))
        broadcast(AudioManager.STREAM_RING)
        assertEquals(true, filtering)
        broadcast(AudioManager.STREAM_MUSIC)
        assertEquals(false, filtering)
        hold.stop()
        assertEquals(true, filtering)
    }

    @Test
    fun `Xiaomi's volume bar counts`() = runTest {
        val hold = hold()
        hold.onAccessibilityEvent(bar(className = "com.xiaomi.mitv.systemui.volume.VolumVerticalDialog"))
        assertEquals(false, filtering)
        hold.stop()
    }

    private companion object {
        const val AOSP_BAR = "com.android.systemui.volume.VolumeDialogImpl$1"
    }
}
