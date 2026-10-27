package dev.lumbina.tv.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.lumbina.tv.core.domain.app.AppUsage
import dev.lumbina.tv.core.domain.app.InstalledApps
import dev.lumbina.tv.core.domain.app.labelsByPackage
import dev.lumbina.tv.core.domain.channels.ChannelRow
import dev.lumbina.tv.core.domain.channels.ChannelSettingsRepository
import dev.lumbina.tv.core.domain.channels.ProgramCard
import dev.lumbina.tv.core.domain.channels.ProgramLauncher
import dev.lumbina.tv.core.domain.channels.TvChannels
import dev.lumbina.tv.core.domain.channels.TvListingsPermission
import dev.lumbina.tv.core.domain.channels.byAppUse
import dev.lumbina.tv.core.domain.channels.shownWatchNext
import dev.lumbina.tv.core.domain.channels.visibleRows
import dev.lumbina.tv.core.domain.channels.watchedApps
import dev.lumbina.tv.core.ui.WhileUiSubscribed
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** [loaded] is false until the TV Provider first answered (its app may still be starting). */
data class ChannelsUi(
    val watchNext: List<ProgramCard> = emptyList(),
    val rows: List<ChannelRow> = emptyList(),
    val loaded: Boolean = false,
    /** The permission the TV Provider wants before it shows other apps' rows; null when it has it. */
    val missingPermission: String? = null,
)

/**
 * What apps published to the TV Provider, for the home screen: "continue watching" and one row
 * per channel, leaving out apps that are no longer installed, channels the user hid and the apps
 * the user took out of "continue watching". The most used apps' channels come first (among equals,
 * the ones being watched), after a mix of all of them.
 */
@HiltViewModel
class ChannelsViewModel @Inject constructor(
    channels: TvChannels,
    settings: ChannelSettingsRepository,
    installedApps: InstalledApps,
    usage: AppUsage,
    private val launcher: ProgramLauncher,
    private val permission: TvListingsPermission,
) : ViewModel() {

    private val missing = MutableStateFlow(permission.missing())

    val state: StateFlow<ChannelsUi> = combine(
        channels.channels(),
        channels.watchNext(),
        settings.settings,
        installedApps.observe(),
        usage.recent(),
    ) { feeds, watchNext, choice, apps, used ->
        val labels = labelsByPackage(apps)
        appNames = labels
        ChannelsUi(
            watchNext = watchNext.shownWatchNext(choice, labels),
            rows = feeds.byAppUse(used, watchNext.watchedApps(labels)).visibleRows(choice, labels),
            loaded = true,
        )
    }.combine(missing) { ui, _ ->
        // Asked again with each reading: granted elsewhere (the setup, drawn over home, which
        // never left the screen) the rows are read again, and home must stop asking for it.
        ui.copy(missingPermission = permission.missing())
    }.stateIn(viewModelScope, WhileUiSubscribed, ChannelsUi(missingPermission = permission.missing()))

    /** Home is on screen again: the permission may have been granted elsewhere meanwhile. */
    fun onHomeShown() = onAccess(granted = permission.missing() == null)

    /** The user answered the system's question about the permission. */
    fun onAccess(granted: Boolean) {
        if (granted && missing.value != null) permission.onGranted()
        missing.value = permission.missing()
    }

    private var appNames = emptyMap<String, String>()
    private val failures = Failures(viewModelScope)

    /** The program that just failed to open, for a few seconds. */
    internal val failed: StateFlow<Failed?> get() = failures.state

    fun open(card: ProgramCard) {
        if (!launcher.open(card)) failures.say(Failed.Program(appNames[card.packageName] ?: card.packageName))
    }
}
