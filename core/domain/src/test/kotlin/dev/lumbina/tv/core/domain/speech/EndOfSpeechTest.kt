package dev.lumbina.tv.core.domain.speech

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class EndOfSpeechTest {

    private var time = 0L
    private val endpoint =
        EndOfSpeech(now = { time }, silenceMillis = 1_000, noSpeechMillis = 8_000, maxMillis = 15_000)

    /** [millis] of a square wave at [amplitude], in 40 ms frames as the remote sends them. */
    private fun hear(millis: Long, amplitude: Int) {
        repeat((millis / FRAME_MILLIS).toInt()) {
            val frame = ByteArray(FRAME_BYTES)
            for (i in 0 until FRAME_BYTES / 2) {
                val s = if (i % 2 == 0) amplitude else -amplitude
                frame[2 * i] = s.toByte()
                frame[2 * i + 1] = (s shr 8).toByte()
            }
            endpoint.hear(frame, frame.size)
            time += FRAME_MILLIS
        }
    }

    @Test
    fun `stops after speech and a second of quiet`() {
        hear(500, NOISE)
        hear(1_200, SPEECH)
        hear(300, NOISE) // a pause between words
        hear(800, SPEECH)
        hear(960, NOISE)
        assertTrue(endpoint.heardSpeech)
        assertTrue(endpoint.listening())
        hear(80, NOISE)
        assertFalse(endpoint.listening())
    }

    @Test
    fun `quiet speech counts when it is well above the room`() {
        hear(500, NOISE) // about -55 dBFS
        hear(400, QUIET_SPEECH)
        assertTrue(endpoint.heardSpeech)
    }

    @Test
    fun `quiet speech from the first frame is missed, as there is no floor yet`() {
        hear(400, QUIET_SPEECH)
        assertFalse(endpoint.heardSpeech)
    }

    @Test
    fun `speech from the first frame counts when it is loud`() {
        hear(600, SPEECH)
        assertTrue(endpoint.heardSpeech)
    }

    @Test
    fun `a key click is not speech`() {
        hear(400, NOISE)
        hear(80, SPEECH)
        hear(2_000, NOISE)
        assertFalse(endpoint.heardSpeech)
        assertTrue(endpoint.listening())
    }

    @Test
    fun `zeros are not quiet`() {
        hear(1_000, SPEECH)
        hear(3_000, 0) // the remote stopped sending audio: no reason to end
        assertTrue(endpoint.listening())
    }

    @Test
    fun `gives up without speech`() {
        hear(7_960, NOISE)
        assertTrue(endpoint.listening())
        hear(40, NOISE)
        assertFalse(endpoint.listening())
    }

    @Test
    fun `stops at the limit while speech goes on`() {
        hear(14_960, SPEECH)
        assertTrue(endpoint.listening())
        hear(40, SPEECH)
        assertFalse(endpoint.listening())
    }

    private companion object {
        const val FRAME_MILLIS = 40L
        const val FRAME_BYTES = 1_280
        const val NOISE = 60 // about -55 dBFS
        const val SPEECH = 3_000 // about -21 dBFS: over EndOfSpeech's loud mark (-30)
        const val QUIET_SPEECH = 400 // about -38 dBFS: under the loud mark, 15 dB and more over NOISE
    }
}
