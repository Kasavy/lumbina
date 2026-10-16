package dev.lumbina.tv.feature.home

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.IconButton
import androidx.tv.material3.IconButtonDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import dev.lumbina.tv.core.designsystem.LocalTheme
import dev.lumbina.tv.core.designsystem.colorOf
import dev.lumbina.tv.core.domain.status.NetworkProblem
import dev.lumbina.tv.core.domain.theme.ThemeRole
import dev.lumbina.tv.core.ui.PillShape

/** The notifications while there are any, as a button of the capsule. */
@Composable
internal fun StatusIcons(status: StatusUi, actions: StatusActions, modifier: Modifier = Modifier) {
    var showingNotices by remember { mutableStateOf(false) }
    // The bell and its panel go with the last notification: closed then, or the panel would
    // open by itself with the next one.
    if (status.notices.isNullOrEmpty() && showingNotices) {
        SideEffect {
            showingNotices = false
            actions.noticesGone()
        }
    }
    Row(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier,
    ) {
        status.notices?.takeIf { it.isNotEmpty() }?.let { notices ->
            IconButton(onClick = { showingNotices = true }, colors = flat()) {
                Box {
                    Icon(
                        painterResource(R.drawable.ic_notifications),
                        contentDescription = stringResource(R.string.status_notifications),
                    )
                    Badge(notices.size, Modifier.align(Alignment.TopEnd))
                    // Here, not in the row: there its node would take a gap and shift the capsule.
                    if (showingNotices) NoticesPanel(notices, actions) { showingNotices = false }
                }
            }
        }
    }
}

private val NetworkProblem.text: Int
    get() = when (this) {
        NetworkProblem.NoConnection -> R.string.status_no_wifi
        NetworkProblem.NoInternet -> R.string.status_no_internet
        NetworkProblem.WeakSignal -> R.string.status_weak_signal
    }

/**
 * "No network", "No internet" or "Weak signal" by the capsule, only while something is wrong. OK
 * opens the device's network settings; [onOpen] is false where the device lets none open.
 */
@Composable
internal fun NetworkPlate(problem: NetworkProblem?, onOpen: () -> Boolean, modifier: Modifier = Modifier) {
    if (problem == null) return
    val context = LocalContext.current
    val warning = LocalTheme.current.colorOf(ThemeRole.Warning)
    Surface(
        onClick = {
            if (!onOpen()) Toast.makeText(context, R.string.status_network_closed, Toast.LENGTH_LONG).show()
        },
        shape = ClickableSurfaceDefaults.shape(PillShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = warning.copy(alpha = PROBLEM_BACKGROUND_ALPHA),
            contentColor = warning,
        ),
        modifier = modifier.homeFocus(LocalHomeFocus.current, FocusKey(FocusKey.BAR, "network")),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
        ) {
            Icon(painterResource(R.drawable.ic_wifi_off), contentDescription = null, modifier = Modifier.size(20.dp))
            Text(stringResource(problem.text), style = MaterialTheme.typography.labelLarge, maxLines = 1)
        }
    }
}

@Composable
private fun Badge(count: Int, modifier: Modifier = Modifier) {
    Text(
        text = if (count > MAX_BADGE) "$MAX_BADGE+" else count.toString(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onPrimary,
        modifier = modifier
            .background(MaterialTheme.colorScheme.primary, CircleShape)
            .padding(horizontal = 4.dp),
    )
}

private const val MAX_BADGE = 9
private const val PROBLEM_BACKGROUND_ALPHA = 0.16f

/** An icon button with no disc until it has focus, so status icons read as indicators. */
@Composable
private fun flat() = IconButtonDefaults.colors(
    containerColor = Color.Transparent,
    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
)
