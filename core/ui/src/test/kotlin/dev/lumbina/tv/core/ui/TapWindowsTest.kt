package dev.lumbina.tv.core.ui

import android.app.Activity
import android.os.Looper
import android.view.View
import android.widget.FrameLayout
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** Where a tap told from outside lands among Lumbina's windows. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TapWindowsTest {

    private val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
    private val screen = FrameLayout(activity).also { activity.setContentView(it) }
    private val windows = TapWindows()
    private val tapped = mutableListOf<String>()

    private fun window(name: String, left: Int, size: Int): View {
        val view = View(activity).apply { setOnClickListener { tapped += name } }
        screen.addView(view, FrameLayout.LayoutParams(size, size).apply { leftMargin = left })
        screen.measure(
            View.MeasureSpec.makeMeasureSpec(SCREEN, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(SCREEN, View.MeasureSpec.EXACTLY),
        )
        screen.layout(0, 0, SCREEN, SCREEN)
        return view
    }

    private fun screenPoint(view: View, dx: Int = 5, dy: Int = 5): Pair<Int, Int> {
        val at = IntArray(2).also(view::getLocationOnScreen)
        return at[0] + dx to at[1] + dy
    }

    @Test
    fun `a tap goes to the topmost window listed, beside it too, as a finger's`() {
        val main = window("screen", 0, SCREEN)
        window("dialog", 100, 200).also(windows::added)
        val menu = window("menu", 400, 100).also(windows::added)
        val (x, y) = screenPoint(menu)
        windows.tapAt(x, y, main)
        val (ox, oy) = screenPoint(main, dx = 600, dy = 600)
        windows.tapAt(ox, oy, main)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(listOf("menu", "menu"), tapped)
    }

    @Test
    fun `a window gone takes no more taps`() {
        val main = window("screen", 0, SCREEN)
        val dialog = window("dialog", 100, 200).also(windows::added)
        windows.removed(dialog)
        val (x, y) = screenPoint(dialog)
        windows.tapAt(x, y, main)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(listOf("screen"), tapped)
    }

    private companion object {
        const val SCREEN = 1000
    }
}
