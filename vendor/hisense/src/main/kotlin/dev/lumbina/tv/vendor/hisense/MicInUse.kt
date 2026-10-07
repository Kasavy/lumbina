package dev.lumbina.tv.vendor.hisense

import android.net.LocalServerSocket
import android.net.LocalSocket
import android.net.LocalSocketAddress
import java.io.IOException

/**
 * How Lumbina's processes tell that one of them reads the remote's mic: the reader listens on a
 * local socket of this name for as long as it reads, and the others see whether anybody does.
 * The remote has room for one reader, and the reader (voice input) and the one who asks (a
 * diagnostics report) live in different processes. The system drops the socket with its process.
 */
internal object MicInUse {
    private const val NAME = "dev.lumbina.tv.mic.in.use"

    /** Held until closed. Never fails: voice input must not depend on this courtesy. */
    fun hold(): AutoCloseable = try {
        val socket = LocalServerSocket(NAME)
        AutoCloseable { socket.close() }
    } catch (_: IOException) {
        AutoCloseable {}
    }

    fun held(): Boolean = try {
        LocalSocket().use { it.connect(LocalSocketAddress(NAME, LocalSocketAddress.Namespace.ABSTRACT)) }
        true
    } catch (_: IOException) {
        false
    }
}
