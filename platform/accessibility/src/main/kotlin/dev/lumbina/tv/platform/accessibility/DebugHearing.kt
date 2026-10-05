package dev.lumbina.tv.platform.accessibility

import dev.lumbina.tv.core.domain.speech.AudioSource
import dev.lumbina.tv.core.domain.speech.AudioStream
import dev.lumbina.tv.core.domain.speech.SpeechSetups
import dev.lumbina.tv.core.domain.speech.VoiceEvent
import java.io.DataInputStream
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/** Debug builds ([DebugCommands]): recognizes a recording as if it was spoken, by the chosen provider. */
internal class DebugHearing(
    private val setups: SpeechSetups,
    private val readiness: VoiceReadiness,
    private val sessions: VoiceSessions,
) {
    /** A 16 kHz mono 16-bit WAV, with the enabled languages; null when nothing was heard. */
    suspend fun hear(file: File): String? {
        val setup = setups.setups().first()
        check(readiness.prepare(setup)) { "voice isn't ready for $setup" }
        val session = sessions.recording(WavFile(file))
        // Off the main thread, as voice input collects it: a cloud recognizer's finish is a request.
        val final = withContext(Dispatchers.IO) { session { false }.filterIsInstance<VoiceEvent.Final>().first() }
        return final.result?.text
    }
}

/** A WAV file's samples as a mic that ends; the format is taken on trust. */
private class WavFile(private val file: File) : AudioSource {
    override fun open(): AudioStream {
        val input = DataInputStream(file.inputStream().buffered())
        input.skipBytes(RIFF_HEADER_BYTES)
        // Chunks until "data": a tag, then a little-endian length.
        while (true) {
            val tag = ByteArray(TAG_BYTES).also(input::readFully).decodeToString()
            val length = Integer.reverseBytes(input.readInt())
            if (tag == "data") break
            input.skipBytes(length + length % 2)
        }
        return object : AudioStream {
            override fun read(dst: ByteArray): Int = input.read(dst)

            override fun close() = input.close()
        }
    }

    private companion object {
        const val RIFF_HEADER_BYTES = 12
        const val TAG_BYTES = 4
    }
}
