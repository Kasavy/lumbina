package dev.lumbina.tv.core.domain.speech

import dev.lumbina.tv.core.domain.MILLIS_PER_SECOND
import java.io.IOException
import java.util.Locale
import kotlin.math.log10
import kotlin.math.sqrt

/** Audio format used everywhere in the voice pipeline: 16 kHz, mono, 16-bit little-endian PCM. */
object Pcm {
    const val SAMPLE_RATE = 16_000
    const val BYTES_PER_SAMPLE = 2

    /** Bytes read from a source at a time: 200 ms of audio. */
    const val CHUNK_BYTES = 6_400

    /** A short frame, 40 ms: what is passed on live (to an app being fed the remote's audio). */
    const val FRAME_BYTES = 1_280

    /** Less audio than this is a tap of the key, not a phrase: not worth a recognizer's while. */
    const val TAP_MILLIS = 500L

    private const val FULL_SCALE = 32_768.0
    private const val DB_PER_DECADE = 20.0

    fun durationMillis(bytes: Long): Long = bytes / BYTES_PER_SAMPLE * MILLIS_PER_SECOND / SAMPLE_RATE

    /** RMS level of [length] bytes of [pcm] in dBFS, or null for all zeros (or no whole sample). */
    fun levelDb(pcm: ByteArray, length: Int): Double? {
        val samples = length / BYTES_PER_SAMPLE
        if (samples == 0) return null
        var sum = 0.0
        for (i in 0 until samples) {
            val lo = pcm[2 * i].toInt() and 0xFF
            val s = (pcm[2 * i + 1].toInt() shl 8) or lo
            sum += s.toDouble() * s
        }
        if (sum == 0.0) return null
        return DB_PER_DECADE * log10(sqrt(sum / samples) / FULL_SCALE)
    }
}

/** A source of microphone audio, e.g. a remote's mic exposed by the firmware, or AudioRecord. */
fun interface AudioSource {
    /** Opens a new capture; blocking I/O, call off the main thread. */
    fun open(): AudioStream
}

interface AudioStream : AutoCloseable {
    /** Reads into [dst]; returns bytes read, 0 if nothing is available yet, -1 at the end. */
    fun read(dst: ByteArray): Int
}

/** One language's reading of an utterance; [confidence] is the mean word confidence (0..1). */
data class Hypothesis(val language: String, val text: String, val confidence: Double)

/** The readings for the log: `ru=0.90"один дома", en=0.62"one doma"`. */
fun List<Hypothesis>.logText(): String =
    joinToString { String.format(Locale.US, "%s=%.2f\"%s\"", it.language, it.confidence, it.text) }

/**
 * Recognition failed for a [reason] worth telling the user: one they can mend, or the service's
 * own failure with the HTTP [status] it answered (0 where there was none). An [IOException] still,
 * for those who only report that it failed.
 */
class SpeechUnavailable(val reason: Reason, message: String, cause: Throwable? = null, val status: Int = 0) :
    IOException(message, cause) {
    /** Whose failure it is, as an app that asked Lumbina for speech is told. */
    enum class Side { Client, Service, Audio, Network }

    enum class Reason(val side: Side) {
        /** A cloud service is chosen, and its key is gone. */
        NoKey(Side.Client),

        /** The service refused the key. */
        KeyRefused(Side.Service),

        /** The service took the key, and its account has nothing to pay with. */
        NotPaid(Side.Service),

        /** The service took the key and does not let it recognize: its rights, or the account behind it. */
        NotAllowed(Side.Service),

        /** The service does not serve the country the request came from. */
        Region(Side.Service),

        /** The service answered with an error that is not about the key, the account or the pace. */
        ServiceError(Side.Service),

        /** The service is asked too often: to wait a little. */
        Busy(Side.Service),

        /** An app that recognizes is still at the phrase before: free in a few seconds. */
        Occupied(Side.Service),

        /** Another app recognizes, and the remote's sound can't be given to it now. */
        MicNotGiven(Side.Audio),

        /** The service can't be reached. */
        NoNetwork(Side.Network),
    }
}

/** A speech recognizer: on the device (a model per language), in the cloud, or another app on the device. */
interface SpeechEngine {
    /** Languages sessions recognize in, preferred first; empty until [prepare] succeeds. */
    val languages: List<String>

    /**
     * Picks the installed ones among [languages], in that order of preference, for sessions to
     * use (blocking). Their models may load only when a session opens. Returns true if any is
     * installed.
     */
    fun prepare(languages: List<String>): Boolean

    /**
     * A new recognition session for [languages], some of [SpeechEngine.languages] in order of
     * preference; blocking (it may first load models, which takes seconds), call off the main
     * thread.
     */
    fun newSession(languages: List<String> = this.languages): SpeechSession
}

interface SpeechSession : AutoCloseable {
    /** Feeds whole 16-bit samples ([length] is even). */
    fun accept(pcm: ByteArray, length: Int)

    /** Best-effort text so far in the preferred language, for live feedback. */
    fun partial(): String

    /**
     * The key was let go: the audio still to come was said before that (the remote delivers it
     * with a lag). May be told more than once.
     */
    fun released() = Unit

    /** Final readings, one per language. */
    fun finish(): List<Hypothesis>
}

/**
 * Picks the language automatically. Hypotheses come in order of preference; the first
 * non-empty one (the primary language) wins unless another is more confident by at least
 * [SECONDARY_MARGIN]. The margin matters because a small model confidently "hears" filler
 * phrases in speech of another language.
 */
object LanguagePicker {
    private const val SECONDARY_MARGIN = 0.15

    fun pick(hypotheses: List<Hypothesis>): Hypothesis? {
        val candidates = hypotheses.filter { it.text.isNotBlank() }
        val primary = candidates.firstOrNull() ?: return null
        val challenger = candidates.drop(1).maxByOrNull { it.confidence }
        return if (challenger != null && challenger.confidence >= primary.confidence + SECONDARY_MARGIN) {
            challenger
        } else {
            primary
        }
    }
}
