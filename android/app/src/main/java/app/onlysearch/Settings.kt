package app.onlysearch

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import android.text.format.DateFormat
import java.util.Date
import kotlin.math.roundToInt
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.core.content.edit
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

/** User settings. Compose-observable; each change is saved to the launcher prefs straight away. */
class Prefs(context: Context) {
    val sp: SharedPreferences = context.getSharedPreferences("launcher", Context.MODE_PRIVATE)

    private inner class P<T>(private val key: String, def: T) : ReadWriteProperty<Any?, T> {
        @Suppress("UNCHECKED_CAST")
        private val state = mutableStateOf((sp.all[key] as? T) ?: def)
        override fun getValue(thisRef: Any?, property: KProperty<*>) = state.value
        override fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
            state.value = value
            sp.edit {
                when (value) {
                    is Boolean -> putBoolean(key, value)
                    is Int -> putInt(key, value)
                    is String -> putString(key, value)
                }
            }
        }
    }

    // Home screen
    var columns by P("set.columns", 4)
    var rows by P("set.rows", 6)
    var iconSize by P("set.iconSize", 100)
    var iconShape by P("set.iconShape", "squircle")
    var labels by P("set.labels", true)
    var lockLayout by P("set.lockLayout", false)
}

lateinit var prefs: Prefs

internal val SettingsBg = Color(0xFF111216)
internal val GroupLine = Color(0xFF24272D)

internal object SIc {
    val back = line("M15 5l-7 7 7 7", 2f)
    val next = line("M9 6l6 6-6 6", 1.8f)
    val calc = line("M6 3h12v18H6z M9 7h6 M9 11.5h.01 M12 11.5h.01 M15 11.5h.01 M9 15h.01 M12 15h.01 M15 15h.01")
    val sources = line("M4 6h16 M4 12h10 M4 18h6")
    val home = line("M4 11l8-7 8 7v9H4z")
    val dock = line("M3 15h18v5H3z M6 10h12")
    val drawer = line("M4 4h7v7H4z M13 4h7v7h-7z M4 13h7v7H4z M13 13h7v7h-7z")
    val gestures = line("M9 11V5a2 2 0 0 1 4 0v6 M13 10a2 2 0 0 1 4 0v4a7 7 0 0 1-7 7 6 6 0 0 1-5-3l-2-4a2 2 0 0 1 3.4-2L9 15")
    val look = line("M12 21a9 9 0 1 1 9-9c0 2-1.5 3-3 3h-2a2 2 0 0 0-1.5 3.3A2 2 0 0 1 12 21z M7.5 11h.01 M11 7h.01 M16 8.5h.01")
    val backup = line("M4 14v6h16v-6 M12 3v12 M7 10l5 5 5-5")
}

/** One settings page: its hub row, the words it can be found by, and its content. */
internal class Page(
    val id: String, val group: String, val icon: ImageVector, val accent: Boolean, val title: String,
    val subtitle: () -> String, val keywords: String, val content: (@Composable () -> Unit)?,
)

@Composable
fun SettingsScreen(apps: Apps, start: String, onClose: () -> Unit) {
    var page by remember { mutableStateOf(start) }
    val pages = remember { settingsPages(apps) }
    BackHandler { if (page == "hub") onClose() else page = "hub" }
    val current = pages.firstOrNull { it.id == page }
    if (current?.content != null) SettingsPage(current.title, onBack = { page = "hub" }) { current.content.invoke() }
    else Hub(apps, pages, onClose) { page = it }
}

private fun settingsPages(apps: Apps): List<Page> = listOf(
    Page("search", "Search", SIc.calc, true, "Search & calculator", { "Math, units, currency, time zones" }, "", null),
    Page("sources", "Search", SIc.sources, true, "Sources & ranking", { "Apps, contacts, settings, web" }, "", null),
    Page("home", "Layout", SIc.home, false, "Home screen", { "Grid ${prefs.columns} × ${prefs.rows} · icons ${prefs.iconSize}%" },
        "columns rows grid icon size shape labels lock layout") { HomeSettings() },
    Page("dock", "Layout", SIc.dock, false, "Dock & search bar", { "4 icons · search bar at bottom" }, "", null),
    Page("drawer", "Layout", SIc.drawer, false, "App drawer & folders", { "Vertical · A–Z scrubber" }, "", null),
    Page("gestures", "Behaviour", SIc.gestures, false, "Gestures", { "Swipe up: app drawer" }, "", null),
    Page("look", "Behaviour", SIc.look, false, "Look & feel", { "Dark · sky accent" }, "", null),
    Page("backup", "System", SIc.backup, false, "Backup & restore", { "Last backup: never" }, "", null),
)

private fun isDefaultHome(context: Context): Boolean =
    if (Build.VERSION.SDK_INT >= 29) context.getSystemService(RoleManager::class.java).isRoleHeld(RoleManager.ROLE_HOME)
    else context.packageManager.resolveActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0)
        ?.activityInfo?.packageName == context.packageName

