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
import dev.lumbina.tv.core.domain.channels.byAppUse
import dev.lumbina.tv.core.domain.channels.visibleRows
import dev.lumbina.tv.core.ui.WhileUiSubscribed
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** [loaded] is false until the TV Provider first answered (its app may still be starting). */
data class ChannelsUi(
    val watchNext: List<ProgramCard> = emptyList(),
    val rows: List<ChannelRow> = emptyList(),
    val loaded: Boolean = false,
)

/**
 * What apps published to the TV Provider, for the home screen: "continue watching" and one row
 * per channel, leaving out apps that are no longer installed and channels the user hid. The
 * most used apps' channels come first, after a mix of all of them.
 */
@HiltViewModel
class ChannelsViewModel @Inject constructor(
    channels: TvChannels,
    settings: ChannelSettingsRepository,
    installedApps: InstalledApps,
    usage: AppUsage,
    private val launcher: ProgramLauncher,
) : ViewModel() {

    val state: StateFlow<ChannelsUi> = combine(
        channels.channels(),
        channels.watchNext(),
        settings.settings,
        installedApps.observe(),
        usage.recent(),
    ) { feeds, watchNext, choice, apps, used ->
        val labels = labelsByPackage(apps)
        val watching = watchNext.filter { it.packageName in labels }
        ChannelsUi(
            watchNext = watching,
            rows = feeds.byAppUse(used, watching.map { it.packageName }.toSet()).visibleRows(choice, labels),
            loaded = true,
        )
    }.stateIn(viewModelScope, WhileUiSubscribed, ChannelsUi())

    fun open(card: ProgramCard) {
        launcher.open(card)
    }
}
