package dev.lumbina.tv.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.flowWithLifecycle
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.Text
import dev.lumbina.tv.core.domain.remote.BINDABLE_GESTURES
import dev.lumbina.tv.core.domain.remote.KeyGesture
import dev.lumbina.tv.core.domain.remote.RemoteAction
import dev.lumbina.tv.core.domain.remote.RemoteKey
import dev.lumbina.tv.core.ui.ConfirmDialog
import dev.lumbina.tv.core.ui.NameDialog
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest

/** The kinds an action is chosen through, so no list is long. */
internal enum class ActionKind(val title: Int) {
    Lumbina(R.string.kind_lumbina),
    App(R.string.kind_app),
    Voice(R.string.kind_voice),
    Player(R.string.kind_player),
    Device(R.string.kind_device),
}

/** The actions of [kind] this device has for [key], in the order they are offered. */
internal fun ButtonsState.actionsOf(kind: ActionKind, key: RemoteKey): List<RemoteAction> = when (kind) {
    ActionKind.Lumbina -> listOfNotNull(
        RemoteAction.OpenLumbina,
        RemoteAction.OpenAllApps,
        RemoteAction.ShowAppSwitcher,
        RemoteAction.ShowQuickMenu,
        RemoteAction.OpenSearch.takeIf { search },
        RemoteAction.ShowSleepTimer,
        RemoteAction.ShowNotifications,
        RemoteAction.ShowScreensaver,
        RemoteAction.OpenLumbinaSettings,
    )

    ActionKind.App -> listOf(RemoteAction.OpenPreviousApp) + apps.map { RemoteAction.OpenApp(it.id) }

    // Voice is the mic button's own: nothing else is held to talk.
    ActionKind.Voice -> listOfNotNull(RemoteAction.CycleVoiceLanguage.takeIf { key == RemoteKey.Mic })

    ActionKind.Player -> listOf(RemoteAction.MediaPlayPause, RemoteAction.MediaNext, RemoteAction.MediaPrevious)

    ActionKind.Device -> listOfNotNull(
        RemoteAction.OpenOtherHome.takeIf { otherHome != null },
        RemoteAction.OpenDeviceSettings.takeIf { deviceSettings },
        RemoteAction.ToggleMute,
        RemoteAction.ScreenOff,
    )
}

/** What a button does, at the right of its row; an app that is gone is said in the warning's color. */
@Composable
internal fun ActionText(state: ButtonsState, action: RemoteAction?) {
    val gone = state.isGone(action)
    val color = if (gone) rowWarning() else Color.Unspecified
    Text(state.labelOf(action), color = color, maxLines = 1)
}

/** The gesture the service has just told of the page's button, for a moment; a null [tried] asks for none. */
@Composable
private fun lastTried(tried: (() -> Flow<KeyGesture>)?): State<KeyGesture?> {
    // Only while the page is in front: under the screensaver or another app the button is its own again.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    return produceState<KeyGesture?>(null, tried != null, lifecycle) {
        tried?.invoke()?.flowWithLifecycle(lifecycle, Lifecycle.State.RESUMED)?.collectLatest {
            value = it
            delay(SEEN_MILLIS)
            value = null
        }
    }
}

/** What a gesture does, with a dot before it while the service has just [seen] that gesture made. */
@Composable
private fun TriedAction(state: ButtonsState, action: RemoteAction?, seen: Boolean) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        // In the row's own text color: seen on a focused row and on any other.
        if (seen) Box(Modifier.size(12.dp).background(LocalContentColor.current, CircleShape))
        ActionText(state, action)
    }
}

/** What a button's page can change besides its gestures, and the gestures the service saw made on it ([tried]). */
internal class KeyEdits(
    val rename: (String) -> Unit,
    val limit: (Boolean) -> Unit,
    val free: () -> Unit,
    val tried: () -> Flow<KeyGesture>,
)

