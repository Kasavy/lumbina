package dev.lumbina.tv.core.domain.text

import kotlinx.coroutines.delay

/**
 * Types on an app's own on-screen keyboard by clicking its keys.
 *
 * Some TV apps show a plain text label as the search line and draw their own keyboard, so
 * there is no editable field. Their keys are clickable nodes labelled with one letter, plus a
 * space key and layout switches ("Рус", "Eng", "123+", "!#1"), enough to type. Characters no layout
 * has (punctuation) are skipped; when nothing could be typed at all, the text is not delivered,
 * so the next way of delivering it gets a try.
 *
 * A space key drawn as a picture has no label. It is then looked for among the keyboard's keys
 * without one, by what a press does to the line being typed ([findSpace]), and looked for again
 * after a layout switch, since another layout may draw its keys in another order. Where it can't
 * be found, typing stops at that space: words run together are worth less than the first ones.
 */
class OnScreenKeyboardSink(
    private val screen: ScreenReader,
    private val clickDelayMillis: Long = 40,
    private val layoutDelayMillis: Long = 300,
    private val minKeys: Int = 20,
    private val maxProbes: Int = 6,
) : TextSink {
    override val name = "on-screen keyboard"

    override suspend fun deliver(text: String): Boolean {
        var keys = scan(spaceAt = null)
        if (keys.letters.size < minKeys) return false
        val typed = StringBuilder()
        for (char in normalize(text)) {
            keys = step(char, keys, typed) ?: break
        }
        return typed.isNotBlank()
    }

    /** Types [char] and notes it in [typed]; null when it is a space that can't be typed, which ends the typing. */
    private suspend fun step(char: Char, keys: Keys, typed: StringBuilder): Keys? {
        // A space with nothing before it to part: the characters there were skipped.
        if (char.isWhitespace() && (typed.isEmpty() || typed.last() == ' ')) return keys
        if (char.isWhitespace() && keys.space == null) return findSpace(keys, typed)?.also { typed.append(' ') }
        val (now, clicked) = type(char, keys)
        if (clicked) typed.append(if (char.isWhitespace()) ' ' else char)
        return now
    }

    /**
     * Clicks the key for [char] among [keys], switching layouts until one has it (skipped if
     * none does). The screen is read again only when it may have changed: after a layout
     * switch, or when a key no longer clicks. Returns the keys now on screen and whether [char]
     * was typed.
     */
    private suspend fun type(char: Char, keys: Keys): Pair<Keys, Boolean> {
        var current = keys
        var rescanned = false
        val tried = mutableSetOf<String>()
        while (true) {
            val key = if (char.isWhitespace()) current.space else current.letters[char]
            if (key != null) {
                if (key.click()) {
                    delay(clickDelayMillis)
                    return current to true
                }
                if (rescanned) return current to false
                rescanned = true
                current = scan(current.spaceAt)
                continue
            }
            val layout = current.layouts.firstOrNull { it.label.lowercase() !in tried } ?: return current to false
            tried += layout.label.lowercase()
            layout.click()
            delay(layoutDelayMillis)
            current = scan(spaceAt = null)
        }
    }

    /**
     * Presses the keys with no label one by one, at most [maxProbes] of them, and reads the line
     * that ends with [typed]: the key that adds a space to it is the space key, and the keys
     * returned have it (the space is typed by then). A key that erased has its letter typed again
     * (an emptied line may show its prompt again instead of nothing).
     * One that changed nothing (a shift) is left as pressed: a second press could lock it, and
     * capitals do a search no harm. Null when the line can't be read, a key did anything else to
     * it, or no key is the space.
     */
    private suspend fun findSpace(keys: Keys, typed: CharSequence): Keys? {
        // The keys were read before the typing: the line is read now.
        var current = scan(spaceAt = null)
        val (at, line) = current.lineEnding(typed) ?: return null
        for (index in keys.unlabeled.indices.take(maxProbes)) {
            if (current.unlabeled.getOrNull(index)?.click() != true) continue
            val pressed = rescanned(index)
            val now = pressed.texts[at]
            when {
                now == "$line " -> return pressed

                now == line -> Unit

                // A line of one character, erased, shows nothing, or the prompt it showed before the typing.
                now == line.dropLast(1) || (line.length == 1 && (now.isNullOrEmpty() || now == keys.texts[at])) ->
                    pressed.letters[typed.last()]?.click()

                else -> return null
            }
            current = rescanned(spaceAt = null)
        }
        return null
    }

    private suspend fun rescanned(spaceAt: Int?): Keys {
        delay(layoutDelayMillis)
        return scan(spaceAt)
    }

    /**
     * The keyboard on screen. [unlabeled] are its keys with no label: those among the others, in
     * the order of the screen; [spaceAt] is the one of them known to be the space. [texts] is
     * everything written on screen by its place in the window, the line being typed among it.
     */
    private class Keys(
        val letters: Map<Char, UiNode>,
        val layouts: List<UiNode>,
        val unlabeled: List<UiNode>,
        val texts: Map<Int, String>,
        val spaceAt: Int?,
        labelledSpace: UiNode?,
    ) {
        val space: UiNode? = labelledSpace ?: spaceAt?.let(unlabeled::getOrNull)

        /**
         * The line that ends with [typed], its place and what it says; null before anything is
         * typed, or when no line shows it. The first such text counts: a search line stands
         * above the suggestions that may repeat it.
         */
        fun lineEnding(typed: CharSequence): Pair<Int, String>? =
            if (typed.isBlank()) null else texts.entries.firstOrNull { it.value.lowercase().endsWith(typed) }?.toPair()
    }

    private fun scan(spaceAt: Int?): Keys {
        val letters = mutableMapOf<Char, UiNode>()
        var space: UiNode? = null
        val layouts = mutableListOf<UiNode>()
        val blank = mutableListOf<IndexedValue<UiNode>>()
        val keysAt = mutableListOf<Int>()
        val texts = linkedMapOf<Int, String>()
        screen.activeWindow()?.inOrder()?.filter { it.isVisible }?.forEachIndexed { index, node ->
            val label = if (node.isClickable) node.label.lowercase() else null
            when {
                label == null -> Unit
                label.isEmpty() -> blank += IndexedValue(index, node)
                label.length == 1 -> letters.putIfAbsent(label[0], node)
                label in SPACE_LABELS -> space = node
                label in LAYOUT_LABELS -> layouts += node
            }
            val isKey = label != null && (label.length == 1 || label in SPACE_LABELS || label in LAYOUT_LABELS)
            if (isKey) keysAt += index else node.text?.let { texts[index] = it }
        }
        // Buttons with no label elsewhere on the screen (cards, a menu) are not the keyboard's.
        val among = keysAt.minOrNull()?.rangeTo(keysAt.max())
        val unlabeled = blank.filter { among != null && it.index in among }.map { it.value }
        return Keys(letters, layouts, unlabeled, texts, spaceAt, space)
    }

    /**
     * The window's nodes parents first, children in their order: what stands between the first
     * key and the last is then the keyboard's own, not what happens to lie as deep elsewhere.
     */
    private fun UiNode.inOrder(): Sequence<UiNode> = sequence {
        yield(this@inOrder)
        children.forEach { yieldAll(it.inOrder()) }
    }

    private fun normalize(text: String) = text.lowercase().replace('ё', 'е').trim()

    private companion object {
        val SPACE_LABELS = setOf("пробел", "space", "␣")
        val LAYOUT_LABELS = setOf("рус", "eng", "en", "ru", "abc", "абв", "123+", "123", "?123", "!#1")
    }
}
