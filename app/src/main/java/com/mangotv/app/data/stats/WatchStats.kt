package com.mangotv.app.data.stats

import java.time.Instant
import java.time.ZoneId
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * "Your stats": what the account's watch history says about how it is used. Pure, so every number is tested directly; the same maths as the
 * web app's `domain/stats.ts`. The history holds one row per movie or episode (the furthest point reached and when it was last watched), so a
 * title watched twice counts once, on the day it was last watched.
 */
data class HistoryItem(
    val providerId: String,
    val contentId: String,
    val isMovie: Boolean,
    val positionMs: Long,
    val durationMs: Long,
    val completed: Boolean,
    val watchedAtMs: Long
)

data class WatchStats(
    val empty: Boolean,
    val totalMs: Long,
    val last7DaysMs: Long,
    val last30DaysMs: Long,
    /** The 7 days before the last 7 (to say whether this week was busier or quieter). */
    val prev7DaysMs: Long,
    val movieMs: Long,
    val showMs: Long,
    val moviesFinished: Int,
    val episodesWatched: Int,
    val showsWatched: Int,
    val activeDays: Int,
    val avgPerActiveDayMs: Long,
    /** Milliseconds watched per weekday, Monday first. */
    val byWeekdayMs: List<Long>,
    /** Index into [byWeekdayMs] (Monday = 0) of the busiest day, or null with no history. */
    val busiestWeekday: Int?,
    /** Consecutive days with something watched, counting back from today (or from yesterday, when nothing was watched yet today). */
    val streakDays: Int,
    val longestStreakDays: Int,
    /** Time watched per local day for the last [HEAT_DAYS] days, oldest first (the last entry is today). */
    val dailyMs: List<Long>,
    val firstWatchedAtMs: Long?
)

/** How many days the activity grid covers: 13 weeks. */
const val HEAT_DAYS = 91
val WEEKDAYS = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")
private const val DAY_MS = 24L * 3600_000

/** How much of one row counts as watched: all of it when finished, otherwise how far in it got (never more than its length). */
fun watchedMsOf(item: HistoryItem): Long {
    val position = max(0L, item.positionMs)
    return if (item.durationMs > 0) (if (item.completed) item.durationMs else min(position, item.durationMs)) else position
}

fun emptyStats() = WatchStats(
    empty = true, totalMs = 0, last7DaysMs = 0, last30DaysMs = 0, prev7DaysMs = 0, movieMs = 0, showMs = 0, moviesFinished = 0, episodesWatched = 0,
    showsWatched = 0, activeDays = 0, avgPerActiveDayMs = 0, byWeekdayMs = List(7) { 0L }, busiestWeekday = null, streakDays = 0,
    longestStreakDays = 0, dailyMs = List(HEAT_DAYS) { 0L }, firstWatchedAtMs = null
)

private fun localDay(ms: Long, zone: ZoneId): Long = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate().toEpochDay()

