package dev.lumbina.tv.core.ui

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.tv.material3.Text
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The card drawn on Android 9, where tv-material's is not used: it still clicks, by OK and as a node. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class DrawnCardTest {

    @get:Rule val compose = createComposeRule()

    private var clicks = 0
    private var longClicks = 0

    private fun show() = compose.setContent {
        TileCard(onClick = { clicks++ }, onLongClick = { longClicks++ }) { Text(TILE) }
    }

    @Test
    fun `it is the drawn card here`() {
        assertFalse(LAYERS_ARE_CHEAP)
    }

    @Test
    fun `it clicks and long-clicks as a node`() {
        show()
        compose.onNodeWithText(TILE, useUnmergedTree = false).assertHasClickAction()
        compose.onNodeWithText(TILE).performSemanticsAction(SemanticsActions.OnClick)
        compose.onNodeWithText(TILE).performSemanticsAction(SemanticsActions.OnLongClick)
        assertEquals(1, clicks)
        assertEquals(1, longClicks)
    }

    @Test
    fun `OK on the focused card clicks it`() {
        show()
        compose.onNodeWithText(TILE).requestFocus()
        compose.onNodeWithText(TILE).performKeyInput { pressKey(Key.DirectionCenter) }
        compose.runOnIdle { assertEquals(1, clicks) }
    }

    private companion object {
        const val TILE = "Tile"
    }
}
