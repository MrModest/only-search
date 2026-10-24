package app.onlysearch

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/** Full-screen editor for a drawer group: its name and which apps are in it. [index] -1 makes a new group. */
@Composable
fun GroupEditor(apps: Apps, index: Int, onClose: () -> Unit) {
    val old = apps.groups.getOrNull(index)
    var name by remember { mutableStateOf(old?.name.orEmpty()) }
    val chosen = remember { mutableStateListOf<String>().apply { addAll(old?.apps.orEmpty()) } }
    var filter by remember { mutableStateOf("") }
    Dialog(onClose, DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        ProvideTextStyle(TextStyle(fontFamily = Inter, color = Fg)) {
            Column(Modifier.fillMaxSize().background(SettingsBg).systemBarsPadding().imePadding().padding(horizontal = 16.dp)) {
                Row(Modifier.fillMaxWidth().padding(top = 8.dp).offset(x = (-8).dp), verticalAlignment = Alignment.CenterVertically) {
                    BackButton(onClose)
                    Text(if (old == null) "New group" else "Edit group", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    if (old != null) Text("Delete", color = Danger, fontSize = 14.sp,
                        modifier = Modifier.clip(CircleShape).clickable { apps.deleteGroup(index); onClose() }.padding(horizontal = 12.dp, vertical = 8.dp))
                    val ok = name.isNotBlank()
                    Box(Modifier.padding(start = 4.dp).height(38.dp).clip(CircleShape).background(if (ok) Accent else Color(0xFF3A3D45))
                        .clickable(enabled = ok) { apps.saveGroup(index, AppGroup(name.trim(), chosen.toList())); onClose() }.padding(horizontal = 18.dp),
                        contentAlignment = Alignment.Center) { Text("Save", color = if (ok) OnAccent else Muted, fontSize = 14.sp, fontWeight = FontWeight.SemiBold) }
                }
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(14.dp)).background(FieldBg).border(1.5.dp, Accent, RoundedCornerShape(14.dp))
                    .padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Field(name, { name = it }, "Group name, e.g. Work", Modifier.weight(1f), autoFocus = old == null) {}
                }
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth().height(44.dp).clip(RoundedCornerShape(14.dp)).background(FieldBg).border(1.dp, Color(0xFF2A2D33), RoundedCornerShape(14.dp))
                    .padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Ic.search, null, tint = Muted, modifier = Modifier.size(18.dp))
                    Field(filter, { filter = it }, "Find apps", Modifier.weight(1f), autoFocus = false, fontSize = 15) {}
                }
                Label("${chosen.size} selected", Modifier.padding(start = 4.dp, top = 16.dp, bottom = 8.dp))
                val list = apps.visible.filter { filter.isBlank() || it.label.contains(filter.trim(), ignoreCase = true) }
                    .sortedByDescending { it.key in chosen }
                LazyColumn(Modifier.weight(1f).clip(RoundedCornerShape(18.dp)).background(FieldBg).border(1.dp, GroupLine, RoundedCornerShape(18.dp))) {
                    items(list, key = { it.key }) { app ->
                        val on = app.key in chosen
                        Row(Modifier.fillMaxWidth().height(54.dp).clickable { if (on) chosen.remove(app.key) else chosen.add(app.key) }.padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            AppIcon(apps, app, 34.dp)
                            Text(app.label, fontSize = 15.sp, modifier = Modifier.weight(1f))
                            Box(Modifier.size(22.dp).clip(CircleShape).background(if (on) Accent else Color.Transparent)
                                .border(1.5.dp, if (on) Accent else PillLine, CircleShape), contentAlignment = Alignment.Center) {
                                if (on) Icon(GIc.check, null, tint = OnAccent, modifier = Modifier.size(14.dp))
                            }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}

internal object GIc {
    val check = line("M5 12.5l4.5 4.5L19 7.5", 2.4f)
    val plus = line("M12 5v14 M5 12h14", 1.8f)
    val more = line("M12 5.5h.01 M12 12h.01 M12 18.5h.01", 2.6f)
}