/**
 * One button: what a press, a double press and a hold do, whether that is so everywhere or on
 * Lumbina's screens alone, and the way back to how it was. While the page is open the button
 * does not do what it was given: the gesture the service reports is marked with a dot, showing
 * that a double press and a hold are caught. The mic is held to talk, and only its double press is to set.
 */
@Composable
internal fun KeyPage(state: ButtonsState, key: RemoteKey, way: ButtonsWay, edits: KeyEdits) {
    val changed = state.settings.bindings.any { it.key == key }
    val mic = key == RemoteKey.Mic
    // Home is Home on every remote, and so is the mic; a button known by a number is the one worth naming.
    val named = key != RemoteKey.Home && !mic
    val gestures = if (mic) listOf(KeyGesture.DoubleTap) else BINDABLE_GESTURES
    val seen by lastTried(edits.tried.takeIf { !mic })
    val tries = !mic && gestures.any { state.actionOf(key, it) != null }
    val links = LocalSettingsLinks.current
    var naming by remember { mutableStateOf(false) }
    if (naming) {
        NameDialog(
            title = stringResource(R.string.button_rename_title),
            initial = state.settings.keyNames[key].orEmpty(),
            saveLabel = stringResource(R.string.name_save),
            cancelLabel = stringResource(R.string.action_cancel),
            onSave = edits.rename,
            onDismiss = { naming = false },
            hint = stringResource(R.string.button_rename_hint),
        )
    }
    InnerPage(state.nameOf(key), way.back) {
        SettingsList {
            if (mic) {
                item(key = "talk") {
                    SettingRow(
                        onClick = { links.page(SettingsPage.Voice) },
                        headlineContent = { Text(stringResource(R.string.gesture_hold)) },
                        trailingContent = { Text(stringResource(R.string.action_talk), maxLines = 1) },
                    )
                }
            }
            items(gestures.size, key = { gestures[it].name }) { index ->
                val gesture = gestures[index]
                SettingRow(
                    onClick = { way.go(ButtonsScreen.Kinds(key, gesture)) },
                    headlineContent = { Text(gesture.label()) },
                    trailingContent = { TriedAction(state, state.actionOf(key, gesture), seen == gesture) },
                    modifier = way.rowOf(ButtonsScreen.Kinds(key, gesture)),
                )
            }
            keyEdits(state, key, way, edits, named, changed) { naming = true }
            // Only a gesture with something on it is told by the service.
            if (tries) item { Note(stringResource(R.string.button_try)) }
        }
    }
}

/** Under a button's gestures: where it is Lumbina's, its name, and the way back to how it was. */
@Suppress("LongParameterList")
private fun LazyListScope.keyEdits(
    state: ButtonsState,
    key: RemoteKey,
    way: ButtonsWay,
    edits: KeyEdits,
    named: Boolean,
    changed: Boolean,
    onRename: () -> Unit,
) {
    // Home brings Lumbina back from anywhere; any other button may be Lumbina's on its screens alone.
    if (named && BINDABLE_GESTURES.any { state.actionOf(key, it) != null }) {
        item(key = "limit") {
            val only = key in state.settings.limited
            SwitchRow(
                title = stringResource(R.string.button_lumbina_only),
                hint = stringResource(R.string.button_lumbina_only_hint),
                checked = only,
                onClick = { edits.limit(!only) },
            )
        }
    }
    if (named || changed) item { GroupLine() }
    if (named) {
        item(key = "rename") {
            SettingRow(
                onClick = onRename,
                headlineContent = { Text(stringResource(R.string.button_rename)) },
            )
        }
    }
    if (changed) {
        item(key = "free") {
            SettingRow(
                onClick = {
                    // The row goes with what it takes back, and a freed button's row in the list
                    // with it: back to the list, at its first row.
                    way.away()
                    edits.free()
                },
                headlineContent = {
                    Text(stringResource(if (named) R.string.button_free else R.string.button_as_it_was))
                },
            )
        }
    }
}

