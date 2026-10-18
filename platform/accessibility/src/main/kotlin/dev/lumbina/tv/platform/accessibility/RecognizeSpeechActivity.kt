package dev.lumbina.tv.platform.accessibility

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent.EXTRA_CONFIDENCE_SCORES
import android.speech.RecognizerIntent.EXTRA_LANGUAGE
import android.speech.RecognizerIntent.EXTRA_MAX_RESULTS
import android.speech.RecognizerIntent.EXTRA_PROMPT
import android.speech.RecognizerIntent.EXTRA_RESULTS
import android.speech.RecognizerIntent.RESULT_AUDIO_ERROR
import android.speech.RecognizerIntent.RESULT_CLIENT_ERROR
import android.speech.RecognizerIntent.RESULT_NETWORK_ERROR
import android.speech.RecognizerIntent.RESULT_NO_MATCH
import android.speech.RecognizerIntent.RESULT_SERVER_ERROR
import android.view.Gravity
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.FrameLayout
import android.widget.TextView
import dev.lumbina.tv.core.domain.speech.SpeechUnavailable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch

/**
 * Lumbina as the system's speech recognizer for apps that ask with `RECOGNIZE_SPEECH`
 * ([android.speech.RecognizerIntent]): listens to the remote's mic in the service's process, the
 * way voice input does, and answers with the text. The app's own screen stays visible behind the
 * caption. Unlike the `RecognitionService` path, the system checks no permission of the asking
 * app here, so apps from the store get their speech too.
 */
class RecognizeSpeechActivity : Activity() {

    private val scope = MainScope()
    private lateinit var caption: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        caption = captionView()
        val app = AppRecognition.current
            ?: return flashAndFinish(getString(R.string.voice_for_apps_unavailable), RESULT_AUDIO_ERROR)
        caption.text = intent.getStringExtra(EXTRA_PROMPT) ?: getString(R.string.voice_listening)
        scope.launch { listen(app) }
    }

    override fun onStop() {
        super.onStop()
        // Left for another screen (Home, say): the app no longer waits for speech.
        if (!isFinishing) {
            setResult(RESULT_CANCELED)
            finish()
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private suspend fun listen(app: AppRecognition) {
        val max = intent.getIntExtra(EXTRA_MAX_RESULTS, Int.MAX_VALUE)
        app.listen(intent.getStringExtra(EXTRA_LANGUAGE), max).flowOn(Dispatchers.Default).collect { speech ->
            when (speech) {
                is AppSpeech.Partial -> if (speech.text.isNotEmpty()) {
                    caption.text =
                        getString(R.string.voice_heard, speech.text)
                }

                is AppSpeech.Heard -> answer(speech)

                AppSpeech.NoSpeech -> flashAndFinish(getString(R.string.voice_no_speech), RESULT_NO_MATCH)

                AppSpeech.NotUnderstood -> flashAndFinish(getString(R.string.voice_not_heard), RESULT_NO_MATCH)

                is AppSpeech.Unavailable -> flashAndFinish(
                    getString(speech.reason.message, speech.status),
                    result(speech.reason),
                    UNAVAILABLE_MILLIS,
                )

                AppSpeech.Failed -> flashAndFinish(getString(R.string.voice_failed), RESULT_AUDIO_ERROR)

                AppSpeech.NotReady -> flashAndFinish(getString(R.string.voice_failed), RESULT_CLIENT_ERROR)
            }
        }
    }

    private fun answer(speech: AppSpeech.Heard) {
        val readings = speech.readings
        caption.text = getString(R.string.voice_heard, readings.first().text)
        val data = Intent()
            .putStringArrayListExtra(EXTRA_RESULTS, ArrayList(readings.map { it.text }))
            .putExtra(EXTRA_CONFIDENCE_SCORES, readings.map { it.confidence.toFloat() }.toFloatArray())
        setResult(RESULT_OK, data)
        // Long enough to see what was heard, short enough not to keep the app waiting.
        scope.launch {
            delay(RESULT_MILLIS)
            finish()
        }
    }

    private fun result(reason: SpeechUnavailable.Reason) = when (reason.side) {
        SpeechUnavailable.Side.Client -> RESULT_CLIENT_ERROR
        SpeechUnavailable.Side.Service -> RESULT_SERVER_ERROR
        SpeechUnavailable.Side.Audio -> RESULT_AUDIO_ERROR
        SpeechUnavailable.Side.Network -> RESULT_NETWORK_ERROR
    }

    private fun flashAndFinish(text: String, result: Int, millis: Long = FLASH_MILLIS) {
        caption.text = text
        setResult(result)
        scope.launch {
            delay(millis)
            finish()
        }
    }

    /** The caption at the bottom, as voice input's overlay shows it. */
    private fun captionView(): TextView {
        val label = label("", TEXT_SP, OverlayPalette.CAPTION_TEXT).apply {
            val pad = dp(PADDING_DP)
            setPadding(pad * 2, pad, pad * 2, pad)
            background = rounded(OverlayPalette.CAPTION, CORNER_DP)
        }
        val params = FrameLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL)
            .apply { bottomMargin = dp(BOTTOM_MARGIN_DP) }
        setContentView(FrameLayout(this).apply { addView(label, params) })
        return label
    }

    private companion object {
        const val FLASH_MILLIS = 1_500L

        /** Longer: these say what to do. */
        const val UNAVAILABLE_MILLIS = 4_000L
        const val RESULT_MILLIS = 600L
        const val TEXT_SP = 22f
        const val PADDING_DP = 16
        const val CORNER_DP = 24
        const val BOTTOM_MARGIN_DP = 48
    }
}
