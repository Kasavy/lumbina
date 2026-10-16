package dev.lumbina.tv.platform.accessibility

import android.os.SystemClock
import dev.lumbina.tv.core.common.LumbinaLog
import dev.lumbina.tv.core.domain.speech.AudioSource
import dev.lumbina.tv.core.domain.speech.EndOfSpeech
import dev.lumbina.tv.core.domain.speech.Hypothesis
import dev.lumbina.tv.core.domain.speech.SpeechUnavailable
import dev.lumbina.tv.core.domain.speech.VoiceEvent
import dev.lumbina.tv.core.domain.speech.VoiceLanguageSetting
import dev.lumbina.tv.core.domain.speech.logText
import java.io.IOException
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart

/**
 * What listening for an app came to. [RecognizeSpeechActivity] and [LumbinaRecognitionService]
 * each tell the app in their own terms; apart from [Partial], each ends the listening.
 */
internal sealed interface AppSpeech {
    data class Partial(val text: String) : AppSpeech

    /** The best reading first, as many as the app asked for. */
    data class Heard(val readings: List<Hypothesis>) : AppSpeech

    /** Nobody spoke. */
    data object NoSpeech : AppSpeech

    /** Somebody spoke, but no words came out of it. */
    data object NotUnderstood : AppSpeech

    /** Recognition failed for a reason to tell the user; [status]: what the service answered, if it did. */
    data class Unavailable(val reason: SpeechUnavailable.Reason, val status: Int = 0) : AppSpeech

    /** The remote's mic can't be read, or the recognizer failed. */
    data object Failed : AppSpeech

    /** Recognition can't start: its model or key has gone since voice was ready. */
    data object NotReady : AppSpeech
}

/**
 * Speech for another app that asked the system for it ([RecognizeSpeechActivity],
 * [LumbinaRecognitionService]): one utterance from the remote's mic, ended by silence
 * ([EndOfSpeech]) since no button is held. If the mic key is held meanwhile ([keyHeld]),
 * listening goes on at least until it is released.
 */
internal class AppRecognition(
    private val audio: AudioSource,
    private val sessions: VoiceSessions,
    private val language: VoiceLanguageSetting,
    private val log: LumbinaLog,
) {
    @Volatile var keyHeld = false

    /** The app said it has heard enough ([stop]). */
    @Volatile private var stopped = false

    /** The latest session's number; [active] while one runs. */
    private val latest = AtomicInteger(0)

    @Volatile private var running = 0

    /** Whether a session is running: the mic key then belongs to it. */
    val active: Boolean get() = running != 0

    /**
     * Listens once, in [requested] language (a BCP 47 tag such as "ru-RU") when recognition has
     * it, otherwise as voice input would; at most [max] readings. [onSpeech] hears when speech
     * starts (capture thread). Collect it off the main thread.
     */
    fun listen(requested: String?, max: Int, onSpeech: () -> Unit = {}): Flow<AppSpeech> {
        val endpoint = EndOfSpeech(now = SystemClock::elapsedRealtime)
        val session = sessions.handsFree(audio) { languages(requested) }
        val id = latest.incrementAndGet()
        // Reset here, not when collecting starts: an app may ask to stop before that.
        keyHeld = false
        stopped = false
        return session.untilSilence(endpoint, wanted = { !stopped }, keep = { keyHeld }, onSpeech = onSpeech)
            .map { event ->
                when (event) {
                    is VoiceEvent.Partial -> AppSpeech.Partial(event.text)
                    is VoiceEvent.Final -> event.forApp(max).also { logResult(event) }
                }
            }
            .catch { e ->
                log.log(TAG, "for an app: failed: $e")
                emit(
                    when (e) {
                        is SpeechUnavailable -> AppSpeech.Unavailable(e.reason, e.status)
                        is IOException -> AppSpeech.Failed
                        is IllegalStateException -> AppSpeech.NotReady
                        else -> throw e
                    },
                )
            }
            .onStart {
                log.log(TAG, "an app asks for speech (${requested ?: "any language"})")
                running = id
            }
            .onCompletion {
                // A screen that went away late must not end the next one's session.
                if (running == id) {
                    running = 0
                    keyHeld = false
                }
            }
    }

    /** Ends the running session now, recognizing what was heard. */
    fun stop() {
        stopped = true
    }

    private fun logResult(event: VoiceEvent.Final) {
        val heard = event.stats.hypotheses.ifEmpty { null }?.logText() ?: "no speech"
        log.log(TAG, "for an app: \"${event.result?.text.orEmpty()}\" (${event.stats.audioMillis} ms audio, $heard)")
    }

    private fun languages(requested: String?): List<String> =
        requestedLanguages(requested, sessions.languages) ?: language.languages()

    companion object {
        /**
         * The service's speech for apps in this process, for the screen and the service the system
         * creates by itself; null while voice isn't ready.
         */
        @Volatile var current: AppRecognition? = null
            private set

        fun offer(recognition: AppRecognition) {
            current = recognition
        }

        /** A service that stops takes down only its own. */
        fun retire(recognition: AppRecognition) {
            if (current === recognition) current = null
        }

        private const val TAG = "voice"
    }
}

/**
 * The language an app asked for ([requested], a BCP 47 tag such as "ru-RU"), when recognition
 * has it among [available]: then only that one. Null when it didn't ask or recognition hasn't it.
 */
internal fun requestedLanguages(requested: String?, available: List<String>): List<String>? {
    val wanted = requested?.let { Locale.forLanguageTag(it).language }?.takeIf { it.isNotEmpty() } ?: return null
    return available.firstOrNull { Locale.forLanguageTag(it).language == wanted }?.let(::listOf)
}

/** The best reading first, then other languages' readings; at most [max]. */
internal fun VoiceEvent.Final.readings(max: Int): List<Hypothesis> {
    val best = result ?: return emptyList()
    val others = stats.hypotheses.filter { it.text.isNotBlank() }
    return (listOf(best) + others).distinctBy { it.text }.take(max.coerceAtLeast(1))
}

/** What to tell the user for each reason, with where to mend it if they can; formatted with the service's status. */
internal val SpeechUnavailable.Reason.message: Int
    get() = when (this) {
        SpeechUnavailable.Reason.NoKey -> R.string.voice_no_key
        SpeechUnavailable.Reason.KeyRefused -> R.string.voice_key_refused
        SpeechUnavailable.Reason.NotPaid -> R.string.voice_not_paid
        SpeechUnavailable.Reason.NotAllowed -> R.string.voice_not_allowed
        SpeechUnavailable.Reason.Region -> R.string.voice_other_country
        SpeechUnavailable.Reason.ServiceError -> R.string.voice_service_error
        SpeechUnavailable.Reason.Busy -> R.string.voice_busy
        SpeechUnavailable.Reason.Occupied -> R.string.voice_occupied
        SpeechUnavailable.Reason.MicNotGiven -> R.string.voice_mic_not_given
        SpeechUnavailable.Reason.NoNetwork -> R.string.voice_no_network
    }

/** No readings at all means the audio wasn't recognized, being silence; blank ones, that speech gave no words. */
internal fun VoiceEvent.Final.forApp(max: Int): AppSpeech {
    val readings = readings(max)
    return when {
        readings.isNotEmpty() -> AppSpeech.Heard(readings)
        stats.hypotheses.isEmpty() -> AppSpeech.NoSpeech
        else -> AppSpeech.NotUnderstood
    }
}
