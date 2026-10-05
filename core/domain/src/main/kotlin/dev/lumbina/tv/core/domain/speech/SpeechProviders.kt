package dev.lumbina.tv.core.domain.speech

import javax.inject.Qualifier
import kotlinx.coroutines.flow.Flow

/** Who recognizes speech: the device itself (offline models), or a cloud service with the user's key. */
enum class SpeechProvider { Device, OpenAi, Yandex }

/** The user's API keys for the cloud [SpeechProvider]s. */
interface SpeechKeys {
    /** The keys set, by provider: for recognition itself. */
    val keys: Flow<Map<SpeechProvider, String>>

    /** The providers with a key set, the keys left unopened. */
    val providers: Flow<Set<SpeechProvider>>

    /** Sets [provider]'s key; a blank one removes it. False if it couldn't be stored. */
    suspend fun set(provider: SpeechProvider, key: String): Boolean
}

/** What recognition is set up with: the [provider] with its [key], the enabled [languages], words to expect. */
data class SpeechSetup(
    val provider: SpeechProvider,
    val key: String?,
    val languages: List<String>,
    val vocabulary: List<String> = emptyList(),
) {
    override fun toString() =
        "SpeechSetup($provider, key ${if (key == null) "unset" else "set"}, $languages, ${vocabulary.size} words)"
}

/**
 * A recognizer in the cloud: it hears the whole utterance, then sends it off with the user's key
 * ([useKey]); until one is set it recognizes nothing ([prepare] is false). No live partials. It
 * tells a session's languages apart itself, and may be told words to expect ([useVocabulary],
 * e.g. app names).
 */
interface CloudSpeechEngine : SpeechEngine {
    fun useKey(key: String?)

    fun useVocabulary(words: List<String>)
}

/** Marks the recognizer on the device, next to the cloud ones. */
@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class DeviceSpeech

/**
 * The recognizer voice input uses: the one on the device or a cloud one, as [choose]n. Everything
 * else is the chosen engine's.
 */
class SpeechEngineChoice(private val device: SpeechEngine, private val cloud: Map<SpeechProvider, CloudSpeechEngine>) :
    SpeechEngine {
    @Volatile private var chosen: SpeechEngine = device

    /** The provider in use: [SpeechProvider.Device] for one without an engine. */
    val provider: SpeechProvider get() = cloud.entries.firstOrNull { it.value === chosen }?.key ?: SpeechProvider.Device

    fun choose(setup: SpeechSetup) {
        val engine = cloud[setup.provider]
        engine?.useKey(setup.key)
        engine?.useVocabulary(setup.vocabulary)
        chosen = engine ?: device
    }

    override val languages: List<String> get() = chosen.languages

    override fun prepare(languages: List<String>): Boolean = chosen.prepare(languages)

    override fun newSession(languages: List<String>): SpeechSession = chosen.newSession(languages)
}
