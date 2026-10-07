package dev.lumbina.tv.core.domain.speech

import dev.lumbina.tv.core.testing.FakeAudioSource
import dev.lumbina.tv.core.testing.FakeSpeechEngine
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RunVoiceSessionTest {

    @Test
    fun `recognizes the utterance and picks the more confident language`() = runTest {
        val ru = Hypothesis("ru", "полицейский с рублёвки", 1.0)
        val en = Hypothesis("en", "wait to get rid of key", 0.7)
        val engine = FakeSpeechEngine(listOf(ru, en))
        // 32000 bytes = 1 s of audio, delivered in odd-sized chunks.
        val source = FakeAudioSource(listOf(ByteArray(16_001), ByteArray(15_999)))
        val session = RunVoiceSession(
            capture = PcmCapture(source, now = { currentTime }),
            engine = engine,
            now = { currentTime },
            captureDispatcher = StandardTestDispatcher(testScheduler),
        )

        var held = true
        val events = session { held.also { held = false } }.toList()

        val final = events.last() as VoiceEvent.Final
        assertEquals(ru, final.result)
        assertEquals(1_000, final.stats.audioMillis)
        assertTrue(engine.fed.all { it % 2 == 0 }, "only whole samples reach the engine: ${engine.fed}")
        assertEquals(32_000, engine.fed.sum())
        assertTrue(engine.closed)
        assertTrue(events.first() is VoiceEvent.Partial)
    }

    @Test
    fun `silence gives an empty result`() = runTest {
        val engine = FakeSpeechEngine(listOf(Hypothesis("ru", "", 0.0)))
        val session = RunVoiceSession(
            capture = PcmCapture(FakeAudioSource(emptyList()), now = { currentTime }),
            engine = engine,
            now = { currentTime },
            captureDispatcher = StandardTestDispatcher(testScheduler),
        )
        val final = session { false }.toList().last() as VoiceEvent.Final
        assertEquals(null, final.result)
    }

    @Test
    fun `tells how loud the audio was, and that none came`() = runTest {
        suspend fun loudest(vararg chunks: ByteArray): Double? {
            val session = RunVoiceSession(
                capture = PcmCapture(FakeAudioSource(chunks.toList()), now = { currentTime }),
                engine = FakeSpeechEngine(listOf(Hypothesis("ru", "раз", 1.0))),
                now = { currentTime },
                captureDispatcher = StandardTestDispatcher(testScheduler),
            )
            return (session { false }.toList().last() as VoiceEvent.Final).stats.loudestDb
        }
        // Full scale: every sample 0x7FFF.
        val loud = ByteArray(1_280) { if (it % 2 == 0) 0xFF.toByte() else 0x7F }

        assertEquals(null, loudest())
        assertEquals(null, loudest(ByteArray(1_280)))
        assertEquals(0.0, loudest(ByteArray(1_280), loud)!!, 0.01)
        // A tenth of full scale is 20 dB below it; the loudest chunk counts wherever it comes.
        val quiet = ByteArray(1_280) { if (it % 2 == 0) 0xCC.toByte() else 0x0C }
        assertEquals(-20.0, loudest(quiet)!!, 0.1)
        assertEquals(0.0, loudest(quiet, loud, quiet)!!, 0.01)
    }

    @Test
    fun `hands every chunk read to onAudio`() = runTest {
        val heard = mutableListOf<Int>()
        val session = RunVoiceSession(
            capture = PcmCapture(FakeAudioSource(listOf(ByteArray(1_280), ByteArray(641))), now = { currentTime }),
            engine = FakeSpeechEngine(listOf(Hypothesis("ru", "раз", 1.0))),
            now = { currentTime },
            captureDispatcher = StandardTestDispatcher(testScheduler),
        )
        session(onAudio = { heard += it.size }, isHeld = { false }).toList()
        assertEquals(listOf(1_280, 641), heard)
    }

    @Test
    fun `audio without speech is not recognized`() = runTest {
        val engine = FakeSpeechEngine(listOf(Hypothesis("ru", "продолжение следует", 0.9)))
        val session = RunVoiceSession(
            capture = PcmCapture(FakeAudioSource(listOf(ByteArray(3_200))), now = { currentTime }),
            engine = engine,
            now = { currentTime },
            captureDispatcher = StandardTestDispatcher(testScheduler),
        )
        val final = session(isHeld = { false }, heardSpeech = { false }).toList().last() as VoiceEvent.Final
        assertEquals(null, final.result)
        assertTrue(final.stats.hypotheses.isEmpty())
        assertTrue(engine.closed)
    }

    @Test
    fun `hands-free, speech is told of once and recognized, silence is not`() = runTest {
        suspend fun listen(audio: ByteArray, onSpeech: () -> Unit): VoiceEvent.Final {
            val session = RunVoiceSession(
                capture = PcmCapture(FakeAudioSource(listOf(audio)), now = { currentTime }),
                engine = FakeSpeechEngine(listOf(Hypothesis("ru", "раз", 1.0))),
                now = { currentTime },
                captureDispatcher = StandardTestDispatcher(testScheduler),
            )
            val endpoint = EndOfSpeech(now = { currentTime })
            return session.untilSilence(endpoint, wanted = {
                false
            }, onSpeech = onSpeech).toList().last() as VoiceEvent.Final
        }
        var spoke = 0
        // 0.4 s of full-scale samples, then of zeros.
        val loud = ByteArray(12_800) { if (it % 2 == 1) 0x70 else 0 }

        assertEquals("раз", listen(loud) { spoke++ }.result?.text)
        assertEquals(1, spoke)
        assertEquals(null, listen(ByteArray(12_800)) { spoke++ }.result)
        assertEquals(1, spoke)
    }

    @Test
    fun `listens before the recognizer is ready, so a slow model load loses no speech`() = runBlocking {
        val spoken = FakeAudioSource(listOf(ByteArray(6_400), ByteArray(6_400)))
        val captured = AtomicInteger()
        val source = object : AudioSource {
            override fun open(): AudioStream {
                val stream = spoken.open()
                return object : AudioStream by stream {
                    override fun read(dst: ByteArray): Int =
                        stream.read(dst).also { if (it > 0) captured.addAndGet(it) }
                }
            }
        }
        val fake = FakeSpeechEngine(listOf(Hypothesis("ru", "котики", 1.0)))
        var capturedAtSessionStart = -1
        val engine = object : SpeechEngine by fake {
            override fun newSession(languages: List<String>): SpeechSession {
                // Loading a model: the whole phrase is said meanwhile.
                Thread.sleep(LOAD_MILLIS)
                capturedAtSessionStart = captured.get()
                return fake.newSession(languages)
            }
        }
        val session = RunVoiceSession(
            capture = PcmCapture(source, now = System::currentTimeMillis),
            engine = engine,
            now = System::currentTimeMillis,
            captureDispatcher = Dispatchers.IO,
        )

        var held = true
        val final = session { held.also { held = false } }.toList().last() as VoiceEvent.Final

        assertEquals(12_800, capturedAtSessionStart)
        assertEquals(12_800, fake.fed.sum())
        assertEquals("котики", final.result?.text)
    }

    private companion object {
        const val LOAD_MILLIS = 300L
    }
}
