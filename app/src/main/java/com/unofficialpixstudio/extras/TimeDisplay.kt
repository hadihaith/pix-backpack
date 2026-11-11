package com.unofficialpixstudio.extras

/*
 * What the clock, stopwatch and countdown put on the display, and how they keep going.
 *
 * The backpack can't be told the time, and sending it a new picture every second isn't
 * practical. But it will play a list of frames by itself, each for its own length of time, so
 * the app works out everything it should show from now on, sends that once, and the backpack
 * carries on alone, even with the phone asleep or out of range. Pure Kotlin.
 */

/**
 * One thing the display shows and how long for. [value] is seconds for the stopwatch and
 * countdown (shown as minutes over seconds) and minutes since midnight for the clock (hours
 * over minutes); null shows dashes or, when [blank], nothing.
 */
data class TimeStep(val value: Int?, val durationMs: Int, val blank: Boolean = false)

object TimeSchedules {

    /** The backpack takes at most 65,535 ms per frame; nothing here asks for more than a minute. */
    private const val MINUTE_MS = 60_000L

    /** The most the two rows of two digits can show, in seconds: 99:59. */
    const val MAX_SECONDS = 99 * 60 + 59

    /**
     * A stopwatch counting up from [fromMs]. To fit the backpack's limit on frames it can't
     * tick every second for ever: it shows every second for the first three minutes, then
     * every ten seconds for seven more, then every minute for twenty, and stops there.
     */
    fun stopwatch(fromMs: Long): List<TimeStep> {
        val steps = mutableListOf<TimeStep>()
        var at = fromMs.coerceIn(0, MAX_SECONDS * 1000L)
        // How long each frame lasts, and how many frames tick at that rate.
        val phases = listOf(1_000L to 180, 10_000L to 42, MINUTE_MS to 20)
        for ((tick, count) in phases) {
            repeat(count) {
                if (at >= MAX_SECONDS * 1000L) return@repeat
                // Up to the next whole tick, so a start part-way through a second stays in step.
                val next = (at / tick + 1) * tick
                steps += TimeStep((at / 1000).toInt(), (next - at).toInt())
                at = next
            }
        }
        steps += TimeStep((at / 1000).toInt().coerceAtMost(MAX_SECONDS), 1000) // where it rests
        return steps
    }

    /**
     * A countdown from [totalSeconds] to zero. The last minute ticks every second; before
     * that it ticks as often as the backpack's limit on frames allows (every second for short
     * countdowns, up to once a minute for the longest). At zero it flashes, then stays on zero.
     */
    fun countdown(totalSeconds: Int): List<TimeStep> {
        val total = totalSeconds.coerceIn(1, MAX_SECONDS)
        val early = (total - FINAL_SECONDS).coerceAtLeast(0)
        val tick = TICKS.first { early / it <= EARLY_FRAMES }
        val steps = mutableListOf<TimeStep>()
        var left = total
        while (left > FINAL_SECONDS) {
            // Down to the next multiple of the tick, so the display lands on round numbers.
            val next = maxOf(FINAL_SECONDS, (left - 1) / tick * tick)
            steps += TimeStep(left, (left - next) * 1000)
            left = next
        }
        while (left > 0) {
            steps += TimeStep(left, 1000)
            left--
        }
        repeat(3) {
            steps += TimeStep(0, 350)
            steps += TimeStep(null, 250, blank = true)
        }
        steps += TimeStep(0, 1000)
        return steps
    }

    /**
     * A clock for the next [minutes] minutes, starting [secondOfDay] seconds (and [millis]) after
     * midnight. It ends on dashes, since a stopped clock showing a time would be a wrong one.
     */
    fun clock(secondOfDay: Int, millis: Int = 0, minutes: Int = CLOCK_MINUTES): List<TimeStep> {
        val steps = mutableListOf<TimeStep>()
        var minute = secondOfDay / 60
        var remaining = (60 - secondOfDay % 60) * 1000 - millis
        // Too little of this minute left to be worth a frame: start on the next one.
        if (remaining < 500) {
            minute++
            remaining += MINUTE_MS.toInt()
        }
        repeat(minutes) {
            steps += TimeStep(minute % MINUTES_IN_DAY, remaining)
            minute++
            remaining = MINUTE_MS.toInt()
        }
        steps += TimeStep(null, 1000)
        return steps
    }

    /** What a list of [steps] is showing [elapsedMs] after it started; its last step once it has run out. */
    fun showing(steps: List<TimeStep>, elapsedMs: Long): TimeStep {
        var passed = 0L
        for (step in steps) {
            passed += step.durationMs
            if (elapsedMs < passed) return step
        }
        return steps.last()
    }

