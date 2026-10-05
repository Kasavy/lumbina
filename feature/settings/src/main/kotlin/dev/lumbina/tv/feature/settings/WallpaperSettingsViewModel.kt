package dev.lumbina.tv.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.lumbina.tv.core.domain.backdrop.DeleteMyMedia
import dev.lumbina.tv.core.domain.backdrop.PhotoFolder
import dev.lumbina.tv.core.domain.backdrop.WallpaperPluginInfo
import dev.lumbina.tv.core.domain.backdrop.WallpaperPlugins
import dev.lumbina.tv.core.domain.backdrop.WallpaperSettings
import dev.lumbina.tv.core.domain.backdrop.WallpaperSettingsRepository
import dev.lumbina.tv.core.domain.backdrop.WallpaperSource
import dev.lumbina.tv.core.domain.home.HomeAppearanceRepository
import dev.lumbina.tv.core.domain.home.HomeBackground
import dev.lumbina.tv.core.ui.WhileUiSubscribed
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class WallpaperPanelState(
    val settings: WallpaperSettings,
    val plugins: List<WallpaperPluginInfo>,
    val myMedia: Int,
    /** What shows with no wallpaper; chosen with the theme. */
    val background: HomeBackground,
)

/** What the wallpaper panel can do. */
interface WallpaperCommands {
    fun choose(source: WallpaperSource)
    fun setChangeInterval(minutes: Int)
    fun toggleDimAtNight()
    fun openPluginSettings(id: String)
    fun deleteMyMedia()
}

@HiltViewModel
class WallpaperSettingsViewModel @Inject constructor(
    private val settings: WallpaperSettingsRepository,
    appearance: HomeAppearanceRepository,
    private val plugins: WallpaperPlugins,
    private val folder: PhotoFolder,
    private val deleteMedia: DeleteMyMedia,
) : ViewModel(),
    WallpaperCommands {

    val state: StateFlow<WallpaperPanelState?> =
        combine(
            settings.settings,
            appearance.settings.map { it.background }.distinctUntilChanged(),
        ) { wallpaper, background ->
            WallpaperPanelState(wallpaper, plugins.installed(), folder.count(), background)
        }.flowOn(Dispatchers.IO)
            .stateIn(viewModelScope, WhileUiSubscribed, null)

    override fun choose(source: WallpaperSource) = edit { it.copy(source = source) }

    override fun setChangeInterval(minutes: Int) = edit { it.copy(changeMinutes = minutes) }

    override fun toggleDimAtNight() = edit { it.copy(dimAtNight = !it.dimAtNight) }

    override fun openPluginSettings(id: String) {
        plugins.openSettings(id)
    }

    override fun deleteMyMedia() {
        viewModelScope.launch(Dispatchers.IO) { deleteMedia(now = System.currentTimeMillis()) }
    }

    private fun edit(transform: (WallpaperSettings) -> WallpaperSettings) {
        viewModelScope.launch { settings.update(transform) }
    }
}
