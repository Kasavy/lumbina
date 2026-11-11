package dev.lumbina.tv.feature.screensaver

import android.service.dreams.DreamService
import android.view.InputDevice
import android.view.MotionEvent
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The system screensaver and a pointer: an air mouse put down may still drift. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LumbinaDreamTest {

    private fun motion(action: Int, source: Int) = MotionEvent.obtain(0, 0, action, 10f, 10f, 0).apply {
        this.source = source
    }

    /** DreamService's own flag, set as it starts waking up; it has no public one. */
    private val DreamService.waking: Boolean
        get() = DreamService::class.java.getDeclaredField("mWaking").apply { isAccessible = true }.getBoolean(this)

    @Test
    fun `a pointer only moved, or its wheel turned, leaves it on`() {
        val dream = LumbinaDream()
        dream.dispatchGenericMotionEvent(motion(MotionEvent.ACTION_HOVER_MOVE, InputDevice.SOURCE_MOUSE))
        dream.dispatchGenericMotionEvent(motion(MotionEvent.ACTION_SCROLL, InputDevice.SOURCE_MOUSE))
        assertFalse(dream.waking)
    }

    @Test
    fun `a stick moved still ends it`() {
        val dream = LumbinaDream()
        dream.dispatchGenericMotionEvent(motion(MotionEvent.ACTION_MOVE, InputDevice.SOURCE_JOYSTICK))
        assertTrue(dream.waking)
    }
}