@Composable
private fun Hub(apps: Apps, pages: List<Page>, onClose: () -> Unit, onOpen: (String) -> Unit) {
    val context = LocalContext.current
    var filter by remember { mutableStateOf("") }
    val version = remember { context.packageManager.getPackageInfo(context.packageName, 0).versionName }
    val isDefault = remember { isDefaultHome(context) }
    Column(Modifier.fillMaxSize().background(SettingsBg).systemBarsPadding().imePadding().verticalScroll(rememberScrollState())
        .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp)) {
        BackButton(onClose, Modifier.offset(x = (-8).dp))
        Text("Settings", fontSize = 32.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.02).em, modifier = Modifier.padding(vertical = 4.dp))
        Text(
            "launcher $version · " + if (isDefault) "set as default ✓" else "set as default →",
            color = if (isDefault) Muted else Accent, fontSize = 11.sp, fontFamily = Mono,
            modifier = Modifier.padding(bottom = 16.dp).clickable(enabled = !isDefault) {
                apps.homeSettings()
            },
        )
        Row(
            Modifier.fillMaxWidth().height(46.dp).clip(RoundedCornerShape(14.dp)).background(FieldBg)
                .border(1.dp, Color(0xFF2A2D33), RoundedCornerShape(14.dp)).padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(Ic.search, null, tint = Muted, modifier = Modifier.size(18.dp))
            Field(filter, { filter = it }, "Search settings", Modifier.weight(1f), autoFocus = false, fontSize = 15) {}
        }
        Spacer(Modifier.height(18.dp))
        val q = filter.trim()
        val shown = pages.filter { q.isEmpty() || "${it.title} ${it.subtitle()} ${it.keywords}".contains(q, ignoreCase = true) }
        if (shown.isEmpty()) Text("Nothing matches “$q”", color = Muted, fontSize = 14.sp, modifier = Modifier.padding(4.dp))
        Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
            shown.groupBy { it.group }.forEach { (group, list) ->
                Group(group, list.map { p -> { NavRow(p) { onOpen(p.id) } } })
            }
        }
    }
}

@Composable
private fun NavRow(p: Page, onClick: () -> Unit) {
    val enabled = p.content != null
    Row(
        Modifier.fillMaxWidth().heightIn(min = 58.dp).clickable(enabled = enabled, onClick = onClick).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(if (p.accent) Color(0xFF16232F) else GroupLine), contentAlignment = Alignment.Center) {
            Icon(p.icon, null, tint = if (p.accent) Accent else Fg, modifier = Modifier.size(18.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(p.title, fontSize = 15.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium, color = if (enabled) Fg else Dim)
            Text(p.subtitle(), color = Muted, fontSize = 12.sp, lineHeight = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Icon(SIc.next, null, tint = Dim, modifier = Modifier.size(16.dp))
    }
}

// ---------- Building blocks shared by the settings pages ----------

@Composable
internal fun BackButton(onClick: () -> Unit, modifier: Modifier = Modifier) =
    Box(modifier.size(44.dp).clip(CircleShape).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(SIc.back, "Back", tint = Fg, modifier = Modifier.size(22.dp))
    }

@Composable
internal fun SettingsPage(title: String, onBack: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().background(SettingsBg).systemBarsPadding().verticalScroll(rememberScrollState())
        .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp)) {
        Row(Modifier.offset(x = (-8).dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            BackButton(onBack)
            Text(title, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(12.dp))
        content()
    }
}

/** A labelled card of rows with hairlines between them. */
@Composable
internal fun Group(title: String?, rows: List<@Composable () -> Unit>, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (title != null) Label(title, Modifier.padding(start = 4.dp))
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(FieldBg).border(1.dp, GroupLine, RoundedCornerShape(18.dp))) {
            rows.forEachIndexed { i, row ->
                if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(GroupLine))
                row()
            }
        }
    }
}

@Composable
internal fun Switch(checked: Boolean, label: String, onChange: (Boolean) -> Unit) {
    Box(
        Modifier.size(48.dp, 28.dp).clip(CircleShape).background(if (checked) Accent else Color(0xFF3A3D45))
            .clickable { onChange(!checked) }.padding(3.dp),
        contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart,
    ) { Box(Modifier.size(22.dp).clip(CircleShape).background(if (checked) OnAccent else Muted)) }
}

@Composable
internal fun SwitchRow(title: String, subtitle: String?, checked: Boolean, monoSubtitle: Boolean = false, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable { onChange(!checked) }.padding(start = 14.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, fontSize = 15.sp, lineHeight = 18.sp)
            if (subtitle != null) Text(subtitle, color = Muted, fontSize = if (monoSubtitle) 11.sp else 12.sp, lineHeight = 15.sp, fontFamily = if (monoSubtitle) Mono else Inter)
        }
        Switch(checked, title, onChange)
    }
}

