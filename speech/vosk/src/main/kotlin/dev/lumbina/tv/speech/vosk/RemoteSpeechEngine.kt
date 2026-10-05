package dev.lumbina.tv.speech.vosk

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.RemoteException
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.lumbina.tv.core.common.LumbinaLog
import dev.lumbina.tv.core.domain.speech.Hypothesis
import dev.lumbina.tv.core.domain.speech.SpeechEngine
import dev.lumbina.tv.core.domain.speech.SpeechSession
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The speech engine as the rest of Lumbina sees it. Recognition runs in [VoiceService]'s own
 * process: bound when a session opens, let go [idleMillis] after the last one closes, so the
 * models' memory is only held while voice is in use. [prepare] only checks which models are
 * installed; they load when a session opens, which then takes seconds (capture doesn't wait,
 * see `RunVoiceSession`). A dead or unreachable voice process fails the session with an
 * [IOException]; the next one starts it again.
 */
@Singleton
internal class RemoteSpeechEngine(
    private val context: Context,
    private val store: VoskModelStore,
    private val log: LumbinaLog,
    private val idleMillis: Long,
    private val connectTimeoutMillis: Long,
) : SpeechEngine {

    @Inject constructor(@ApplicationContext context: Context, store: VoskModelStore, log: LumbinaLog) :
        this(context, store, log, IDLE_MILLIS, CONNECT_TIMEOUT_MILLIS)

    @Volatile private var wanted: List<String> = emptyList()
    private val connection = Connection()

    override val languages: List<String> get() = wanted

    override fun prepare(languages: List<String>): Boolean {
        wanted = languages.filter(store::isInstalled)
        return wanted.isNotEmpty()
    }

    override fun newSession(languages: List<String>): SpeechSession {
        connection.sessionOpening()
        var opened = false
        try {
            val remote = ipc { connection.engine().newSession(wanted, languages) }
            opened = true
            return Session(remote)
        } finally {
            if (!opened) connection.sessionClosed()
        }
    }

    private inner class Session(private val remote: IVoiceSession) : SpeechSession {
        private var closed = false

        override fun accept(pcm: ByteArray, length: Int) = ipc { remote.accept(pcm, length) }

        override fun partial(): String = ipc { remote.partial() }

        override fun finish(): List<Hypothesis> {
            val result = ipc { remote.finish() }
            val languages = result.getStringArray(VoiceService.LANGUAGES).orEmpty()
            val texts = result.getStringArray(VoiceService.TEXTS).orEmpty()
            val confidences = result.getDoubleArray(VoiceService.CONFIDENCES) ?: DoubleArray(0)
            return languages.indices.map { Hypothesis(languages[it], texts[it], confidences[it]) }
        }

        override fun close() {
            if (closed) return
            closed = true
            runCatching { remote.close() }
            connection.sessionClosed()
        }
    }

    /** The binding to [VoiceService], held while sessions are open and for a while after. */
    private inner class Connection : ServiceConnection {
        private val lock = Object()
        private val main = Handler(Looper.getMainLooper())
        private val letGo = Runnable { release() }
        private var bound = false
        private var engine: IVoiceEngine? = null
        private var open = 0

        fun sessionOpening() = synchronized(lock) {
            open++
            main.removeCallbacks(letGo)
        }

        fun sessionClosed() = synchronized(lock) {
            open--
            if (open == 0) main.postDelayed(letGo, idleMillis)
        }

        /** Binds if needed and waits for the service; blocking, off the main thread. */
        fun engine(): IVoiceEngine = synchronized(lock) {
            engine?.let { if (it.asBinder().isBinderAlive) return it else engine = null }
            if (!bound) {
                bound = context.bindService(Intent(context, VoiceService::class.java), this, Context.BIND_AUTO_CREATE)
                if (!bound) throw IOException("voice service can't be bound")
                log.log(TAG, "starting the voice process")
            }
            val deadline = System.currentTimeMillis() + connectTimeoutMillis
            while (engine == null) {
                val left = deadline - System.currentTimeMillis()
                if (!bound || left <= 0) {
                    // Let go of a binding that isn't coming, so the next session binds afresh.
                    unbind()
                    throw IOException("voice process didn't come up")
                }
                lock.wait(left)
            }
            return engine!!
        }

        override fun onServiceConnected(name: ComponentName, service: IBinder) = synchronized(lock) {
            // A connection let go meanwhile (it took too long) stays let go.
            if (!bound) return@synchronized
            engine = IVoiceEngine.Stub.asInterface(service)
            lock.notifyAll()
        }

        /** The process died; the system starts it again while we stay bound. */
        override fun onServiceDisconnected(name: ComponentName) = synchronized(lock) {
            log.log(TAG, "voice process gone")
            engine = null
        }

        override fun onBindingDied(name: ComponentName) = synchronized(lock) {
            unbind()
            lock.notifyAll()
        }

        /** Idle: lets the voice process go, unless a session opened meanwhile. */
        private fun release() = synchronized(lock) {
            if (open > 0 || !bound) return@synchronized
            unbind()
            log.log(TAG, "voice idle: letting the voice process go")
        }

        private fun unbind() {
            if (!bound) return
            context.unbindService(this)
            bound = false
            engine = null
        }
    }

    private companion object {
        const val TAG = "voice"
        const val IDLE_MILLIS = 5 * 60_000L
        const val CONNECT_TIMEOUT_MILLIS = 10_000L

        /** A failed call across processes as an [IOException], which voice input reports. */
        inline fun <T> ipc(call: () -> T): T = try {
            call()
        } catch (e: RemoteException) {
            throw IOException("voice process: $e", e)
        }
    }
}
