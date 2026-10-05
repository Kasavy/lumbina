package dev.lumbina.tv.platform.adb

import java.io.File
import java.io.RandomAccessFile
import java.math.BigInteger
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.interfaces.RSAPublicKey
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

/**
 * Lumbina's adb key: an RSA-2048 pair kept in a directory of the app's files ([loadOrCreate]). The
 * device remembers the public key once the user allows it ("Always allow"), so later connections
 * need no prompt.
 */
internal class AdbKey(private val keys: KeyPair) {

    /** The device's challenge, signed the way adbd checks it: PKCS#1 v1.5 over a SHA-1 digest. */
    fun sign(token: ByteArray): ByteArray = Signature.getInstance("NONEwithRSA").run {
        initSign(keys.private)
        update(SHA1_DIGEST_INFO)
        update(token)
        sign()
    }

    /** The public key in adb's format (the `adbkey.pub` line), zero-terminated as adbd expects. */
    fun publicKeyLine(name: String): ByteArray {
        val encoded = Base64.getEncoder().encodeToString(androidPublicKey(keys.public as RSAPublicKey))
        return "$encoded $name\u0000".toByteArray(Charsets.US_ASCII)
    }

    companion object {
        private const val KEY_BITS = 2048
        private const val WORDS = KEY_BITS / Int.SIZE_BITS
        private const val PRIVATE_FILE = "adbkey"
        private const val PUBLIC_FILE = "adbkey.x509"
        private const val LOCK_FILE = "adbkey.lock"

        /** DER prefix of a SHA-1 DigestInfo; adb's token takes the place of the digest. */
        private val SHA1_DIGEST_INFO = byteArrayOf(
            0x30, 0x21, 0x30, 0x09, 0x06, 0x05, 0x2b, 0x0e, 0x03, 0x02, 0x1a, 0x05, 0x00, 0x04, 0x14,
        )

        /**
         * The pair in [dir], made there on first use. Both of Lumbina's processes may get here at
         * once, so a file lock lets only one of them generate and the other reads its result.
         */
        fun loadOrCreate(dir: File): AdbKey {
            load(dir)?.let { return AdbKey(it) }
            dir.mkdirs()
            return RandomAccessFile(File(dir, LOCK_FILE), "rw").use { lockFile ->
                lockFile.channel.lock().use {
                    AdbKey(load(dir) ?: generate(dir))
                }
            }
        }

        private fun load(dir: File): KeyPair? {
            val private = File(dir, PRIVATE_FILE)
            val public = File(dir, PUBLIC_FILE)
            if (!private.isFile || !public.isFile) return null
            val factory = KeyFactory.getInstance("RSA")
            return runCatching {
                KeyPair(
                    factory.generatePublic(X509EncodedKeySpec(public.readBytes())),
                    factory.generatePrivate(PKCS8EncodedKeySpec(private.readBytes())),
                )
            }.getOrNull()
        }

        private fun generate(dir: File): KeyPair {
            val pair = KeyPairGenerator.getInstance("RSA").apply { initialize(KEY_BITS) }.generateKeyPair()
            writeAtomically(File(dir, PUBLIC_FILE), pair.public.encoded)
            writeAtomically(File(dir, PRIVATE_FILE), pair.private.encoded)
            return pair
        }

        private fun writeAtomically(file: File, bytes: ByteArray) {
            val temp = File(file.parentFile, "${file.name}.tmp")
            temp.writeBytes(bytes)
            if (!temp.renameTo(file)) {
                file.delete()
                temp.renameTo(file)
            }
        }

        /**
         * Android's `RSAPublicKey` struct: word count, -1/n mod 2^32, the modulus, R^2 mod n
         * (R = 2^2048) as little-endian 32-bit words, then the exponent.
         */
        internal fun androidPublicKey(key: RSAPublicKey): ByteArray {
            val n = key.modulus
            val r32 = BigInteger.ONE.shiftLeft(Int.SIZE_BITS)
            val n0inv = r32.subtract(n.mod(r32).modInverse(r32)).toInt()
            val rr = BigInteger.ONE.shiftLeft(KEY_BITS * 2).mod(n)
            val buffer = ByteBuffer.allocate((3 + WORDS * 2) * Int.SIZE_BYTES).order(ByteOrder.LITTLE_ENDIAN)
            buffer.putInt(WORDS).putInt(n0inv)
            putWords(buffer, n)
            putWords(buffer, rr)
            buffer.putInt(key.publicExponent.toInt())
            return buffer.array()
        }

        private fun putWords(buffer: ByteBuffer, value: BigInteger) {
            repeat(WORDS) { i -> buffer.putInt(value.shiftRight(i * Int.SIZE_BITS).toInt()) }
        }
    }
}
