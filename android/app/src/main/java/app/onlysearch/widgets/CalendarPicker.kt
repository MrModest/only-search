package app.onlysearch.widgets

import app.onlysearch.Accent
import app.onlysearch.Fg
import app.onlysearch.Muted
import app.onlysearch.PillLine
import app.onlysearch.PopupBg
import app.onlysearch.Sans
import app.onlysearch.prefs
import app.onlysearch.settings.Stepper
import app.onlysearch.settings.Switch
import app.onlysearch.settings.SwitchRow
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
