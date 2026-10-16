package dev.lumbina.tv.baselineprofile

import android.os.SystemClock
import android.view.InputDevice
import android.view.KeyCharacterMap
import android.view.KeyEvent
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.platform.app.InstrumentationRegistry

private const val HOLD_MILLIS = 3_000L

// As a remote repeats a held key: the first repeat after 400 ms, then every 50 ms.
private const val FIRST_REPEAT_MILLIS = 400L
private const val REPEAT_MILLIS = 50L

/**
 * ▼ held through home and ▲ held back, as a remote does it: what single presses never show, the
 * rows coming faster than one at a time.
 */
internal fun MacrobenchmarkScope.holdThroughHome() {
    holdKey(KeyEvent.KEYCODE_DPAD_DOWN, HOLD_MILLIS)
    device.waitForIdle()
    holdKey(KeyEvent.KEYCODE_DPAD_UP, HOLD_MILLIS)
    device.waitForIdle()
}

/** [code] held for [millis]: a press, its repeats, a release, sent as the system sends a remote's. */
private fun holdKey(code: Int, millis: Long) {
    val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
    val down = SystemClock.uptimeMillis()
    fun send(action: Int, repeat: Int) {
        val event = KeyEvent(
            down, SystemClock.uptimeMillis(), action, code, repeat, 0, KeyCharacterMap.VIRTUAL_KEYBOARD, 0,
            KeyEvent.FLAG_FROM_SYSTEM, InputDevice.SOURCE_DPAD,
        )
        automation.injectInputEvent(event, false)
    }
    send(KeyEvent.ACTION_DOWN, 0)
    var repeat = 0
    var wait = FIRST_REPEAT_MILLIS
    while (SystemClock.uptimeMillis() - down + wait < millis) {
        Thread.sleep(wait)
        send(KeyEvent.ACTION_DOWN, ++repeat)
        wait = REPEAT_MILLIS
    }
    send(KeyEvent.ACTION_UP, 0)
}
