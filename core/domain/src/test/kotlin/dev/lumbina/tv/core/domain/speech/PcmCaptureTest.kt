package dev.lumbina.tv.core.domain.speech

import dev.lumbina.tv.core.testing.FakeAudioSource
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PcmCaptureTest {

    private fun TestScope.capture(source: AudioSource) = PcmCapture(
        source = source,
        now = { currentTime },
        drainIdleMillis = 300,
        drainMaxMillis = 1_500,
        maxMillis = 15_000,
        pollMillis = 10,
    )

    private fun bytes(n: Int) = ByteArray(n) { 1 }
    private val nothing = ByteArray(0)

    @Test
    fun `keeps reading after release until the source is idle`() = runTest {
        // Audio arrives with a lag: data, a gap, the tail after release, then silence.
        val source = FakeAudioSource(listOf(bytes(100), nothing, bytes(50)))
        var held = true
        val chunks = capture(source).capture {
            val h = held
            held = false // released right after the first read
            h
        }.toList()

        assertEquals(listOf(100, 50), chunks.map { it.size })
        assertTrue(currentTime in 300..400, "stopped ${currentTime}ms in, expected after ~300ms idle")
        assertEquals(1, source.closed)
    }

    @Test
    fun `a stuck button is capped`() = runTest {
        val source = FakeAudioSource(emptyList())
        capture(source).capture { true }.toList()
        assertTrue(currentTime in 15_000..15_100)
        assertEquals(1, source.closed)
    }

    @Test
    fun `aligner carries odd bytes to the next chunk`() {
        val aligner = SampleAligner()
        assertEquals(2, aligner.align(bytes(3)).size) // 1 byte waits
        assertEquals(4, aligner.align(bytes(3)).size) // 1 + 3
        assertEquals(0, aligner.align(bytes(0)).size)
    }
}
