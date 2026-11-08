package dev.lumbina.tv.platform.accessibility

import android.os.SystemClock
import dev.lumbina.tv.core.domain.access.AdbState
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Whether Lumbina's connection to the device's debugging works, for the phone page, which asks as
 * it opens and before every file it sends. Each check is a connection of its own and waits behind
 * whatever holds adb (a prompt, an install): the answer is kept a while, and a check still waiting
 * for adb after a moment gives the last answer instead. A connection already under way is not
 * cut short: its handshake runs out first. Called from the page server's threads.
 */
internal class ConnectedLately(
    private val state: suspend () -> AdbState,
    private val now: () -> Long = SystemClock::elapsedRealtime,
) {
    @Volatile private var known: Known? = null

    fun connected(): Boolean {
        val last = known
        if (last != null && now() - last.at < KEEP_MILLIS) return last.connected
        val fresh = runBlocking { withTimeoutOrNull(WAIT_MILLIS) { state() == AdbState.Connected } }
            ?: return last?.connected == true
        known = Known(now(), fresh)
        return fresh
    }

    private class Known(val at: Long, val connected: Boolean)

    private companion object {
        const val KEEP_MILLIS = 30_000L
        const val WAIT_MILLIS = 1_500L
    }
}
