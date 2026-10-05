package dev.lumbina.tv.speech.cloud

import dev.lumbina.tv.core.domain.speech.Pcm
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** [pcm] in the pipeline's format ([Pcm]) as a WAV file. */
internal fun wav(pcm: ByteArray): ByteArray = ByteBuffer.allocate(HEADER_BYTES + pcm.size)
    .order(ByteOrder.LITTLE_ENDIAN)
    .put("RIFF".toByteArray())
    .putInt(HEADER_BYTES - RIFF_PREAMBLE + pcm.size)
    .put("WAVEfmt ".toByteArray())
    .putInt(FMT_BYTES)
    .putShort(PCM_FORMAT)
    .putShort(CHANNELS)
    .putInt(Pcm.SAMPLE_RATE)
    .putInt(Pcm.SAMPLE_RATE * CHANNELS * Pcm.BYTES_PER_SAMPLE)
    .putShort((CHANNELS * Pcm.BYTES_PER_SAMPLE).toShort())
    .putShort(BITS_PER_SAMPLE)
    .put("data".toByteArray())
    .putInt(pcm.size)
    .put(pcm)
    .array()

private const val HEADER_BYTES = 44
private const val RIFF_PREAMBLE = 8
private const val FMT_BYTES = 16
private const val PCM_FORMAT: Short = 1
private const val CHANNELS: Short = 1
private const val BITS_PER_SAMPLE: Short = 16
