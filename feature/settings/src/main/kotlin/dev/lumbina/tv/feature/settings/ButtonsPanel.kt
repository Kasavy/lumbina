package dev.lumbina.tv.feature.settings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dev.lumbina.tv.core.domain.remote.BINDABLE_GESTURES
import dev.lumbina.tv.core.domain.remote.KeyGesture
import dev.lumbina.tv.core.domain.remote.RemoteKey
import dev.lumbina.tv.core.ui.ConfirmDialog
import dev.lumbina.tv.core.ui.keyScroll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow

/** Where the buttons' pages are; each is a page inside "Buttons and keyboard". */
internal sealed interface ButtonsScreen {
    data object Keys : ButtonsScreen
    data object Capture : ButtonsScreen
    data class Key(val key: RemoteKey) : ButtonsScreen
    data class Kinds(val key: RemoteKey, val gesture: KeyGesture) : ButtonsScreen
    data class Actions(val key: RemoteKey, val gesture: KeyGesture, val kind: ActionKind) : ButtonsScreen
}

/**
 * How the pages lead to one another. [left]: the page just come back from, whose row takes the
 * focus again ([rowOf]) and says so by [arrived].
 */
internal class ButtonsWay(
    val go: (ButtonsScreen) -> Unit,
    val back: () -> Unit,
    val backBy: (Int) -> Unit,
    val left: ButtonsScreen? = null,
    val arrived: () -> Unit = {},
    /** One page up with no row to come back to: what led here is going away. */
    val away: () -> Unit = {},
)

/** For the row that leads to [screen]: it takes the focus when that page is come back from. */
@Composable
internal fun ButtonsWay.rowOf(screen: ButtonsScreen): Modifier = Modifier.focusedOnReturn(left == screen, arrived)

/** Each time the page is back on screen, what the device has is read anew (the service may be on now). */
@Suppress("ViewModelForwarding") // the page's own state holder, asked to read again
@Composable
private fun RecheckOnResume(viewModel: ButtonsViewModel) {
    LifecycleResumeEffect(viewModel) {
        viewModel.recheck()
        onPauseOrDispose {}
    }
}

/**
 * The remote's buttons: a row per button that has something of the user's on it, a way to take
 * another, and for each button what a press, a double press and a hold do. [onLeave]: Back from
 * the list.
 */
@Suppress("ViewModelForwarding") // the pages are parts of one screen and share its state holder
@Composable
internal fun ButtonsPages(onLeave: () -> Unit, viewModel: ButtonsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    RecheckOnResume(viewModel)
    val current = state ?: return
    var stack by remember { mutableStateOf(listOf<ButtonsScreen>(ButtonsScreen.Keys)) }
    var left by remember { mutableStateOf<ButtonsScreen?>(null) }
    val hold = LocalParkFocus.current
    val pop = { levels: Int ->
        val kept = stack.dropLast(levels.coerceAtMost(stack.size - 1))
        left = stack.getOrNull(kept.size)
        stack = kept
    }
    val way = ButtonsWay(
        go = { next ->
            hold()
            left = null
            stack = stack + next
        },
        back = { if (stack.size > 1) pop(1) else onLeave() },
        backBy = { levels ->
            hold()
            pop(levels)
        },
        left = left,
        arrived = { left = null },
        away = {
            hold()
            pop(1)
            left = null
        },
    )
    // Counts the times the list was set back as it was: its rows go, and the focus starts over.
    var resets by remember { mutableIntStateOf(0) }
    when (val at = stack.last()) {
        ButtonsScreen.Keys -> key(resets) {
            KeysPage(current, way, viewModel::toggleVolumeHold) {
                hold()
                viewModel.free()
                resets++
            }
        }

        ButtonsScreen.Capture -> CapturePage(current.serviceOn, viewModel::pressed, way) { key ->
            hold()
            stack = stack.dropLast(1) + ButtonsScreen.Key(key)
        }

        is ButtonsScreen.Key -> KeyPage(
            state = current,
            key = at.key,
            way = way,
            edits = KeyEdits(
                rename = { viewModel.rename(at.key, it) },
                limit = { viewModel.limit(at.key, it) },
                free = { viewModel.free(at.key) },
                tried = { viewModel.tried(at.key) },
            ),
        )

        is ButtonsScreen.Kinds -> KindsPage(current, at, way, viewModel::bind)

        is ButtonsScreen.Actions -> ActionsPage(current, at, way, viewModel::bind)
    }
}

