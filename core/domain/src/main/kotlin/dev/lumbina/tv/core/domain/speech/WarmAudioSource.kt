package dev.lumbina.tv.core.domain.speech

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Opens [upstream] when a capture starts and keeps reading it for [idleMillis] after the
 * last capture, so follow-up captures start without the connection handshake and include
 * audio from just before they were opened ([prerollMillis]).
 *
 * Needed for a remote whose mic is exposed by the firmware through a service that only starts
 * pulling the remote's audio once a client is connected, so opening on key down loses the
 * first syllable. Staying connected for good would keep the remote's mic on (its light blinks
 * and the battery drains), hence the idle timeout. Reconnects after errors while wanted.
 * Captures read from memory, so their reads never block, and each open capture gets every
 * chunk: the remote has room for one reader, but voice input and an app being fed its audio may
 * listen at once. [onWake] reports how long the first audio took after connecting.
 */
class WarmAudioSource(
    private val upstream: AudioSource,
    private val now: () -> Long,
    private val onError: (Exception) -> Unit = {},
    private val onWake: (firstAudioMillis: Long) -> Unit = {},
    private val prerollMillis: Long = 1_500,
    private val idleMillis: Long = 30_000,
    private val retryMillis: Long = 2_000,
    private val pollMillis: Long = 20,
    private val chunkBytes: Int = Pcm.CHUNK_BYTES,
) : AudioSource {

    private class Chunk(val bytes: ByteArray, val at: Long)

    private val lock = Any()
    private val recent = ArrayDeque<Chunk>() // pre-roll, trimmed to prerollMillis
    private val captures = mutableListOf<ArrayDeque<ByteArray>>()
    private var lastUse = Long.MIN_VALUE / 2
    private var wokeAt: Long? = null
    private var job: Job? = null
    private var scope: CoroutineScope? = null
    private var dispatcher: CoroutineDispatcher? = null

    /** Where to read (blocking I/O) once a capture asks for audio. Nothing is opened yet. */
    fun attach(scope: CoroutineScope, dispatcher: CoroutineDispatcher) = synchronized(lock) {
        this.scope = scope
        this.dispatcher = dispatcher
    }

    fun stop() = synchronized(lock) {
        job?.cancel()
        job = null
    }

    override fun open(): AudioStream = synchronized(lock) {
        val pending = ArrayDeque<ByteArray>()
        val t = now()
        recent.filter { t - it.at <= prerollMillis }.forEach { pending.addLast(it.bytes) }
        recent.clear() // don't replay the same audio into the next capture
        captures += pending
        lastUse = t
        wake()
        CaptureStream(pending)
    }

    private fun wake() {
        if (job?.isActive == true) return
        val scope = checkNotNull(scope) { "attach() first" }
        wokeAt = now()
        job = scope.launch(checkNotNull(dispatcher)) { pump() }
    }

    /** Connected while a capture runs or one ended less than [idleMillis] ago. */
    private fun wanted(): Boolean = synchronized(lock) { captures.isNotEmpty() || now() - lastUse < idleMillis }

    // A resilience boundary: whatever the source throws, keep reconnecting while wanted.
    @Suppress("TooGenericExceptionCaught")
    private suspend fun CoroutineScope.pump() {
        val buf = ByteArray(chunkBytes)
        while (isActive && wanted()) {
            try {
                upstream.open().use { stream -> readWhileWanted(stream, buf) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                onError(e)
            }
            if (wanted()) delay(retryMillis)
        }
        val self = coroutineContext[Job]
        synchronized(lock) {
            if (job !== self) return
            job = null
            // A capture that opened while this loop was ending saw it still running: start anew.
            if (wanted()) wake() else recent.clear()
        }
    }

    private suspend fun CoroutineScope.readWhileWanted(stream: AudioStream, buf: ByteArray) {
        var ended = false
        while (isActive && !ended && wanted()) {
            val n = stream.read(buf)
            // On "nothing yet" wait a little: non-blocking sources must not spin.
            when {
                n < 0 -> ended = true
                n == 0 -> delay(pollMillis)
                else -> accept(buf.copyOf(n))
            }
        }
    }

    private fun accept(bytes: ByteArray) {
        val woke = synchronized(lock) {
            val t = now()
            recent.addLast(Chunk(bytes, t))
            while (recent.isNotEmpty() && t - recent.first().at > prerollMillis) recent.removeFirst()
            captures.forEach { it.addLast(bytes) }
            wokeAt?.let { t - it }.also { wokeAt = null }
        }
        woke?.let(onWake)
    }

    private inner class CaptureStream(private val pending: ArrayDeque<ByteArray>) : AudioStream {
        override fun read(dst: ByteArray): Int = synchronized(lock) {
            val next = pending.removeFirstOrNull() ?: return 0
            val n = minOf(next.size, dst.size)
            next.copyInto(dst, endIndex = n)
            if (n < next.size) pending.addFirst(next.copyOfRange(n, next.size))
            n
        }

        override fun close() = synchronized(lock) {
            // By identity: two empty queues are equal, and the other capture's must stay.
            if (captures.removeAll { it === pending }) {
                // What this capture heard must not open the next one; only audio after this point may.
                recent.clear()
            }
            lastUse = now()
        }
    }
}
