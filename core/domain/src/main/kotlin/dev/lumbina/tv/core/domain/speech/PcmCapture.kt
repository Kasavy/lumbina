package dev.lumbina.tv.core.domain.speech

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Push-to-talk capture: reads audio while the button is held, then keeps draining briefly
 * after release, because a remote delivers its audio with a lag.
 *
 * Reads as fast as audio arrives; slow consumers must buffer (e.g. `buffer(UNLIMITED)`),
 * otherwise the source's own buffer may overflow and drop speech.
 */
class PcmCapture(
    private val source: AudioSource,
    private val now: () -> Long,
    private val drainIdleMillis: Long = 300,
    private val drainMaxMillis: Long = 1_500,
    private val maxMillis: Long = 15_000,
    private val pollMillis: Long = 10,
    private val chunkBytes: Int = Pcm.CHUNK_BYTES,
) {
    /** Emits PCM chunks until drained; [isHeld] reports whether the button is still down. */
    fun capture(isHeld: () -> Boolean): Flow<ByteArray> = flow {
        source.open().use { stream ->
            val startedAt = now()
            var releasedAt = 0L
            var lastDataAt = startedAt
            val buf = ByteArray(chunkBytes)
            var ended = false
            while (!ended) {
                val t = now()
                if (releasedAt == 0L && !isHeld()) releasedAt = t
                val drained = releasedAt != 0L &&
                    (t - lastDataAt > drainIdleMillis || t - releasedAt > drainMaxMillis)
                val n = if (drained || t - startedAt > maxMillis) -1 else stream.read(buf)
                when {
                    n < 0 -> ended = true

                    n == 0 -> delay(pollMillis)

                    else -> {
                        lastDataAt = t
                        emit(buf.copyOf(n))
                    }
                }
            }
        }
    }
}

/** Keeps 16-bit samples whole across chunks of arbitrary (odd) length. */
class SampleAligner {
    private var carry: ByteArray = ByteArray(0)

    /** Returns the whole samples of `carry + chunk`; an odd trailing byte waits for the next chunk. */
    fun align(chunk: ByteArray): ByteArray {
        val data = if (carry.isEmpty()) chunk else carry + chunk
        val even = data.size and 1.inv()
        carry = data.copyOfRange(even, data.size)
        return if (even == data.size) data else data.copyOf(even)
    }
}
