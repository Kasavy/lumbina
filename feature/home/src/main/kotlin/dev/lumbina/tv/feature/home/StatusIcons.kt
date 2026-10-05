package dev.lumbina.tv.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.IconButton
import androidx.tv.material3.IconButtonDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dev.lumbina.tv.core.domain.status.NetworkProblem
import dev.lumbina.tv.core.domain.status.problem
import dev.lumbina.tv.core.ui.Choice
import dev.lumbina.tv.core.ui.ChoiceDialog

/** What the status icons can do; grouped to keep signatures short. */
class StatusActions(val openNotice: (String) -> Unit, val clearNotices: () -> Unit)

/**
 * The network, only when something is wrong with it (a plain label, never focused), and the
 * notifications while there are any, as the one button here.
 */
@Composable
internal fun StatusIcons(status: StatusUi, actions: StatusActions, modifier: Modifier = Modifier) {
    var showingNotices by remember { mutableStateOf(false) }
    Row(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier,
    ) {
        status.network?.problem()?.let { NetworkWarning(it.text) }
        status.notices?.takeIf { it.isNotEmpty() }?.let { notices ->
            IconButton(onClick = { showingNotices = true }, colors = flat()) {
                Box {
                    Icon(
                        painterResource(R.drawable.ic_notifications),
                        contentDescription = stringResource(R.string.status_notifications),
                    )
                    Badge(notices.size, Modifier.align(Alignment.TopEnd))
                }
            }
        }
    }
    if (showingNotices) NotificationsDialog(status.notices.orEmpty(), actions) { showingNotices = false }
}

private val NetworkProblem.text: Int
    get() = when (this) {
        NetworkProblem.NoConnection -> R.string.status_no_wifi
        NetworkProblem.NoInternet -> R.string.status_no_internet
        NetworkProblem.WeakSignal -> R.string.status_weak_signal
    }

@Composable
private fun NetworkWarning(text: Int, modifier: Modifier = Modifier) {
    val color = MaterialTheme.colorScheme.primary
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .background(color.copy(alpha = PROBLEM_BACKGROUND_ALPHA), RoundedCornerShape(percent = PILL))
            .padding(horizontal = 14.dp, vertical = 6.dp),
    ) {
        Icon(
            painterResource(R.drawable.ic_wifi_off),
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(20.dp),
        )
        Text(stringResource(text), style = MaterialTheme.typography.labelLarge, color = color)
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

@Composable
private fun NotificationsDialog(notices: List<NoticeUi>, actions: StatusActions, onDismiss: () -> Unit) {
    val clear = Choice(stringResource(R.string.status_clear_notifications)) { actions.clearNotices() }
    ChoiceDialog(
        title = stringResource(
            if (notices.isEmpty()) R.string.status_no_notifications else R.string.status_notifications,
        ),
        choices = notices.map { notice ->
            Choice(notice.title, listOfNotNull(notice.app, notice.text).joinToString(" · ")) {
                actions.openNotice(notice.key)
            }
        } + if (notices.isEmpty()) emptyList() else listOf(clear),
        onDismiss = onDismiss,
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
