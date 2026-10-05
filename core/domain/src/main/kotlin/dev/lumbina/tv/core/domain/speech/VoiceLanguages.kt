package dev.lumbina.tv.core.domain.speech

import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first

/** One row of the language list: its model and whether voice input uses it. */
data class VoiceLanguage(val language: String, val sizeBytes: Long, val model: ModelState, val enabled: Boolean) {
    val installed: Boolean get() = model == ModelState.Installed
}

/** The language list and recognition mode as the settings screen shows them. */
data class VoiceLanguagesState(val languages: List<VoiceLanguage>, val mode: VoiceLanguageMode) {
    val enabled: List<VoiceLanguage> get() = languages.filter { it.enabled }

    /** Modes to choose from: Auto, then each enabled, installed language. */
    val modes: List<VoiceLanguageMode>
        get() = listOf(VoiceLanguageMode.Auto) +
            enabled.filter { it.installed }.map { VoiceLanguageMode.Only(it.language) }

    /** Download size of enabled languages that are not installed yet. */
    val missingBytes: Long get() = enabled.filterNot { it.installed }.sumOf { it.sizeBytes }
}

/**
 * Turning languages on and off, keeping [VoiceSettings] and the installed [SpeechModels] in
 * step: turning a language on downloads it, removing one deletes its model and forgets it.
 */
class VoiceLanguages @Inject constructor(
    private val models: SpeechModels,
    private val settings: VoiceSettingsRepository,
) {
    /** Enabled languages first (in order of preference), then the rest of the catalog. */
    val state: Flow<VoiceLanguagesState> = combine(settings.settings, models.states) { current, states ->
        val rows = models.catalog.map { info ->
            VoiceLanguage(
                language = info.language,
                sizeBytes = info.sizeBytes,
                model = states[info.language] ?: ModelState.Missing,
                enabled = info.language in current.languages,
            )
        }
        val order = current.languages.withIndex().associate { (i, lang) -> lang to i }
        VoiceLanguagesState(rows.sortedBy { order[it.language] ?: Int.MAX_VALUE }, current.mode)
    }

    /** Turns [language] on or off; turning on downloads its model if needed. */
    suspend fun setEnabled(language: String, enabled: Boolean) {
        settings.update { current ->
            val languages = if (enabled) (current.languages + language).distinct() else current.languages - language
            current.copy(languages = languages, mode = current.mode.without(language, enabled))
        }
        if (enabled) models.install(language)
    }

    /** Turns [language] off and deletes its model. */
    suspend fun remove(language: String) {
        setEnabled(language, enabled = false)
        models.delete(language)
    }

    /** Downloads every enabled language that is missing or failed. */
    suspend fun installEnabled() {
        settings.settings.first().languages.forEach(models::install)
    }

    suspend fun setMode(mode: VoiceLanguageMode) = settings.update { it.copy(mode = mode) }

    /** A mode fixed on a language that is being turned off falls back to Auto. */
    private fun VoiceLanguageMode.without(language: String, enabled: Boolean): VoiceLanguageMode =
        if (!enabled && this == VoiceLanguageMode.Only(language)) VoiceLanguageMode.Auto else this
}
