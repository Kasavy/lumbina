package dev.lumbina.tv.platform.tvprovider

import dev.lumbina.tv.core.domain.channels.ChannelFeed
import dev.lumbina.tv.core.domain.channels.ProgramCard
import dev.lumbina.tv.core.domain.channels.TvChannel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.testTimeSource
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PacedTest {

    @Test
    fun `an app that never stops writing is still read, every few seconds and no more often`() = runTest {
        val changes = MutableSharedFlow<Unit>(extraBufferCapacity = 64)
        val readAt = mutableListOf<Long>()
        val reading = launch { changes.paced(500, 3_000, 3_000, testTimeSource).collect { readAt += currentTime } }
        runCurrent()
        // A change every 300 ms for 20 s: never a quiet half second.
        repeat(67) {
            changes.tryEmit(Unit)
            advanceTimeBy(300)
        }
        reading.cancel()
        assertEquals(0L, readAt.first())
        val gaps = readAt.zipWithNext { a, b -> b - a }
        assertEquals(true, gaps.isNotEmpty() && gaps.all { it in 3_000..6_300 })
    }

    @Test
    fun `a burst makes one reading once it pauses`() = runTest {
        val changes = MutableSharedFlow<Unit>(extraBufferCapacity = 64)
        val readAt = mutableListOf<Long>()
        val reading = launch { changes.paced(500, 3_000, 3_000, testTimeSource).collect { readAt += currentTime } }
        runCurrent()
        changes.tryEmit(Unit)
        runCurrent()
        advanceTimeBy(10_000)
        repeat(5) {
            changes.tryEmit(Unit)
            advanceTimeBy(100)
        }
        advanceTimeBy(10_000)
        reading.cancel()
        assertEquals(listOf(0L, 10_900L), readAt)
    }

    @Test
    fun `quick changes keep the wait going until the burst pauses`() = runTest {
        val changes = MutableSharedFlow<Unit>(extraBufferCapacity = 64)
        val readAt = mutableListOf<Long>()
        val reading = launch { changes.paced(500, 3_000, 3_000, testTimeSource).collect { readAt += currentTime } }
        runCurrent()
        changes.tryEmit(Unit)
        runCurrent()
        advanceTimeBy(10_000)
        // An app writing a channel and its programs one by one, 20 ms apart, for a second.
        repeat(50) {
            changes.tryEmit(Unit)
            advanceTimeBy(20)
        }
        advanceTimeBy(10_000)
        reading.cancel()
        // Read half a second after the last change, not a few changes into the burst.
        assertEquals(listOf(0L, 11_480L), readAt)
    }
}

class SteadiedTest {

    private val num = TvChannel(1, "ru.yourok.num", "Новинки", null, null)
    private val vk = TvChannel(2, "com.vk.tv", "Рекомендации", null, null)
    private fun feed(channel: TvChannel, vararg ids: Long) =
        ChannelFeed(channel, ids.map { ProgramCard(it, channel.packageName, "t$it", null, null, null, null) })

    @Test
    fun `a row whose programs are rewritten keeps its place for one reading`() {
        val before = steadied(listOf(num, vk), listOf(feed(num, 1, 2), feed(vk, 3)), ShownFeeds())
        // NUM deleted all its programs and has not written the new ones yet.
        val during = steadied(listOf(num, vk), listOf(feed(vk, 3)), before)
        assertEquals(listOf(feed(num, 1, 2), feed(vk, 3)), during.feeds)
        val after = steadied(listOf(num, vk), listOf(feed(num, 4), feed(vk, 3)), during)
        assertEquals(listOf(feed(num, 4), feed(vk, 3)), after.feeds)
    }

    @Test
    fun `a row still empty the next time goes, and so does one whose channel went`() {
        val before = steadied(listOf(num, vk), listOf(feed(num, 1), feed(vk, 3)), ShownFeeds())
        val once = steadied(listOf(num, vk), listOf(feed(vk, 3)), before)
        assertEquals(listOf(feed(vk, 3)), steadied(listOf(num, vk), listOf(feed(vk, 3)), once).feeds)
        assertEquals(listOf(feed(vk, 3)), steadied(listOf(vk), listOf(feed(vk, 3)), before).feeds)
    }
}
