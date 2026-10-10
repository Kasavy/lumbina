package dev.lumbina.tv.platform.adb

import java.io.File
import java.io.InputStream
import java.math.BigInteger
import java.net.ServerSocket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.KeyPairGenerator
import java.security.interfaces.RSAPublicKey
import java.util.Base64
import javax.crypto.Cipher
import kotlin.concurrent.thread
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AdbConnectionTest {

    @get:Rule val folder = TemporaryFolder()

    @Test
    fun `messages survive encoding`() {
        val message = AdbMessage(AdbMessage.WRTE, 7, 9, "hi".toByteArray())
        val read = AdbMessage.read(message.encode().inputStream())
        assertEquals(AdbMessage.WRTE, read.command)
        assertEquals(7, read.arg0)
        assertEquals(9, read.arg1)
        assertArrayEquals("hi".toByteArray(), read.data)
    }

    @Test
    fun `public key is in adb's format`() {
        val pair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val blob = AdbKey.androidPublicKey(pair.public as RSAPublicKey)
        val buffer = ByteBuffer.wrap(blob).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(524, blob.size)
        assertEquals(64, buffer.int)
        val n0inv = buffer.int.toLong() and 0xffffffffL
        val n0 = buffer.int.toLong() and 0xffffffffL
        // n0inv is -1/n mod 2^32.
        assertEquals(0xffffffffL, (n0inv * n0) and 0xffffffffL)
        assertEquals(65537, buffer.getInt(blob.size - 4))
    }

    @Test
    fun `keys are kept`() {
        val first = AdbKey.loadOrCreate(folder.root)
        val second = AdbKey.loadOrCreate(folder.root)
        assertArrayEquals(first.publicKeyLine("x"), second.publicKeyLine("x"))
    }

    @Test
    fun `signs the token and runs a shell command`() {
        val key = AdbKey.loadOrCreate(folder.root)
        FakeAdbd(knownKey = key).use { adbd ->
            val connection = requireNotNull(AdbConnection.open("127.0.0.1", adbd.port, key, false, 1_000))
            assertEquals("echo:hello", connection.shell("hello", 1_000))
            connection.close()
        }
    }

    @Test
    fun `feeds a command its input in pieces the device takes`() {
        val key = AdbKey.loadOrCreate(folder.root)
        val bytes = ByteArray(10_000) { it.toByte() }
        FakeAdbd(knownKey = key).use { adbd ->
            val connection = requireNotNull(AdbConnection.open("127.0.0.1", adbd.port, key, false, 1_000))
            assertEquals("took:10000", connection.exec("take 10000", bytes, 1_000))
            connection.close()
            assertArrayEquals(bytes, adbd.taken)
            assertEquals(listOf(4096, 4096, 1808), adbd.pieces)
        }
    }

    @Test
    fun `an unknown key is refused without the prompt`() {
        val key = AdbKey.loadOrCreate(folder.root)
        FakeAdbd(knownKey = AdbKey.loadOrCreate(File(folder.root, "other"))).use { adbd ->
            assertNull(AdbConnection.open("127.0.0.1", adbd.port, key, false, 1_000))
        }
    }

    @Test
    fun `offering the key connects once the user allows it`() {
        val key = AdbKey.loadOrCreate(folder.root)
        FakeAdbd(knownKey = null).use { adbd ->
            assertNotNull(AdbConnection.open("127.0.0.1", adbd.port, key, true, 1_000))
            assertNotNull(adbd.offered)
        }
    }

    /**
     * One-connection adbd: checks signatures against [knownKey], accepts any offered key, echoes
     * shell, and for `exec:take N` reads N bytes of input (up to 4096 a message) before it answers.
     */
    private class FakeAdbd(private val knownKey: AdbKey?) : AutoCloseable {
        private val server = ServerSocket(0)
        val port: Int get() = server.localPort

        @Volatile var offered: RSAPublicKey? = null

        @Volatile var taken = ByteArray(0)
        val pieces = mutableListOf<Int>()

        private val worker = thread {
            runCatching { serve() }
        }

        private fun serve() {
            val socket = server.accept()
            val input = socket.getInputStream()
            val output = socket.getOutputStream()
            fun send(message: AdbMessage) = output.write(message.encode())
            assertEquals(AdbMessage.CNXN, AdbMessage.read(input).command)
            val token = ByteArray(20) { it.toByte() }
            send(AdbMessage(AdbMessage.AUTH, AdbMessage.AUTH_TOKEN, 0, token))
            val signature = AdbMessage.read(input)
            if (knownKey != null && verifies(knownKey, token, signature.data)) {
                send(AdbMessage(AdbMessage.CNXN, AdbMessage.VERSION, 4096, "device::\u0000".toByteArray()))
            } else {
                send(AdbMessage(AdbMessage.AUTH, AdbMessage.AUTH_TOKEN, 0, token))
                val offer = runCatching { AdbMessage.read(input) }.getOrNull() ?: return
                if (offer.arg0 != AdbMessage.AUTH_RSA_PUBLIC_KEY) return
                offered = parse(offer.data)
                send(AdbMessage(AdbMessage.CNXN, AdbMessage.VERSION, 4096, "device::\u0000".toByteArray()))
            }
            val open = runCatching { AdbMessage.read(input) }.getOrNull() ?: return
            val service = String(open.data).trimEnd('\u0000')
            send(AdbMessage(AdbMessage.OKAY, 99, open.arg0))
            if (service.startsWith("exec:take ")) {
                take(service.substringAfterLast(' ').toInt(), open.arg0, input, ::send)
            } else {
                echo(service.removePrefix("shell:"), open.arg0, input, ::send)
            }
        }

        private fun take(wanted: Int, id: Int, input: InputStream, send: (AdbMessage) -> Unit) {
            while (taken.size < wanted) {
                val piece = AdbMessage.read(input)
                assertEquals(AdbMessage.WRTE, piece.command)
                pieces += piece.data.size
                taken += piece.data
                send(AdbMessage(AdbMessage.OKAY, 99, id))
            }
            send(AdbMessage(AdbMessage.WRTE, 99, id, "took:${taken.size}".toByteArray()))
            AdbMessage.read(input) // OKAY
            send(AdbMessage(AdbMessage.CLSE, 99, id))
            AdbMessage.read(input) // CLSE
        }

        private fun echo(command: String, id: Int, input: InputStream, send: (AdbMessage) -> Unit) {
            send(AdbMessage(AdbMessage.WRTE, 99, id, "echo:$command".toByteArray()))
            AdbMessage.read(input) // OKAY
            send(AdbMessage(AdbMessage.CLSE, 99, id))
            AdbMessage.read(input) // CLSE
        }

        /** The signature decrypts to the SHA-1 DigestInfo prefix followed by the token. */
        private fun verifies(key: AdbKey, token: ByteArray, signature: ByteArray): Boolean {
            val public = parse(key.publicKeyLine("x"))
            val plain = Cipher.getInstance("RSA/ECB/PKCS1Padding").run {
                init(Cipher.DECRYPT_MODE, public)
                runCatching { doFinal(signature) }.getOrNull()
            } ?: return false
            return plain.takeLast(token.size) == token.toList()
        }

        private fun parse(line: ByteArray): RSAPublicKey {
            val blob = Base64.getDecoder().decode(String(line).substringBefore(' '))
            val buffer = ByteBuffer.wrap(blob).order(ByteOrder.LITTLE_ENDIAN)
            val words = buffer.int
            buffer.int
            val modulusBytes = ByteArray(words * 4)
            buffer.get(modulusBytes)
            val modulus = BigInteger(1, modulusBytes.reversedArray())
            buffer.position(buffer.position() + words * 4)
            val exponent = BigInteger.valueOf(buffer.int.toLong())
            return java.security.KeyFactory.getInstance("RSA")
                .generatePublic(java.security.spec.RSAPublicKeySpec(modulus, exponent)) as RSAPublicKey
        }

        override fun close() {
            server.close()
            worker.join(2_000)
        }
    }
}
