package app.onlysearch.widgets

import app.onlysearch.Accent
import app.onlysearch.Card
import app.onlysearch.CardLine
import app.onlysearch.Dim
import app.onlysearch.Fg
import app.onlysearch.Mono
import app.onlysearch.Muted
import app.onlysearch.WidgetCorner
import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.CircleShape
import java.util.Calendar
import java.util.Date
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Timed events from [range].first hours before the current hour to [range].second hours after it, each hour the same
 * height, so events are as tall as they last (15:30–17:30 fills half of 15:00, all of 16:00, half of 17:00).
 * Overlapping events share the width. Each day's [trim] hours (from, until) collapse into a 2-hour gap when no event touches them.
 */
@Composable
internal fun DayCard(hidden: Set<Long>, range: Pair<Int, Int>, trim: Pair<Int, Int>?, modifier: Modifier) {
    val hours = range.first + range.second
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val (start, now, all) = produceState(Triple(0L, 0L, emptyList<Event>()), calendarChanges, range) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                val now = System.currentTimeMillis()
                val start = dayStart(now, range.first)
                value = Triple(start, now, withContext(Dispatchers.IO) { events(context, start, start + hours * HourMs) })
                kotlinx.coroutines.delay(60_000 - System.currentTimeMillis() % 60_000)
            }
        }
    }.value
    val timed = all.filter { !it.allDay && it.calendar !in hidden && it.end > start }
    val end = start + hours * HourMs
    val gaps = trim?.let { (from, until) ->
        (-1..2).mapNotNull { d ->
            fun at(h: Int, days: Int) = Calendar.getInstance().apply {
                timeInMillis = start; add(Calendar.DAY_OF_YEAR, d + days); set(Calendar.HOUR_OF_DAY, h); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            }.timeInMillis
            val f = maxOf(at(from, 0), start); val t = minOf(at(until, if (until <= from) 1 else 0), end)
            (f to t).takeIf { t - f > 2 * HourMs && timed.none { it.begin < t && it.end > f } }
        }
    }.orEmpty()
    // Screen position in hours: plain hours count as one, a gap as two however long it is.
    fun pos(t: Long): Float {
        var shift = 0L
        for ((f, to) in gaps) {
            if (t >= to) shift += to - f - 2 * HourMs
            else if (t > f) return (f - start - shift + (t - f) * 2 * HourMs / (to - f)) / HourMs.toFloat()
        }
        return (t - start - shift) / HourMs.toFloat()
    }
    val shape = RoundedCornerShape(WidgetCorner)
    Column(modifier.fillMaxSize().clip(shape).background(Card.copy(alpha = 0.82f)).border(1.dp, CardLine, shape).clickable { openCalendar(context) }
        .padding(horizontal = 10.dp, vertical = 10.dp)) {
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val hour = maxHeight / pos(end)
            val labelW = if (DateFormat.is24HourFormat(context)) 40.dp else 48.dp
            val step = listOf(1, 2, 3, 4, 6).firstOrNull { hour * it >= 14.dp } ?: 12
            // Hour lines, plus quarter and half hour lines once there's room for them; none inside a gap.
            val ticks = if (hour >= 96.dp) 4 else if (hour >= 48.dp) 2 else 1
            val clock = Calendar.getInstance()
            for (q in 0..hours * ticks) {
                val t = start + q * HourMs / ticks
                if (gaps.any { t > it.first && t < it.second }) continue
                val whole = q % ticks == 0
                Box(Modifier.offset(x = labelW, y = hour * pos(t)).fillMaxWidth().height(1.dp).background(if (whole) CardLine else CardLine.copy(alpha = 0.45f)))
                clock.timeInMillis = t
                if (whole && t < end && clock.get(Calendar.HOUR_OF_DAY) % step == 0) Text(
                    DateFormat.format(if (DateFormat.is24HourFormat(context)) "HH:00" else "h a", Date(t)).toString(),
                    color = Dim, fontSize = 10.sp, fontFamily = Mono, maxLines = 1, modifier = Modifier.offset(y = hour * pos(t) - 6.dp),
                )
            }
            gaps.forEach { (f, to) ->
                Box(
                    Modifier.offset(x = labelW, y = hour * pos(f) + 3.dp).fillMaxWidth().height(hour * 2 - 6.dp).clip(RoundedCornerShape(3.dp))
                        .background(CardLine.copy(alpha = 0.5f)),
                    contentAlignment = Alignment.Center,
                ) { Text("${(to - f) / HourMs} h skipped", color = Muted, fontSize = 11.sp, maxLines = 1) }
            }
            val (lane, lanes) = assignLanes(timed)
            val width = maxWidth - labelW - 4.dp
            timed.forEachIndexed { k, e ->
                val top = hour * pos(e.begin.coerceAtLeast(start))
                val bottom = hour * pos(e.end.coerceAtMost(end))
                val w = width / lanes[k]
                Column(
                    Modifier.offset(x = labelW + 4.dp + w * lane[k], y = top).size(w - 2.dp, (bottom - top - 1.dp).coerceAtLeast(3.dp))
                        .clip(RoundedCornerShape(3.dp)).background(Color(e.color).copy(alpha = if (e.end < now) 0.2f else 0.4f))
                        .border(width = 1.dp, color = Color(e.color).copy(alpha = 0.6f), shape = RoundedCornerShape(3.dp))
                        .clickable { openCalendar(context, e.begin) }.padding(horizontal = 4.dp),
                ) {
                    Text(e.title, fontSize = 11.sp, lineHeight = 13.sp, color = if (e.end < now) Muted else Fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (bottom - top >= 28.dp) Text("${hm(context, e.begin)} – ${hm(context, e.end)}", fontSize = 10.sp, lineHeight = 12.sp, color = Muted, maxLines = 1)
                }
            }
            val y = hour * pos(now)
            Box(Modifier.offset(x = labelW - 3.dp, y = y - 3.dp).size(6.dp).clip(CircleShape).background(Accent))
            Box(Modifier.offset(x = labelW, y = y - 1.dp).fillMaxWidth().height(2.dp).background(Accent))
        }
    }
}
