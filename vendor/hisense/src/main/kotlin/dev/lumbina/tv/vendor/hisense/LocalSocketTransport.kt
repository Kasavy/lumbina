package dev.lumbina.tv.vendor.hisense

import android.net.LocalSocket
import android.net.LocalSocketAddress
import java.io.IOException

/** [PcmShareTransport] over the abstract local socket served by HsPcmShare. */
internal class LocalSocketTransport private constructor(private val socket: LocalSocket) : PcmShareTransport {

    private val output = socket.outputStream
    private val input = socket.inputStream

    override fun send(command: String) {
        output.write(command.toByteArray())
        output.flush()
    }

    override fun receive(dst: ByteArray): Int = input.read(dst)

    override fun close() {
        runCatching { socket.close() }
    }

    companion object {
        // Longer than HsPcmShare holds an idle request before answering "no data".
        private const val READ_TIMEOUT_MILLIS = 10_000

        fun connect(
            name: String = PcmShareProtocol.SOCKET,
            readTimeoutMillis: Int = READ_TIMEOUT_MILLIS,
        ): LocalSocketTransport {
            val socket = LocalSocket()
            try {
                socket.connect(LocalSocketAddress(name, LocalSocketAddress.Namespace.ABSTRACT))
                socket.soTimeout = readTimeoutMillis
            } catch (e: IOException) {
                runCatching { socket.close() }
                throw e
            }
            return LocalSocketTransport(socket)
        }
    }
}
