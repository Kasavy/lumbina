package dev.lumbina.tv.platform.accessibility

import android.content.Context
import android.content.pm.PackageManager
import android.net.LocalServerSocket
import android.net.LocalSocket
import android.net.LocalSocketAddress
import android.os.Build
import android.os.Process
import android.os.SystemClock
import androidx.annotation.RequiresApi
import dev.lumbina.tv.core.common.LumbinaLog
import dev.lumbina.tv.core.domain.access.AccessSettingsRepository
import dev.lumbina.tv.core.domain.access.LocalAccess
import dev.lumbina.tv.core.domain.access.micAllowed
import dev.lumbina.tv.core.domain.speech.AudioSource
import dev.lumbina.tv.core.domain.speech.Pcm
import java.io.IOException
import java.io.OutputStream
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The remote's mic for apps that record by themselves instead of asking the system for speech:
 * Android's microphone input has no sound on such a device, so their voice search hears nothing.
 * A helper ([MicInjector]), started as the shell over Lumbina's own adb, injects audio into the
 * recordings of the apps the user allowed; Lumbina sends it the remote's audio while one of them
 * records, and stays the remote's only reader. When another app records, the helper says so and
 * [onHeard] gets its package: the user is asked, and an answer restarts the helper with the new
 * set of apps. So does an allowed app that turns up under a uid the helper wasn't given: it was
 * installed, or installed again, after the helper started. The helper lives exactly as long as
 * its connection to Lumbina, so nothing is left behind when Lumbina's process goes.
 *
 * An app that recognizes speech for Lumbina itself ([SpeechAppEngine]) is given a feed without
 * asking, but gets audio only while a session of Lumbina's has it listening: the session sends what
 * it already reads, and no second reader is opened. Recording by itself, it is asked about like
 * any other app.
 */