fun computeStats(items: List<HistoryItem>, nowMs: Long = System.currentTimeMillis(), zone: ZoneId = ZoneId.systemDefault()): WatchStats {
    if (items.isEmpty()) return emptyStats()
    val today = localDay(nowMs, zone)
    var total = 0L
    var last7 = 0L
    var last30 = 0L
    var prev7 = 0L
    var movieMs = 0L
    var showMs = 0L
    var moviesFinished = 0
    var episodes = 0
    var first = Long.MAX_VALUE
    val byWeekday = LongArray(7)
    val dailyMs = LongArray(HEAT_DAYS)
    val days = HashSet<Long>()
    val shows = HashSet<String>()
    for (item in items) {
        val ms = watchedMsOf(item)
        val at = item.watchedAtMs
        val age = nowMs - at
        total += ms
        if (age <= 7 * DAY_MS) last7 += ms
        if (age <= 30 * DAY_MS) last30 += ms
        if (age > 7 * DAY_MS && age <= 14 * DAY_MS) prev7 += ms
        if (item.isMovie) movieMs += ms else showMs += ms
        first = min(first, at)
        val day = localDay(at, zone)
        byWeekday[Instant.ofEpochMilli(at).atZone(zone).dayOfWeek.value - 1] += ms
        if (ms > 0) days.add(day)
        val ago = (today - day).toInt()
        if (ago in 0 until HEAT_DAYS) dailyMs[HEAT_DAYS - 1 - ago] += ms
        if (item.isMovie) {
            if (item.completed) moviesFinished++
        } else {
            shows.add("${item.providerId}|${item.contentId}")
            if (item.completed) episodes++
        }
    }
    val ordered = days.sorted()
    var longest = 0
    var run = 0
    ordered.forEachIndexed { i, day ->
        run = if (i > 0 && day == ordered[i - 1] + 1) run + 1 else 1
        longest = max(longest, run)
    }
    var cursor = if (today in days) today else today - 1
    var streak = 0
    while (cursor in days) { streak++; cursor-- }
    val peak = byWeekday.max()
    return WatchStats(
        empty = false, totalMs = total, last7DaysMs = last7, last30DaysMs = last30, prev7DaysMs = prev7, movieMs = movieMs, showMs = showMs,
        moviesFinished = moviesFinished, episodesWatched = episodes, showsWatched = shows.size, activeDays = days.size,
        avgPerActiveDayMs = if (days.isNotEmpty()) total / days.size else 0, byWeekdayMs = byWeekday.toList(),
        busiestWeekday = if (peak > 0) byWeekday.indexOf(peak) else null, streakDays = streak, longestStreakDays = longest,
        dailyMs = dailyMs.toList(), firstWatchedAtMs = first
    )
}

/** "126 hours" / "1 hour 5 minutes" / "40 minutes" / "under a minute", for the big numbers. */
fun formatDuration(ms: Long): String {
    val totalMinutes = max(0L, ms) / 60_000
    if (totalMinutes < 1) return if (ms > 0) "under a minute" else "0 minutes"
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    val h = "$hours ${if (hours == 1L) "hour" else "hours"}"
    val m = "$minutes ${if (minutes == 1L) "minute" else "minutes"}"
    if (hours == 0L) return m
    // Past a day's worth the minutes are noise.
    return if (hours >= 24 || minutes == 0L) h else "$h $m"
}

/** "45m" / "3h" / "12.5h" for a bar label; empty for nothing. */
fun formatShort(ms: Long): String {
    if (ms <= 0) return ""
    val minutes = (ms / 60_000.0).roundToInt()
    if (minutes < 60) return "${max(1, minutes)}m"
    val hours = ms / 3_600_000.0
    return if (hours < 10) "${(hours * 10).roundToInt() / 10.0}h".replace(".0h", "h") else "${hours.roundToInt()}h"
}

enum class WeekDirection { UP, DOWN, SAME }
data class WeekChange(val direction: WeekDirection, val percent: Int)

/** This week against the one before, or null when there is nothing to compare. */
fun weekChange(last7: Long, prev7: Long): WeekChange? {
    if (prev7 <= 0) return null
    val percent = (((last7 - prev7).toDouble() / prev7) * 100).roundToInt()
    return WeekChange(if (percent > 0) WeekDirection.UP else if (percent < 0) WeekDirection.DOWN else WeekDirection.SAME, kotlin.math.abs(percent))
}

/** One day of the activity grid: [ms] is null for the blank cells that start the first week on a Monday; [level] is 0 (nothing) to 4 (the busiest days). */
data class HeatCell(val ms: Long?, val level: Int)

/** The activity grid's cells, oldest first: padded to start on a Monday, then one cell per day. Read as columns of 7 (Monday to Sunday). */
fun heatCells(dailyMs: List<Long>, nowMs: Long = System.currentTimeMillis(), zone: ZoneId = ZoneId.systemDefault()): List<HeatCell> {
    val maxMs = dailyMs.maxOrNull() ?: 0L
    val firstDay = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate().minusDays((dailyMs.size - 1).toLong())
    val pad = firstDay.dayOfWeek.value - 1
    val cells = ArrayList<HeatCell>(pad + dailyMs.size)
    repeat(pad) { cells.add(HeatCell(null, 0)) }
    dailyMs.forEach { ms ->
        val level = if (ms <= 0 || maxMs <= 0) 0 else min(4, max(1, ceil(ms.toDouble() / maxMs * 4).toInt()))
        cells.add(HeatCell(ms, level))
    }
    return cells
}