/** The first step of choosing an action: its kind, or one of the two that need no second step. */
@Composable
internal fun KindsPage(
    state: ButtonsState,
    at: ButtonsScreen.Kinds,
    way: ButtonsWay,
    onBind: (RemoteKey, KeyGesture, RemoteAction?) -> Unit,
) {
    val now = state.actionOf(at.key, at.gesture)
    // Home's press taken off Lumbina is asked about before it is done: with nothing on it, Home
    // opens the device's launcher; set to do nothing, it does nothing at all.
    var asking by remember { mutableStateOf<HomeOff?>(null) }
    val homePress = at.key == RemoteKey.Home && at.gesture == KeyGesture.ShortPress
    val set = { action: RemoteAction? ->
        onBind(at.key, at.gesture, action)
        way.backBy(1)
    }
    InnerPage(at.gesture.label(), way.back) {
        SettingsList {
            ActionKind.entries.filter { state.actionsOf(it, at.key).isNotEmpty() }.forEach { kind ->
                item(key = kind.name) {
                    SettingRow(
                        onClick = { way.go(ButtonsScreen.Actions(at.key, at.gesture, kind)) },
                        headlineContent = { Text(stringResource(kind.title)) },
                        trailingContent = now?.takeIf { it in state.actionsOf(kind, at.key) }?.let { chosen ->
                            { ActionText(state, chosen) }
                        },
                        modifier = way.rowOf(ButtonsScreen.Actions(at.key, at.gesture, kind)),
                    )
                }
            }
            item { GroupLine() }
            item(key = "nothing") {
                SettingRow(
                    onClick = { if (homePress) asking = HomeOff.Nothing else set(RemoteAction.Nothing) },
                    headlineContent = { Text(stringResource(R.string.action_nothing)) },
                    trailingContent = { RowRadio(now == RemoteAction.Nothing) },
                )
            }
            item(key = "device") {
                SettingRow(
                    onClick = { if (homePress) asking = HomeOff.Device else set(null) },
                    headlineContent = { Text(stringResource(R.string.action_as_device)) },
                    trailingContent = { RowRadio(now == null) },
                )
            }
            if (at.gesture == KeyGesture.DoubleTap) item { Note(stringResource(R.string.gesture_double_note)) }
        }
    }
    asking?.let { off ->
        HomeOffDialog(off, onDismiss = { asking = null }) {
            asking = null
            set(off.action)
        }
    }
}

/** What Home's press is about to become instead of opening Lumbina: as on the device, or nothing at all. */
private enum class HomeOff(val action: RemoteAction?, val text: Int) {
    Device(null, R.string.home_press_off_text),
    Nothing(RemoteAction.Nothing, R.string.home_press_nothing_text),
}

@Composable
private fun HomeOffDialog(off: HomeOff, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    ConfirmDialog(
        title = stringResource(R.string.home_press_off_title),
        text = stringResource(off.text),
        confirmLabel = stringResource(R.string.home_press_off_confirm),
        cancelLabel = stringResource(R.string.action_cancel),
        onConfirm = onConfirm,
        onDismiss = onDismiss,
    )
}

/** The second step: the actions of one kind, the one in use marked. Choosing leads back to the button. */
@Composable
internal fun ActionsPage(
    state: ButtonsState,
    at: ButtonsScreen.Actions,
    way: ButtonsWay,
    onBind: (RemoteKey, KeyGesture, RemoteAction?) -> Unit,
) {
    val now = state.actionOf(at.key, at.gesture)
    val actions = state.actionsOf(at.kind, at.key)
    InnerPage(stringResource(at.kind.title), way.back) {
        SettingsList {
            items(actions.size) { index ->
                val action = actions[index]
                SettingRow(
                    onClick = {
                        onBind(at.key, at.gesture, action)
                        way.backBy(2)
                    },
                    headlineContent = { Text(state.labelOf(action), maxLines = 1) },
                    trailingContent = { RowRadio(now == action) },
                )
            }
        }
    }
}

/** How long a gesture the service told stays marked on a button's page. */
private const val SEEN_MILLIS = 1500L
