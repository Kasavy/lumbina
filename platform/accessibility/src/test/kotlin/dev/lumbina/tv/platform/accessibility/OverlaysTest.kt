package dev.lumbina.tv.platform.accessibility

import android.app.Activity
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** What the phone touchpad's click finds in the service's own overlays, and the pointer kept above them. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OverlaysTest {

    private val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
    private val screen = FrameLayout(activity).also { activity.setContentView(it) }
    private val overlays = Overlays()
    private val clicked = mutableListOf<String>()

    /** An overlay window at ([left], [top]), [size] square, laid out on the screen. */
    private fun window(left: Int, top: Int, size: Int, vararg children: View): FrameLayout =
        window(FrameLayout(activity), left, top, size, *children)

    /** A menu's frame ([PointerFrame]): its padding round the panel is a margin. */
    private fun frame(left: Int, top: Int, size: Int, vararg children: View): FrameLayout =
        window(PointerFrame(activity), left, top, size, *children)

    private fun window(window: FrameLayout, left: Int, top: Int, size: Int, vararg children: View): FrameLayout {
        children.forEach { window.addView(it) }
        screen.addView(
            window,
            FrameLayout.LayoutParams(size, size).apply {
                leftMargin = left
                topMargin = top
            },
        )
        layOut()
        overlays.added(window)
        return window
    }

    private fun button(name: String, left: Int, top: Int, size: Int, long: (() -> Boolean)? = null) =
        View(activity).apply {
            layoutParams = FrameLayout.LayoutParams(size, size).apply {
                leftMargin = left
                topMargin = top
            }
            setOnClickListener { clicked += name }
            long?.let { held -> setOnLongClickListener { held() } }
        }

    private fun layOut() {
        screen.measure(
            View.MeasureSpec.makeMeasureSpec(SCREEN, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(SCREEN, View.MeasureSpec.EXACTLY),
        )
        screen.layout(0, 0, SCREEN, SCREEN)
    }

    private fun at(view: View): Pair<Int, Int> {
        val xy = IntArray(2).also(view::getLocationOnScreen)
        return xy[0] + view.width / 2 to xy[1] + view.height / 2
    }

    @Test
    fun `a click on a line clicks it`() {
        val line = button("line", 10, 10, 50)
        window(100, 100, 200, line)
        val (x, y) = at(line)
        assertEquals(true, overlays.clickAt(x, y, long = false))
        assertEquals(listOf("line"), clicked)
    }

    @Test
    fun `an overlay's empty place keeps the click from what lies under`() {
        val menu = window(100, 100, 200, panel(200, button("line", 0, 0, 20)))
        val xy = IntArray(2).also(menu::getLocationOnScreen)
        assertEquals(false, overlays.clickAt(xy[0] + 150, xy[1] + 150, long = false))
        assertTrue(clicked.isEmpty())
    }

    @Test
    fun `outside every overlay the click goes on to the apps`() {
        window(100, 100, 50, button("line", 0, 0, 50))
        assertNull(overlays.clickAt(5, 5, long = false))
    }

    @Test
    fun `held where nothing takes a long click, it is a click`() {
        val line = button("line", 0, 0, 50)
        window(100, 100, 100, line)
        val (x, y) = at(line)
        assertEquals(true, overlays.clickAt(x, y, long = true))
        assertEquals(listOf("line"), clicked)
    }

    @Test
    fun `held on a card that closes its app, the long click`() {
        val card = button("card", 0, 0, 50, long = {
            clicked += "closed"
            true
        })
        window(100, 100, 100, card)
        val (x, y) = at(card)
        overlays.clickAt(x, y, long = true)
        assertEquals(listOf("closed"), clicked)
    }

    @Test
    fun `the overlay shown last is the one clicked where two cover the point`() {
        window(100, 100, 200, button("ribbon", 0, 0, 200))
        val warning = button("warning", 0, 0, 100)
        window(150, 150, 100, warning)
        val (x, y) = at(warning)
        overlays.clickAt(x, y, long = false)
        assertEquals(listOf("warning"), clicked)
    }

    @Test
    fun `a window taken off is no longer clicked`() {
        val line = button("line", 0, 0, 50)
        val menu = window(100, 100, 50, line)
        val (x, y) = at(line)
        overlays.removed(menu)
        (menu.parent as ViewGroup).removeView(menu)
        assertNull(overlays.clickAt(x, y, long = false))
    }

    @Test
    fun `the pointer is raised once for each overlay that comes, not for itself`() {
        var raised = 0
        val pointer = window(0, 0, 10)
        overlays.keepOnTop(pointer) { raised++ }
        window(100, 100, 50)
        assertEquals(1, raised)
        overlays.added(pointer)
        assertEquals(1, raised)
        overlays.keepOnTop(null)
        window(200, 200, 50)
        assertEquals(1, raised)
    }

    @Test
    fun `the pointer itself is never clicked`() {
        val pointer = button("pointer", 0, 0, 30)
        val window = window(10, 10, 30, pointer)
        overlays.keepOnTop(window) {}
        overlays.removed(window)
        val (x, y) = at(pointer)
        assertFalse(overlays.clickAt(x, y, long = false) == true)
        assertTrue(clicked.isEmpty())
    }

    @Test
    fun `the touchpad's pointer over a frame is heard by it, beside it is not`() {
        var heard = 0
        val frame = PointerFrame(activity).apply { onPointer = { heard++ } }
        screen.addView(
            frame,
            FrameLayout.LayoutParams(200, 200).apply {
                leftMargin = 100
                topMargin = 100
            },
        )
        layOut()
        overlays.added(frame)
        val (x, y) = at(frame)
        overlays.pointedAt(x, y)
        overlays.pointedAt(x + 300, y + 300)
        assertEquals(1, heard)
    }

    @Test
    fun `a mouse over a line inside the frame is heard by the frame`() {
        var heard = 0
        val frame = PointerFrame(activity).apply { onPointer = { heard++ } }
        frame.addView(button("line", 0, 0, 50))
        screen.addView(frame, FrameLayout.LayoutParams(200, 200))
        layOut()
        val hover = MotionEvent.obtain(0, 0, MotionEvent.ACTION_HOVER_MOVE, 10f, 10f, 0)
        hover.source = InputDevice.SOURCE_MOUSE
        frame.dispatchGenericMotionEvent(hover)
        assertEquals(1, heard)
    }

    @Test
    fun `a click beside an overlay closes it as a mouse's would, and goes on to what is there`() {
        var closed = false
        val ribbon = window(100, 100, 200, button("card", 0, 0, 50))
        ribbon.closedByClickOutside { closed = true }
        assertNull(overlays.clickAt(600, 600, long = false))
        assertTrue(closed)
    }

    @Test
    fun `a click in the margin around an overlay's panel closes it and goes on to what is there`() {
        var closed = false
        val menu = frame(100, 100, 200, panel(150, button("line", 0, 0, 20)))
        menu.closedByClickOutside { closed = true }
        val xy = IntArray(2).also(menu::getLocationOnScreen)
        assertNull(overlays.clickAt(xy[0] + 180, xy[1] + 180, long = false))
        assertTrue(closed)
        assertTrue(clicked.isEmpty())
    }

    @Test
    fun `a mouse's press in that margin closes it too, on the panel it does not`() {
        var closed = 0
        val menu = frame(100, 100, 200, panel(150, button("line", 0, 0, 20)))
        menu.closedByClickOutside { closed++ }
        fun press(x: Float, y: Float) {
            val down = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, x, y, 0)
            menu.dispatchTouchEvent(down)
            down.recycle()
        }
        press(100f, 100f)
        assertEquals(0, closed)
        press(180f, 180f)
        assertEquals(1, closed)
    }

    @Test
    fun `a card drawn by its own root takes a click on its padding, not the app under it`() {
        val plate = android.widget.LinearLayout(activity).apply {
            setPadding(40, 40, 40, 40)
            addView(View(activity), android.widget.LinearLayout.LayoutParams(20, 20))
            setOnClickListener { clicked += "plate" }
        }
        val xy = IntArray(2).also(window(100, 100, 200, plate)::getLocationOnScreen)
        assertEquals(true, overlays.clickAt(xy[0] + 180, xy[1] + 180, long = false))
        assertEquals(listOf("plate"), clicked)
    }

    /** A panel in an overlay's window, [size] square from its corner: the rest of the window is margin. */
    private fun panel(size: Int, vararg children: View) = FrameLayout(activity).apply {
        layoutParams = FrameLayout.LayoutParams(size, size)
        children.forEach { addView(it) }
    }

    private companion object {
        const val SCREEN = 1000
    }
}
