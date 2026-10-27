package app.onlysearch.widgets

import app.onlysearch.WallMuted
import app.onlysearch.line
import app.onlysearch.shadowed
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
import androidx.compose.ui.unit.sp

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

private object AIc {
    val calendar = line("M4 6h16v14H4z M4 10h16 M8 3v5 M16 3v5")
}
