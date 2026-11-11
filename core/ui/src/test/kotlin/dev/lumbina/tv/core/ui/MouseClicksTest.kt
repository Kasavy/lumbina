package dev.lumbina.tv.core.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.MouseButton
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.Text
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** A mouse clicks tv-material's own components, which take OK from the D-pad only. */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MouseClicksTest {

    @get:Rule val compose = createComposeRule()

    private var first = 0
    private var second = 0
    private var longClicks = 0
    private var disabled = 0
    private var taps = 0

    private fun show() = compose.setContent {
        Box(Modifier.fillMaxSize().mouseClicks().testTag(ROOT)) {
            // What has no click of its own, under the buttons.
            Box(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { taps++ } })
            Column {
                Button(onClick = { first++ }) { Text(FIRST) }
                Button(onClick = { second++ }, onLongClick = { longClicks++ }) { Text(SECOND) }
                Button(onClick = { disabled++ }, enabled = false) { Text(DISABLED) }
            }
        }
    }

    @Test
    fun `a click clicks the button under the pointer and focuses it`() {
        show()
        compose.onNodeWithText(SECOND).performMouseInput { click(center) }
        compose.runOnIdle {
            assertEquals(0, first)
            assertEquals(1, second)
        }
        compose.onNodeWithText(SECOND).assertIsFocused()
    }

    @Test
    fun `holding the button long-clicks while it is held, and its release clicks nothing`() {
        show()
        compose.onNodeWithText(SECOND).performMouseInput {
            moveTo(center)
            press()
        }
        // The long click comes on the frame clock's time, not on a following event.
        compose.mainClock.advanceTimeBy(HELD_MILLIS)
        compose.runOnIdle { assertEquals(1, longClicks) }
        compose.onNodeWithText(SECOND).performMouseInput { release() }
        compose.runOnIdle {
            assertEquals(1, longClicks)
            assertEquals(0, second)
        }
    }

    @Test
    fun `held on a button with no long click, it clicks when let go`() {
        show()
        compose.onNodeWithText(FIRST).performMouseInput { longClick(center) }
        compose.runOnIdle { assertEquals(1, first) }
    }

    @Test
    fun `a disabled button is focused, not clicked`() {
        show()
        compose.onNodeWithText(DISABLED).performMouseInput { click(center) }
        compose.runOnIdle { assertEquals(0, disabled + taps) }
        compose.onNodeWithText(DISABLED).assertIsFocused()
    }

    @Test
    fun `let go off the button, the press clicks nothing`() {
        show()
        compose.onNodeWithText(SECOND).performMouseInput {
            moveTo(center)
            press()
        }
        compose.onNodeWithText(FIRST).performMouseInput {
            moveTo(center)
            release()
        }
        compose.runOnIdle { assertEquals(0, first + second + longClicks) }
    }

    @Test
    fun `the other buttons are left to the components`() {
        var clicks = 0
        compose.setContent {
            Box(Modifier.fillMaxSize().mouseClicks()) {
                // Foundation's own click answers any button: the main one is taken from it, the others not.
                Box(Modifier.size(100.dp).clickable { clicks++ }.testTag(INNER))
            }
        }
        compose.onNodeWithTag(INNER).performMouseInput { click(center, MouseButton.Secondary) }
        compose.runOnIdle { assertEquals(1, clicks) }
        compose.onNodeWithTag(INNER).assertIsNotFocused()
    }

    @Test
    fun `held past a long press with nothing for it and let go elsewhere, nothing is clicked`() {
        show()
        compose.onNodeWithText(FIRST).performMouseInput {
            moveTo(center)
            press()
        }
        compose.mainClock.advanceTimeBy(HELD_MILLIS)
        compose.onNodeWithText(SECOND).performMouseInput {
            moveTo(center)
            release()
        }
        compose.runOnIdle { assertEquals(0, first + second) }
    }

    @Test
    fun `what came in the pressed one's place meanwhile is not clicked`() {
        var swapped by mutableStateOf(false)
        compose.setContent {
            Box(Modifier.fillMaxSize().mouseClicks()) {
                // A list putting another item where the pressed one was.
                if (swapped) {
                    Button(onClick = { second++ }, modifier = Modifier.size(100.dp).testTag(INNER)) {}
                } else {
                    Button(onClick = { first++ }, modifier = Modifier.size(100.dp).testTag(INNER)) {}
                }
            }
        }
        compose.onNodeWithTag(INNER).performMouseInput {
            moveTo(center)
            press()
        }
        compose.runOnIdle { swapped = true }
        compose.onNodeWithTag(INNER).performMouseInput { release() }
        compose.runOnIdle { assertEquals(0, first + second) }
    }

    @Test
    fun `the pressed one moved away meanwhile is still clicked`() {
        var moved by mutableStateOf(false)
        var clicked = 0
        compose.setContent {
            Column(Modifier.fillMaxSize().mouseClicks()) {
                // Before Android 13 the press puts the window in touch mode, and a list scrolls to
                // where the focus went: the pressed row slides from under the pointer.
                Box(Modifier.size(if (moved) 150.dp else 0.dp))
                Button(onClick = { clicked++ }, modifier = Modifier.size(100.dp).testTag(INNER)) {}
            }
        }
        compose.onNodeWithTag(INNER).performMouseInput {
            moveTo(center)
            press()
        }
        compose.runOnIdle { moved = true }
        compose.onRoot().performMouseInput { release() }
        compose.runOnIdle { assertEquals(1, clicked) }
    }

    @Test
    fun `a double click on what opens a window leaves the new window's button alone`() {
        var open by mutableStateOf(false)
        compose.setContent {
            ProvideMouseClicks {
                Box(Modifier.fillMaxSize().mouseClicks()) {
                    Button(onClick = { open = true }, modifier = Modifier.size(100.dp).testTag(OUTER)) {}
                    if (open) {
                        LumbinaPopup(onDismissRequest = {}) {
                            Button(onClick = { first++ }, modifier = Modifier.size(100.dp).testTag(INNER)) {}
                        }
                    }
                }
            }
        }
        // The clock as a person's double click has it: the popup is on screen a few frames after the first click,
        // not after every animation the click set off has run.
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag(OUTER).performMouseInput { click(center) }
        repeat(FRAMES_TO_OPEN) { compose.mainClock.advanceTimeByFrame() }
        compose.onNodeWithTag(INNER).performMouseInput { click(center) }
        compose.mainClock.autoAdvance = true
        compose.runOnIdle { assertEquals(0, first) }
        // A click of its own, a while later, is the window's.
        compose.mainClock.advanceTimeBy(HELD_MILLIS)
        compose.onNodeWithTag(INNER).performMouseInput {
            advanceEventTime(HELD_MILLIS)
            click(center)
        }
        compose.runOnIdle { assertEquals(1, first) }
    }

    @Test
    fun `a double click on what opens the next page in the same window leaves that page alone`() {
        var page by mutableStateOf(false)
        compose.setContent {
            Box(Modifier.fillMaxSize().mouseClicks()) {
                if (page) {
                    Button(onClick = { first++ }, modifier = Modifier.size(100.dp).testTag(INNER)) {}
                } else {
                    Button(onClick = { page = true }, modifier = Modifier.size(100.dp).testTag(OUTER)) {}
                }
            }
        }
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag(OUTER).performMouseInput { click(center) }
        repeat(FRAMES_TO_OPEN) { compose.mainClock.advanceTimeByFrame() }
        compose.onNodeWithTag(INNER).performMouseInput { click(center) }
        compose.mainClock.autoAdvance = true
        compose.runOnIdle { assertEquals(0, first) }
    }

    @Test
    fun `a double click on the same button clicks it twice, a quick press away from it is a press`() {
        var beside = 0
        compose.setContent {
            Box(Modifier.fillMaxSize().mouseClicks().closedByClickOutside { beside++ }) {
                Button(onClick = { first++ }, modifier = Modifier.size(100.dp).testTag(INNER)) {}
            }
        }
        compose.onNodeWithTag(INNER).performMouseInput {
            click(center)
            advanceEventTime(DOUBLE_GAP_MILLIS)
            click(center)
            // Within a double click of the last but away from it: a press of its own, not a double click's half.
            advanceEventTime(DOUBLE_GAP_MILLIS)
            click(Offset(width + 50f, height + 50f))
        }
        compose.runOnIdle {
            assertEquals(2, first)
            assertEquals(1, beside)
        }
    }

    @Test
    fun `a quick click on another button, away from the last, is a click`() {
        show()
        compose.onNodeWithText(FIRST).performMouseInput { click(center) }
        compose.onNodeWithText(SECOND).performMouseInput {
            advanceEventTime(DOUBLE_GAP_MILLIS)
            click(center)
        }
        compose.runOnIdle { assertEquals(1 to 1, first to second) }
    }

    @Test
    fun `where nothing clicks, the press goes on to what is there`() {
        show()
        compose.onNodeWithTag(ROOT).performMouseInput { click(Offset(width - 1f, height - 1f)) }
        compose.runOnIdle {
            assertEquals(0, first + second + longClicks)
            assertEquals(1, taps)
        }
    }

    @Test
    fun `a popup's own root clicks its buttons, wherever it stands`() {
        compose.setContent {
            Box(Modifier.fillMaxSize().mouseClicks()) {
                LumbinaPopup(onDismissRequest = {}, alignment = Alignment.BottomEnd) {
                    Button(onClick = { first++ }) { Text(FIRST) }
                }
            }
        }
        compose.onNodeWithText(FIRST).performMouseInput { click(center) }
        compose.runOnIdle { assertEquals(1, first) }
    }

    @Test
    fun `a cancelled press clicks nothing`() {
        show()
        compose.onNodeWithText(SECOND).performMouseInput {
            moveTo(center)
            press()
            cancel()
        }
        compose.runOnIdle { assertEquals(0, second + longClicks) }
    }

    @Test
    fun `of overlapping buttons the one on top is clicked, of nested ones the inner`() {
        compose.setContent {
            Box(Modifier.fillMaxSize().mouseClicks()) {
                Button(onClick = { first++ }, modifier = Modifier.size(200.dp)) {}
                Button(onClick = { second++ }, modifier = Modifier.size(100.dp).testTag(OUTER)) {
                    Button(onClick = { longClicks++ }, modifier = Modifier.size(40.dp).testTag(INNER)) {}
                }
            }
        }
        // The corner of the smaller one on top, away from the one inside it.
        compose.onNodeWithTag(OUTER).performMouseInput { click(Offset(width - 2f, height - 2f)) }
        compose.onNodeWithTag(INNER).performMouseInput { click(center) }
        compose.runOnIdle {
            assertEquals(0, first)
            assertEquals(1, second)
            assertEquals(1, longClicks)
        }
    }

    @Test
    fun `a badge over a tile's corner leaves the click to the tile`() {
        compose.setContent {
            Box(Modifier.fillMaxSize().mouseClicks()) {
                Box {
                    Button(onClick = { first++ }, modifier = Modifier.size(120.dp)) {}
                    TickBadge(checked = true, Modifier.align(Alignment.TopEnd).testTag(BADGE))
                }
            }
        }
        compose.onNodeWithTag(BADGE).performMouseInput { click(center) }
        compose.runOnIdle { assertEquals(1, first) }
    }

    @Test
    fun `touch is left to the components`() {
        var clicks = 0
        compose.setContent {
            Box(Modifier.fillMaxSize().mouseClicks()) {
                Box(Modifier.size(100.dp).clickable { clicks++ }.testTag(INNER))
            }
        }
        compose.onNodeWithTag(INNER).performTouchInput { click(center) }
        // Once, by its own handling: the mouse's way would click it a second time.
        compose.runOnIdle { assertEquals(1, clicks) }
    }

    private companion object {
        const val ROOT = "root"
        const val FIRST = "First"
        const val SECOND = "Second"
        const val DISABLED = "Disabled"
        const val INNER = "inner"
        const val OUTER = "outer"
        const val BADGE = "badge"
        const val HELD_MILLIS = 1_000L
        const val FRAMES_TO_OPEN = 3
        const val DOUBLE_GAP_MILLIS = 100L
    }
}

/** On Android 9 the tile is drawn by Lumbina and clicks by a pointer itself: a mouse clicks it once, not twice. */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class MouseClicksDrawnCardTest {

    @get:Rule val compose = createComposeRule()

    @Test
    fun `the drawn tile clicks once, through the semantics`() {
        var clicks = 0
        compose.setContent {
            Column(Modifier.fillMaxSize().mouseClicks()) {
                TileCard(onClick = { clicks++ }, modifier = Modifier.size(200.dp, 120.dp)) { Text("Tile") }
            }
        }
        compose.onNodeWithText("Tile").performMouseInput { click(center) }
        compose.runOnIdle { assertEquals(1, clicks) }
        // Its own pointer handling would click it but leave the focus where it was.
        compose.onNodeWithText("Tile").assertIsFocused()
    }
}
