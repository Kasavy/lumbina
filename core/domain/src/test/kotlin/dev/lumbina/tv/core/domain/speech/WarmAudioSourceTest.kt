package dev.lumbina.tv.core.domain.speech

import java.io.IOException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WarmAudioSourceTest {

    /** Upstream fed by the test; each read returns the next queued chunk or 0. */
    private class LiveSource : AudioSource {
        val chunks = Channel<ByteArray>(Channel.UNLIMITED)
        var opened = 0
        var closed = 0
        var failNextOpen = false

        val connected: Boolean get() = opened - closed > 0

        override fun open(): AudioStream {
            if (failNextOpen) {
                failNextOpen = false
                throw IOException("service not up yet")
            }
            opened++
            return object : AudioStream {
                override fun read(dst: ByteArray): Int {
                    val next = chunks.tryReceive().getOrNull() ?: return 0
                    next.copyInto(dst)
                    return next.size
                }

                override fun close() {
                    closed++
                }
            }
        }
    }

    private fun TestScope.warm(
        source: LiveSource,
        errors: MutableList<Exception> = mutableListOf(),
        wakes: MutableList<Long> = mutableListOf(),
    ) = WarmAudioSource(
        source,
        now = { currentTime },
        onError = { errors += it },
        onWake = { wakes += it },
        prerollMillis = 1_000,
        idleMillis = 10_000,
    ).also { it.attach(backgroundScope, StandardTestDispatcher(testScheduler)) }

    private fun AudioStream.drain(): List<Int> {
        val sizes = mutableListOf<Int>()
        val buf = ByteArray(10_000)
        while (true) {
            val n = read(buf)
            if (n <= 0) return sizes
            sizes += n
        }
    }

    // The pump polls a non-blocking fake in a tight loop; advancing time lets it run.
    private fun TestScope.pumpFor(millis: Long) {
        advanceTimeBy(millis)
        runCurrent()
    }

    @Test
    fun `nothing is opened until a capture asks, so the remote's mic stays off`() = runTest {
        val source = LiveSource()
        warm(source)
        pumpFor(5_000)
        assertEquals(0, source.opened)
    }

    @Test
    fun `the first capture connects and reports when audio arrived`() = runTest {
        val source = LiveSource()
        val wakes = mutableListOf<Long>()
        val warm = warm(source, wakes = wakes)
        val stream = warm.open()
        pumpFor(300)
        source.chunks.trySend(ByteArray(200))
        pumpFor(50)
        assertTrue(source.connected)
        assertEquals(listOf(200), stream.drain())
        assertEquals(1, wakes.size)
        assertTrue(wakes.single() in 300L..360L)
        stream.close()
    }

    @Test
    fun `a follow-up capture starts with audio from just before it opened`() = runTest {
        val source = LiveSource()
        val warm = warm(source)
        warm.open().close()
        pumpFor(50)
        source.chunks.trySend(ByteArray(100)) // first syllable, before the key is handled
        pumpFor(50)

        val stream = warm.open()
        source.chunks.trySend(ByteArray(200)) // the rest, live
        pumpFor(50)

        assertEquals(listOf(100, 200), stream.drain())
        assertEquals(1, source.opened)
        stream.close()
    }

    @Test
    fun `captures open at once each get every chunk, and one closing leaves the other`() = runTest {
        val source = LiveSource()
        val warm = warm(source)
        val app = warm.open() // an app fed the remote's audio
        val voice = warm.open() // voice input, at the same time
        source.chunks.trySend(ByteArray(100))
        pumpFor(50)
        assertEquals(listOf(100), app.drain())
        assertEquals(listOf(100), voice.drain())

        voice.close()
        source.chunks.trySend(ByteArray(200))
        pumpFor(50)
        assertEquals(listOf(200), app.drain())
        assertEquals(1, source.opened)
        app.close()
    }

    @Test
    fun `a capture opened right after another does not start with the end of its phrase`() = runTest {
        val source = LiveSource()
        val warm = warm(source)
        val first = warm.open()
        source.chunks.trySend(ByteArray(100)) // the last word of the first phrase
        pumpFor(50)
        assertEquals(listOf(100), first.drain())
        first.close()
        pumpFor(200)

        assertTrue(warm.open().drain().isEmpty())
    }

    @Test
    fun `audio older than the pre-roll is not replayed`() = runTest {
        val source = LiveSource()
        val warm = warm(source)
        warm.open().close()
        pumpFor(50)
        source.chunks.trySend(ByteArray(100))
        pumpFor(1_500)

        assertTrue(warm.open().drain().isEmpty())
    }

    @Test
    fun `disconnects after being idle and reconnects on the next capture`() = runTest {
        val source = LiveSource()
        val warm = warm(source)
        val stream = warm.open()
        pumpFor(20_000) // a capture in progress keeps the connection however long it takes
        assertTrue(source.connected)
        stream.close()
        pumpFor(9_000)
        assertTrue(source.connected)
        pumpFor(2_000)
        assertEquals(false, source.connected)

        warm.open()
        pumpFor(50)
        assertEquals(2, source.opened)
        assertTrue(source.connected)
    }

    @Test
    fun `reconnects after the upstream fails while wanted`() = runTest {
        val source = LiveSource().apply { failNextOpen = true }
        val errors = mutableListOf<Exception>()
        val warm = warm(source, errors)
        warm.open()
        pumpFor(2_500)
        assertEquals(1, errors.size)
        assertEquals(1, source.opened)
        assertTrue(source.connected)
    }
}
