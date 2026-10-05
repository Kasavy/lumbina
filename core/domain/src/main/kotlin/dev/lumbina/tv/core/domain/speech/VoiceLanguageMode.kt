package dev.lumbina.tv.core.domain.speech

/**
 * Which languages voice input listens for: all of them with automatic picking, or exactly
 * one (no wrong-language picks, and only one model runs, so it is faster).
 */
sealed interface VoiceLanguageMode {
    data object Auto : VoiceLanguageMode

    data class Only(val language: String) : VoiceLanguageMode

    /** Languages to recognize, given those installed (in order of preference). */
    fun languages(available: List<String>): List<String> = when (this) {
        Auto -> available
        is Only -> listOf(language).filter { it in available }.ifEmpty { available }
    }

    /** Auto → each installed language in turn → Auto. */
    fun next(available: List<String>): VoiceLanguageMode {
        if (available.size < 2) return Auto
        return when (this) {
            Auto -> Only(available.first())
            is Only -> available.getOrNull(available.indexOf(language) + 1)?.let(::Only) ?: Auto
        }
    }
}
