package dev.lumbina.tv.core.domain.speech

import dev.lumbina.tv.core.domain.app.InstalledApps
import dev.lumbina.tv.core.domain.home.HomeLayoutRepository
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart

/**
 * What voice input recognizes with, following the settings, the keys and the installed apps: the
 * chosen provider with its key, the enabled languages and, for a cloud provider, the apps' names
 * (custom ones too) to expect. The layout with the custom names is read again on each refresh
 * rather than observed: it is changed in the launcher's process.
 */
class SpeechSetups @Inject constructor(
    private val settings: VoiceSettingsRepository,
    private val keys: SpeechKeys,
    private val installed: InstalledApps,
    private val layout: HomeLayoutRepository,
) {
    @OptIn(ExperimentalCoroutinesApi::class)
    fun setups(refresh: Flow<Unit> = emptyFlow()): Flow<SpeechSetup> {
        val chosen = settings.settings.map { it.provider to it.languages }.distinctUntilChanged()
        return combine(chosen, keys.keys) { (provider, languages), keys ->
            SpeechSetup(provider, keys[provider], languages)
        }
            .distinctUntilChanged()
            .flatMapLatest { setup ->
                if (setup.provider.isCloud) {
                    appNames(refresh).map { setup.copy(vocabulary = it) }
                } else {
                    flowOf(setup)
                }
            }
    }

    private fun appNames(refresh: Flow<Unit>): Flow<List<String>> =
        combine(installed.observe(), refresh.onStart { emit(Unit) }) { apps, _ -> apps }
            .map { apps -> layout.layout.first().spokenNames(apps).map { it.label }.distinct() }
            .distinctUntilChanged()
}