/** Home, every button the user took, then "take a button" and "as it was", then held volume buttons. */
@Composable
private fun KeysPage(state: ButtonsState, way: ButtonsWay, onVolumeHold: () -> Unit, onReset: () -> Unit) {
    var asking by remember { mutableStateOf(false) }
    // The buttons every remote has: Home, and the mic where Lumbina hears it.
    val own = listOfNotNull(RemoteKey.Home, RemoteKey.Mic.takeIf { state.mic })
    InnerPage(stringResource(R.string.buttons_title), way.back) {
        SettingsList {
            if (!state.serviceOn) item(key = "service") { ServiceOff() }
            items(own.size, key = { own[it].id }) { index ->
                SettingRow(
                    onClick = { way.go(ButtonsScreen.Key(own[index])) },
                    headlineContent = { Text(state.nameOf(own[index])) },
                    modifier = way.rowOf(ButtonsScreen.Key(own[index])),
                )
            }
            items(state.boundKeys.size, key = { state.boundKeys[it].id }) { index ->
                val key = state.boundKeys[index]
                // What it does is said here only when it does one thing: three would not fit a row.
                val only = BINDABLE_GESTURES.mapNotNull { state.actionOf(key, it) }.singleOrNull()
                SettingRow(
                    onClick = { way.go(ButtonsScreen.Key(key)) },
                    headlineContent = { Text(state.nameOf(key)) },
                    trailingContent = only?.let { { ActionText(state, it) } },
                    modifier = way.rowOf(ButtonsScreen.Key(key)),
                )
            }
            item { GroupLine() }
            item(key = "capture") {
                SettingRow(
                    onClick = { way.go(ButtonsScreen.Capture) },
                    headlineContent = { Text(stringResource(R.string.buttons_take)) },
                    modifier = way.rowOf(ButtonsScreen.Capture),
                )
            }
            if (state.settings.bindings.isNotEmpty()) {
                item(key = "reset") {
                    SettingRow(
                        onClick = { asking = true },
                        headlineContent = { Text(stringResource(R.string.buttons_reset)) },
                    )
                }
            }
            if (state.volumeHoldable) volumeHold(state.settings.volumeHold, onVolumeHold)
        }
    }
    if (asking) {
        ConfirmDialog(
            title = stringResource(R.string.buttons_reset_title),
            text = stringResource(R.string.buttons_reset_text),
            confirmLabel = stringResource(R.string.buttons_reset_confirm),
            cancelLabel = stringResource(R.string.action_cancel),
            onConfirm = {
                asking = false
                onReset()
            },
            onDismiss = { asking = false },
        )
    }
}

/** Whether a held volume button goes on changing the volume, in a group of its own. */
private fun LazyListScope.volumeHold(on: Boolean, onClick: () -> Unit) {
    item { GroupLine() }
    item(key = "volume") {
        SwitchRow(
            title = stringResource(R.string.buttons_volume_hold),
            hint = stringResource(R.string.buttons_volume_hold_hint),
            checked = on,
            onClick = onClick,
        )
    }
}

/** Why nothing set here works, said first; OK leads to where the service is turned on. */
@Composable
private fun ServiceOff() {
    val links = LocalSettingsLinks.current
    SettingRow(
        onClick = links.setup,
        headlineContent = { Text(stringResource(R.string.buttons_service_off), color = rowWarning()) },
    )
}

/**
 * "Press a button on the remote": the remote's service tells which one it was. A button that
 * can't be taken is said so and the page waits on; one that never comes is the firmware's own, and
 * that is said after a while. Back leaves.
 */
