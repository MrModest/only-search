package app.onlysearch.widgets

import app.onlysearch.Accent
import app.onlysearch.AppSpan
import app.onlysearch.Card
import app.onlysearch.CardLine
import app.onlysearch.Cell
import app.onlysearch.WidgetCorner
import app.onlysearch.prefs
import app.onlysearch.search.askPermission
import android.Manifest
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.CalendarContract
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

/** Widgets drawn by the launcher itself, offered at the top of the widget picker. Sizes are in half cells. */
internal class Builtin(val kind: String, val label: String, val minW: Int, val minH: Int, val size: () -> Pair<Int, Int>) {
    val agenda get() = kind != "clock"
}

internal val Builtins = listOf(
    Builtin("clock", "Clock", 2 * AppSpan, AppSpan) { prefs.columns to 2 * AppSpan },
    Builtin("event", "Next event", 2 * AppSpan, AppSpan) { prefs.columns to AppSpan },
    Builtin("today", "Today", 2 * AppSpan, 2 * AppSpan) { prefs.columns to 2 * AppSpan },
    Builtin("day", "Day", 2 * AppSpan, 3 * AppSpan) { prefs.columns to prefs.rows },
    Builtin("month", "Month", 3 * AppSpan, 2 * AppSpan) { prefs.columns to 4 * AppSpan },
)

/** A built-in widget's content. Agenda widgets ask for calendar access in place and show only the calendars picked for them. */
@Composable
internal fun BuiltinWidget(cell: Cell, modifier: Modifier = Modifier, preview: Boolean = false) {
    val kind = Builtins.firstOrNull { it.kind == cell.builtin } ?: return
    if (!kind.agenda) return Clock(modifier.fillMaxSize())
    val context = LocalContext.current
    val granted = calendarChanges >= 0 && hasCalendar(context)
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    // Watched only while started; the widgets reload on every start anyway.
    DisposableEffect(lifecycle, granted) {
        if (!granted) return@DisposableEffect onDispose {}
        val changes = object : ContentObserver(Handler(Looper.getMainLooper())) { override fun onChange(selfChange: Boolean) { calendarChanges++ } }
        val observer = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_START) context.contentResolver.registerContentObserver(CalendarContract.CONTENT_URI, true, changes)
            if (e == Lifecycle.Event.ON_STOP) context.contentResolver.unregisterContentObserver(changes)
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer); context.contentResolver.unregisterContentObserver(changes) }
    }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { if (it) calendarChanges++ }
    // Access can change outside our prompts (system settings, another app's grant): re-check whenever we come back.
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
        "month" -> MonthCard(hidden, modifier)
        else -> DayCard(hidden, prefs.dayRange(cell.key), prefs.dayTrim(cell.key), modifier)
    }
}
