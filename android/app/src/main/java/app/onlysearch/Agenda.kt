package app.onlysearch

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.CalendarContract
import android.text.format.DateFormat
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.window.Dialog
import java.util.Calendar
import java.util.Date
import java.util.TimeZone
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class Event(val title: String, val begin: Long, val end: Long, val allDay: Boolean, val calendar: Long, val color: Int)

data class CalendarInfo(val id: Long, val name: String, val account: String, val color: Int)

fun hasCalendar(context: Context) = context.checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED

/** Event instances overlapping [from, to), earliest first; empty without permission. Blocking. */
fun events(context: Context, from: Long, to: Long): List<Event> {
    if (!hasCalendar(context)) return emptyList()
    val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().also { ContentUris.appendId(it, from); ContentUris.appendId(it, to) }.build()
    val cols = arrayOf(CalendarContract.Instances.TITLE, CalendarContract.Instances.BEGIN, CalendarContract.Instances.END, CalendarContract.Instances.ALL_DAY,
        CalendarContract.Instances.CALENDAR_ID, CalendarContract.Instances.DISPLAY_COLOR)
    val events = mutableListOf<Event>()
    runCatching {
        context.contentResolver.query(uri, cols, null, null, "${CalendarContract.Instances.BEGIN} ASC")?.use { c ->
            while (c.moveToNext()) events += Event(c.getString(0).orEmpty().ifBlank { "(no title)" }, c.getLong(1), c.getLong(2), c.getInt(3) == 1, c.getLong(4), c.getInt(5))
        }
    }
    return events
}

/** Today's events that haven't ended yet, earliest first. Blocking. */
fun todaysEvents(context: Context): List<Event> {
    val now = System.currentTimeMillis()
    val end = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59); set(Calendar.SECOND, 59) }.timeInMillis
    // All-day instances are stamped in UTC; keep the ones covering today, and timed ones still to come today.
    return events(context, now - 86_400_000L, end).filter { if (it.allDay) coversToday(it, now) else it.end > now && it.begin <= end }
}

/** Today's local date as UTC midnight, the way all-day instances are stamped. */
internal fun todayUtc(now: Long, tz: TimeZone = TimeZone.getDefault()): Long {
    val local = Calendar.getInstance(tz).apply { timeInMillis = now }
    return Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
        clear(); set(local.get(Calendar.YEAR), local.get(Calendar.MONTH), local.get(Calendar.DAY_OF_MONTH))
    }.timeInMillis
}

internal fun coversToday(e: Event, now: Long, tz: TimeZone = TimeZone.getDefault()) = todayUtc(now, tz).let { e.begin <= it && e.end > it }

/** Start of the local hour [back] hours before [now]. */
internal fun dayStart(now: Long, back: Int, tz: TimeZone = TimeZone.getDefault()): Long =
    Calendar.getInstance(tz).apply { timeInMillis = now; set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis - back * HourMs

/** Overlapping runs of events split the width into lanes, each event taking the first lane free when it starts. Returns (lane, lane count) per event. */
internal fun assignLanes(timed: List<Event>): Pair<IntArray, IntArray> {
    val lane = IntArray(timed.size); val lanes = IntArray(timed.size)
    var i = 0
    while (i < timed.size) {
        val ends = mutableListOf<Long>(); var runEnd = timed[i].end; var j = i
        while (j < timed.size && (j == i || timed[j].begin < runEnd)) {
            val e = timed[j]
            val free = ends.indexOfFirst { it <= e.begin }
            lane[j] = if (free >= 0) free.also { ends[it] = e.end } else ends.size.also { ends += e.end }
            runEnd = maxOf(runEnd, e.end); j++
        }
        for (k in i until j) lanes[k] = ends.size
        i = j
    }
    return lane to lanes
}

fun calendars(context: Context): List<CalendarInfo> {
    if (!hasCalendar(context)) return emptyList()
    val cols = arrayOf(CalendarContract.Calendars._ID, CalendarContract.Calendars.CALENDAR_DISPLAY_NAME, CalendarContract.Calendars.ACCOUNT_NAME, CalendarContract.Calendars.CALENDAR_COLOR)
    val out = mutableListOf<CalendarInfo>()
    runCatching {
        context.contentResolver.query(CalendarContract.Calendars.CONTENT_URI, cols, null, null, null)?.use { c ->
            while (c.moveToNext()) out += CalendarInfo(c.getLong(0), c.getString(1).orEmpty(), c.getString(2).orEmpty(), c.getInt(3))
        }
    }
    return out.sortedWith(compareBy({ it.account }, { it.name.lowercase() }))
}

/** Bumped when calendar access is granted or calendar data changes, so open agenda widgets reload. */
private var calendarChanges by mutableIntStateOf(0)

/** Today's events, refreshed once a minute; empty without permission. */
@Composable
fun rememberEvents(): List<Event> {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    return produceState(emptyList(), calendarChanges) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                value = withContext(Dispatchers.IO) { todaysEvents(context) }
                kotlinx.coroutines.delay(60_000 - System.currentTimeMillis() % 60_000)
            }
        }
    }.value
}

