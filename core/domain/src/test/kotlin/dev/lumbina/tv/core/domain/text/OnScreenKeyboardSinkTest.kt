package dev.lumbina.tv.core.domain.text

import dev.lumbina.tv.core.testing.FakeScreenReader
import dev.lumbina.tv.core.testing.FakeUiNode
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** An app-drawn keyboard: a label as the search line, letter keys, space and layout switches. */
class OnScreenKeyboardSinkTest {

    private val typed = StringBuilder()
    private var layout = "ru"
    private val screen = FakeScreenReader()

    private fun key(label: String) = FakeUiNode(text = label.uppercase(), isClickable = true) { typed.append(label) }

    private fun keyboard(): FakeUiNode {
        val letters = when (layout) {
            "ru" -> RU
            "en" -> EN
            else -> DIGITS
        }
        val keys = letters.map { key(it.toString()) } + listOf(
            FakeUiNode(description = "Пробел", isClickable = true) { typed.append(' ') },
            FakeUiNode(text = "Рус", isClickable = true) {
                layout = "ru"
                screen.root = keyboard()
            },
            FakeUiNode(text = "Eng", isClickable = true) {
                layout = "en"
                screen.root = keyboard()
            },
            FakeUiNode(text = "!#1", isClickable = true) {
                layout = "digits"
                screen.root = keyboard()
            },
        )
        return FakeUiNode(children = listOf(FakeUiNode(text = "Голосовой поиск")) + keys)
    }

    private fun sink() = OnScreenKeyboardSink(screen, clickDelayMillis = 0, layoutDelayMillis = 0)

    @Test
    fun `types russian with space, mapping ё to е`() = runTest {
        screen.root = keyboard()
        assertTrue(sink().deliver("полицейский с рублёвки"))
        assertEquals("полицейский с рублевки", typed.toString())
    }

    @Test
    fun `switches layouts for english words`() = runTest {
        screen.root = keyboard()
        assertTrue(sink().deliver("blade раннер"))
        assertEquals("blade раннер", typed.toString())
    }

    @Test
    fun `digits are typed on the layout behind the symbols switch`() = runTest {
        screen.root = keyboard()
        assertTrue(sink().deliver("1 2 3 прием"))
        assertEquals("1 2 3 прием", typed.toString())
    }

    @Test
    fun `skips characters no layout has`() = runTest {
        screen.root = keyboard()
        sink().deliver("ок!")
        assertEquals("ок", typed.toString())
    }

    @Test
    fun `reads the screen again only after a layout switch`() = runTest {
        screen.root = keyboard()
        var scans = 0
        val counting = object : ScreenReader by screen {
            override fun activeWindow(): UiNode? = screen.activeWindow().also { scans++ }
        }
        OnScreenKeyboardSink(counting, clickDelayMillis = 0, layoutDelayMillis = 0).deliver("кот cat")
        assertEquals("кот cat", typed.toString())
        // Once at the start, then after each switch: "Рус" (no "c" there) and "Eng".
        assertEquals(3, scans)
    }

    @Test
    fun `nothing typed is not delivered, so another way gets a try`() = runTest {
        // An A–Z jump bar: enough one-letter buttons, none of them Russian.
        screen.root = FakeUiNode(children = EN.map { key(it.toString()) })
        assertFalse(sink().deliver("кот"))
        assertEquals("", typed.toString())
    }

    @Test
    fun `screens without a keyboard are not typed on`() = runTest {
        screen.root = FakeUiNode(children = listOf(key("a"), key("b")))
        assertFalse(sink().deliver("ab"))
        assertEquals("", typed.toString())
    }

    /**
     * A keyboard whose space is a picture: three keys with no label (erase, space, shift) among
     * the letters, and the line showing what is typed. A button with no label elsewhere is not its.
     */
    private fun pictureKeyboard(line: FakeUiNode): FakeUiNode {
        fun letter(char: Char) = FakeUiNode(text = char.toString(), isClickable = true) { line.text += char }
        val erase = FakeUiNode(isClickable = true) { line.text = line.text.orEmpty().dropLast(1) }
        val space = FakeUiNode(isClickable = true) { line.text += " " }
        val shift = FakeUiNode(isClickable = true)
        val keys = RU.take(11).map(::letter) + erase + RU.drop(11).take(11).map(::letter) + space +
            RU.drop(22).map(::letter) + shift + FakeUiNode(text = "Eng", isClickable = true)
        val card = FakeUiNode(isClickable = true) { line.text = "opened a film" }
        return FakeUiNode(children = listOf(line) + keys + card)
    }

    @Test
    fun `a space key with no label is found by what it does to the line`() = runTest {
        val line = FakeUiNode(text = "")
        screen.root = pictureKeyboard(line)
        assertTrue(sink().deliver("раз два три"))
        assertEquals("раз два три", line.text)
    }

