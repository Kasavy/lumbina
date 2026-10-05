package dev.lumbina.tv.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.lumbina.tv.core.domain.app.InstalledApps
import dev.lumbina.tv.core.domain.channels.ChannelSettingsRepository
import dev.lumbina.tv.core.domain.channels.TvChannels
import dev.lumbina.tv.core.domain.channels.TvListingsPermission
import dev.lumbina.tv.core.ui.WhileUiSubscribed
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** One channel in the settings list: [key] identifies it across republishing. */
data class ChannelChoice(val key: String, val name: String, val app: String, val programs: Int, val shown: Boolean)

/**
 * [missingPermission]: what the user can grant for other apps' channels and "continue watching"
 * to show (the system's TV Provider asks for one), null when nothing is missing.
 */
data class ChannelsPanelState(
    val available: Boolean,
    val channels: List<ChannelChoice>,
    val missingPermission: String? = null,
)

@HiltViewModel
class ChannelSettingsViewModel @Inject constructor(
    private val channels: TvChannels,
    private val permission: TvListingsPermission,
    installedApps: InstalledApps,
    private val settings: ChannelSettingsRepository,
) : ViewModel() {

    /** Bumped when the permission may have changed: the provider sends no notice of that. */
    private val access = MutableStateFlow(0)

    /** The permission missing when last checked: once granted, the rows are read again. */
    private var missing: String? = null

    val state: StateFlow<ChannelsPanelState?> =
        combine(channels.channels(), settings.settings, installedApps.observe(), access) { feeds, choice, apps, _ ->
            val labels = apps.associate { it.id.packageName to it.label }
            ChannelsPanelState(
                available = channels.isAvailable(),
                missingPermission = permission.missing(),
                channels = feeds.mapNotNull { feed ->
                    val app = labels[feed.channel.packageName] ?: return@mapNotNull null
                    val key = feed.channel.key
                    ChannelChoice(key, feed.channel.name, app, feed.programs.size, key !in choice.hiddenChannels)
                },
            )
        }.stateIn(viewModelScope, WhileUiSubscribed, null)

    /**
     * The permission may have changed: the dialog closed, or the page came back from the system's
     * settings, where it can be granted too. Once granted, the rows (home's too) are read again.
     */
    fun recheck() {
        val now = permission.missing()
        if (missing != null && now == null) permission.onGranted()
        missing = now
        access.update { it + 1 }
    }

    fun toggle(key: String) {
        viewModelScope.launch {
            settings.update {
                val hidden = if (key in it.hiddenChannels) it.hiddenChannels - key else it.hiddenChannels + key
                it.copy(hiddenChannels = hidden)
            }
        }
    }
}
