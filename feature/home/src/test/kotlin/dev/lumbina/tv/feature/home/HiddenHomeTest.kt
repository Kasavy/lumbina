package dev.lumbina.tv.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.Text
import dev.lumbina.tv.core.ui.mouseClicks
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Home under a screen drawn over it in the same window, as the settings: out of a pointer's reach. */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HiddenHomeTest {

    @get:Rule val compose = createComposeRule()

    private var clicks = 0
    private var hidden by mutableStateOf(true)

    private fun show() = compose.setContent {
        Box(Modifier.fillMaxSize().mouseClicks()) {
            Box(Modifier.hiddenUnder(hidden, null)) {
                Button(onClick = { clicks++ }, modifier = Modifier.size(200.dp)) { Text("Tile") }
            }
            // A panel's heading over it: nothing to click there.
            Box(Modifier.size(200.dp).background(Color.Black).testTag(PANEL))
        }
    }

    @Test
    fun `a click on the screen over home leaves what is under it alone`() {
        show()
        compose.onNodeWithTag(PANEL).performMouseInput { click(center) }
        compose.runOnIdle { assertEquals(0, clicks) }
    }

    @Test
    fun `home under it is out of the accessibility tree too, which the phone's touchpad walks`() {
        show()
        compose.onNodeWithText("Tile").assertDoesNotExist()
        hidden = false
        compose.onNodeWithText("Tile").assertExists()
    }

    @Test
    fun `the arrows don't walk from the screen over home into home`() {
        val panel = FocusRequester()
        compose.setContent {
            Column(Modifier.fillMaxSize()) {
                Box(Modifier.hiddenUnder(hidden = true, over = null)) {
                    Button(onClick = {}, modifier = Modifier.testTag(TILE)) { Text("Tile") }
                }
                Button(onClick = {}, modifier = Modifier.focusRequester(panel).testTag(ROW)) { Text("Row") }
            }
        }
        compose.runOnIdle { panel.requestFocus() }
        compose.onNodeWithTag(ROW).performKeyInput { pressKey(Key.DirectionUp) }
        compose.onNodeWithTag(ROW, useUnmergedTree = true).assertIsFocused()
    }

    @Test
    fun `home shown again takes clicks again`() {
        show()
        hidden = false
        compose.onNodeWithTag(PANEL).performMouseInput { click(center) }
        compose.runOnIdle { assertEquals(1, clicks) }
    }

    private companion object {
        const val PANEL = "panel"
        const val TILE = "tile"
        const val ROW = "row"
    }
}
