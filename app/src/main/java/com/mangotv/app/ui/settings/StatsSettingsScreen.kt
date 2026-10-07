package com.mangotv.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AvTimer
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.TrendingFlat
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mangotv.app.data.stats.HEAT_DAYS
import com.mangotv.app.data.stats.WEEKDAYS
import com.mangotv.app.data.stats.WatchStats
import com.mangotv.app.data.stats.WeekDirection
import com.mangotv.app.data.stats.formatDuration
import com.mangotv.app.data.stats.formatShort
import com.mangotv.app.data.stats.heatCells
import com.mangotv.app.data.stats.weekChange
import com.mangotv.app.ui.components.MangoButton
import com.mangotv.app.ui.components.TvFocusSurface
import com.mangotv.app.ui.theme.ArcAccent
import com.mangotv.app.ui.theme.ArcBlue
import com.mangotv.app.ui.theme.ArcCyan
import com.mangotv.app.ui.theme.ArcViolet
import com.mangotv.app.ui.theme.ArcWarn
import com.mangotv.app.ui.theme.MangoSurface
import com.mangotv.app.ui.theme.MangoSurfaceHigh
import com.mangotv.app.ui.theme.TextPrimary
import com.mangotv.app.ui.theme.TextSecondary
import com.mangotv.app.ui.theme.TextTertiary
import java.text.DateFormat
import java.util.Date

/**
 * Settings > Your stats (Arc TV Plus): how much you watch, from the watch history of the profile in use. Plain text isn't focusable, so each
 * block of numbers is a focus stop, which is what lets the remote scroll down the whole page.
 */
@Composable
fun ColumnScope.StatsSettingsContent(
    contentFocusRequester: FocusRequester,
    sidebarFocusRequester: FocusRequester,
    viewModel: StatsViewModel = viewModel()
) {
    val plus by viewModel.plus.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()
    when {
        // The tab can't be opened without Plus; this only guards a stale selection.
        !plus.active -> Column(Modifier.weight(1f)) {
            Icon(Icons.Filled.Lock, contentDescription = null, tint = TextTertiary)
            Spacer(Modifier.height(8.dp))
            Text("Your stats are only for Arc TV Plus.", color = TextPrimary, style = MaterialTheme.typography.titleMedium)
        }
        state is StatsUiState.Loading -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = ArcAccent) }
        state is StatsUiState.Error -> Column(Modifier.weight(1f)) {
            Text("Couldn't read your watch history right now.", color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(12.dp))
            MangoButton(text = "Try again", icon = Icons.Filled.TrendingFlat, onClick = viewModel::load, focusRequester = contentFocusRequester, focusLeft = sidebarFocusRequester, compact = true)
        }
        else -> {
            val ready = state as StatsUiState.Ready
            StatsPanel(ready.stats, ready.truncated, contentFocusRequester, sidebarFocusRequester, Modifier.weight(1f))
        }
    }
}

