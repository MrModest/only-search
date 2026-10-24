package app.onlysearch

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.Intent
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
import java.util.Calendar
import java.util.Date
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class Event(val title: String, val begin: Long, val end: Long, val allDay: Boolean)

fun hasCalendar(context: Context) = context.checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED

/** Today's events that haven't ended yet, earliest first. Blocking. */
fun todaysEvents(context: Context): List<Event> {
    if (!hasCalendar(context)) return emptyList()
    val now = System.currentTimeMillis()
    val end = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59); set(Calendar.SECOND, 59) }.timeInMillis
    val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().also { ContentUris.appendId(it, now - 86_400_000L); ContentUris.appendId(it, end) }.build()
    val cols = arrayOf(CalendarContract.Instances.TITLE, CalendarContract.Instances.BEGIN, CalendarContract.Instances.END, CalendarContract.Instances.ALL_DAY)
    val events = mutableListOf<Event>()
    runCatching {
        context.contentResolver.query(uri, cols, null, null, "${CalendarContract.Instances.BEGIN} ASC")?.use { c ->
            while (c.moveToNext()) events += Event(c.getString(0).orEmpty().ifBlank { "(no title)" }, c.getLong(1), c.getLong(2), c.getInt(3) == 1)
        }
    }
    // All-day instances are stamped in UTC; keep the ones covering today, and timed ones still to come today.
    return events.filter { if (it.allDay) it.end > now - 86_400_000L / 2 else it.end > now && it.begin <= end }
}

/** Refreshes once a minute; empty without permission or with both agenda options off. */
@Composable
fun rememberEvents(): List<Event> {
    val context = LocalContext.current
    val on = prefs.nextEvent || prefs.todayCard
    return produceState(emptyList(), on) {
        while (on) {
            value = withContext(Dispatchers.IO) { todaysEvents(context) }
            kotlinx.coroutines.delay(60_000 - System.currentTimeMillis() % 60_000)
        }
        value = emptyList()
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
    val next = events.firstOrNull { !it.allDay } ?: return
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
    val shape = RoundedCornerShape(22.dp)
    Column(modifier.fillMaxWidth().clip(shape).background(Card.copy(alpha = 0.82f)).border(1.dp, CardLine, shape).clickable { openCalendar(context) }
        .padding(horizontal = 18.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Label("Today", color = Muted)
        if (events.isEmpty()) Text("Nothing else today", color = Muted, fontSize = 15.sp)
        val firstTimed = events.firstOrNull { !it.allDay }
        events.take(4).forEach { e ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(if (e.allDay) "all day" else hm(context, e.begin), color = if (e == firstTimed) Accent else Muted, fontSize = 13.sp, fontFamily = Mono,
                    letterSpacing = 0.02.em, maxLines = 1, modifier = Modifier.width(if (DateFormat.is24HourFormat(context)) 52.dp else 72.dp))
                Text(e.title, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, color = if (!e.allDay && e.end < now) Dim else Fg)
            }
        }
    }
}

private object AIc {
    val calendar = line("M4 6h16v14H4z M4 10h16 M8 3v5 M16 3v5")
}
