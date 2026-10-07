package dev.lumbina.tv.vendor.hisense

import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket

/**
 * [PcmShareTransport] over the loopback port where HsPcmShare serves the same protocol as on its
 * local socket (seen on a Vidda projector with Android 14). Only [PcmShareProbe] asks there, to
 * tell whether firmware without the socket has the port.
 */
internal class TcpTransport private constructor(private val socket: Socket) : PcmShareTransport {

    private val output = socket.getOutputStream()
    private val input = socket.getInputStream()

    override fun send(command: String) {
        output.write(command.toByteArray())
        output.flush()
    }

    override fun receive(dst: ByteArray): Int = input.read(dst)

    override fun close() {
        runCatching { socket.close() }
    }

    companion object {
        const val PORT = 9091

        fun connect(timeoutMillis: Int): TcpTransport {
            val socket = Socket()
            try {
                socket.connect(InetSocketAddress("127.0.0.1", PORT), timeoutMillis)
                socket.soTimeout = timeoutMillis
            } catch (e: IOException) {
                runCatching { socket.close() }
                throw e
            }
            return TcpTransport(socket)
        }
    }
}