internal class MicInjection(
    private val context: Context,
    private val access: LocalAccess,
    private val settings: AccessSettingsRepository,
    private val onHeard: (String) -> Unit,
    private val log: LumbinaLog,
) {
    /** Whether the remote's audio goes to an app that records by itself right now: the mic key is then that app's. */
    @Volatile var feeding = false
        private set

    private var job: Job? = null

    @Volatile private var server: LocalServerSocket? = null

    @Volatile private var helper: LocalSocket? = null

    @Volatile private var name: String? = null

    /** The apps to feed changed: the helper in service is let go and the next starts at once. */
    @Volatile private var changed = false

    /** A helper was started and has not connected yet. */
    @Volatile private var connecting = false

    /** Whether each start of the helper connected: what an answer that changed the apps waits for. */
    val outcomes = MutableSharedFlow<Boolean>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    /** Ends the wait before the next start. */
    private val hurry = Channel<Unit>(Channel.CONFLATED)

    /** Apps given a feed without the user's leave: those Lumbina itself asks for speech. */
    @Volatile private var extra: Set<String> = emptySet()

    /** What the helper in service feeds, by uid; empty while there is none. */
    @Volatile private var fedNow: Map<Int, String> = emptyMap()

    /** The uids that record now, as the helper in service tells; none while there is no helper. */
    @Volatile private var recordingNow: Set<Int> = emptySet()

    /** The session of Lumbina's own that has an app listening. */
    @Volatile private var owner: Owner? = null

    private class Owner(val uid: Int)

    /** One writer to the helper at a time: a session's audio and the pump's never interleave. */
    private val writing = Any()

    /** The side of [SpeechAppEngine]: an app that recognizes for Lumbina itself. */
    val speech = Speech()

    inner class Speech {
        /**
         * The app [SpeechAppEngine] chose before this connection, fed from the helper's first start:
         * told by [alsoFeed] a moment later, it would start the helper twice.
         */
        fun feedFromStart(app: String) {
            extra = extra + app
        }

        /** Gives [app] a feed too from now on; true if the helper starts again to take it in. */
        fun alsoFeed(app: String): Boolean {
            if (app in extra) return false
            extra = extra + app
            restart()
            return true
        }

        /** A helper is on its way: who it will feed is known a moment later. */
        val starting: Boolean get() = changed || connecting

        /** The uid under which the helper in service feeds [app]; null while it doesn't. */
        fun uidFed(app: String): Int? = fedNow.entries.firstOrNull { it.value == app }?.key

        /** Whether [uid] records right now: its own doing, or a listening it was asked for and has not ended. */
        fun records(uid: Int): Boolean = uid in recordingNow

        /** Keeps who records now, from the helper's word; always true, to stand in a condition. */
        fun noted(said: HelperLine.Recording): Boolean {
            recordingNow = if (said.on) recordingNow + said.uid else recordingNow - said.uid
            return true
        }

        /** [uid]'s recordings are a session's of Lumbina's own until what is returned is [release]d. */
        fun own(uid: Int): Any = Owner(uid).also { owner = it }

        /** Gives the recordings back, unless a later session has taken them since. */
        fun release(token: Any) {
            if (owner === token) owner = null
        }

        /** A session's audio for the app it has listening; false with no helper to take it. */
        fun send(pcm: ByteArray, length: Int): Boolean {
            return try {
                // A socket closed meanwhile throws on being asked for its stream, too.
                val out = helper?.outputStream ?: return false
                synchronized(writing) { out.write(pcm, 0, length) }
                true
            } catch (_: IOException) {
                false
            }
        }
    }

    @OptIn(FlowPreview::class)
    fun start(scope: CoroutineScope, audio: AudioSource) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        job = scope.launch(Dispatchers.IO) {
            launch {
                // A few switches flipped in a row in the settings make one restart.
                settings.settings.map { it.micAllowed }.distinctUntilChanged().drop(1)
                    .debounce(SETTLE_MILLIS)
                    .collect { restart() }
            }
            try {
                keepHelper(this, audio)
            } catch (e: IOException) {
                log.log(TAG, "mic helper: $e")
            } finally {
                close()
            }
        }
    }

    /**
     * Ends serving: the helper sees its connection go and exits, taking its audio policy along.
     * Closing the sockets wakes the blocking reads; a connection of Lumbina's own wakes accept.
     */
    fun stop() {
        job?.cancel()
        job = null
        name?.let { wake(it) }
        close()
    }

    /**
     * [changed] is set before the helper is looked for, and [keepHelper] looks at it after naming
     * the helper: whichever comes second sees the other, so a change is never slept through.
     */
    private fun restart() {
        changed = true
        runCatching { helper?.shutdownInput() }
        hurry.trySend(Unit)
    }

    private fun close() {
        runCatching { helper?.shutdownInput() }
        runCatching { helper?.close() }
        runCatching { server?.close() }
        helper = null
        server = null
        feeding = false
        changed = false
        connecting = false
    }

    /**
     * Starts the helper, serves it while it is connected, and starts it again when it goes. The
     * socket's name is new for every start, so a service started again in the same process never
     * finds it taken.
     */
    @RequiresApi(Build.VERSION_CODES.Q)
    private suspend fun keepHelper(scope: CoroutineScope, audio: AudioSource) {
        val name = "lumbina.mic.${Process.myPid()}.${starts.incrementAndGet()}"
        this.name = name
        val listening = LocalServerSocket(name).also { server = it }
        // Waits grow while there is no adb access or the helper doesn't last, up to a few minutes.
        var wait = RETRY_MIN_MILLIS
        while (scope.isActive) {
            changed = false
            hurry.tryReceive()
            val leave = settings.settings.first().micAllowed
            val allowed = leave + extra
            val apps = context.packageManager.installed(allowed)
            connecting = true
            val socket = if (launch(name, apps.keys)) accept(scope, listening, name) else null
            val servedFrom = SystemClock.elapsedRealtime()
            if (socket != null && scope.isActive) {
                log.log(TAG, "mic helper connected (${apps.values.joinToString().ifEmpty { "no app allowed yet" }})")
                // Before a session may write to it: the helper takes nothing ahead of this line.
                runCatching { socket.outputStream.write("${MicInjector.GO}\n".toByteArray()) }
                helper = socket
                fedNow = apps
                connecting = false
                outcomes.tryEmit(true)
                socket.use {
                    if (!changed) serve(scope, it, Fed(apps, allowed, leave), audio)
                }
                helper = null
                fedNow = emptyMap()
                log.log(TAG, "mic helper gone")
            } else {
                socket?.close()
                connecting = false
                outcomes.tryEmit(false)
            }
            if (changed) {
                wait = RETRY_MIN_MILLIS
                continue
            }
            val lasted = socket != null && SystemClock.elapsedRealtime() - servedFrom >= HEALTHY_MILLIS
            wait = if (lasted) RETRY_MIN_MILLIS else (wait * 2).coerceAtMost(RETRY_MAX_MILLIS)
            withTimeoutOrNull(wait) { hurry.receive() }
        }
    }

    /** False when Lumbina has no adb access (yet). */
    @RequiresApi(Build.VERSION_CODES.Q)
    private suspend fun launch(name: String, uids: Collection<Int>): Boolean {
        val apk = context.applicationInfo.sourceDir
        return access.startShellProgram(apk, MicInjector::class.java.name, listOf(name) + uids.map(Int::toString))
    }

    /**
     * The helper's connection, or null if it doesn't come within [ACCEPT_MILLIS]: then Lumbina
     * connects to itself to end the wait. Only the shell is let in.
     */
    private fun accept(scope: CoroutineScope, server: LocalServerSocket, name: String): LocalSocket? {
        val waker = scope.launch(Dispatchers.IO) {
            delay(ACCEPT_MILLIS)
            wake(name)
        }
        val socket = server.accept()
        waker.cancel()
        if (socket.peerCredentials.uid == Process.SHELL_UID) return socket
        socket.close()
        log.log(TAG, "mic helper didn't connect")
        return null
    }

    /**
     * Sends the remote's audio while any of the apps records; returns when the helper hangs up. One
     * pump at a time: a new one starts only once the last has stopped, so writes never interleave.
     */
    private fun serve(scope: CoroutineScope, socket: LocalSocket, fed: Fed, audio: AudioSource) {
        val apps = fed.apps
        val recording = mutableSetOf<Int>()
        var pump: Job? = null
        try {
            socket.inputStream.bufferedReader().forEachLine { line ->
                when (val said = HelperLine.of(line)) {
                    is HelperLine.Log -> log.log(TAG, "mic helper: ${said.text}")

                    is HelperLine.Recording -> if (speech.noted(said) && !fed.pumpsTo(said, owner?.uid)) {
                        log.log(TAG, "${apps[said.uid]} records, and is not sent the remote's mic by the pump")
                        // Not a session's, so it records by itself with no leave: the user is asked.
                        if (said.uid != owner?.uid) apps[said.uid]?.let(onHeard)
                    } else {
                        val uid = said.uid
                        val on = said.on
                        if (on) recording += uid else recording -= uid
                        val what = if (on) "records: the remote's mic to it" else "stopped recording"
                        log.log(TAG, "${apps[uid]} $what")
                        feeding = recording.isNotEmpty()
                        if (!feeding) {
                            pump?.cancel()
                        } else if (pump?.isActive != true) {
                            val last = pump
                            pump = scope.launch(Dispatchers.IO) {
                                last?.join()
                                pump(audio, socket.outputStream)
                            }
                        }
                    }

                    is HelperLine.Heard -> heard(said.uid, fed)

                    null -> Unit // not from our helper: ignored rather than fatal
                }
            }
        } catch (_: IOException) {
            // The helper went away; it unregisters its policy itself.
        } finally {
            pump?.cancel()
            feeding = false
            recordingNow = emptySet()
        }
    }

    private fun heard(uid: Int, fed: Fed) {
        val manager = context.packageManager
        val app = manager.packageOf(uid) ?: return
        when (HeardApp.of(app, uid, fed.allowed, manager.uidOf(app))) {
            HeardApp.Ask -> {
                log.log(TAG, "$app records by itself, with no leave to hear the remote")
                onHeard(app)
            }

            HeardApp.Restart -> {
                log.log(TAG, "$app is allowed, but came after the mic helper started: starting it again")
                restart()
            }

            HeardApp.Ignore -> Unit
        }
    }

    // A resilience boundary: a mic failure ends this recording's audio, not the helper.
    @Suppress("TooGenericExceptionCaught")
    private suspend fun CoroutineScope.pump(audio: AudioSource, out: OutputStream) {
        val sent = Sent()
        try {
            audio.open().use { stream ->
                val buffer = ByteArray(Pcm.FRAME_BYTES)
                while (isActive) {
                    val n = stream.read(buffer)
                    when {
                        n < 0 -> return

                        n == 0 -> delay(POLL_MILLIS)

                        else -> {
                            synchronized(writing) { out.write(buffer, 0, n) }
                            sent.add(buffer, n)
                        }
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log.log(TAG, "remote's mic for an app: $e")
        } finally {
            log.log(TAG, "sent the app $sent")
        }
    }

    /** What an app was sent, for the log: tells a silent remote from an app that didn't hear it. */
    private class Sent {
        private var bytes = 0L
        private var loudest: Double? = null

        fun add(pcm: ByteArray, length: Int) {
            bytes += length
            val db = Pcm.levelDb(pcm, length) ?: return
            loudest = maxOf(loudest ?: db, db)
        }

        override fun toString(): String {
            val level = loudest?.let { "loudest ${it.toInt()} dB" } ?: "all silence"
            return "${Pcm.durationMillis(bytes)} ms of the remote's mic, $level"
        }
    }

    /** Connects to Lumbina's own socket: wakes an accept that would wait for nobody. */
    private fun wake(name: String) {
        runCatching { LocalSocket().use { it.connect(LocalSocketAddress(name)) } }
    }

    /**
     * What a helper was started with: the [apps] it feeds, by uid, out of the [allowed] ones; those
     * of them the user gave [leave] to hear the remote whenever they record.
     */
    class Fed(val apps: Map<Int, String>, val allowed: Set<String>, val leave: Set<String>) {
        /**
         * Whether the pump answers this line. Not when the app starts recording for a session of
         * Lumbina's own ([owned]), which sends the audio itself; nor when it has a feed only as
         * Lumbina's recognizer and records by itself.
         */
        fun pumpsTo(said: HelperLine.Recording, owned: Int?): Boolean =
            !said.on || (said.uid != owned && apps[said.uid] in leave)
    }

    private companion object {
        const val TAG = "voice"

        const val POLL_MILLIS = 10L
        const val ACCEPT_MILLIS = 10_000L
        const val RETRY_MIN_MILLIS = 5_000L
        const val RETRY_MAX_MILLIS = 300_000L
        const val SETTLE_MILLIS = 1_000L

        /** A helper that stayed this long was working: the next one starts without a long wait. */
        const val HEALTHY_MILLIS = 60_000L

        /** Makes each start's socket name new within the process. */
        val starts = AtomicInteger()
    }
}

/** Null for a uid shared by several apps, or one Lumbina can't see. */
private fun PackageManager.packageOf(uid: Int): String? = getPackagesForUid(uid)?.singleOrNull()

/** Those of the [allowed] apps that are installed, by uid. */
private fun PackageManager.installed(allowed: Set<String>): Map<Int, String> =
    allowed.mapNotNull { app -> uidOf(app)?.let { it to app } }.toMap()

private fun PackageManager.uidOf(app: String): Int? = try {
    getPackageUid(app, 0)
} catch (_: PackageManager.NameNotFoundException) {
    null
}

/** What to do about an app the helper heard recording, which it doesn't feed. */
internal enum class HeardApp {
    /** The user hasn't allowed it: it is asked about. */
    Ask,

    /** Allowed, but installed (or installed again) after the helper started: a new helper feeds it. */
    Restart,

    /** Allowed, and still not the app Lumbina would feed (another user's copy): a restart would change nothing. */
    Ignore,
    ;

    companion object {
        /** [uidNow] is the uid [app] has for Lumbina now, null when it can't be told. */
        fun of(app: String, uid: Int, allowed: Set<String>, uidNow: Int?): HeardApp = when {
            app !in allowed -> Ask
            uidNow == uid -> Restart
            else -> Ignore
        }
    }
}

/**
 * A line from the helper ([MicInjector]): something to log, an allowed app starting or stopping to
 * record, or another app heard recording.
 */
internal sealed interface HelperLine {
    data class Log(val text: String) : HelperLine

    data class Recording(val uid: Int, val on: Boolean) : HelperLine

    data class Heard(val uid: Int) : HelperLine

    companion object {
        /** Null for anything that is none of the three. */
        fun of(line: String): HelperLine? {
            val words = line.split(' ', limit = 3)
            return when (words.first()) {
                "log" -> Log(line.removePrefix("log "))

                "rec" -> {
                    val uid = words.getOrNull(1)?.toIntOrNull()
                    val state = words.getOrNull(2)
                    if (uid == null || (state != "0" && state != "1")) null else Recording(uid, state == "1")
                }

                "heard" -> words.getOrNull(1)?.toIntOrNull()?.takeIf { words.size == 2 }?.let(::Heard)

                else -> null
            }
        }
    }
}
