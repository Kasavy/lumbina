package dev.lumbina.tv.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.lumbina.tv.core.domain.input.InputSettings

/** Text input: Lumbina's own keyboard, or the device's. */
@Composable
fun InputPanel(state: InputSettings?, onToggleLumbinaKeyboard: () -> Unit, modifier: Modifier = Modifier) {
    if (state == null) return
    Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = modifier) {
        Header(stringResource(R.string.input_keyboard))
        SwitchRow(
            title = stringResource(R.string.input_lumbina_keyboard),
            hint = stringResource(R.string.input_lumbina_keyboard_hint),
            checked = state.useLumbinaKeyboard,
            onClick = onToggleLumbinaKeyboard,
        )
        Note(stringResource(R.string.input_system_keyboard_note))
    }
}
