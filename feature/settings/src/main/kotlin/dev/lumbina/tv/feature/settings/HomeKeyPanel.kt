package dev.lumbina.tv.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.lumbina.tv.core.domain.remote.HomeScreen
import dev.lumbina.tv.core.ui.Choice

/** The other launcher for Home's menu, and what the device does when turned on. */
@Composable
fun HomeKeyPanel(
    state: HomeKeyState?,
    onUseHome: (HomeScreen) -> Unit,
    onOpenOnPowerOn: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (state == null) return
    Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = modifier) {
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
        Header(stringResource(R.string.power_on_header))
        ChoiceRow(
            title = stringResource(R.string.access_power_on),
            options = listOf(
                Choice(stringResource(R.string.access_power_on_lumbina), chosen = state.openOnPowerOn) {
                    onOpenOnPowerOn(true)
                },
                Choice(stringResource(R.string.access_power_on_nothing), chosen = !state.openOnPowerOn) {
                    onOpenOnPowerOn(false)
                },
            ),
        )
    }
}
