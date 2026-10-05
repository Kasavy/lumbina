package dev.lumbina.tv.platform.adb

import java.io.DataInputStream
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * One adb protocol message: a 24-byte little-endian header (command, two arguments, payload
 * length, payload checksum, command xor all ones) and the payload.
 */
internal class AdbMessage(val command: Int, val arg0: Int, val arg1: Int, val data: ByteArray = ByteArray(0)) {

    fun encode(): ByteArray = ByteBuffer.allocate(HEADER_SIZE + data.size).order(ByteOrder.LITTLE_ENDIAN)
        .putInt(command)
        .putInt(arg0)
        .putInt(arg1)
        .putInt(data.size)
        .putInt(checksum(data))
        .putInt(command.inv())
        .put(data)
        .array()

    override fun toString(): String = "${name(command)}($arg0, $arg1, ${data.size} bytes)"

    companion object {
        const val HEADER_SIZE = 24
        const val CNXN = 0x4e584e43
        const val AUTH = 0x48545541
        const val OPEN = 0x4e45504f
        const val OKAY = 0x59414b4f
        const val CLSE = 0x45534c43
        const val WRTE = 0x45545257

        /** The version whose peers still check [checksum]; newer adbd accepts it too. */
        const val VERSION = 0x01000000
        const val MAX_DATA = 256 * 1024

        const val AUTH_TOKEN = 1
        const val AUTH_SIGNATURE = 2
        const val AUTH_RSA_PUBLIC_KEY = 3

        private const val BYTE_MASK = 0xff
        private const val MAX_PAYLOAD = 1024 * 1024

        fun checksum(data: ByteArray): Int = data.sumOf { it.toInt() and BYTE_MASK }

        fun read(input: InputStream): AdbMessage {
            val stream = DataInputStream(input)
            val header = ByteArray(HEADER_SIZE).also(stream::readFully)
            val buffer = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
            val command = buffer.int
            val arg0 = buffer.int
            val arg1 = buffer.int
            val length = buffer.int
            buffer.int // checksum: TCP already guards the bytes
            if (buffer.int != command.inv() || length !in 0..MAX_PAYLOAD) throw IOException("bad adb header")
            return AdbMessage(command, arg0, arg1, ByteArray(length).also(stream::readFully))
        }

        private fun name(command: Int): String = String(
            ByteBuffer.allocate(Int.SIZE_BYTES).order(ByteOrder.LITTLE_ENDIAN).putInt(command).array(),
            Charsets.US_ASCII,
        )
    }
}
