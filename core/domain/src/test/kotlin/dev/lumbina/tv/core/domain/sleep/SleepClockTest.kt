package dev.lumbina.tv.core.domain.sleep

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SleepClockTest {

    private var time = 0L
    private val clock = SleepClock(now = { time })
    private val minute = 60_000L

    @Test
    fun `idle sleep warns a minute before and a press postpones it`() {
        val settings = SleepSettings(autoSleepMinutes = 10)
        time = 8 * minute
        assertEquals(SleepDecision.Stay, clock.decide(settings))
        time = 9 * minute + 30_000
        assertEquals(SleepDecision.Warn(30, SleepReason.Idle), clock.decide(settings))
        clock.onActivity()
        assertEquals(SleepDecision.Stay, clock.decide(settings))
        time += 10 * minute
        assertEquals(SleepDecision.Sleep(SleepReason.Idle), clock.decide(settings))
    }

    @Test
    fun `the earlier deadline wins`() {
        val settings = SleepSettings(autoSleepMinutes = 60, timerEndsAt = 5 * minute)
        time = 4 * minute + 59_500
        assertEquals(SleepDecision.Warn(1, SleepReason.Timer), clock.decide(settings))
        time = 5 * minute
        assertEquals(SleepDecision.Sleep(SleepReason.Timer), clock.decide(settings))
    }

    @Test
    fun `hours of standby are not idle time once the screen comes on`() {
        val settings = SleepSettings(autoSleepMinutes = 180, screensaverMinutes = 10)
        // Restarted while the device was going to standby.
        clock.onScreen(on = false)
        time = 20 * 60 * minute
        assertEquals(SleepDecision.Stay, clock.decide(settings))
        assertEquals(Long.MAX_VALUE, clock.nextCheckIn(settings))
        clock.onScreen(on = true)
        assertEquals(SleepDecision.Stay, clock.decide(settings))
        assertEquals(false, clock.screensaverDue(settings))
        time += 179 * minute
        assertEquals(SleepDecision.Warn(60, SleepReason.Idle), clock.decide(settings))
        // Still on: no reset.
        clock.onScreen(on = true)
        assertEquals(SleepDecision.Warn(60, SleepReason.Idle), clock.decide(settings))
    }

    @Test
    fun `nothing set means never`() {
        time = 1_000 * minute
        assertEquals(SleepDecision.Stay, clock.decide(SleepSettings(autoSleepMinutes = null)))
    }

    @Test
    fun `a timer that ran out long ago is ignored`() {
        var now = 1_000_000L
        val clock = SleepClock(now = { now })
        val settings = SleepSettings(autoSleepMinutes = null, timerEndsAt = now - 1_000)
        assertEquals(SleepDecision.Sleep(SleepReason.Timer), clock.decide(settings))
        now += 30_000
        assertEquals(SleepDecision.Stay, clock.decide(settings))
    }

    @Test
    fun `the screensaver is due after the idle delay`() {
        val settings = SleepSettings(screensaverMinutes = 5)
        time += 4 * minute
        assertEquals(false, clock.screensaverDue(settings))
        time += minute
        assertEquals(true, clock.screensaverDue(settings))
        clock.onActivity()
        assertEquals(false, clock.screensaverDue(settings))
        assertEquals(false, clock.screensaverDue(settings.copy(screensaverMinutes = null)))
    }

    @Test
    fun `the next check is when the warning starts`() {
        val settings = SleepSettings(autoSleepMinutes = 10, screensaverMinutes = null)
        time = 2 * minute
        assertEquals(7 * minute, clock.nextCheckIn(settings))
        clock.onActivity()
        assertEquals(9 * minute, clock.nextCheckIn(settings))
    }

    @Test
    fun `during the warning the next check is the next second of the countdown`() {
        val settings = SleepSettings(autoSleepMinutes = null, timerEndsAt = 5 * minute, screensaverMinutes = null)
        time = 4 * minute + 30_400
        assertEquals(SleepDecision.Warn(30, SleepReason.Timer), clock.decide(settings))
        assertEquals(600L, clock.nextCheckIn(settings))
        time += 600
        assertEquals(SleepDecision.Warn(29, SleepReason.Timer), clock.decide(settings))
        assertEquals(1_000L, clock.nextCheckIn(settings))
        time = 5 * minute - 1_000
        assertEquals(1_000L, clock.nextCheckIn(settings))
        time += 1_000
        assertEquals(SleepDecision.Sleep(SleepReason.Timer), clock.decide(settings))
    }

    @Test
    fun `a finished timer is checked again when it goes stale`() {
        val settings = SleepSettings(autoSleepMinutes = null, timerEndsAt = 5 * minute, screensaverMinutes = null)
        time = 5 * minute + 10_000
        assertEquals(20_000L, clock.nextCheckIn(settings))
        time += 20_000
        assertEquals(SleepDecision.Stay, clock.decide(settings))
        assertEquals(Long.MAX_VALUE, clock.nextCheckIn(settings))
    }

    @Test
    fun `the earliest of timer, idle sleep and screensaver wins`() {
        val settings = SleepSettings(autoSleepMinutes = 60, timerEndsAt = 30 * minute, screensaverMinutes = 10)
        assertEquals(10 * minute, clock.nextCheckIn(settings))
        time = 10 * minute
        assertEquals(19 * minute, clock.nextCheckIn(settings))
    }

    @Test
    fun `nothing set means no check is due`() {
        val settings = SleepSettings(autoSleepMinutes = null, screensaverMinutes = null)
        assertEquals(Long.MAX_VALUE, clock.nextCheckIn(settings))
    }
}
