@file:Suppress("ForbiddenImport")

package dev.lumbina.tv.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties

/*
 * Lumbina's windows over its screen. Each has semantics of its own, so each root reads the mouse
 * ([mouseClicks]), and is listed for the phone touchpad's taps ([TapWindows]); Compose's Dialog and
 * Popup are not used elsewhere (detekt's ForbiddenImport).
 */

/** A dialog whose root reads the mouse. */
@Composable
fun LumbinaDialog(
    onDismissRequest: () -> Unit,
    properties: DialogProperties = DialogProperties(),
    content: @Composable () -> Unit,
) {
    Dialog(onDismissRequest = onDismissRequest, properties = properties) {
        ListedForTaps()
        MouseClicks(content = content)
    }
}

/** A popup that takes the keys: Lumbina's popups are all worked with the remote. */
@Composable
fun LumbinaPopup(
    onDismissRequest: () -> Unit,
    alignment: Alignment = Alignment.TopStart,
    offset: IntOffset = IntOffset.Zero,
    content: @Composable () -> Unit,
) {
    Popup(
        alignment = alignment,
        offset = offset,
        onDismissRequest = onDismissRequest,
        properties = PopupProperties(focusable = true),
    ) {
        ListedForTaps()
        MouseClicks(content = content)
    }
}
