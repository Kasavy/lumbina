package dev.lumbina.tv.platform.accessibility

import android.content.Intent
import android.os.Bundle
import android.os.RemoteException
import android.speech.RecognitionService
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import dev.lumbina.tv.core.domain.speech.Hypothesis
import dev.lumbina.tv.core.domain.speech.SpeechUnavailable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch

/**
 * Lumbina as the system's speech recognizer for apps that ask with [SpeechRecognizer]: the same
 * listening as [RecognizeSpeechActivity], answered through the [Callback] (partial results when
 * asked for). The system first checks the asking app's microphone permission and, on the Vidda
 * projector, refuses apps installed from a store before this service hears of them,
 * so it serves only the apps the firmware lets through. No check is bypassed.
 */
class LumbinaRecognitionService : RecognitionService() {

    private val scope = MainScope()
    private var job: Job? = null

    override fun onStartListening(intent: Intent, callback: Callback) {
        job?.cancel()
        val app = AppRecognition.current ?: return callback.tell { error(SpeechRecognizer.ERROR_AUDIO) }
        val partials = intent.getBooleanExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
        val max = intent.getIntExtra(RecognizerIntent.EXTRA_MAX_RESULTS, Int.MAX_VALUE)
        callback.tell { readyForSpeech(Bundle()) }
        val speech = app.listen(intent.getStringExtra(RecognizerIntent.EXTRA_LANGUAGE), max) {
            scope.launch { callback.tell { beginningOfSpeech() } }
        }
        job = scope.launch {
            speech.flowOn(Dispatchers.Default).collect { answer(callback, it, partials) }
        }
    }

    /** The app has heard enough: recognize what was said so far. */
    override fun onStopListening(callback: Callback) {
        AppRecognition.current?.stop()
    }

    override fun onCancel(callback: Callback) {
        job?.cancel()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun answer(callback: Callback, speech: AppSpeech, partials: Boolean) {
        when (speech) {
            is AppSpeech.Partial -> if (partials) callback.tell { partialResults(results(speech.text)) }
            is AppSpeech.Heard -> callback.ended { results(results(speech.readings)) }
            AppSpeech.NoSpeech -> callback.ended { error(SpeechRecognizer.ERROR_SPEECH_TIMEOUT) }
            AppSpeech.NotUnderstood -> callback.ended { error(SpeechRecognizer.ERROR_NO_MATCH) }
            is AppSpeech.Unavailable -> callback.tell { error(error(speech.reason)) }
            AppSpeech.Failed -> callback.tell { error(SpeechRecognizer.ERROR_AUDIO) }
            AppSpeech.NotReady -> callback.tell { error(SpeechRecognizer.ERROR_CLIENT) }
        }
    }

    private fun error(reason: SpeechUnavailable.Reason) = when (reason) {
        SpeechUnavailable.Reason.NoKey -> SpeechRecognizer.ERROR_CLIENT
        SpeechUnavailable.Reason.KeyRefused -> SpeechRecognizer.ERROR_SERVER
        SpeechUnavailable.Reason.NoNetwork -> SpeechRecognizer.ERROR_NETWORK
    }

    /** The listening ran to its end, whatever came of it. */
    private inline fun Callback.ended(crossinline call: Callback.() -> Unit) = tell {
        endOfSpeech()
        call()
    }

    private fun results(readings: List<Hypothesis>) = Bundle().apply {
        putStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION, ArrayList(readings.map { it.text }))
        putFloatArray(SpeechRecognizer.CONFIDENCE_SCORES, readings.map { it.confidence.toFloat() }.toFloatArray())
    }

    private fun results(text: String) = Bundle().apply {
        putStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION, arrayListOf(text))
    }

    /** The asking app may have gone meanwhile; then there is nobody to tell. */
    private inline fun Callback.tell(call: Callback.() -> Unit) {
        try {
            call()
        } catch (_: RemoteException) {
            job?.cancel()
        }
    }
}
