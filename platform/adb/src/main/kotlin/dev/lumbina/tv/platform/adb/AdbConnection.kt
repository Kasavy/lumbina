package dev.lumbina.tv.platform.adb

import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket

/**
 * One adb connection over TCP (blocking; call off the main thread). [open] does the handshake:
 * the device challenges with a token and Lumbina signs it; if the device doesn't know the key,
 * sending the public key makes it show its "Allow debugging?" prompt.
 */
internal class AdbConnection private constructor(
    private val socket: Socket,
    private val input: InputStream,
    private val output: OutputStream,
) : Closeable {

    private var nextLocalId = 1

    /** The most the device takes in one message; it says so when the connection is made. */
    private var maxData = AdbMessage.MAX_DATA

    /** Runs [command] in `shell:` and returns everything it printed. */
    fun shell(command: String, timeoutMillis: Int): String = run("shell:$command", null, timeoutMillis)

    /**
     * Runs [command] in `exec:` (its input and output are raw bytes) with [input] as its input, and
     * returns everything it printed. The command ends the stream when it has read what it needs.
     */
    fun exec(command: String, input: ByteArray, timeoutMillis: Int): String = run("exec:$command", input, timeoutMillis)

    /**
     * Opens [service] and talks to it until the device closes the stream. The device allows one
     * message of ours at a time: each piece of [input] goes after its answer to the one before.
     */
    private fun run(service: String, input: ByteArray?, timeoutMillis: Int): String {
        socket.soTimeout = timeoutMillis
        val localId = nextLocalId++
        send(AdbMessage(AdbMessage.OPEN, localId, 0, "$service\u0000".toByteArray()))
        val out = ByteArrayOutputStream()
        var sent = 0
        while (true) {
            val message = AdbMessage.read(input = this.input)
            when (message.command) {
                AdbMessage.WRTE -> {
                    out.write(message.data)
                    send(AdbMessage(AdbMessage.OKAY, localId, message.arg0))
                }

                AdbMessage.CLSE -> {
                    send(AdbMessage(AdbMessage.CLSE, localId, message.arg0))
                    return out.toString(Charsets.UTF_8.name())
                }

                AdbMessage.OKAY -> if (input != null && sent < input.size) {
                    val piece = input.copyOfRange(sent, minOf(sent + maxData, input.size))
                    sent += piece.size
                    send(AdbMessage(AdbMessage.WRTE, localId, message.arg0, piece))
                }

                else -> throw IOException("unexpected $message")
            }
        }
    }

    override fun close() {
        runCatching { socket.close() }
    }

    private fun send(message: AdbMessage) {
        output.write(message.encode())
        output.flush()
    }

    /** True once connected; false when the key isn't allowed (and [offerKey] is off, or the user said no). */
    private fun handshake(key: AdbKey, offerKey: Boolean, promptMillis: Int): Boolean {
        send(AdbMessage(AdbMessage.CNXN, AdbMessage.VERSION, AdbMessage.MAX_DATA, HOST_BANNER.toByteArray()))
        var signed = false
        var offered = false
        while (true) {
            val message = try {
                AdbMessage.read(input)
            } catch (e: IOException) {
                // After the key was offered, a closed or silent connection means "not allowed".
                if (offered) return false else throw e
            }
            when {
                message.command == AdbMessage.CNXN -> {
                    if (message.arg1 in 1 until AdbMessage.MAX_DATA) maxData = message.arg1
                    return true
                }

                message.command != AdbMessage.AUTH || message.arg0 != AdbMessage.AUTH_TOKEN -> Unit

                !signed -> {
                    signed = true
                    send(AdbMessage(AdbMessage.AUTH, AdbMessage.AUTH_SIGNATURE, 0, key.sign(message.data)))
                }

                !offerKey || offered -> return false

                else -> {
                    // The device shows its prompt now and answers with CNXN once the user allows.
                    offered = true
                    socket.soTimeout = promptMillis
                    send(AdbMessage(AdbMessage.AUTH, AdbMessage.AUTH_RSA_PUBLIC_KEY, 0, key.publicKeyLine(KEY_NAME)))
                }
            }
        }
    }

    companion object {
        private const val CONNECT_TIMEOUT_MILLIS = 2_000
        private const val READ_TIMEOUT_MILLIS = 5_000
        private const val HOST_BANNER = "host::lumbina\u0000"
        private const val KEY_NAME = "lumbina@dev.lumbina.tv"

        /**
         * Connects to adbd at [host]:[port]; null when the key isn't allowed. Throws IOException
         * when nothing listens (debugging off). With [offerKey], waits up to [promptMillis] for the
         * user to answer the device's prompt.
         */
        fun open(host: String, port: Int, key: AdbKey, offerKey: Boolean, promptMillis: Int): AdbConnection? {
            val socket = Socket()
            try {
                socket.connect(InetSocketAddress(host, port), CONNECT_TIMEOUT_MILLIS)
                socket.soTimeout = READ_TIMEOUT_MILLIS
                val connection = AdbConnection(socket, socket.getInputStream(), socket.getOutputStream())
                if (connection.handshake(key, offerKey, promptMillis)) return connection
            } catch (e: IOException) {
                socket.close()
                throw e
            }
            socket.close()
            return null
        }
    }
}