@Composable
internal fun ValueRow(title: String, value: String, valueColor: Color = Accent, icon: ImageVector? = null, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = if (icon != null) 56.dp else 50.dp).clickable(onClick = onClick).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (icon != null) Box(Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(GroupLine), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = Fg, modifier = Modifier.size(18.dp))
        }
        Text(title, fontSize = 15.sp, modifier = Modifier.weight(1f).padding(start = if (icon != null) 2.dp else 2.dp))
        Text(value, color = valueColor, fontSize = if (icon != null) 13.sp else 14.sp, maxLines = 1)
        Icon(SIc.next, null, tint = Dim, modifier = Modifier.size(16.dp))
    }
}

// ---------- Pages ----------

@Composable
private fun HomeSettings() {
    HomePreview()
    Group(null, listOf(
        { Stepper("Columns", prefs.columns, 3..6) { prefs.columns = it } },
        { Stepper("Rows", prefs.rows, 1..8) { prefs.rows = it } },
        {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row {
                    Text("Icon size", fontSize = 15.sp, modifier = Modifier.weight(1f))
                    Text("${prefs.iconSize}%", color = Accent, fontSize = 13.sp, fontFamily = Mono)
                }
                Slider(
                    prefs.iconSize.toFloat(), { prefs.iconSize = (it / 5).roundToInt() * 5 }, valueRange = 70f..130f, steps = 11,
                    colors = SliderDefaults.colors(thumbColor = Accent, activeTrackColor = Accent, inactiveTrackColor = Color(0xFF3A3D45),
                        activeTickColor = Color.Transparent, inactiveTickColor = Color.Transparent),
                )
            }
        },
        {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Icon shape", fontSize = 15.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconShapes.forEach { name ->
                        val on = prefs.iconShape == name
                        Box(
                            Modifier.weight(1f).height(56.dp).clip(RoundedCornerShape(14.dp)).background(if (on) AccentCard else Color.Transparent)
                                .border(if (on) 1.5.dp else 1.dp, if (on) Accent else ChipLine, RoundedCornerShape(14.dp))
                                .clickable { prefs.iconShape = name }.semantics { contentDescription = name },
                            contentAlignment = Alignment.Center,
                        ) { Box(Modifier.size(28.dp).clip(iconShape(name, 28.dp)).background(if (on) Accent else Color(0xFF6B6E76))) }
                    }
                }
            }
        },
        { SwitchRow("Show labels", null, prefs.labels) { prefs.labels = it } },
        { SwitchRow("Lock layout", "Prevent accidental moves", prefs.lockLayout) { prefs.lockLayout = it } },
    ))
}

/** Small live model of the home screen: clock, the icon grid at the chosen size and shape, search bar. */
@Composable
private fun HomePreview() {
    val dot = Color(0xFF24262C)
    Column(
        Modifier.fillMaxWidth().wrapContentWidth().padding(top = 0.dp, bottom = 18.dp).size(170.dp, 250.dp)
            .clip(RoundedCornerShape(22.dp)).background(Sheet).border(1.dp, PillLine, RoundedCornerShape(22.dp))
            .drawBehind {
                val step = 10.dp.toPx()
                var y = step / 2
                while (y < size.height) { var x = step / 2; while (x < size.width) { drawCircle(dot, 1.dp.toPx(), Offset(x, y)); x += step }; y += step }
            }
            .padding(start = 12.dp, end = 12.dp, top = 18.dp, bottom = 12.dp)
    ) {
        Text(DateFormat.format("h:mm", Date()).toString(), fontSize = 30.sp, fontWeight = FontWeight.Light, letterSpacing = (-0.04).em)
        Spacer(Modifier.weight(1f))
        val cell = 26.dp * prefs.iconSize / 100 * 4 / maxOf(4, prefs.columns)
        Column(verticalArrangement = Arrangement.spacedBy(if (prefs.labels) 8.dp else 5.dp)) {
            repeat(minOf(prefs.rows, 4)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    repeat(prefs.columns) {
                        Box(Modifier.size(cell).clip(iconShape(prefs.iconShape, cell)).background(Color(0xFF23262C))
                            .border(1.dp, PillLine, iconShape(prefs.iconShape, cell)))
                    }
                }
            }
        }
        Box(Modifier.padding(top = 12.dp).fillMaxWidth().height(20.dp).clip(CircleShape).background(Color(0xFF23262C)))
    }
}

@Composable
private fun Stepper(title: String, value: Int, range: IntRange, onChange: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().height(56.dp).padding(start = 14.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, fontSize = 15.sp, modifier = Modifier.weight(1f))
        StepButton("−", "Fewer ${title.lowercase()}", value > range.first) { onChange(value - 1) }
        Text("$value", color = Accent, fontSize = 15.sp, fontFamily = Mono, textAlign = TextAlign.Center, modifier = Modifier.width(40.dp))
        StepButton("+", "More ${title.lowercase()}", value < range.last) { onChange(value + 1) }
    }
}

@Composable
private fun StepButton(text: String, label: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFF23262C)).border(1.dp, PillLine, RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, onClick = onClick).semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) { Text(text, fontSize = 18.sp, color = if (enabled) Fg else Dim) }
}
