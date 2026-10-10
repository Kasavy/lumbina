package dev.lumbina.tv.vendor.hisense

import dev.lumbina.tv.core.domain.speech.AudioSource
import dev.lumbina.tv.core.domain.speech.AudioStream
import java.io.IOException

/**
 * Protocol of Hisense's HsPcmShare service (`com.hisense.pcm.share`), which decodes the
 * Nanosic remote's microphone and serves 16 kHz mono 16-bit PCM on the abstract local
 * socket [SOCKET]. Reconstructed from the service; one recording session per connection:
 *
 * ```
 * start_pcmrecorder_v_0#<user>#0#4        -> "OK#<clientId>#"   (4 = Nanosic remote)
 * get_pcmdata_v_0#<maxBytes>#<clientId>   -> PCM bytes, or a single 0 byte when none yet
 * stop_pcmrecorder_v_0#<clientId>#4       -> "OK"
 * get_pcminfo_v_0#0#4#                    -> "#SAMPLERATE:16000#BUFFERSIZE:1280#", no recorder needed
 * ```
 *
 * The service on a Vidda projector with Android 14 takes "start" and "stop" with a closing '#'
 * as well, and 16000 where the start has 0 ([startRated]); it serves the same on a loopback port
 * ([TcpTransport]).
 */
internal object PcmShareProtocol {
    const val PACKAGE = "com.hisense.pcm.share"
    const val SOCKET = "GETSYSTEMPCMDATALOCAL"

    /** Who is recording, as the service is told. */
    const val USER = "lumbina"

    /**
     * The most to ask for in one read: one frame of the service (40 ms). It keeps only six frames
     * for a client and drops what doesn't fit, so asking for 200 ms at a time loses a second or
     * more of audio every few seconds (measured on the Vidda projector).
     */
    const val FRAME_BYTES = 1_280
    private const val SOURCE_NANO_REMOTE = 4

    fun start(user: String = USER) = "start_pcmrecorder_v_0#$user#0#$SOURCE_NANO_REMOTE"

    /** The other spelling of "start"; [PcmShareProbe] asks with both, for firmware that takes only one. */
    fun startRated(user: String = USER) = "start_pcmrecorder_v_0#$user#16000#$SOURCE_NANO_REMOTE#"

    fun info() = "get_pcminfo_v_0#0#$SOURCE_NANO_REMOTE#"

    /** The client id from a start reply; throws if the service refused. */
    fun clientId(reply: String): String {
        val parts = reply.split('#')
        if (parts.size < 2 || parts[0] != "OK" || parts[1].isEmpty()) {
            throw IOException("HsPcmShare refused: \"$reply\"")
        }
        return parts[1]
    }

    fun read(clientId: String, maxBytes: Int) = "get_pcmdata_v_0#$maxBytes#$clientId"

    fun stop(clientId: String) = "stop_pcmrecorder_v_0#$clientId#$SOURCE_NANO_REMOTE"

    /** "Stop" for a recorder started with [startRated]. */
    fun stopRated(clientId: String) = stop(clientId) + "#"

    /** The service answers a single zero byte when no audio is buffered yet. */
    fun isNoData(bytes: ByteArray, count: Int) = count == 1 && bytes[0] == 0.toByte()
}

/** A connection to the HsPcmShare socket; replies arrive as raw bytes. */
internal interface PcmShareTransport : AutoCloseable {
    fun send(command: String)

    /** Blocking read of whatever the service sent; -1 when the connection closed. */
    fun receive(dst: ByteArray): Int
}

/**
 * Remote-mic audio via HsPcmShare. While a stream is open, what [inUse] gave is held: it tells
 * Lumbina's other processes that the remote is being read (see [MicInUse]).
 */
internal class PcmShareAudioSource(
    private val inUse: () -> AutoCloseable = { AutoCloseable {} },
    private val connect: () -> PcmShareTransport,
) : AudioSource {

    override fun open(): AudioStream {
        val transport = connect()
        try {
            transport.send(PcmShareProtocol.start())
            val reply = ByteArray(REPLY_BYTES)
            val n = transport.receive(reply)
            if (n <= 0) throw IOException("HsPcmShare closed the connection")
            return PcmShareStream(transport, PcmShareProtocol.clientId(String(reply, 0, n)), inUse())
        } catch (e: IOException) {
            transport.close()
            throw e
        }
    }

    private companion object {
        const val REPLY_BYTES = 256
    }
}

internal class PcmShareStream(
    private val transport: PcmShareTransport,
    private val clientId: String,
    private val held: AutoCloseable,
) : AudioStream {

    override fun read(dst: ByteArray): Int {
        transport.send(PcmShareProtocol.read(clientId, minOf(dst.size, PcmShareProtocol.FRAME_BYTES)))
        val n = transport.receive(dst)
        return if (n > 0 && PcmShareProtocol.isNoData(dst, n)) 0 else n
    }

    override fun close() {
        runCatching { transport.send(PcmShareProtocol.stop(clientId)) }
        transport.close()
        runCatching { held.close() }
    }
}