@Composable
private fun CapturePage(
    serviceOn: Boolean,
    pressed: () -> Flow<RemoteKey>,
    way: ButtonsWay,
    onKey: (RemoteKey) -> Unit,
) {
    var note by remember { mutableStateOf<Int?>(null) }
    val taken by rememberUpdatedState(onKey)
    val keys by rememberUpdatedState(pressed)
    // Only while the page is in front: under the screensaver or another app the keys are not the page's.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle) {
        keys().flowWithLifecycle(lifecycle, Lifecycle.State.RESUMED).collect { key ->
            // The mic is held to talk: its press and release are not gestures to hand out.
            if (key.canBeBound && key != RemoteKey.Mic) taken(key) else note = R.string.capture_locked
        }
    }
    LaunchedEffect(Unit) {
        delay(NOT_REACHING_MILLIS)
        if (note == null) note = R.string.capture_none
    }
    val scroll = rememberScrollState()
    // The note comes last and, with large text, under the panel's edge: brought into view as it
    // appears. The arrows may never reach the page while the service listens.
    LaunchedEffect(note) {
        if (note == null) return@LaunchedEffect
        withFrameNanos { }
        scroll.animateScrollTo(scroll.maxValue)
    }
    InnerPage(stringResource(R.string.buttons_take_title), way.back, keys = stringResource(R.string.capture_back)) {
        Column(Modifier.fillMaxSize()) {
            if (!serviceOn) ServiceOff()
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                // Focusable for the focus to stand on; OK is taken by the service while it listens.
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .fillMaxWidth()
                        .keyScroll(scroll)
                        .verticalScroll(scroll)
                        .focusable()
                        .padding(horizontal = 16.dp, vertical = 24.dp),
                ) {
                    RemoteDrawing(Modifier.padding(bottom = 24.dp))
                    Text(
                        text = stringResource(R.string.capture_press),
                        style = MaterialTheme.typography.titleLarge,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        text = stringResource(R.string.capture_fixed),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    note?.let {
                        Text(
                            text = stringResource(it),
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 16.dp),
                        )
                    }
                }
            }
        }
    }
}

/**
 * A remote drawn by its outline, the ring of arrows and a few keys; one key is lit in the highlight
 * color, for "a button of yours". Drawn on a grid of [REMOTE_UNITS_WIDE] units across.
 */
@Composable
@Suppress("MagicNumber") // the numbers are the drawing
private fun RemoteDrawing(modifier: Modifier = Modifier) {
    val line = MaterialTheme.colorScheme.onSurfaceVariant
    val lit = MaterialTheme.colorScheme.primary
    Canvas(modifier.size(width = 75.dp, height = 150.dp)) {
        val unit = size.width / REMOTE_UNITS_WIDE
        fun at(x: Float, y: Float) = Offset(x * unit, y * unit)
        val stroke = Stroke(width = 2 * unit, cap = StrokeCap.Round)
        drawRoundRect(line, at(12f, 4f), Size(36 * unit, 112 * unit), CornerRadius(14 * unit), style = stroke)
        drawCircle(line, 11 * unit, at(30f, 34f), style = stroke)
        drawCircle(line, 3.5f * unit, at(30f, 34f), style = stroke)
        listOf(22f to 58f, 34f to 58f, 22f to 70f, 34f to 70f, 34f to 86f).forEach { (x, y) ->
            drawLine(line, at(x, y), at(x + 4, y), stroke.width, StrokeCap.Round)
        }
        drawLine(line, at(22f, 100f), at(38f, 100f), stroke.width, StrokeCap.Round)
        drawCircle(lit, 3.6f * unit, at(24f, 86f))
        drawCircle(lit.copy(alpha = 0.5f), 6 * unit, at(24f, 86f), style = Stroke(width = unit))
    }
}

/** The drawing's grid: the remote's outline sits on it 36 units wide and 112 tall. */
private const val REMOTE_UNITS_WIDE = 60f

/** How long a press is waited for before the page says some buttons never reach Lumbina. */
private const val NOT_REACHING_MILLIS = 10_000L
