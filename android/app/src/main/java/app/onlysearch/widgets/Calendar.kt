package app.onlysearch.widgets

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.CalendarContract
import android.text.format.DateFormat
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import java.util.Calendar
import java.util.Date
import java.util.TimeZone
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
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
internal var calendarChanges by mutableIntStateOf(0)

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

internal fun hm(context: Context, t: Long) = DateFormat.format(if (DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm a", Date(t)).toString()

internal const val HourMs = 3_600_000L
