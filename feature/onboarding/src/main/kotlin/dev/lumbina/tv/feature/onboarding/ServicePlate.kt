package dev.lumbina.tv.feature.onboarding

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import dev.lumbina.tv.core.designsystem.LocalTheme
import dev.lumbina.tv.core.designsystem.colorOf
import dev.lumbina.tv.core.domain.theme.ThemeRole
import dev.lumbina.tv.core.ui.PillShape

/**
 * "The remote's service is off", by the capsule: after the setup was finished or skipped, home
 * would otherwise say nothing when the service is not running. OK opens the setup at that step.
 */
@Composable
fun ServicePlateRoute(modifier: Modifier = Modifier, viewModel: SetupViewModel = hiltViewModel()) {
    val off by viewModel.serviceOff.collectAsStateWithLifecycle()
    if (!off) return
    val warning = LocalTheme.current.colorOf(ThemeRole.Warning)
    Surface(
        onClick = viewModel::reopen,
        shape = ClickableSurfaceDefaults.shape(PillShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = warning.copy(alpha = PLATE_ALPHA),
            contentColor = warning,
        ),
        modifier = modifier,
    ) {
        Text(
            stringResource(R.string.plate_service_off),
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
        )
    }
}

/** What is left of the setup, for the settings to say: how many steps, and the way back into it. */
class SetupLeft(val steps: Int, val open: () -> Unit)

@Composable
fun rememberSetupLeft(viewModel: SetupViewModel = hiltViewModel()): SetupLeft {
    val steps by viewModel.left.collectAsStateWithLifecycle()
    return remember(steps, viewModel) { SetupLeft(steps, viewModel::reopen) }
}

private const val PLATE_ALPHA = 0.16f
