package com.unofficialpixstudio.extras

import com.unofficialpixstudio.ble.PixAnimation
import com.unofficialpixstudio.ble.PixCommandEncoder
import com.unofficialpixstudio.ble.PixDirection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TimeDisplayTest {

    /** Everything a schedule must satisfy for the backpack to accept and play it. */
    private fun assertPlayable(steps: List<TimeStep>, look: TimeLook) {
        assertTrue("${steps.size} frames", steps.size in 1..PixAnimation.MAX_FRAMES)
        assertTrue(steps.all { it.durationMs in 1..0xFFFF })
        val frames = TimeFace.frames(16, 20, steps, look)
        val animation = PixAnimation(16, 20, frames, steps.map { it.durationMs }, PixDirection.NORMAL_STOP)
        assertTrue(PixCommandEncoder().uploadAnimation(animation).size > frames.size)
    }

    @Test
    fun stopwatchCountsEverySecondAtFirstThenLessOften() {
        val steps = TimeSchedules.stopwatch(0)
        assertPlayable(steps, TimeLook.Stopwatch)
        assertEquals((0 until 180).toList(), steps.take(180).map { it.value })
        assertTrue(steps.take(180).all { it.durationMs == 1000 })
        assertEquals(listOf(180, 190, 200), steps.drop(180).take(3).map { it.value })
        // The frames add up to the time the last one shows, so the count never drifts from real time.
        assertEquals(steps.last().value!! * 1000L, steps.dropLast(1).sumOf { it.durationMs.toLong() })
    }

    @Test
    fun stopwatchResumedPartWayThroughASecondStaysInStep() {
        val steps = TimeSchedules.stopwatch(12_400)
        assertPlayable(steps, TimeLook.Stopwatch)
        assertEquals(TimeStep(12, 600), steps[0])
        assertEquals(TimeStep(13, 1000), steps[1])
        assertEquals(steps.last().value!! * 1000L - 12_400, steps.dropLast(1).sumOf { it.durationMs.toLong() })
    }

    @Test
    fun stopwatchNeverShowsMoreThanTheDigitsCan() {
        val steps = TimeSchedules.stopwatch(TimeSchedules.MAX_SECONDS * 1000L - 5_500)
        assertPlayable(steps, TimeLook.Stopwatch)
        assertEquals(TimeSchedules.MAX_SECONDS, steps.last().value)
        assertTrue(steps.all { it.value!! <= TimeSchedules.MAX_SECONDS })
    }

    @Test
    fun countdownsOfEveryLengthFitAndTakeExactlyAsLongAsSet() {
        for (total in listOf(1, 5, 59, 60, 61, 90, 240, 241, 300, 1500, 1795, 3600, 5999)) {
            val steps = TimeSchedules.countdown(total)
            assertPlayable(steps, TimeLook.Countdown)
            assertEquals("$total", total, steps.first().value)
            val counting = steps.takeWhile { it.value != 0 }
            assertEquals("$total", total * 1000L, counting.sumOf { it.durationMs.toLong() })
            // Always downwards, and every second of the last minute.
            assertEquals("$total", counting.map { it.value!! }.sortedDescending(), counting.map { it.value })
            assertEquals("$total", (minOf(total, 60) downTo 1).toList(), counting.takeLast(minOf(total, 60)).map { it.value })
            assertEquals(TimeStep(0, 1000), steps.last())
        }
    }

    @Test
    fun longCountdownLandsOnRoundNumbers() {
        // 25 minutes can't tick every second; it ticks every ten until the last minute.
        val steps = TimeSchedules.countdown(25 * 60)
        assertEquals(listOf(1500, 1490, 1480), steps.take(3).map { it.value })
        assertEquals(listOf(1795, 1790, 1780), TimeSchedules.countdown(1795).take(3).map { it.value })
    }

    @Test
    fun clockTurnsOverOnTheMinuteAndPastMidnight() {
        // 23:58:40.250
        val steps = TimeSchedules.clock(secondOfDay = 23 * 3600 + 58 * 60 + 40, millis = 250, minutes = 4)
        assertPlayable(steps, TimeLook.Clock)
        assertEquals(TimeStep(23 * 60 + 58, 19_750), steps[0])
        assertEquals(listOf(23 * 60 + 59, 0, 1), steps.subList(1, 4).map { it.value })
        assertTrue(steps.subList(1, 4).all { it.durationMs == 60_000 })
        assertNull(steps.last().value)
        assertPlayable(TimeSchedules.clock(0), TimeLook.Clock)
    }

    @Test
    fun clockStartedAtTheVeryEndOfAMinuteSkipsToTheNext() {
        val steps = TimeSchedules.clock(secondOfDay = 10 * 3600 + 59, millis = 800, minutes = 2)
        assertEquals(TimeStep(10 * 60 + 1, 60_200), steps[0])
        assertPlayable(steps, TimeLook.Clock)
    }

    @Test
    fun showingFollowsTheStepsAndRestsOnTheLast() {
        val steps = TimeSchedules.stopwatch(0)
        assertEquals(0, TimeSchedules.showing(steps, 0).value)
        assertEquals(0, TimeSchedules.showing(steps, 999).value)
        assertEquals(1, TimeSchedules.showing(steps, 1000).value)
        assertEquals(180, TimeSchedules.showing(steps, 185_000).value)
        assertEquals(steps.last(), TimeSchedules.showing(steps, 100_000_000))
    }

    @Test
    fun digitsAreDistinctAndFillBothRows() {
        val drawn = (0..9).map { TimeFace.draw(16, 20, it * 11, it * 11, 0xFF0000, 0x00FF00).toList() }
        assertEquals(10, drawn.toSet().size)
        val eights = TimeFace.draw(16, 20, 88, 88, 0xFF0000, 0x00FF00)
        // Top row red, bottom row green, each touching both sides and its outer edge.
        assertEquals(0xFF0000, eights[0])
        assertEquals(0xFF0000, eights[15])
        assertEquals(0x00FF00, eights[19 * 16])
        assertEquals(0x00FF00, eights[19 * 16 + 15])
        // Every digit's segments are a subset of the eight's.
        for (frame in drawn) frame.forEachIndexed { i, colour -> if (colour != 0) assertEquals(eights[i], colour) }
    }

    @Test
    fun twelveHourClockNeverShowsZeroOrThirteen() {
        fun top(minuteOfDay: Int, twelveHour: Boolean) =
            TimeFace.frame(16, 20, TimeStep(minuteOfDay, 0), TimeLook.Clock, twelveHour).toList()
        fun hours(h: Int) = TimeFace.draw(16, 20, h, 5, TimeLook.Clock.top, TimeLook.Clock.bottom).toList()
        assertEquals(hours(12), top(5, twelveHour = true)) // 00:05 is 12:05
        assertEquals(hours(1), top(13 * 60 + 5, twelveHour = true))
        assertEquals(hours(12), top(12 * 60 + 5, twelveHour = true))
        assertEquals(hours(0), top(5, twelveHour = false))
        assertEquals(hours(13), top(13 * 60 + 5, twelveHour = false))
    }

    @Test
    fun tinyDisplaysGetABlankFrameRatherThanACrash() {
        assertTrue(TimeFace.draw(4, 4, 12, 34, 0xFFFFFF, 0xFFFFFF).all { it == 0 })
        for ((w, h) in listOf(12 to 12, 22 to 23, 7 to 11)) TimeFace.draw(w, h, 88, 88, 0xFFFFFF, 0xFFFFFF)
    }
}
