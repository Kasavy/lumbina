package dev.lumbina.tv.core.domain.text

import dev.lumbina.tv.core.testing.FakeScreenReader
import dev.lumbina.tv.core.testing.FakeUiNode
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DeliverTextTest {

    private val searches = mutableListOf<String>()
    private val search = object : YouTubeSearch {
        override suspend fun search(query: String): Boolean {
            searches += query
            return true
        }
    }

    private fun chain(screen: FakeScreenReader) = DeliverText(
        listOf(
            FocusedFieldSink(screen),
            AnyFieldSink(screen),
            OnScreenKeyboardSink(screen, clickDelayMillis = 0, layoutDelayMillis = 0),
            LauncherSearchSink(screen, setOf("dev.lumbina.tv"), search::search),
        ),
    )

    @Test
    fun `focused field gets the text and is submitted`() = runTest {
        val field = FakeUiNode(isEditable = true)
        val screen = FakeScreenReader(root = FakeUiNode(children = listOf(field)), focused = field)
        assertEquals("focused field", chain(screen)("котики"))
        assertEquals("котики", field.text)
        assertTrue(field.submitted)
    }

    @Test
    fun `a message field gets the text but is left for the user to send`() = runTest {
        val field = FakeUiNode(isEditable = true, sendsOnSubmit = true)
        val screen = FakeScreenReader(root = FakeUiNode(children = listOf(field)), focused = field)
        assertEquals("focused field", chain(screen)("привет"))
        assertEquals("привет", field.text)
        assertFalse(field.submitted)
    }

    @Test
    fun `a visible field is used when focus sits elsewhere`() = runTest {
        val key = FakeUiNode(text = "Й", isClickable = true)
        val field = FakeUiNode(isEditable = true)
        val screen = FakeScreenReader(root = FakeUiNode(children = listOf(key, field)), focused = key)
        assertEquals("visible field", chain(screen)("interstellar"))
        assertEquals("interstellar", field.text)
    }

    @Test
    fun `with several visible fields and focus elsewhere none is guessed`() = runTest {
        val login = FakeUiNode(isEditable = true)
        val password = FakeUiNode(isEditable = true)
        val screen = FakeScreenReader(root = FakeUiNode(packageName = "other", children = listOf(login, password)))
        assertNull(chain(screen)("котики"))
        assertNull(login.text)
        assertNull(password.text)
    }

    @Test
    fun `hidden fields are ignored`() = runTest {
        val hidden = FakeUiNode(isEditable = true, isVisible = false)
        val screen = FakeScreenReader(root = FakeUiNode(packageName = "other", children = listOf(hidden)))
        assertNull(chain(screen)("x"))
    }

    @Test
    fun `search only while a launcher is in front`() = runTest {
        assertEquals(
            "launcher search",
            chain(FakeScreenReader(root = FakeUiNode(packageName = "dev.lumbina.tv")))("котики"),
        )
        assertEquals(listOf("котики"), searches)

        assertNull(chain(FakeScreenReader(root = FakeUiNode(packageName = "ru.kinopoisk.tv")))("котики"))
        assertEquals(1, searches.size)
    }

    @Test
    fun `a launcher's search that did not open is a failure, not a text with nowhere to go`() = runTest {
        val launcher = FakeScreenReader(root = FakeUiNode(packageName = "dev.lumbina.tv"))
        val refusing = DeliverText(listOf(LauncherSearchSink(launcher, setOf("dev.lumbina.tv")) { false }))
        assertEquals(Delivery.Failed("launcher search"), refusing.deliver("x"))
        assertEquals(Delivery.Nowhere, chain(FakeScreenReader()).deliver("x"))
    }

    @Test
    fun `nothing on screen means nothing delivered`() = runTest {
        assertFalse(FocusedFieldSink(FakeScreenReader()).deliver("x"))
        assertNull(chain(FakeScreenReader())("x"))
    }
}
