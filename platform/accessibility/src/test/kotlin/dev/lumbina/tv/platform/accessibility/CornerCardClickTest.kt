package dev.lumbina.tv.platform.accessibility

import android.view.KeyEvent
import android.view.ViewGroup
import android.widget.TextView
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** A corner card's button clicked (a mouse, the phone touchpad) is OK on it, as its owner hears it. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CornerCardClickTest {

    @Test
    fun `a button clicked is chosen and OK sent to the card's owner`() {
        val card = CornerCard(RuntimeEnvironment.getApplication())
        val heard = mutableListOf<Pair<Int, Int>>()
        card.takesKeys { event ->
            heard += event.action to card.selected
            event.keyCode == KeyEvent.KEYCODE_DPAD_CENTER
        }
        card.show("Debugging is off", listOf("Connect…", "Not now"))
        buttons(card).first { it.text == "Not now" }.performClick()
        assertEquals(listOf(KeyEvent.ACTION_DOWN to 1, KeyEvent.ACTION_UP to 1), heard)
        card.hide()
    }

    /** The card's buttons, found in its window. */
    private fun buttons(card: CornerCard): List<TextView> {
        val root = CornerCard::class.java.getDeclaredField("root").apply { isAccessible = true }.get(card) as ViewGroup
        val found = mutableListOf<TextView>()
        fun walk(group: ViewGroup) {
            for (i in 0 until group.childCount) {
                when (val child = group.getChildAt(i)) {
                    is ViewGroup -> walk(child)
                    is TextView -> if (child.hasOnClickListeners()) found += child
                }
            }
        }
        walk(root)
        return found
    }
}
