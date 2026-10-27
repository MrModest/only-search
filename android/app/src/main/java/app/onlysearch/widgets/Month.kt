package app.onlysearch.widgets

import app.onlysearch.Accent
import app.onlysearch.WallFg
import app.onlysearch.WallMuted
import app.onlysearch.line
import app.onlysearch.shadowed
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import android.icu.util.Calendar
import android.icu.util.ULocale
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** First day shown in [month]'s grid: the start of the week holding the 1st. */
internal fun gridStart(month: LocalDate, firstDay: DayOfWeek): LocalDate = month.withDayOfMonth(1).with(TemporalAdjusters.previousOrSame(firstDay))

internal fun gridWeeks(month: LocalDate, firstDay: DayOfWeek) =
    (ChronoUnit.DAYS.between(gridStart(month, firstDay), month.with(TemporalAdjusters.lastDayOfMonth())) / 7 + 1).toInt()

/** An event's piece within one week row: columns [from, to), drawn in [lane] under the day numbers. */
internal data class Bar(val event: Event, val week: Int, val from: Int, val to: Int, val lane: Int)

/** Events cut into one bar per week row they touch. Longer bars take the upper lanes, then earlier ones. */
internal fun monthBars(events: List<Event>, start: LocalDate, weeks: Int, zone: ZoneId = ZoneId.systemDefault()): List<Bar> {
    // All-day instances are stamped as UTC midnights, timed ones end exclusively.
    fun day(t: Long, allDay: Boolean) = ChronoUnit.DAYS.between(start, Instant.ofEpochMilli(t).atZone(if (allDay) ZoneOffset.UTC else zone).toLocalDate()).toInt()
    val spans = events.map { e ->
        val a = day(e.begin, e.allDay)
        Triple(e, a, maxOf(a + 1, if (e.allDay) day(e.end, true) else day(maxOf(e.begin, e.end - 1), false) + 1))
    }
    return (0 until weeks).flatMap { w ->
        val segs = spans.mapNotNull { (e, a, b) -> Triple(e, maxOf(a, w * 7) - w * 7, minOf(b, w * 7 + 7) - w * 7).takeIf { it.second < it.third } }
            .sortedWith(compareBy({ it.second }, { -it.third }))
        val lane = assignLanes(segs.map { it.first.copy(begin = it.second.toLong(), end = it.third.toLong()) }).first
        segs.mapIndexed { i, (e, f, t) -> Bar(e, w, f, t, lane[i]) }
    }
}

