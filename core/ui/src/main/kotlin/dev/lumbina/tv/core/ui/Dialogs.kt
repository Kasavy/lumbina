package dev.lumbina.tv.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ListItem
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Text

/** One entry of a [ChoiceDialog], with an optional second line; [chosen] marks the one in effect. */
class Choice(val title: String, val subtitle: String? = null, val chosen: Boolean = false, val onChoose: () -> Unit)

/**
 * A titled list of actions, with an optional [hint] under the title; choosing one closes the
 * dialog and runs it. The [Choice.chosen] one is marked, and the focus starts on it.
 */
@Composable
fun ChoiceDialog(
    title: String,
    choices: List<Choice>,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    hint: String? = null,
) {
    // Once: an entry scrolled out and back must not take the focus again.
    var placed by remember { mutableStateOf(false) }
    MenuDialog(title = title, onDismiss = onDismiss, modifier = modifier, hint = hint) {
        items(choices) { choice ->
            val focus = remember { FocusRequester() }
            if (choice.chosen && !placed) {
                LaunchedEffect(Unit) {
                    focus.requestFocusSoon()
                    placed = true
                }
            }
            ListItem(
                selected = false,
                onClick = {
                    onDismiss()
                    choice.onChoose()
                },
                headlineContent = { Text(choice.title) },
                supportingContent = choice.subtitle?.let { { Text(it, maxLines = 2) } },
                trailingContent = if (choice.chosen) ({ Text(TICK) }) else null,
                modifier = Modifier.focusRequester(focus),
            )
        }
    }
}

/**
 * A titled list in the [SidePanel]; its [items] decide themselves what choosing them does, e.g.
 * switches that stay open. Presses still down when it appears (OK held to open it) are ignored.
 */
@Composable
fun MenuDialog(
    title: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    hint: String? = null,
    items: LazyListScope.() -> Unit,
) {
    SidePanel(title = title, onDismiss = onDismiss, modifier = modifier, subtitle = hint) {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp), content = items)
    }
}

/**
 * Asks for a name, typed on [LumbinaKeyboard] when [LocalUseLumbinaKeyboard] is on, otherwise on the
 * device's keyboard; voice input can fill it either way. A blank name is passed on as is where it
 * means something (an app goes back to its own name); where it means nothing, [needsText] keeps
 * the save button off and Done from closing the dialog until there is text.
 * Lumbina's keyboard is a compact block right under [anchor] (the tile being named), so the
 * tile stays in sight; with nothing to name on screen it sits at the top in the middle. [hint] is
 * a line under the field, e.g. what an empty name does.
 */
@Composable
fun NameDialog(
    title: String,
    initial: String,
    saveLabel: String,
    cancelLabel: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    anchor: Rect? = null,
    hint: String? = null,
    needsText: Boolean = false,
) {
    val lumbinaKeyboard = LocalUseLumbinaKeyboard.current
    var value by remember { mutableStateOf(TextFieldValue(initial, TextRange(initial.length))) }
    val focus = remember { FocusRequester() }
    val ready = !needsText || value.text.isNotBlank()
    val save = { if (ready) onDismiss().also { onSave(value.text) } }
    if (lumbinaKeyboard) {
        AnchoredPanel(anchor = anchor, width = KEYBOARD_WIDTH, onDismiss = onDismiss) {
            Column(verticalArrangement = Arrangement.spacedBy(9.dp), modifier = modifier.padding(14.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                NameField(
                    value = value,
                    onValueChange = { value = it },
                    onDone = save,
                    compact = true,
                    modifier = Modifier.focusProperties { canFocus = false },
                )
                hint?.let { NameHint(it) }
                LumbinaKeyboard(
                    text = value.text,
                    onTextChange = { value = TextFieldValue(it, TextRange(it.length)) },
                    firstKey = focus,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = onDismiss, scale = ButtonDefaults.scale(focusedScale = 1f)) {
                        Text(cancelLabel, style = MaterialTheme.typography.labelLarge)
                    }
                    Button(onClick = save, enabled = ready, scale = ButtonDefaults.scale(focusedScale = 1f)) {
                        Text(saveLabel, style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    } else {
        Dialog(onDismissRequest = onDismiss) {
            Column(
                verticalArrangement = Arrangement.spacedBy(20.dp),
                modifier = modifier.width(DIALOG_WIDTH).dialogSurface().padding(28.dp),
            ) {
                Text(title, style = MaterialTheme.typography.titleLarge)
                NameField(
                    value = value,
                    onValueChange = { value = it },
                    onDone = save,
                    modifier = Modifier.focusRequester(focus),
                )
                hint?.let { NameHint(it) }
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    OutlinedButton(onClick = onDismiss) { Text(cancelLabel) }
                    Button(onClick = save, enabled = ready) { Text(saveLabel) }
                }
            }
        }
    }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
}

@Composable
private fun NameHint(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}

/** The text field; a caret is drawn after the text since the field may never take focus. */
@Composable
private fun NameField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val style = if (compact) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleMedium
    val textStyle = style.copy(color = MaterialTheme.colorScheme.onSurface)
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = textStyle,
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        visualTransformation = CaretAtEnd(MaterialTheme.colorScheme.primary),
        modifier = modifier
            .fillMaxWidth()
            .border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp))
            .padding(horizontal = if (compact) 10.dp else 16.dp, vertical = if (compact) 6.dp else 12.dp),
    )
}

