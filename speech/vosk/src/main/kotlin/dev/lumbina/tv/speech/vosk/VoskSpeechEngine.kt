package dev.lumbina.tv.speech.vosk

import dev.lumbina.tv.core.common.LumbinaLog
import dev.lumbina.tv.core.domain.speech.Hypothesis
import dev.lumbina.tv.core.domain.speech.Pcm
import dev.lumbina.tv.core.domain.speech.SpeechEngine
import dev.lumbina.tv.core.domain.speech.SpeechSession
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import org.json.JSONObject
import org.vosk.LibVosk
import org.vosk.LogLevel
import org.vosk.Model
import org.vosk.Recognizer

/**
 * Offline recognition with one Vosk model per installed language. Every utterance runs
 * through all models; [dev.lumbina.tv.core.domain.speech.LanguagePicker] then keeps the most
 * confident reading. Models take a few seconds and ~100 MB of RAM each to load.
 */
@Singleton
class VoskSpeechEngine @Inject constructor(private val store: VoskModelStore, private val log: LumbinaLog) :
    SpeechEngine {

    @Volatile private var models: Map<String, Model> = emptyMap()

    override val languages: List<String> get() = models.keys.toList()

    @Synchronized
    override fun prepare(languages: List<String>): Boolean {
        LibVosk.setLogLevel(LogLevel.WARNINGS)
        val loaded = models
        val wanted = languages.filter(store::isInstalled)
        val fresh = wanted.filter { it !in loaded }.mapNotNull { lang ->
            runCatching { lang to Model(store.directory(lang).absolutePath) }
                .onFailure { log.log(TAG, "model $lang failed to load: $it") }
                .getOrNull()
        }.toMap()
        val all = loaded + fresh
        models = wanted.mapNotNull { lang -> all[lang]?.let { lang to it } }.toMap()
        // Recognizers hold their own reference, so a session still running keeps working.
        loaded.filterKeys { it !in models }.forEach { (lang, model) ->
            model.close()
            log.log(TAG, "unloaded $lang")
        }
        return models.isNotEmpty()
    }

    // With prepare(): a model must not be closed between being picked and a Recognizer taking it.
    @Synchronized
    override fun newSession(languages: List<String>): SpeechSession {
        val selected = languages.mapNotNull { lang -> models[lang]?.let { lang to it } }
        check(selected.isNotEmpty()) { "no speech models loaded for $languages" }
        return VoskSession(selected.map { (lang, model) -> Track(lang, model) })
    }

    /** One model's recognizer plus what it recognized so far. */
    private class Track(val language: String, model: Model) : AutoCloseable {
        val recognizer = Recognizer(model, Pcm.SAMPLE_RATE.toFloat()).apply { setWords(true) }
        private val text = StringBuilder()
        private val confidences = mutableListOf<Double>()

        fun accept(pcm: ByteArray, length: Int) {
            // Vosk ends a phrase on a pause; bank it now, the final result won't repeat it.
            if (recognizer.acceptWaveForm(pcm, length)) bank(recognizer.result)
        }

        fun partial(): String = join(text.toString(), JSONObject(recognizer.partialResult).optString("partial"))

        fun finish(): Hypothesis {
            bank(recognizer.finalResult)
            val confidence = if (confidences.isEmpty()) 0.0 else confidences.average()
            return Hypothesis(language, text.toString(), confidence)
        }

        private fun bank(json: String) {
            val result = JSONObject(json)
            val phrase = result.optString("text").trim()
            if (phrase.isNotEmpty()) text.append(if (text.isEmpty()) phrase else " $phrase")
            val words = result.optJSONArray("result") ?: return
            for (i in 0 until words.length()) confidences += words.getJSONObject(i).optDouble("conf", 0.0)
        }

        override fun close() = recognizer.close()
    }

    /**
     * Runs each language's recognizer on its own thread (TV SoCs have several slow cores),
     * which roughly halves the wait after the button is released. A recognizer is only ever
     * touched by its own thread. Live partial text is the primary recognizer's latest reading,
     * refreshed in the background so [partial] never waits for the queue.
     */
    private class VoskSession(private val tracks: List<Track>) : SpeechSession {
        private val workers = tracks.map { Executors.newSingleThreadExecutor() }

        @Volatile private var latestPartial = ""

        override fun accept(pcm: ByteArray, length: Int) {
            val chunk = pcm.copyOf(length)
            tracks.forEachIndexed { i, track -> workers[i].execute { track.accept(chunk, length) } }
        }

        override fun partial(): String {
            workers.first().execute { latestPartial = tracks.first().partial() }
            return latestPartial
        }

        override fun finish(): List<Hypothesis> = tracks.mapIndexed { i, track -> workers[i].submit(track::finish) }
            .map { it.get() }

        override fun close() {
            tracks.forEachIndexed { i, track -> workers[i].execute(track::close) }
            workers.forEach(ExecutorService::shutdown)
            workers.forEach { it.awaitTermination(CLOSE_TIMEOUT_SECONDS, TimeUnit.SECONDS) }
        }

        private companion object {
            const val CLOSE_TIMEOUT_SECONDS = 5L
        }
    }

    private companion object {
        const val TAG = "vosk"

        fun join(done: String, partial: String): String = listOf(done, partial.trim())
            .filter(String::isNotEmpty)
            .joinToString(" ")
    }
}
