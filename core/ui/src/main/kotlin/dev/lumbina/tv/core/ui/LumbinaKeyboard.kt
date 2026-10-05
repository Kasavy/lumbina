package dev.lumbina.tv.core.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import java.util.Locale

/**
 * Whether text fields in Lumbina's dialogs use [LumbinaKeyboard] (true) or the device's keyboard.
 * A local because it is one app-wide preference every dialog needs, set once at the root.
 */
@Suppress("CompositionLocalAllowlist")
val LocalUseLumbinaKeyboard = staticCompositionLocalOf { true }

/**
 * Starts or stops voice input into the focused dialog's field (like the remote's mic button);
 * null on devices without voice input, which hides the keyboard's mic key.
 */
@Suppress("CompositionLocalAllowlist")
val LocalVoiceInput = staticCompositionLocalOf<(() -> Unit)?> { null }

/**
 * Letter layouts of [LumbinaKeyboard]. [label] names the layout on the language key;
 * [lettersLabel] leads back to letters from the symbols page.
 */
internal enum class KeyboardLanguage(val label: String, val lettersLabel: String, val rows: List<String>) {
    English("EN", "ABC", listOf("qwertyuiop", "asdfghjkl", "zxcvbnm")),
    Russian("RU", "АБВ", listOf("йцукенгшщзхъ", "фывапролджэ", "ячсмитьбюё")),
    ;

    fun next(): KeyboardLanguage = entries[(ordinal + 1) % entries.size]

    companion object {
        /** The layout last switched to, shared by all dialogs while the app runs. */
        var last: KeyboardLanguage =
            if (Locale.getDefault().language == "ru") Russian else English
    }
}

private val SymbolRows = listOf("1234567890", "-_.,!?:;'\"", "()&@#+=*/%")

/** Pure key logic of [LumbinaKeyboard], kept apart from the UI so it can be tested. */
internal object KeyboardEditing {
    fun type(text: String, char: Char, shift: Boolean): String = text + if (shift) char.uppercaseChar() else char

    fun backspace(text: String): String = text.dropLast(1)

    fun space(text: String): String = if (text.isEmpty() || text.endsWith(' ')) text else "$text "
}

/**
 * An on-screen keyboard for the remote's D-pad: Russian and English letters, digits and symbols,
 * Shift (on at the start), Space, Backspace (hold to clear) and a mic key where voice input is
 * available ([LocalVoiceInput]). [firstKey] focuses the top-left key.
 */
@Composable
fun LumbinaKeyboard(
    text: String,
    onTextChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    firstKey: FocusRequester = remember { FocusRequester() },
) {
    var language by remember { mutableStateOf(KeyboardLanguage.last) }
    var symbols by remember { mutableStateOf(false) }
    var shift by remember { mutableStateOf(text.isEmpty()) }
    val rows = if (symbols) SymbolRows else language.rows
    Column(verticalArrangement = Arrangement.spacedBy(KEY_GAP), modifier = modifier.fillMaxWidth()) {
        rows.forEachIndexed { rowIndex, row ->
            KeyRow {
                row.forEachIndexed { i, char ->
                    val label = if (shift && !symbols) char.uppercase() else char.toString()
                    Key(
                        label = label,
                        onClick = {
                            onTextChange(KeyboardEditing.type(text, char, shift && !symbols))
                            shift = false
                        },
                        modifier = if (rowIndex == 0 && i == 0) Modifier.focusRequester(firstKey) else Modifier,
                    )
                }
            }
        }
        KeyRow {
            LocalVoiceInput.current?.let { voice -> Key(icon = R.drawable.ic_key_mic, onClick = voice, weight = WIDE) }
            Key(icon = R.drawable.ic_key_shift, onClick = {
                shift = !shift
            }, highlighted = shift && !symbols, weight = WIDE)
            Key(
                label = language.next().label,
                onClick = {
                    language = language.next().also { KeyboardLanguage.last = it }
                    symbols = false
                },
                weight = WIDE,
            )
            Key(label = if (symbols) language.lettersLabel else "?123", onClick = { symbols = !symbols }, weight = WIDE)
            Key(icon = R.drawable.ic_key_space, onClick = { onTextChange(KeyboardEditing.space(text)) }, weight = SPACE)
            Key(
                icon = R.drawable.ic_key_backspace,
                onClick = {
                    val next = KeyboardEditing.backspace(text)
                    onTextChange(next)
                    if (next.isEmpty()) shift = true
                },
                onLongClick = {
                    onTextChange("")
                    shift = true
                },
                weight = WIDE,
            )
        }
    }
}

@Composable
private fun KeyRow(content: @Composable RowScope.() -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(KEY_GAP), modifier = Modifier.fillMaxWidth(), content = content)
}

@Composable
private fun RowScope.Key(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    label: String = "",
    @DrawableRes icon: Int? = null,
    onLongClick: (() -> Unit)? = null,
    highlighted: Boolean = false,
    weight: Float = 1f,
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        onLongClick = onLongClick,
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(5.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (highlighted) scheme.primary else scheme.surfaceVariant,
            contentColor = if (highlighted) scheme.onPrimary else scheme.onSurface,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.1f),
        modifier = modifier.weight(weight).height(KEY_HEIGHT),
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            if (icon != null) {
                Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(ICON_SIZE))
            } else {
                Text(label, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

// Compact (54 px keys on a 1080p screen) so the keyboard covers little of the screen.
private val KEY_HEIGHT = 27.dp
private val KEY_GAP = 3.dp
private val ICON_SIZE = 16.dp
private const val WIDE = 1.5f
private const val SPACE = 4f