    /** How long the clock keeps itself, in minutes. */
    const val CLOCK_MINUTES = 120

    private const val MINUTES_IN_DAY = 24 * 60
    private const val FINAL_SECONDS = 60
    private const val EARLY_FRAMES = 180
    private val TICKS = intArrayOf(1, 5, 10, 15, 30, 60)
}

/** The colours of each time display's two rows of digits, 0xRRGGBB. */
enum class TimeLook(val top: Int, val bottom: Int) {
    Clock(0x40D0FF, 0xFFFFFF),
    Stopwatch(0x30A0FF, 0x60FF60),
    Countdown(0xFFB000, 0xFF4020),
}

/**
 * Draws two numbers, one above the other, in the largest seven-segment digits that fit: two
 * digits a row, so hours over minutes or minutes over seconds.
 */
object TimeFace {

    /**
     * A frame showing [top] over [bottom] (each 0–99, shown as two digits; null for dashes) in
     * the given 0xRRGGBB colours.
     */
    fun draw(width: Int, height: Int, top: Int?, bottom: Int?, topColour: Int, bottomColour: Int): IntArray {
        val pixels = IntArray(width * height)
        val gap = if (minOf(width, height) >= 12) 2 else 1
        val digitWidth = (width - gap) / 2
        val digitHeight = (height - gap) / 2
        if (digitWidth < 3 || digitHeight < 5) return pixels // too small a display for digits
        val left = (width - 2 * digitWidth - gap) / 2
        val topEdge = (height - 2 * digitHeight - gap) / 2
        val thick = (digitWidth / 3).coerceAtLeast(1)

        fun row(number: Int?, y: Int, colour: Int) {
            val tens = number?.let { it.coerceIn(0, 99) / 10 }
            val ones = number?.let { it.coerceIn(0, 99) % 10 }
            digit(pixels, width, tens, left, y, digitWidth, digitHeight, thick, colour)
            digit(pixels, width, ones, left + digitWidth + gap, y, digitWidth, digitHeight, thick, colour)
        }
        row(top, topEdge, topColour)
        row(bottom, topEdge + digitHeight + gap, bottomColour)
        return pixels
    }

    /**
     * The frame for [step]: minutes over seconds for a timer, or hours over minutes for a
     * clock, where [twelveHour] shows 1–12 rather than 0–23.
     */
    fun frame(width: Int, height: Int, step: TimeStep, look: TimeLook, twelveHour: Boolean = false): IntArray {
        if (step.blank) return IntArray(width * height)
        val value = step.value ?: return draw(width, height, null, null, look.top, look.bottom)
        val top = if (twelveHour) (value / 60 + 11) % 12 + 1 else value / 60
        return draw(width, height, top, value % 60, look.top, look.bottom)
    }

    /** Every frame of [steps], in order. */
    fun frames(width: Int, height: Int, steps: List<TimeStep>, look: TimeLook, twelveHour: Boolean = false): List<IntArray> =
        steps.map { frame(width, height, it, look, twelveHour) }

    /*
     * The seven segments, lettered the usual way:
     *      a
     *    f   b
     *      g
     *    e   c
     *      d
     */
    private val segments = arrayOf("abcdef", "bc", "abged", "abgcd", "fgbc", "afgcd", "afgedc", "abc", "abcdefg", "abcdfg")

    /** One digit ([value] null for a dash) in a box [w] × [h] with its corner at ([x0], [y0]). */
    private fun digit(pixels: IntArray, width: Int, value: Int?, x0: Int, y0: Int, w: Int, h: Int, thick: Int, colour: Int) {
        val lit = if (value == null) "g" else segments[value]
        // The middle bar is a row thinner when that's what it takes to sit exactly half-way down.
        val middle = if ((h - thick) % 2 == 0) thick else (thick - 1).coerceAtLeast(1)
        val middleTop = (h - middle) / 2
        fun fill(xs: IntRange, ys: IntRange) {
            for (y in ys) for (x in xs) pixels[(y0 + y) * width + x0 + x] = colour
        }
        val upper = 0 until middleTop + middle
        val lower = middleTop until h
        for (segment in lit) {
            when (segment) {
                'a' -> fill(0 until w, 0 until thick)
                'b' -> fill(w - thick until w, upper)
                'c' -> fill(w - thick until w, lower)
                'd' -> fill(0 until w, h - thick until h)
                'e' -> fill(0 until thick, lower)
                'f' -> fill(0 until thick, upper)
                'g' -> fill(0 until w, middleTop until middleTop + middle)
            }
        }
    }
}