/** The stats themselves, as a scrolling page; separate from the view model so it can be rendered on its own. */
@Composable
fun StatsPanel(stats: WatchStats, truncated: Boolean, contentFocusRequester: FocusRequester, sidebarFocusRequester: FocusRequester, modifier: Modifier = Modifier) {
    if (stats.empty) {
        Column(modifier) {
            Text("Nothing here yet. Watch something and your stats will appear.", color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
        }
        return
    }
    LazyColumn(
        modifier = modifier,
        // Room for a focused block's scale-up, same as the other tabs.
        contentPadding = SettingsListPadding,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item(key = "hero") { StatBlock(contentFocusRequester, sidebarFocusRequester) { HeroBlock(stats) } }
        item(key = "tiles") { StatBlock(null, sidebarFocusRequester) { TilesBlock(stats) } }
        if (stats.movieMs + stats.showMs > 0) item(key = "split") { StatBlock(null, sidebarFocusRequester) { SplitBlock(stats) } }
        item(key = "heat") { StatBlock(null, sidebarFocusRequester) { HeatBlock(stats) } }
        item(key = "bars") { StatBlock(null, sidebarFocusRequester) { WeekdayBlock(stats) } }
        item(key = "note") {
            Text(
                text = "Counted from your watch history${if (truncated) " (the most recent part of it)" else ""}. A title you watch again counts once, on the day you last watched it.",
                color = TextTertiary,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }
    }
}

/** One block of stats as a focus stop (it does nothing when pressed): Left goes back to the sidebar, Up / Down move along the page. */
@Composable
private fun StatBlock(focusRequester: FocusRequester?, sidebarFocusRequester: FocusRequester, content: @Composable () -> Unit) {
    TvFocusSurface(
        onClick = {},
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        focusedScale = 1.01f,
        backgroundColor = MangoSurface,
        borderColor = TextPrimary,
        focusRequester = focusRequester,
        focusLeft = sidebarFocusRequester
    ) {
        Box(Modifier.fillMaxWidth().padding(14.dp)) { content() }
    }
}

@Composable
private fun HeroBlock(stats: WatchStats) {
    val change = weekChange(stats.last7DaysMs, stats.prev7DaysMs)
    val since = stats.firstWatchedAtMs?.let { DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(it)) }
    Box(
        Modifier.fillMaxWidth().background(
            Brush.horizontalGradient(listOf(ArcBlue.copy(alpha = 0.22f), ArcViolet.copy(alpha = 0.20f))),
            RoundedCornerShape(12.dp)
        ).padding(16.dp)
    ) {
        Column {
            Text("You've watched", color = TextTertiary, style = MaterialTheme.typography.labelMedium)
            Text(
                text = formatDuration(stats.totalMs),
                style = MaterialTheme.typography.displaySmall.copy(brush = Brush.horizontalGradient(listOf(ArcCyan, ArcBlue, ArcViolet)), fontWeight = FontWeight.ExtraBold)
            )
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (since != null) Text("since $since", color = TextSecondary, style = MaterialTheme.typography.labelMedium)
                if (change != null) {
                    val (icon, color, text) = when (change.direction) {
                        WeekDirection.UP -> Triple(Icons.Filled.TrendingUp, ArcCyan, "${change.percent}% more than last week")
                        WeekDirection.DOWN -> Triple(Icons.Filled.TrendingDown, ArcWarn, "${change.percent}% less than last week")
                        WeekDirection.SAME -> Triple(Icons.Filled.TrendingFlat, TextSecondary, "Same as last week")
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.background(color.copy(alpha = 0.14f), RoundedCornerShape(percent = 50)).padding(horizontal = 10.dp, vertical = 3.dp)
                    ) {
                        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(text, color = color, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun TilesBlock(stats: WatchStats) {
    val streakNote = when {
        stats.longestStreakDays > stats.streakDays -> "best ${stats.longestStreakDays}"
        stats.streakDays > 0 && stats.streakDays == stats.longestStreakDays -> "your best"
        else -> null
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Tile(Icons.Filled.DateRange, "Last 7 days", formatDuration(stats.last7DaysMs), null, Modifier.weight(1f))
            Tile(Icons.Filled.CalendarMonth, "Last 30 days", formatDuration(stats.last30DaysMs), null, Modifier.weight(1f))
            Tile(
                Icons.Filled.LocalFireDepartment, "Day streak", if (stats.streakDays == 1) "1 day" else "${stats.streakDays} days", streakNote, Modifier.weight(1f),
                hot = stats.streakDays >= 3
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Tile(Icons.Filled.AvTimer, "Per day you watch", formatDuration(stats.avgPerActiveDayMs), "${stats.activeDays} days in all", Modifier.weight(1f))
            Tile(Icons.Filled.Movie, "Movies finished", stats.moviesFinished.toString(), null, Modifier.weight(1f))
            Tile(
                Icons.Filled.Tv, "Episodes watched", stats.episodesWatched.toString(),
                if (stats.showsWatched > 0) "across ${stats.showsWatched} ${if (stats.showsWatched == 1) "show" else "shows"}" else null, Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun Tile(icon: ImageVector, label: String, value: String, note: String?, modifier: Modifier, hot: Boolean = false) {
    val accent = if (hot) Color(0xFFFF9F43) else ArcAccent
    Column(modifier.background(MangoSurfaceHigh, RoundedCornerShape(10.dp)).padding(horizontal = 12.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(5.dp))
            Text(label, color = TextTertiary, style = MaterialTheme.typography.labelSmall, maxLines = 1)
        }
        Spacer(Modifier.height(3.dp))
        Text(value, color = TextPrimary, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        if (note != null) Text(note, color = TextTertiary, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun SplitBlock(stats: WatchStats) {
    val total = (stats.movieMs + stats.showMs).toFloat()
    val moviePct = Math.round(stats.movieMs / total * 100)
    Column {
        Text("Movies or shows", color = TextPrimary, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth().height(10.dp).background(ArcViolet, RoundedCornerShape(percent = 50))) {
            if (moviePct > 0) Box(Modifier.weight(moviePct.toFloat()).height(10.dp).background(ArcCyan, RoundedCornerShape(percent = 50)))
            if (moviePct < 100) Spacer(Modifier.weight((100 - moviePct).toFloat()))
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Movies ${formatDuration(stats.movieMs)} · $moviePct%", color = ArcCyan, style = MaterialTheme.typography.labelMedium)
            Text("Shows ${formatDuration(stats.showMs)} · ${100 - moviePct}%", color = ArcViolet, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun HeatBlock(stats: WatchStats) {
    val cells = heatCells(stats.dailyMs)
    val columns = cells.chunked(7)
    Column {
        Text("Last ${HEAT_DAYS / 7} weeks", color = TextPrimary, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            columns.forEach { week ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    week.forEach { cell -> HeatSquare(cell.ms != null, cell.level) }
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
            Text("Less ", color = TextTertiary, style = MaterialTheme.typography.labelSmall)
            (0..4).forEach { HeatSquare(true, it, size = 10); Spacer(Modifier.width(3.dp)) }
            Text(" More", color = TextTertiary, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun HeatSquare(real: Boolean, level: Int, size: Int = 16) {
    val color = when {
        !real -> Color.Transparent
        level == 0 -> MangoSurfaceHigh
        level == 1 -> ArcCyan.copy(alpha = 0.28f)
        level == 2 -> ArcCyan.copy(alpha = 0.52f)
        else -> ArcCyan
    }
    Box(Modifier.size(size.dp).background(color, RoundedCornerShape(4.dp)))
}

@Composable
private fun WeekdayBlock(stats: WatchStats) {
    val peak = (stats.byWeekdayMs.maxOrNull() ?: 1L).coerceAtLeast(1L)
    Column {
        Text(
            text = "Busiest day" + (stats.busiestWeekday?.let { ": ${WEEKDAYS[it]}" } ?: ""),
            color = TextPrimary, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            WEEKDAYS.forEachIndexed { i, day ->
                val isPeak = i == stats.busiestWeekday
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        formatShort(stats.byWeekdayMs[i]).ifEmpty { " " }, color = ArcCyan, fontWeight = if (isPeak) FontWeight.Bold else FontWeight.Normal,
                        style = MaterialTheme.typography.labelSmall.copy(shadow = Shadow(ArcCyan.copy(alpha = 0.6f), blurRadius = 12f))
                    )
                    Box(Modifier.height(72.dp).fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
                        val fraction = (stats.byWeekdayMs[i].toFloat() / peak).coerceIn(0.05f, 1f)
                        Box(
                            Modifier.width(26.dp).height((72 * fraction).dp).shadow(
                                elevation = if (isPeak) 14.dp else 8.dp, shape = RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp, bottomStart = 3.dp, bottomEnd = 3.dp),
                                ambientColor = ArcCyan, spotColor = ArcCyan
                            ).background(
                                Brush.verticalGradient(listOf(ArcCyan.copy(alpha = if (isPeak) 1f else 0.8f), ArcBlue.copy(alpha = if (isPeak) 1f else 0.8f), ArcViolet.copy(alpha = if (isPeak) 1f else 0.8f))),
                                RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp, bottomStart = 3.dp, bottomEnd = 3.dp)
                            )
                        )
                    }
                    Text(day.take(3), color = TextSecondary, style = MaterialTheme.typography.labelSmall.copy(shadow = Shadow(ArcCyan.copy(alpha = 0.4f), blurRadius = 12f)))
                }
            }
        }
    }
}