fun openCalendar(context: Context, at: Long = System.currentTimeMillis()) = runCatching {
    context.startActivity(Intent(Intent.ACTION_VIEW, ContentUris.withAppendedId(Uri.parse("content://com.android.calendar/time"), at)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

private fun hm(context: Context, t: Long) = DateFormat.format(if (DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm a", Date(t)).toString()

/** "Standup at 9:30", "Standup now", under the clock. */
@Composable
fun NextEventLine(events: List<Event>) {
    val context = LocalContext.current
    val now = System.currentTimeMillis()
    val next = events.firstOrNull { !it.allDay } ?: return Text("No more events today", color = WallMuted, fontSize = 15.sp, style = shadowed(),
        modifier = Modifier.padding(start = 8.dp, top = 8.dp))
    Row(Modifier.padding(start = 8.dp, top = 4.dp).clip(RoundedCornerShape(10.dp)).clickable { openCalendar(context, next.begin) }.padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(AIc.calendar, null, tint = Color(0xFFD9D5CD), modifier = Modifier.size(16.dp))
        Text(if (next.begin <= now) "${next.title} now" else "${next.title} at ${hm(context, next.begin)}", color = Color(0xFFD9D5CD), fontSize = 15.sp,
            style = shadowed(), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun TodayCard(events: List<Event>, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val now = System.currentTimeMillis()
    val shape = RoundedCornerShape(WidgetCorner)
    Column(modifier.fillMaxSize().clip(shape).background(Card.copy(alpha = 0.82f)).border(1.dp, CardLine, shape).clickable { openCalendar(context) }
        .padding(horizontal = 18.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Label("Today", color = Muted)
        if (events.isEmpty()) Text("Nothing else today", color = Muted, fontSize = 15.sp)
        val firstTimed = events.firstOrNull { !it.allDay }
        events.forEach { e ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(if (e.allDay) "all day" else hm(context, e.begin), color = if (e == firstTimed) Accent else Muted, fontSize = 13.sp, fontFamily = Mono,
                    letterSpacing = 0.02.em, maxLines = 1, modifier = Modifier.width(if (DateFormat.is24HourFormat(context)) 52.dp else 72.dp))
                Text(e.title, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, color = if (!e.allDay && e.end < now) Dim else Fg)
            }
        }
    }
}

/** Widgets drawn by the launcher itself, offered at the top of the widget picker. Sizes are in half cells. */
internal class Builtin(val kind: String, val label: String, val minW: Int, val minH: Int, val size: () -> Pair<Int, Int>) {
    val agenda get() = kind != "clock"
}

internal val Builtins = listOf(
    Builtin("clock", "Clock", 2 * AppSpan, AppSpan) { prefs.columns to 2 * AppSpan },
    Builtin("event", "Next event", 2 * AppSpan, AppSpan) { prefs.columns to AppSpan },
    Builtin("today", "Today", 2 * AppSpan, 2 * AppSpan) { prefs.columns to 2 * AppSpan },
    Builtin("day", "Day", 2 * AppSpan, 3 * AppSpan) { prefs.columns to prefs.rows },
)

/** A built-in widget's content. Agenda widgets ask for calendar access in place and show only the calendars picked for them. */
@Composable
internal fun BuiltinWidget(cell: Cell, modifier: Modifier = Modifier, preview: Boolean = false) {
    val kind = Builtins.firstOrNull { it.kind == cell.builtin } ?: return
    if (!kind.agenda) return Clock(modifier.fillMaxSize())
    val context = LocalContext.current
    val granted = calendarChanges >= 0 && hasCalendar(context)
    DisposableEffect(granted) {
        if (!granted) return@DisposableEffect onDispose {}
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) { override fun onChange(selfChange: Boolean) { calendarChanges++ } }
        context.contentResolver.registerContentObserver(CalendarContract.CONTENT_URI, true, observer)
        onDispose { context.contentResolver.unregisterContentObserver(observer) }
    }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { if (it) calendarChanges++ }
    // Access can change outside our prompts (system settings, another app's grant): re-check whenever we come back.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, granted) {
        val observer = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_START && hasCalendar(context) != granted) calendarChanges++ }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    if (calendarChanges >= 0 && !hasCalendar(context)) return Column(
        modifier.fillMaxSize().clip(RoundedCornerShape(WidgetCorner)).background(Card.copy(alpha = 0.82f)).border(1.dp, CardLine, RoundedCornerShape(WidgetCorner))
            .then(if (preview) Modifier else Modifier.clickable { askPermission(context, Manifest.permission.READ_CALENDAR, ask) }).padding(horizontal = 14.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(kind.label, fontSize = 15.sp, maxLines = 1)
        Text("Tap to allow calendar access", color = Accent, fontSize = 13.sp, maxLines = 1)
    }
    val hidden = prefs.hiddenCalendars(cell.key)
    when (kind.kind) {
        "event" -> NextEventLine(rememberEvents().filter { it.calendar !in hidden })
        "today" -> TodayCard(rememberEvents().filter { it.calendar !in hidden }, modifier)
        else -> DayCard(hidden, prefs.dayRange(cell.key), prefs.dayTrim(cell.key), modifier)
    }
}

internal const val HourMs = 3_600_000L

/**
 * Timed events from [range].first hours before the current hour to [range].second hours after it, each hour the same
 * height, so events are as tall as they last (15:30–17:30 fills half of 15:00, all of 16:00, half of 17:00).
 * Overlapping events share the width. Each day's [trim] hours (from, until) collapse into a 2-hour gap when no event touches them.
 */
@Composable
private fun DayCard(hidden: Set<Long>, range: Pair<Int, Int>, trim: Pair<Int, Int>?, modifier: Modifier) {
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

/** Which calendars an agenda widget shows. Unticked ones are remembered, so new calendars show up by default. */
@Composable
internal fun CalendarPicker(key: String, onClose: () -> Unit) {
    val context = LocalContext.current
    val all by produceState<List<CalendarInfo>?>(null) { value = withContext(Dispatchers.IO) { calendars(context) } }
    Dialog(onClose) {
        ProvideTextStyle(TextStyle(fontFamily = Sans, color = Fg)) {
            Column(Modifier.clip(RoundedCornerShape(22.dp)).background(PopupBg).border(1.dp, PillLine, RoundedCornerShape(22.dp)).padding(vertical = 18.dp)) {
                if (key.startsWith("b:day:")) {
                    Text("Time range", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 20.dp))
                    val (back, ahead) = prefs.dayRange(key)
                    Column(Modifier.padding(horizontal = 6.dp, vertical = 6.dp)) {
                        Stepper("Hours before now", back, 0..12) { prefs.setDayRange(key, it to ahead) }
                        Stepper("Hours after now", ahead, 1..24) { prefs.setDayRange(key, back to it) }
                    }
                    // Hours that collapse into a small gap when nothing's on, e.g. the night.
                    val trim = prefs.dayTrim(key)
                    Column(Modifier.padding(horizontal = 6.dp)) {
                        SwitchRow("Skip empty hours", "A small gap instead, unless an event falls in them", trim != null) { prefs.setDayTrim(key, if (it) 0 to 7 else null) }
                    }
                    if (trim != null) Column(Modifier.padding(horizontal = 6.dp)) {
                        Stepper("Skip from (hour)", trim.first, 0..23) { prefs.setDayTrim(key, it to trim.second) }
                        Stepper("Until (hour)", trim.second, 0..23) { prefs.setDayTrim(key, trim.first to it) }
                    }
                }
                Text("Calendars", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 20.dp))
                if (all?.isEmpty() == true) Text("No calendars on this phone, or no calendar access.", color = Muted, fontSize = 14.sp, modifier = Modifier.padding(20.dp))
                Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()).padding(top = 8.dp)) {
                    all.orEmpty().forEach { cal ->
                        val on = cal.id !in prefs.hiddenCalendars(key)
                        Row(
                            Modifier.fillMaxWidth().clickable {
                                val hidden = prefs.hiddenCalendars(key)
                                prefs.setHiddenCalendars(key, if (on) hidden + cal.id else hidden - cal.id)
                            }.padding(horizontal = 20.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Box(Modifier.size(12.dp).clip(CircleShape).background(Color(cal.color)))
                            Column(Modifier.weight(1f)) {
                                Text(cal.name, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(cal.account, color = Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            Switch(on, cal.name) { _ -> prefs.setHiddenCalendars(key, prefs.hiddenCalendars(key).let { h -> if (on) h + cal.id else h - cal.id }) }
                        }
                    }
                }
                Text("Done", color = Accent, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.align(Alignment.End).padding(end = 12.dp, top = 6.dp).clip(CircleShape).clickable(onClick = onClose).padding(10.dp))
            }
        }
    }
}

private object AIc {
    val calendar = line("M4 6h16v14H4z M4 10h16 M8 3v5 M16 3v5")
}
