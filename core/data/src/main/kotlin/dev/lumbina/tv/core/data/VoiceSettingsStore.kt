package dev.lumbina.tv.core.data

import dev.lumbina.tv.core.domain.speech.DefaultVoiceLanguages
import dev.lumbina.tv.core.domain.speech.SpeechProvider
import dev.lumbina.tv.core.domain.speech.VoiceLanguageMode
import dev.lumbina.tv.core.domain.speech.VoiceSettings
import kotlinx.serialization.Serializable

/**
 * On-disk form: `null` languages mean "never chosen", so defaults can follow what is installed;
 * the provider by name, `null` for the device.
 */
@Serializable
internal data class StoredVoiceSettings(
    val languages: List<String>? = null,
    val onlyLanguage: String? = null,
    val commands: Boolean = true,
    val provider: String? = null,
)

internal fun StoredVoiceSettings.toDomain(defaults: DefaultVoiceLanguages) = VoiceSettings(
    languages = languages ?: defaults.get(),
    mode = onlyLanguage?.let(VoiceLanguageMode::Only) ?: VoiceLanguageMode.Auto,
    commands = commands,
    provider = SpeechProvider.entries.firstOrNull { it.name == provider } ?: SpeechProvider.Device,
)

internal fun VoiceSettings.toStored() = StoredVoiceSettings(
    languages = languages,
    onlyLanguage = (mode as? VoiceLanguageMode.Only)?.language,
    commands = commands,
    provider = provider.takeUnless { it == SpeechProvider.Device }?.name,
)
