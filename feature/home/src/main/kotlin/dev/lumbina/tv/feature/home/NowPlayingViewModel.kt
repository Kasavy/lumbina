package dev.lumbina.tv.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.lumbina.tv.core.domain.app.InstalledApps
import dev.lumbina.tv.core.domain.app.labelsByPackage
import dev.lumbina.tv.core.domain.home.HomeAppearanceRepository
import dev.lumbina.tv.core.domain.status.MediaSessions
import dev.lumbina.tv.core.domain.status.NowPlaying
import dev.lumbina.tv.core.ui.WhileUiSubscribed
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** What is playing, with the app by its name. */
data class NowPlayingUi(val playing: NowPlaying, val app: String)

@HiltViewModel
class NowPlayingViewModel @Inject constructor(
    private val sessions: MediaSessions,
    installedApps: InstalledApps,
    appearance: HomeAppearanceRepository,
) : ViewModel() {

    /** Null while nothing plays or the user switched the bar off. */
    val state: StateFlow<NowPlayingUi?> = combine(
        sessions.nowPlaying(),
        installedApps.observe(),
        appearance.settings,
    ) { playing, apps, look ->
        playing?.takeIf { look.showNowPlaying }?.let { now ->
            NowPlayingUi(now, labelsByPackage(apps)[now.packageName] ?: now.packageName)
        }
    }.stateIn(viewModelScope, WhileUiSubscribed, null)

    fun playPause() = sessions.playPause()

    fun next() = sessions.next()

    fun previous() = sessions.previous()
}