/**
 * Asks before doing something that can't be undone: [text] explains, [confirmLabel] does it.
 * [width] for a long [text]: the system's own dialog width is narrow on a TV. A text too long
 * for the screen (large fonts) scrolls with up and down; the buttons stay in view.
 */
@Composable
fun ConfirmDialog(
    title: String,
    text: String,
    confirmLabel: String,
    cancelLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp? = null,
) {
    val guard = remember { FreshPressGuard() }
    // Focus starts on the harmless button: an OK pressed out of habit doesn't confirm.
    val cancel = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { cancel.requestFocus() } }
    val scroll = rememberScrollState()
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = width == null)) {
        Column(
            verticalArrangement = Arrangement.spacedBy(24.dp),
            modifier = modifier
                .then(if (width != null) Modifier.width(width) else Modifier)
                .onPreviewKeyEvent(guard::swallow)
                .keyScroll(scroll)
                .dialogSurface()
                .padding(32.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            Text(text, modifier = Modifier.weight(1f, fill = false).verticalScroll(scroll))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedButton(onClick = onDismiss, modifier = Modifier.focusRequester(cancel)) { Text(cancelLabel) }
                Button(onClick = onConfirm) { Text(confirmLabel) }
            }
        }
    }
}

/**
 * Commands to run on a computer: [text] says where, each of [lines] is one command, and the QR
 * code holds the same lines, so a phone can pass them on. [note] is said before them if given.
 */
@Composable
fun CommandsDialog(
    title: String,
    text: String,
    lines: List<String>,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    note: String? = null,
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = modifier.width(COMMANDS_WIDTH).dialogSurface().padding(28.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                    Text(text, style = MaterialTheme.typography.bodyMedium)
                    note?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                    lines.forEach { command ->
                        Text(
                            text = command,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                QrCode(lines.joinToString("\n"), size = COMMANDS_QR)
            }
            Button(onClick = onDismiss) { Text(stringResource(R.string.commands_close)) }
        }
    }
}

private val COMMANDS_WIDTH = 760.dp
private val COMMANDS_QR = 180.dp

/** A dialog's or a window's ground: the theme's panel with round corners. */
@Composable
fun Modifier.dialogSurface(): Modifier = background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp))

private val DIALOG_WIDTH = 480.dp

// About 900 × 470 px on a 1080p screen: under half the width, so the named tile stays in view.
private val KEYBOARD_WIDTH = 450.dp

/** Shows a caret after the text; the caret is not part of the value. */
private class CaretAtEnd(private val color: Color) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val shown = buildAnnotatedString {
            append(text)
            withStyle(SpanStyle(color = color)) { append(CARET) }
        }
        val mapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int = offset

            override fun transformedToOriginal(offset: Int): Int = minOf(offset, text.length)
        }
        return TransformedText(shown, mapping)
    }

    private companion object {
        const val CARET = "▏"
    }
}
