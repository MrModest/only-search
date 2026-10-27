package app.onlysearch.widgets

import app.onlysearch.Accent
import app.onlysearch.Card
import app.onlysearch.CardLine
import app.onlysearch.Dim
import app.onlysearch.Fg
import app.onlysearch.Label
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

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
