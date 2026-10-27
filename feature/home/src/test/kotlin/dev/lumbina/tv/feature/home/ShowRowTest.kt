package dev.lumbina.tv.feature.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** A row brought up for its settings comes up from its start, wherever home stood. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ShowRowTest {

    @get:Rule val compose = createComposeRule()

    private lateinit var grid: LazyGridState
    private lateinit var scope: CoroutineScope

    // Home in short: the bar, a row of apps, then a category laid out as a grid of 30 tiles.
    private val keys = listOf("header", "title:favorites", "row:favorites", "title:films") +
        (1..30).map { "films:$it" } + listOf("title:kids", "row:kids", "title:news") + (1..5).map { "news:$it" }

    private fun show() = compose.setContent {
        grid = rememberLazyGridState()
        scope = rememberCoroutineScope()
        LazyVerticalGrid(GridCells.Fixed(1), Modifier.height(400.dp), state = grid) {
            items(keys.size, key = { keys[it] }) { Box(Modifier.height(100.dp)) }
        }
    }

    private fun showRow(rowKey: String) {
        compose.runOnIdle { scope.launch { grid.showRow(rowKey) } }
        compose.waitForIdle()
    }

    @Test
    fun `a grid seen from its middle comes up from its heading`() {
        show()
        compose.runOnIdle { scope.launch { grid.scrollToItem(keys.indexOf("films:12")) } }
        compose.waitForIdle()

        showRow("category:films")

        assertEquals(keys.indexOf("title:films"), compose.runOnIdle { grid.firstVisibleItemIndex })
    }

    @Test
    fun `a row out of sight is found below`() {
        show()

        showRow("category:kids")

        assertEquals(keys.indexOf("title:kids"), compose.runOnIdle { grid.firstVisibleItemIndex })
    }
}
