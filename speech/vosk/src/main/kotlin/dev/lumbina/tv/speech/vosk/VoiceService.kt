package dev.lumbina.tv.speech.vosk

import android.app.Service
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Process
import android.os.SystemClock
import dagger.hilt.android.AndroidEntryPoint
import dev.lumbina.tv.core.common.LumbinaLog
import dev.lumbina.tv.core.domain.speech.SpeechSession
import javax.inject.Inject

/**
 * The recognizer, in a process of its own so its models stay out of the always-running ones.
 * Bound by [RemoteSpeechEngine] while voice is in use; once it lets go, the process ends itself:
 * the models' native memory isn't given back on unloading. Anything a call throws
 * reaches the caller as an [IllegalStateException]: other exceptions would crash this process on
 * the binder thread.
 */
@AndroidEntryPoint
class VoiceService : Service() {

    @Inject internal lateinit var engine: VoskSpeechEngine

    @Inject lateinit var log: LumbinaLog

    private val binder = object : IVoiceEngine.Stub() {
        override fun newSession(load: List<String>, languages: List<String>): IVoiceSession = guarded {
            val started = SystemClock.elapsedRealtime()
            val had = engine.languages
            check(engine.prepare(load)) { "no speech models for $load" }
            if (engine.languages != had) {
                log.log(TAG, "models ready in ${SystemClock.elapsedRealtime() - started} ms: ${engine.languages}")
            }
            RemoteSession(engine.newSession(languages))
        }
    }

    override fun onCreate() {
        super.onCreate()
        running = true
    }

    override fun onBind(intent: Intent): IBinder = binder

    override fun onDestroy() {
        running = false
        log.log(TAG, "idle: ending the voice process")
        // A second later: a rebind meanwhile keeps the process, models and all.
        Handler(Looper.getMainLooper()).postDelayed(
            { if (!running) Process.killProcess(Process.myPid()) },
            EXIT_DELAY_MILLIS,
        )
        super.onDestroy()
    }

    private class RemoteSession(private val session: SpeechSession) : IVoiceSession.Stub() {
        override fun accept(pcm: ByteArray, length: Int) = guarded { session.accept(pcm, length) }

        override fun partial(): String = guarded { session.partial() }

        override fun finish(): Bundle = guarded {
            val hypotheses = session.finish()
            Bundle().apply {
                putStringArray(LANGUAGES, hypotheses.map { it.language }.toTypedArray())
                putStringArray(TEXTS, hypotheses.map { it.text }.toTypedArray())
                putDoubleArray(CONFIDENCES, hypotheses.map { it.confidence }.toDoubleArray())
            }
        }

        override fun close() = guarded { session.close() }
    }

    internal companion object {
        private const val TAG = "voice"
        private const val EXIT_DELAY_MILLIS = 1_000L
        const val LANGUAGES = "languages"
        const val TEXTS = "texts"
        const val CONFIDENCES = "confidences"

        /** Whether a service instance is alive in this process; main thread only. */
        private var running = false

        @Suppress("TooGenericExceptionCaught") // whatever it is, it must not take the process down
        private inline fun <T> guarded(block: () -> T): T = try {
            block()
        } catch (e: IllegalStateException) {
            throw e
        } catch (e: Exception) {
            throw IllegalStateException(e.toString(), e)
        }
    }
}
