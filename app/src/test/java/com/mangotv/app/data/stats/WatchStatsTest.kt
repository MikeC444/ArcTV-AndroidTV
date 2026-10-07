package com.mangotv.app.data.stats

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class WatchStatsTest {
    private val zone: ZoneId = ZoneId.of("UTC")
    private val hour = 3_600_000L
    private fun at(text: String): Long = LocalDateTime.parse(text).atZone(zone).toInstant().toEpochMilli()
    private val now = at("2026-10-07T18:00:00") // a Wednesday

    private fun item(
        id: String = "m1", movie: Boolean = true, position: Long = 2 * hour, duration: Long = 2 * hour, completed: Boolean = true, watchedAt: String = "2026-10-07T12:00:00"
    ) = HistoryItem("p", id, movie, position, duration, completed, at(watchedAt))

    @Test
    fun `counts all of a finished title and how far in an unfinished one got`() {
        assertEquals(100, watchedMsOf(HistoryItem("p", "a", true, 10, 100, true, 0)))
        assertEquals(40, watchedMsOf(HistoryItem("p", "a", true, 40, 100, false, 0)))
        assertEquals(100, watchedMsOf(HistoryItem("p", "a", true, 400, 100, false, 0)))
        assertEquals(40, watchedMsOf(HistoryItem("p", "a", true, 40, 0, false, 0)))
        assertEquals(0, watchedMsOf(HistoryItem("p", "a", true, -5, 100, false, 0)))
    }

    @Test
    fun `is empty for no history`() {
        assertTrue(computeStats(emptyList(), now, zone).empty)
        assertNull(computeStats(emptyList(), now, zone).busiestWeekday)
    }

    @Test
    fun `totals the time, compares the weeks and splits movies from shows`() {
        val stats = computeStats(
            listOf(
                item("m1", watchedAt = "2026-10-06T20:00:00"),
                item("m2", watchedAt = "2026-09-28T20:00:00", position = hour, duration = hour),
                item("s1", movie = false, position = hour, duration = hour, watchedAt = "2026-10-07T10:00:00")
            ),
            now, zone
        )
        assertEquals(4 * hour, stats.totalMs)
        assertEquals(3 * hour, stats.last7DaysMs)
        assertEquals(hour, stats.prev7DaysMs)
        assertEquals(3 * hour, stats.movieMs)
        assertEquals(hour, stats.showMs)
        assertEquals(2, stats.moviesFinished)
        assertEquals(1, stats.episodesWatched)
        assertEquals(1, stats.showsWatched)
        assertEquals(3, stats.activeDays)
        assertEquals(HEAT_DAYS, stats.dailyMs.size)
        assertEquals(hour, stats.dailyMs.last())
        assertEquals(2 * hour, stats.dailyMs[HEAT_DAYS - 2])
    }

    @Test
    fun `finds the busiest weekday with Monday first`() {
        // 2026-10-05 is a Monday, 2026-10-07 a Wednesday.
        val stats = computeStats(listOf(item("a", watchedAt = "2026-10-05T20:00:00", position = hour, duration = hour), item("b", watchedAt = "2026-10-07T10:00:00")), now, zone)
        assertEquals(2, stats.busiestWeekday)
        assertEquals(hour, stats.byWeekdayMs[0])
    }

    @Test
    fun `counts the streak back from today or from yesterday`() {
        fun days(vararg d: String) = d.mapIndexed { i, day -> item("x$i", watchedAt = "${day}T20:00:00") }
        assertEquals(3, computeStats(days("2026-10-07", "2026-10-06", "2026-10-05", "2026-10-02"), now, zone).streakDays)
        assertEquals(2, computeStats(days("2026-10-06", "2026-10-05"), now, zone).streakDays)
        assertEquals(0, computeStats(days("2026-10-04"), now, zone).streakDays)
        assertEquals(3, computeStats(days("2026-10-01", "2026-10-02", "2026-10-03", "2026-10-06"), now, zone).longestStreakDays)
    }

    @Test
    fun `reads naturally`() {
        assertEquals("0 minutes", formatDuration(0))
        assertEquals("under a minute", formatDuration(30_000))
        assertEquals("1 minute", formatDuration(60_000))
        assertEquals("1 hour 5 minutes", formatDuration(65 * 60_000L))
        assertEquals("2 hours", formatDuration(2 * hour))
        assertEquals("126 hours", formatDuration(126 * hour + 5 * 60_000))
        assertEquals("", formatShort(0))
        assertEquals("1m", formatShort(20_000))
        assertEquals("45m", formatShort(45 * 60_000L))
        assertEquals("2.5h", formatShort((2.5 * hour).toLong()))
        assertEquals("3h", formatShort(3 * hour))
        assertEquals("14h", formatShort(14 * hour))
    }

    @Test
    fun `says how this week compares`() {
        assertEquals(WeekChange(WeekDirection.UP, 50), weekChange(3, 2))
        assertEquals(WeekChange(WeekDirection.DOWN, 50), weekChange(1, 2))
        assertEquals(WeekChange(WeekDirection.SAME, 0), weekChange(2, 2))
        assertNull(weekChange(5, 0))
    }

    @Test
    fun `starts the activity grid on a Monday and grades the days`() {
        val daily = MutableList(HEAT_DAYS) { 0L }
        daily[HEAT_DAYS - 1] = 4 * hour
        daily[HEAT_DAYS - 2] = hour
        val cells = heatCells(daily, now, zone)
        val pad = cells.indexOfFirst { it.ms != null }
        // 2026-07-09 (the first of the 91 days ending 2026-10-07) is a Thursday: three blank cells put it under Thursday.
        assertEquals(3, pad)
        assertEquals(HeatCell(4 * hour, 4), cells.last())
        assertEquals(HeatCell(hour, 1), cells[cells.size - 2])
        assertEquals(HeatCell(0, 0), cells[pad])
    }
}
