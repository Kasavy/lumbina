package dev.lumbina.tv.platform.tvprovider

import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Change notifications paced into readings: the first at once; then each after its burst pauses
 * for [quietMillis], but no later than [maxWaitMillis] after the first change of the burst (an app
 * that never stops writing still gets read), and at least [gapMillis] after the reading before.
 * What comes meanwhile makes one reading, not many. The burst is timed by [clock]: counting the
 * waits instead would end it after a few quick changes, in the middle of an app's writing.
 */
internal fun Flow<Unit>.paced(
    quietMillis: Long,
    maxWaitMillis: Long,
    gapMillis: Long,
    clock: TimeSource = TimeSource.Monotonic,
): Flow<Unit> = channelFlow {
    val changes = Channel<Unit>(Channel.CONFLATED)
    launch { collect { changes.send(Unit) } }
    changes.receive()
    send(Unit)
    while (true) {
        delay(gapMillis)
        changes.receive()
        val began = clock.markNow()
        var burst = true
        while (burst) {
            val left = maxWaitMillis - began.elapsedNow().inWholeMilliseconds
            burst = left > 0 && withTimeoutOrNull(minOf(quietMillis, left).milliseconds) { changes.receive() } != null
        }
        send(Unit)
    }
}
