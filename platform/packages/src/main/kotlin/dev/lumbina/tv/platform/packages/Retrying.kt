package dev.lumbina.tv.platform.packages

import dev.lumbina.tv.core.network.HttpStatusException
import java.io.IOException
import kotlinx.coroutines.delay

/**
 * A failure another try would not change: a file bigger than published, the package not written
 * to the device's storage.
 */
internal class FinalAnswer(message: String) : IOException(message)

/**
 * [block], tried again after each of [pauses] when the network fails on the way (a timeout, a
 * handshake that did not finish, a dropped connection: a box's Wi-Fi does that now and then) or the
 * server is busy (5xx, 408, 429). A [FinalAnswer] or any other 4xx ends it at once; so does running
 * out of tries, with the last failure. Each failure that is tried again goes to [note].
 */
internal suspend fun <T> retrying(pauses: List<Long>, note: (IOException) -> Unit, block: suspend () -> T): T {
    for (pause in pauses) {
        try {
            return block()
        } catch (e: IOException) {
            if (!e.isTransient()) throw e
            note(e)
            delay(pause)
        }
    }
    return block()
}

private fun IOException.isTransient(): Boolean = when (this) {
    is FinalAnswer -> false
    is HttpStatusException -> code !in CLIENT_ERRORS || code in BUSY
    else -> true
}

private val CLIENT_ERRORS = 400..499

/** Request Timeout and Too Many Requests: the server asks to come back later. */
private val BUSY = setOf(408, 429)