    @Test
    fun `a single character erased while the space is looked for is typed again`() = runTest {
        // An empty line shows a prompt, as a search field does.
        val line = FakeUiNode(text = "Search")
        fun show(text: String) {
            line.text = text.ifEmpty { "Search" }
        }
        var written = ""
        fun letter(char: Char) = FakeUiNode(text = char.toString(), isClickable = true) {
            written += char
            show(written)
        }
        val erase = FakeUiNode(isClickable = true) {
            written = written.dropLast(1)
            show(written)
        }
        val space = FakeUiNode(isClickable = true) {
            written += " "
            show(written)
        }
        val keys = RU.take(11).map(::letter) + erase + RU.drop(11).take(11).map(::letter) + space +
            RU.drop(22).map(::letter)
        screen.root = FakeUiNode(children = listOf(line) + keys)
        assertTrue(sink().deliver("я тут"))
        assertEquals("я тут", written)
    }

    @Test
    fun `a key that typed something else after a single character is not taken for erasing`() = runTest {
        val line = FakeUiNode(text = "")
        fun letter(char: Char) = FakeUiNode(text = char.toString(), isClickable = true) { line.text += char }
        val dot = FakeUiNode(isClickable = true) { line.text += "." }
        val keys = RU.take(11).map(::letter) + dot + RU.drop(11).map(::letter)
        screen.root = FakeUiNode(children = listOf(line) + keys)
        sink().deliver("я тут")
        assertEquals("я.", line.text)
    }

    @Test
    fun `text already in the line stays, and the new one goes after it`() = runTest {
        val line = FakeUiNode(text = "кот")
        screen.root = pictureKeyboard(line)
        assertTrue(sink().deliver("и пес"))
        assertEquals("коти пес", line.text)
    }

    @Test
    fun `with no space to be found, typing stops at the first word`() = runTest {
        // The line shows nothing of what is typed, so no key can be told from another.
        val keys = RU.map { key(it.toString()) } + FakeUiNode(isClickable = true) { typed.append('?') } +
            FakeUiNode(text = "Eng", isClickable = true)
        screen.root = FakeUiNode(children = listOf(FakeUiNode(text = "Голосовой поиск")) + keys)
        assertTrue(sink().deliver("раз два"))
        assertEquals("раз", typed.toString())
    }

    @Test
    fun `a suggestion repeating the line is not taken for it`() = runTest {
        val line = FakeUiNode(text = "")
        val board = pictureKeyboard(line)
        screen.root = FakeUiNode(children = listOf(board, FakeUiNode(text = "раз")))
        assertTrue(sink().deliver("раз два"))
        assertEquals("раз два", line.text)
    }

    @Test
    fun `a space after characters that were skipped is not typed`() = runTest {
        val line = FakeUiNode(text = "")
        screen.root = pictureKeyboard(line)
        assertTrue(sink().deliver("5 раз - два"))
        assertEquals("раз два", line.text)
    }

    @Test
    fun `the space is looked for again in another layout`() = runTest {
        val line = FakeUiNode(text = "")
        screen.root = twoLayouts(line, russian = true)
        assertTrue(sink().deliver("раз two"))
        assertEquals("раз two", line.text)
    }

    @Test
    fun `a key that changes nothing is pressed once, and the next one is tried`() = runTest {
        val line = FakeUiNode(text = "")
        val shift = FakeUiNode(isClickable = true)
        val space = FakeUiNode(isClickable = true) { line.text += " " }
        val keys = RU.map { char -> FakeUiNode(text = char.toString(), isClickable = true) { line.text += char } }
        screen.root = FakeUiNode(children = listOf(line) + keys.take(3) + shift + space + keys.drop(3))
        assertTrue(sink().deliver("раз два три"))
        assertEquals("раз два три", line.text)
        assertEquals(1, shift.clicks)
    }

    @Test
    fun `only a few keys are pressed blindly`() = runTest {
        val line = FakeUiNode(text = "")
        val blind = List(10) { FakeUiNode(isClickable = true) }
        val keys = RU.map { char -> FakeUiNode(text = char.toString(), isClickable = true) { line.text += char } }
        screen.root = FakeUiNode(children = listOf(line) + keys.take(1) + blind + keys.drop(1))
        assertTrue(sink().deliver("раз два"))
        assertEquals("раз", line.text)
        assertEquals(6, blind.sumOf { it.clicks })
    }

    /** Erase and space with no label, in one order in the Russian layout and the other in the English. */
    private fun twoLayouts(line: FakeUiNode, russian: Boolean): FakeUiNode {
        fun letter(char: Char) = FakeUiNode(text = char.toString(), isClickable = true) { line.text += char }
        val erase = FakeUiNode(isClickable = true) { line.text = line.text.orEmpty().dropLast(1) }
        val space = FakeUiNode(isClickable = true) { line.text += " " }
        val switch = FakeUiNode(text = if (russian) "Eng" else "Рус", isClickable = true) {
            screen.root = twoLayouts(line, !russian)
        }
        val letters = (if (russian) RU else EN).map(::letter)
        val blind = if (russian) listOf(space, erase) else listOf(erase, space)
        return FakeUiNode(children = listOf(line) + letters.take(5) + blind + letters.drop(5) + switch)
    }

    private companion object {
        const val RU = "йцукенгшщзхфывапролджэячсмитьбю"
        const val EN = "qwertyuiopasdfghjklzxcvbnm"
        const val DIGITS = "1234567890"
    }
}
