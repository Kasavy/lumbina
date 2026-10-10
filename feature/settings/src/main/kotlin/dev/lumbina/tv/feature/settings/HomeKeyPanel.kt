package dev.lumbina.tv.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.lumbina.tv.core.domain.remote.HomeScreen
import dev.lumbina.tv.core.ui.Choice

/** What the remote's Home key does besides opening Lumbina. */
@Composable
fun HomeKeyPanel(
    state: HomeKeyState?,
    onToggleDoubleHome: () -> Unit,
    onUseHome: (HomeScreen) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (state == null) return
    Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = modifier) {
        Header(stringResource(R.string.home_key_header))
        SwitchRow(
            title = stringResource(R.string.home_key_double),
            hint = stringResource(R.string.home_key_double_hint),
            checked = state.doubleHomeRecents,
            onClick = onToggleDoubleHome,
        )
        // With one other home screen there is nothing to choose, with none no menu item.
        state.otherHome.chosen?.takeIf { state.otherHome.screens.size > 1 }?.let { chosen ->
            ChoiceRow(
                title = stringResource(R.string.home_key_other),
                hint = stringResource(R.string.home_key_other_hint),
                options = state.otherHome.screens.map { screen ->
                    Choice(screen.label, chosen = screen == chosen) { onUseHome(screen) }
                },
            )
        }
    }
}
