package dev.lumbina.tv.core.domain.speech

import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface VoiceEvent {
    data class Partial(val text: String) : VoiceEvent

    /** [result] is null when nothing was recognized. */
    data class Final(val result: Hypothesis?, val stats: VoiceStats) : VoiceEvent
}

data class VoiceStats(
    val audioMillis: Long,
    val finishedAfterReleaseMillis: Long,
    val hypotheses: List<Hypothesis>,
    /** The level of the loudest chunk, in dBFS; null when no sound came at all (no audio, or all zeros). */
    val loudestDb: Double? = null,
)

/**
 * One push-to-talk utterance: capture → recognize → pick the language.
 *
 * Capture starts at once on [captureDispatcher] (blocking reads; the blocking session opens there
 * too) behind an unbounded buffer:
 * opening the recognition session may first load a model, which takes seconds, and speech said
 * meanwhile waits in the buffer instead of being lost; recognition, slower than real time on TV
 * hardware, never delays reading either. Emits live partials (throttled) and one
 * [VoiceEvent.Final]. Collect it off the main thread.
 */
class RunVoiceSession(
    private val capture: PcmCapture,
    private val engine: SpeechEngine,
    private val now: () -> Long,
    private val captureDispatcher: CoroutineDispatcher,
    private val languages: () -> List<String> = { engine.languages },
    private val partialEveryMillis: Long = 300,
) {
    /**
     * [isHeld] reports whether to keep listening. [onAudio] hears each chunk as soon as it is read,
     * ahead of recognition, which may lag (e.g. an [EndOfSpeech]). [heardSpeech] is asked once the
     * audio ended: if false, the audio is not recognized at all, so a cloud recognizer is not paid
     * for silence (and doesn't make up a phrase from it).
     */
    operator fun invoke(
        heardSpeech: () -> Boolean = { true },
        onAudio: (ByteArray) -> Unit = {},
        isHeld: () -> Boolean,
    ): Flow<VoiceEvent> = channelFlow {
        val releasedAt = AtomicLong(0)
        val trackedHeld = {
            val held = isHeld()
            if (!held) releasedAt.compareAndSet(0, now())
            held
        }
        val chunks = Channel<ByteArray>(Channel.UNLIMITED)
        launch(captureDispatcher) {
            try {
                capture.capture(trackedHeld).collect {
                    onAudio(it)
                    chunks.send(it)
                }
            } finally {
                chunks.close()
            }
        }
        withContext(captureDispatcher) { engine.newSession(languages()) }.use { session ->
            val aligner = SampleAligner()
            var audioBytes = 0L
            var loudest: Double? = null
            var lastPartial = ""
            var lastPartialAt = Long.MIN_VALUE / 2
            for (chunk in chunks) {
                audioBytes += chunk.size
                val samples = aligner.align(chunk)
                if (samples.isEmpty()) continue
                Pcm.levelDb(samples, samples.size)?.let { level -> loudest = maxOf(loudest ?: level, level) }
                session.accept(samples, samples.size)
                val t = now()
                if (t - lastPartialAt >= partialEveryMillis) {
                    lastPartialAt = t
                    val partial = session.partial()
                    if (partial != lastPartial) {
                        lastPartial = partial
                        send(VoiceEvent.Partial(partial))
                    }
                }
            }
            val hypotheses = if (heardSpeech()) session.finish() else emptyList()
            val released = releasedAt.get()
            val stats = VoiceStats(
                audioMillis = Pcm.durationMillis(audioBytes),
                finishedAfterReleaseMillis = if (released == 0L) 0 else now() - released,
                hypotheses = hypotheses,
                loudestDb = loudest,
            )
            send(VoiceEvent.Final(LanguagePicker.pick(hypotheses), stats))
        }
    }

    /**
     * Hands-free, where no button is held: listens until [endpoint] hears the speech end, and no
     * longer than [wanted]. While [keep], it goes on whatever the endpoint says. [onSpeech] hears
     * when speech starts (capture thread). Silence is not recognized at all.
     */
    fun untilSilence(
        endpoint: EndOfSpeech,
        wanted: () -> Boolean,
        keep: () -> Boolean = { false },
        onSpeech: () -> Unit = {},
    ): Flow<VoiceEvent> = invoke(
        heardSpeech = { endpoint.heardSpeech },
        onAudio = {
            val before = endpoint.heardSpeech
            endpoint.hear(it, it.size)
            if (!before && endpoint.heardSpeech) onSpeech()
        },
        isHeld = { wanted() && (keep() || endpoint.listening()) },
    )
}