/** A month (the current one unless paged) on the wallpaper, events as bars across their days, "+N" for those that don't fit. */
@Composable
internal fun MonthCard(hidden: Set<Long>, modifier: Modifier) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    // ICU honours the regional preference (`-u-fw-mon`) and knows the locale's weekend.
    val locales = LocalConfiguration.current.locales
    val (firstDay, weekend) = remember(locales) {
        val cal = Calendar.getInstance(ULocale.forLocale(locales[0]))
        val w = cal.weekData
        fun day(icu: Int) = DayOfWeek.SUNDAY.plus(icu - 1L)
        day(cal.firstDayOfWeek) to (0..(w.weekendCease - w.weekendOnset + 7) % 7).map { day(w.weekendOnset).plus(it.toLong()) }.toSet()
    }
    // Months away from the current one; back to the current month whenever home is left.
    var shift by remember { mutableIntStateOf(0) }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_STOP) shift = 0 }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    val (today, month, all) = produceState(Triple(LocalDate.now(), LocalDate.now(), emptyList<Event>()), calendarChanges, shift) {
        var loaded: LocalDate? = null
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                val now = LocalDate.now()
                if (now != loaded) {
                    val month = now.plusMonths(shift.toLong())
                    val zone = ZoneId.systemDefault()
                    val start = gridStart(month, firstDay)
                    val from = start.minusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
                    val to = start.plusWeeks(gridWeeks(month, firstDay).toLong()).plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
                    value = Triple(now, month, withContext(Dispatchers.IO) { events(context, from, to) })
                    loaded = now
                }
                kotlinx.coroutines.delay(60_000 - System.currentTimeMillis() % 60_000)
            }
        }
    }.value
    val start = gridStart(month, firstDay)
    val weeks = gridWeeks(month, firstDay)
    val bars = remember(all, hidden, start) { monthBars(all.filter { it.calendar !in hidden }, start, weeks) }
    val locale = Locale.getDefault()
    val line = WallFg.copy(alpha = 0.14f)
    @Composable
    fun Arrow(icon: ImageVector, name: String, by: Int) = Box(
        Modifier.size(48.dp, 32.dp).clip(RoundedCornerShape(8.dp)).clickable { shift += by }.semantics { contentDescription = name },
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, tint = WallFg, modifier = Modifier.size(18.dp)) }
    Column(modifier.fillMaxSize().padding(horizontal = 4.dp)) {
        Row(Modifier.fillMaxWidth().padding(bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Arrow(MIc.prev, "Previous month", -1)
            Text(month.format(DateTimeFormatter.ofPattern("LLLL yyyy", locale)).replaceFirstChar { it.titlecase(locale) }, color = WallFg, fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold, style = shadowed(), textAlign = TextAlign.Center, maxLines = 1,
                modifier = Modifier.weight(1f).then(if (shift != 0) Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClickLabel = "Back to this month") { shift = 0 } else Modifier))
            Arrow(MIc.next, "Next month", 1)
        }
        Row(Modifier.fillMaxWidth().padding(bottom = 2.dp)) {
            for (d in 0..6) Text(firstDay.plus(d.toLong()).getDisplayName(TextStyle.SHORT_STANDALONE, locale), color = WallMuted, fontSize = 11.sp,
                style = shadowed(), textAlign = TextAlign.Center, maxLines = 1, modifier = Modifier.weight(1f))
        }
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val colW = maxWidth / 7
            val rowH = maxHeight / weeks
            val numH = 16.dp
            val laneH = 14.dp
            val fit = ((rowH - numH) / laneH).toInt().coerceAtLeast(0)
            for (w in 0 until weeks) for (d in 0..6) {
                val date = start.plusDays(w * 7L + d)
                val out = date.month != month.month
                Box(
                    Modifier.offset(colW * d, rowH * w).size(colW, rowH)
                        .background(if (date.dayOfWeek in weekend) WallFg.copy(alpha = 0.15f) else Color.Transparent)
                        .background(if (date == today) Accent.copy(alpha = 0.4f) else if (out) Color.Black.copy(alpha = 0.3f) else Color.Transparent)
                        .border(0.5.dp, line)
                        .clickable { openCalendar(context, date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()) }
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                ) {
                    Text("${date.dayOfMonth}", color = if (out) WallMuted else WallFg, fontSize = 12.sp, lineHeight = 13.sp, style = shadowed(),
                        fontWeight = if (date == today) FontWeight.Bold else FontWeight.Normal, maxLines = 1)
                }
            }
            bars.filter { it.lane < fit }.forEach { b ->
                Box(
                    Modifier.offset(colW * b.from + 1.dp, rowH * b.week + numH + laneH * b.lane).size(colW * (b.to - b.from) - 2.dp, laneH - 2.dp)
                        .clip(RoundedCornerShape(3.dp)).background(Color(b.event.color).copy(alpha = 0.9f))
                        .clickable { openCalendar(context, b.event.begin) }.padding(horizontal = 3.dp),
                ) { Text(b.event.title, color = Color.White, fontSize = 10.sp, lineHeight = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
            // Bars that don't fit: "+N" at the top right of each day they cover.
            for (w in 0 until weeks) for (d in 0..6) {
                val more = bars.count { it.week == w && it.lane >= fit && d >= it.from && d < it.to }
                if (more > 0) Text("+$more", color = WallFg, fontSize = 10.sp, lineHeight = 13.sp, style = shadowed(), maxLines = 1, textAlign = TextAlign.End,
                    modifier = Modifier.offset(colW * d, rowH * w + 2.dp).width(colW - 4.dp))
            }
        }
    }
}

private object MIc {
    val prev = line("M15 6l-6 6 6 6", 2f)
    val next = line("M9 6l6 6-6 6", 2f)
}
