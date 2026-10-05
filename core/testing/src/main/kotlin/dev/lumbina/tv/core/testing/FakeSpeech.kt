package dev.lumbina.tv.core.testing

import dev.lumbina.tv.core.domain.speech.AudioSource
import dev.lumbina.tv.core.domain.speech.AudioStream
import dev.lumbina.tv.core.domain.speech.Hypothesis
import dev.lumbina.tv.core.domain.speech.SpeechEngine
import dev.lumbina.tv.core.domain.speech.SpeechSession

/**
 * Plays back scripted reads: each entry is one `read()` result — bytes, or an empty
 * array for "nothing yet". After the script ends, reads return 0 (nothing yet) forever.
 */
class FakeAudioSource(private val script: List<ByteArray>) : AudioSource {
    var opened = 0
        private set
    var closed = 0
        private set

    override fun open(): AudioStream {
        opened++
        val reads = ArrayDeque(script)
        return object : AudioStream {
            override fun read(dst: ByteArray): Int {
                val next = reads.removeFirstOrNull() ?: return 0
                // Like a socket: a read returns at most dst.size; the rest comes next time.
                val n = minOf(next.size, dst.size)
                next.copyInto(dst, endIndex = n)
                if (n < next.size) reads.addFirst(next.copyOfRange(n, next.size))
                return n
            }

            override fun close() {
                closed++
            }
        }
    }
}

/** Records fed audio and returns fixed hypotheses; models of [installed] languages can be prepared. */
class FakeSpeechEngine(
    private val hypotheses: List<Hypothesis>,
    private val installed: List<String> = hypotheses.map { it.language },
) : SpeechEngine {
    override var languages: List<String> = installed
        private set

    /** Every language list [prepare] was asked for, in order. */
    val prepared = mutableListOf<List<String>>()
    val fed = mutableListOf<Int>()
    var closed = false
        private set

    override fun prepare(languages: List<String>): Boolean {
        prepared += languages
        this.languages = languages.filter { it in installed }
        return this.languages.isNotEmpty()
    }

    override fun newSession(languages: List<String>): SpeechSession = object : SpeechSession {
        override fun accept(pcm: ByteArray, length: Int) {
            fed += length
        }

        override fun partial(): String = "…${fed.sum()}"

        override fun finish(): List<Hypothesis> = hypotheses.filter { it.language in languages }

        override fun close() {
            closed = true
        }
    }
}
