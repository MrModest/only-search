package app.onlysearch.settings

import app.onlysearch.Accent
import app.onlysearch.actionLabel
import app.onlysearch.gesture
import app.onlysearch.AccentCard
import app.onlysearch.AccentLine
import app.onlysearch.Accents
import app.onlysearch.App
import app.onlysearch.AppGroup
import app.onlysearch.AppSpan
import app.onlysearch.Apps
import app.onlysearch.ChipLine
import app.onlysearch.Dim
import app.onlysearch.Divider
import app.onlysearch.Expr
import app.onlysearch.Fg
import app.onlysearch.Field
import app.onlysearch.FieldBg
import app.onlysearch.FieldLine
import app.onlysearch.GestureSettings
import app.onlysearch.Ic
import app.onlysearch.Knob
import app.onlysearch.Label
import app.onlysearch.MainActivity
import app.onlysearch.MaxColumns
import app.onlysearch.MaxRows
import app.onlysearch.Mono
import app.onlysearch.Muted
import app.onlysearch.OnAccent
import app.onlysearch.PillLine
import app.onlysearch.PopupBg
import app.onlysearch.Sans
import app.onlysearch.Sheet
import app.onlysearch.Soulver
import app.onlysearch.backupJson
import app.onlysearch.drawer.GroupEditor
import app.onlysearch.home.AppIcon
import app.onlysearch.home.IconShapes
import app.onlysearch.home.iconShape
import app.onlysearch.imports.iconPacks
import app.onlysearch.imports.importNova
import app.onlysearch.isLight
import app.onlysearch.line
import app.onlysearch.pick
import app.onlysearch.prefs
import app.onlysearch.restoreJson
import app.onlysearch.search.Chip
import app.onlysearch.search.ago
import app.onlysearch.search.askPermission
import app.onlysearch.search.hasContacts
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import android.text.format.DateFormat
import java.util.Date
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.core.content.edit

internal val SettingsBg get() = if (isLight) Color(0xFFF2F1EE) else if (prefs.black) Color.Black else Color(0xFF111216)
internal val GroupLine get() = pick(0xFF24272D, 0xFFE3E1DC)

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
    // Opened straight on a page (e.g. Layout in edit mode): Back goes back to where it came from, not to the hub.
    val back = { if (page == "hub" || page == start) onClose() else page = "hub" }
    BackHandler(onBack = back)
    val current = pages.firstOrNull { it.id == page }
    if (current?.content != null) SettingsPage(current.title, onBack = back) { current.content.invoke() }
    else Hub(apps, pages, onClose) { page = it }
}

private fun settingsPages(apps: Apps): List<Page> = listOf(
    Page("search", "Search", SIc.calc, true, "Search & calculator", { "Math, units, currency, time zones" },
        "calculator math units currency rates dates time zones finance natural language compute time home currency unit system metric imperial decimal point comma dot") { CalcSettings() },
    Page("sources", "Search", SIc.sources, true, "Sources & ranking",
        { (listOf("Apps") + listOfNotNull("contacts".takeIf { prefs.srcContacts }, "settings".takeIf { prefs.srcSettings }, "web".takeIf { prefs.srcWeb })).joinToString(", ") },
        "contacts people settings web engine google duckduckgo fuzzy frequent ranking") { SourcesSettings() },
    Page("home", "Layout", SIc.home, false, "Home screen", { "Grid ${prefs.columns / AppSpan} × ${prefs.rows / AppSpan} · icons ${prefs.iconSize}%" },
        "columns rows grid icon size shape labels lock layout") { HomeSettings() },
    Page("dock", "Layout", SIc.dock, false, "Dock & search bar",
        { (if (prefs.showDock) "${prefs.dockSize} icons" else "no dock") + " · " + SearchBars.first { it.first == prefs.searchBar }.third },
        "dock icons search bar position top bottom microphone voice") { DockSettings() },
    Page("drawer", "Layout", SIc.drawer, false, "App drawer & folders",
        { listOfNotNull("${prefs.drawerColumns} columns", "A–Z scrubber".takeIf { prefs.scrubber }, apps.groups.size.let { if (it == 1) "1 group" else "$it groups" }).joinToString(" · ") },
        "drawer columns suggested scrubber groups folders hidden apps unhide") { DrawerSettings(apps) },
    Page("gestures", "Behaviour", SIc.gestures, false, "Gestures",
        { "Swipe down: ${actionLabel(apps, gesture("gDown")).lowercase()} · double tap: ${actionLabel(apps, gesture("gDoubleTap")).lowercase().removeSuffix(" screen")}" },
        "gestures swipe double tap pinch home button dock lock notifications type anywhere keyboard reset query") { GestureSettings(apps) },
    Page("look", "Behaviour", SIc.look, false, "Look & feel",
        { listOfNotNull(Themes.first { it.first == prefs.theme }.second + " theme", "pure black".takeIf { prefs.black && !isLight }, "${prefs.accent} accent", "themed icons".takeIf { prefs.themedIcons }).joinToString(" · ") },
        "theme light dark system accent colour color black themed icons monochrome wallpaper dim icon pack") { LookSettings(apps) },
    Page("backup", "System", SIc.backup, false, "Backup & restore",
        { "Last backup: " + prefs.sp.getLong("lastBackup", 0L).let { if (it == 0L) "never" else ago(it) } },
        "backup restore export import json file nova launcher") { BackupSettings(apps) },
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
    Column(Modifier.fillMaxSize().background(SettingsBg).imePadding().verticalScroll(rememberScrollState()).systemBarsPadding()
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
                .border(1.dp, FieldLine, RoundedCornerShape(14.dp)).padding(horizontal = 14.dp),
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
        Box(Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(if (p.accent) AccentCard else GroupLine), contentAlignment = Alignment.Center) {
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
    Column(Modifier.fillMaxSize().background(SettingsBg).verticalScroll(rememberScrollState()).systemBarsPadding()
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
internal fun Switch(checked: Boolean, label: String, onChange: ((Boolean) -> Unit)?) {
    Box(
        Modifier.size(48.dp, 28.dp).clip(CircleShape).background(if (checked) Accent else Knob)
            .then(if (onChange == null) Modifier else Modifier.toggleable(checked, role = Role.Switch, onValueChange = onChange).semantics { contentDescription = label })
            .padding(3.dp),
        contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart,
    ) { Box(Modifier.size(22.dp).clip(CircleShape).background(if (checked) OnAccent else Muted)) }
}

@Composable
internal fun SwitchRow(title: String, subtitle: String?, checked: Boolean, monoSubtitle: Boolean = false, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).toggleable(checked, role = Role.Switch, onValueChange = onChange).padding(start = 14.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, fontSize = 15.sp, lineHeight = 18.sp)
            if (subtitle != null) Text(subtitle, color = Muted, fontSize = if (monoSubtitle) 11.sp else 12.sp, lineHeight = 15.sp, fontFamily = if (monoSubtitle) Mono else Sans)
        }
        Switch(checked, title, null)
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

/** A [ValueRow] that opens a menu of (id, label) options, the selected one in the accent colour. */
@Composable
private fun ValueMenu(title: String, value: String, options: List<Pair<String, String>>, selected: String, modifier: Modifier = Modifier, onPick: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        ValueRow(title, value) { open = true }
        Box(Modifier.align(Alignment.BottomEnd)) {
            DropdownMenu(open, { open = false }, modifier, shape = RoundedCornerShape(16.dp), containerColor = PopupBg) {
                options.forEach { (id, name) ->
                    DropdownMenuItem({ Text(name, color = if (id == selected) Accent else Fg) }, { onPick(id); open = false })
                }
            }
        }
    }
}

// ---------- Pages ----------

@Composable
private fun HomeSettings() {
    // Shown in app cells; the grid underneath is in half cells, and an odd half cell left over rounds down.
    HomePreview()
    Group(null, listOf(
        { Stepper("Columns", prefs.columns / AppSpan, 3..MaxColumns / AppSpan) { prefs.columns = it * AppSpan } },
        { Stepper("Rows", prefs.rows / AppSpan, 1..MaxRows / AppSpan) { prefs.rows = it * AppSpan } },
        {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row {
                    Text("Icon size", fontSize = 15.sp, modifier = Modifier.weight(1f))
                    Text("${prefs.iconSize}%", color = Accent, fontSize = 13.sp, fontFamily = Mono)
                }
                ThinSlider("Icon size", prefs.iconSize, 70..130, 5) { prefs.iconSize = it }
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
                        ) { Box(Modifier.size(28.dp).clip(iconShape(name, 28.dp)).background(if (on) Accent else pick(0xFF6B6E76, 0xFFB5B2AC))) }
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
    val dot = pick(0xFF24262C, 0xFFE3E1DC)
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
        val columns = prefs.columns / AppSpan
        val cell = 26.dp * prefs.iconSize / 100 * 4 / maxOf(4, columns)
        Column(verticalArrangement = Arrangement.spacedBy(if (prefs.labels) 8.dp else 5.dp)) {
            repeat(minOf(prefs.rows / AppSpan, 3)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    repeat(columns) {
                        Box(Modifier.size(cell).clip(iconShape(prefs.iconShape, cell)).background(Divider)
                            .border(1.dp, PillLine, iconShape(prefs.iconShape, cell)))
                    }
                }
            }
        }
        Box(Modifier.padding(top = 12.dp).fillMaxWidth().height(20.dp).clip(CircleShape).background(Divider))
    }
}

@Composable
internal fun Stepper(title: String, value: Int, range: IntRange, onChange: (Int) -> Unit) {
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
        Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(Divider).border(1.dp, PillLine, RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, onClick = onClick).semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) { Text(text, fontSize = 18.sp, color = if (enabled) Fg else Dim) }
}

/** Thin track with a round thumb, like the mock-up's range input. Tap or drag; snaps to [step]. */
@Composable
private fun ThinSlider(label: String, value: Int, range: IntRange, step: Int, onChange: (Int) -> Unit) {
    val frac = (value - range.first).toFloat() / (range.last - range.first)
    val current by rememberUpdatedState(value)
    fun pickFrac(f: Float) {
        val v = range.first + (f.coerceIn(0f, 1f) * (range.last - range.first) / step).roundToInt() * step
        if (v != current) onChange(v)
    }
    fun pick(x: Float, width: Int) = pickFrac(x / width)
    BoxWithConstraints(
        Modifier.fillMaxWidth().height(32.dp)
            .semantics {
                contentDescription = label
                progressBarRangeInfo = ProgressBarRangeInfo(value.toFloat(), range.first.toFloat()..range.last.toFloat())
                setProgress { t -> pickFrac((t - range.first) / (range.last - range.first)); true }
            }
            .pointerInput(range) { detectTapGestures { pick(it.x, size.width) } }
            .pointerInput(range) { detectHorizontalDragGestures { c, _ -> pick(c.position.x, size.width) } },
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(Modifier.fillMaxWidth().height(4.dp).clip(CircleShape).background(Knob))
        Box(Modifier.fillMaxWidth(frac).height(4.dp).clip(CircleShape).background(Accent))
        Box(Modifier.offset(x = (maxWidth - 20.dp) * frac).size(20.dp).clip(CircleShape).background(Accent).border(3.dp, SettingsBg, CircleShape))
    }
}

@Composable
private fun DrawerSettings(apps: Apps) {
    var editing by remember { mutableStateOf<Int?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Group(null, listOf(
            { Stepper("Columns", prefs.drawerColumns, 3..6) { prefs.drawerColumns = it } },
            { SwitchRow("Suggested apps", "Your most used apps at the top", prefs.suggested) { prefs.suggested = it } },
            { SwitchRow("A–Z scrubber", "Letter strip on the right edge", prefs.scrubber) { prefs.scrubber = it } },
        ))
        Group("Groups", apps.groups.mapIndexed<AppGroup, @Composable () -> Unit> { i, g -> { ValueRow(g.name, "${g.apps.size} apps", Muted) { editing = i } } } +
            listOf<@Composable () -> Unit>({ ValueRow("New group", "+", Accent) { editing = -1 } }))
        val hidden = apps.hidden.mapNotNull(apps::byKey)
        if (hidden.isNotEmpty()) Group("Hidden apps", hidden.map<App, @Composable () -> Unit> { app ->
            {
                Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    AppIcon(apps, app, 34.dp)
                    Text(app.label, fontSize = 15.sp, modifier = Modifier.weight(1f))
                    Chip("Unhide", Color.Transparent, ChipLine, Sans, 13) { apps.hide(app, false) }
                }
            }
        })
    }
    editing?.let { i -> GroupEditor(apps, i) { editing = null } }
}

@Composable
private fun SourcesSettings() {
    val context = LocalContext.current
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { prefs.srcContacts = it }
    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Group("Search in", listOf(
            { SwitchRow("Apps", "Always on", true) {} },
            {
                SwitchRow("Contacts", if (hasContacts(context)) "Names, with call and message buttons" else "Asks for contacts access", prefs.srcContacts) { on ->
                    if (on && !hasContacts(context)) askPermission(context, android.Manifest.permission.READ_CONTACTS, ask) else prefs.srcContacts = on
                }
            },
            { SwitchRow("Settings", "System settings and this launcher's pages", prefs.srcSettings) { prefs.srcSettings = it } },
            { SwitchRow("Web search", "A last row that searches the web", prefs.srcWeb) { prefs.srcWeb = it } },
        ))
        Group("Ranking", listOf(
            { SwitchRow("Prefer apps you use", "Frequent apps win ties", prefs.boostFrequent) { prefs.boostFrequent = it } },
            { SwitchRow("Fuzzy matching", "“gml” finds Gmail", prefs.fuzzy) { prefs.fuzzy = it } },
            { ValueMenu("Search engine", prefs.engine, listOf("Google", "DuckDuckGo", "Bing", "Startpage").map { it to it }, prefs.engine) { prefs.engine = it } },
        ))
    }
}

internal val Themes = listOf("system" to "System", "light" to "Light", "dark" to "Dark")
internal val UnitSystems = listOf("metric" to "Metric", "imperial" to "Imperial (UK)", "customaryUS" to "US customary")
internal val Currencies = listOf("USD" to "$", "EUR" to "€", "GBP" to "£", "JPY" to "¥", "CHF" to "CHF", "CAD" to "$", "AUD" to "$", "CNY" to "¥",
    "INR" to "₹", "RUB" to "₽", "SEK" to "kr", "NOK" to "kr", "DKK" to "kr", "PLN" to "zł", "CZK" to "Kč", "TRY" to "₺", "BRL" to "R$", "MXN" to "$",
    "KRW" to "₩", "SGD" to "$", "HKD" to "$", "NZD" to "$", "ZAR" to "R", "ILS" to "₪", "AED" to "د.إ", "UAH" to "₴", "KZT" to "₸", "GEL" to "₾", "AMD" to "֏")

@Composable
private fun CalcSettings() {
    val scope = rememberCoroutineScope()
    var preview by remember { mutableStateOf("") }
    LaunchedEffect(prefs.unitSystem) { preview = Soulver.evaluate("\$48 + 15% tip") }
    val ratesAt = prefs.sp.getLong("ratesAt", 0L)
    Row(
        Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 18.dp).clip(RoundedCornerShape(16.dp)).background(AccentCard)
            .border(1.dp, AccentLine, RoundedCornerShape(16.dp)).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("\$48 + 15% tip", color = Expr, fontSize = 13.sp, fontFamily = Mono, modifier = Modifier.weight(1f))
        Text(preview, color = Accent, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        Text("preview", color = Muted, fontSize = 10.sp, fontFamily = Mono)
    }
    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Group("Understand queries", listOf(
            { SwitchRow("Math & expressions", "(128*3.5)/4 + 12^2", prefs.calcMath, monoSubtitle = true) { prefs.calcMath = it } },
            { SwitchRow("Units", "5 km in miles · 72 F in C", prefs.calcUnits, monoSubtitle = true) { prefs.calcUnits = it } },
            {
                SwitchRow("Currency", "rates refresh every 6 h" + if (ratesAt > 0) " · updated ${ago(ratesAt)}" else "", prefs.calcCurrency, monoSubtitle = true) { prefs.calcCurrency = it }
            },
            { SwitchRow("Dates & time zones", "today + 3 weeks · time in Tokyo", prefs.calcDates, monoSubtitle = true) { prefs.calcDates = it } },
            { SwitchRow("Finance", "interest · tip · % of · % change", prefs.calcFinance, monoSubtitle = true) { prefs.calcFinance = it } },
            { SwitchRow("Natural language", "\$10 for lunch + 15% tip", prefs.calcNatural, monoSubtitle = true) { prefs.calcNatural = it } },
            { SwitchRow("Show compute time", "e.g. 16.5 ms in the header", prefs.computeTime, monoSubtitle = true) { prefs.computeTime = it } },
        ))
        Group("Defaults", listOf(
            {
                ValueMenu("Home currency", "${prefs.homeCurrency} ${Currencies.firstOrNull { it.first == prefs.homeCurrency }?.second.orEmpty()}".trim(),
                    Currencies.map { (code, sym) -> code to "$code  $sym" }, prefs.homeCurrency, Modifier.heightIn(max = 360.dp)) { prefs.homeCurrency = it }
            },
            {
                ValueMenu("Unit system", UnitSystems.first { it.first == prefs.unitSystem }.second, UnitSystems, prefs.unitSystem) {
                    prefs.unitSystem = it
                    scope.launch { Soulver.setUnitSystem(it) }
                }
            },
            { SwitchRow("Dot and comma are both decimal points", "12.5 = 12,5 · no thousands separators", prefs.anyDecimal, monoSubtitle = true) { prefs.anyDecimal = it } },
            {
                ValueMenu("↵ on a result", if (prefs.enterCopies == "both") "Copy expression = answer" else "Copy answer",
                    listOf("answer" to "Copy answer", "both" to "Copy expression = answer"), prefs.enterCopies) { prefs.enterCopies = it }
            },
        ))
    }
}

internal val SearchBars = listOf(
    Triple("bottom", "Below the dock", "search bar at bottom"), Triple("above", "Above the dock", "search bar above dock"),
    Triple("top", "Top of the screen", "search bar at top"), Triple("hidden", "Hidden", "no search bar"),
)

@Composable
private fun DockSettings() {
    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Group("Dock", listOf(
            { SwitchRow("Show dock", "Apps that stay on every page", prefs.showDock) { prefs.showDock = it } },
            { Stepper("Icons", prefs.dockSize, 3..6) { prefs.dockSize = it } },
        ))
        Group("Search bar", listOf(
            { ValueMenu("Position", SearchBars.first { it.first == prefs.searchBar }.second, SearchBars.map { (id, name) -> id to name }, prefs.searchBar) { prefs.searchBar = it } },
            { SwitchRow("Voice button", "Mic at the end of the bar", prefs.mic) { prefs.mic = it } },
        ))
        if (prefs.searchBar == "hidden") Text("Without the bar, swipe down on home (Gestures) or use the drawer's search to find things.",
            color = Muted, fontSize = 13.sp, lineHeight = 19.sp, modifier = Modifier.padding(horizontal = 4.dp))
    }
}

@Composable
private fun LookSettings(apps: Apps) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val packs = remember { iconPacks(context) }
    var choosing by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Group("Icons", listOf {
            Box {
                ValueRow("Icon pack", packs.firstOrNull { it.first == prefs.iconPack }?.second ?: "App icons") { choosing = true }
                Box(Modifier.align(Alignment.BottomEnd)) {
                    DropdownMenu(choosing, { choosing = false }, containerColor = PopupBg) {
                        (listOf("" to "App icons") + packs).forEach { (pkg, name) ->
                            DropdownMenuItem({ Text(name, color = if (pkg == prefs.iconPack) Accent else Fg) }, {
                                choosing = false
                                if (pkg != prefs.iconPack) { prefs.iconPack = pkg; scope.launch { apps.reloadIcons() } }
                            })
                        }
                    }
                }
            }
        })
        if (packs.isEmpty()) Text("No icon packs installed. Packs made for Nova or ADW work, e.g. ones exported by Icon Pack Studio.",
            color = Dim, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 4.dp).offset(y = (-10).dp))
        var picking by remember { mutableStateOf(false) }
        if (picking) AccentPicker { picking = false }
        Group("Accent", listOf {
            Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                val custom = prefs.accent.startsWith("#")
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable { picking = true }.padding(4.dp).semantics { contentDescription = "custom" }) {
                    Box(Modifier.size(40.dp).clip(CircleShape)
                        .background(androidx.compose.ui.graphics.Brush.sweepGradient(listOf(Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red)))
                        .border(if (custom) 3.dp else 0.dp, if (custom) Fg else Color.Transparent, CircleShape))
                    Text("Custom", color = if (custom) Fg else Muted, fontSize = 12.sp)
                }
                Accents.forEach { (name, dark, light) ->
                    val color = if (isLight) light else dark
                    val on = prefs.accent == name
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable { prefs.accent = name }.padding(4.dp).semantics { contentDescription = name }) {
                        Box(Modifier.size(40.dp).clip(CircleShape).background(color).border(if (on) 3.dp else 0.dp, if (!on) Color.Transparent else if (name == "white") Dim else Fg, CircleShape))
                        Text(name.replaceFirstChar { it.uppercase() }, color = if (on) Fg else Muted, fontSize = 12.sp)
                    }
                }
            }
        })
        Group("Theme", listOf(
            { ValueMenu("Theme", Themes.first { it.first == prefs.theme }.second, Themes, prefs.theme) { prefs.theme = it } },
            { SwitchRow("Pure black", "Black sheets for OLED screens in the dark theme", prefs.black) { prefs.black = it } },
            {
                SwitchRow("Themed icons", if (android.os.Build.VERSION.SDK_INT >= 33) "Monochrome icons in the accent colour, where apps provide one" else "Needs Android 13",
                    prefs.themedIcons) { prefs.themedIcons = it && android.os.Build.VERSION.SDK_INT >= 33 }
            },
            {
                Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row {
                        Column(Modifier.weight(1f)) {
                            Text("Dim wallpaper", fontSize = 15.sp)
                            Text("Keeps the clock readable on bright wallpapers", color = Muted, fontSize = 12.sp)
                        }
                        Text("${prefs.dim}%", color = Accent, fontSize = 13.sp, fontFamily = Mono)
                    }
                    ThinSlider("Dim wallpaper", prefs.dim, 0..60, 5) { prefs.dim = it }
                }
            },
        ))
    }
}

/** Hue, saturation and brightness sliders for a custom accent; it applies as you drag. */
@Composable
private fun AccentPicker(onClose: () -> Unit) {
    val start = remember { FloatArray(3).also { android.graphics.Color.colorToHSV(prefs.accentColor.toArgb(), it) } }
    var hue by remember { mutableStateOf(start[0].roundToInt()) }
    var sat by remember { mutableStateOf((start[1] * 100).roundToInt()) }
    var bri by remember { mutableStateOf((start[2] * 100).roundToInt()) }
    val initial = remember { Triple(hue, sat, bri) }
    LaunchedEffect(hue, sat, bri) {
        if (Triple(hue, sat, bri) == initial) return@LaunchedEffect // just opening it keeps a named accent
        val argb = android.graphics.Color.HSVToColor(floatArrayOf(hue.toFloat(), sat / 100f, bri / 100f))
        prefs.accent = String.format("#%06X", argb and 0xFFFFFF)
    }
    androidx.compose.ui.window.Dialog(onClose) {
        Column(Modifier.clip(RoundedCornerShape(22.dp)).background(PopupBg).border(1.dp, PillLine, RoundedCornerShape(22.dp)).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(36.dp).clip(CircleShape).background(Accent))
                Text("Custom accent", color = Fg, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Text(String.format("#%06X", prefs.accentColor.toArgb() and 0xFFFFFF), color = Muted, fontSize = 13.sp, fontFamily = Mono)
            }
            listOf(Triple("Hue", hue, 0..360), Triple("Saturation", sat, 0..100), Triple("Brightness", bri, 0..100)).forEach { (label, v, range) ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(label, color = Muted, fontSize = 12.sp)
                    ThinSlider(label, v, range, 1) { when (label) { "Hue" -> hue = it; "Saturation" -> sat = it; else -> bri = it } }
                }
            }
            Text("Done", color = Accent, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.align(Alignment.End).clip(CircleShape).clickable(onClick = onClose).padding(10.dp))
        }
    }
}

/** A fresh start after a restore, so no saved screen state (like the shown page) outlives the old layout. */
private fun restart(context: Context) {
    context.startActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
}

@Composable
private fun BackupSettings(apps: Apps) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var nova by remember { mutableStateOf<android.net.Uri?>(null) }
    val openNova = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { nova = it }
    var last by remember { mutableStateOf(prefs.sp.getLong("lastBackup", 0L)) }
    var restoring by remember { mutableStateOf<String?>(null) }
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            runCatching { withContext(Dispatchers.IO) { context.contentResolver.openOutputStream(uri)!!.use { it.write(backupJson().toByteArray()) } } }
                .onSuccess {
                    last = System.currentTimeMillis(); prefs.sp.edit { putLong("lastBackup", last) }
                    android.widget.Toast.makeText(context, "Backup saved", android.widget.Toast.LENGTH_SHORT).show()
                }
                .onFailure { android.widget.Toast.makeText(context, "Couldn't save: ${it.message}", android.widget.Toast.LENGTH_LONG).show() }
        }
    }
    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            runCatching { withContext(Dispatchers.IO) { context.contentResolver.openInputStream(uri)!!.use { it.readBytes().decodeToString() } } }
                .onSuccess { restoring = it }
                .onFailure { android.widget.Toast.makeText(context, "Couldn't read the file", android.widget.Toast.LENGTH_LONG).show() }
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Group(null, listOf(
            { ValueRow("Back up now", if (last == 0L) "never backed up" else "last ${ago(last)}", Muted) {
                save.launch("only-search-backup-" + java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date()) + ".json")
            } },
            { ValueRow("Restore from a file", "", Muted) { open.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) } },
        ))
        Group("Other launchers", listOf(
            { ValueRow("Import from Nova Launcher", ".novabackup", Muted) { openNova.launch(arrayOf("*/*")) } },
        ))
        Text("A backup holds your home pages, dock, drawer groups, hidden apps, custom names, gestures, all settings and calculator history. " +
            "Widgets come back on this phone unless removed since.", color = Muted, fontSize = 13.sp, lineHeight = 19.sp,
            modifier = Modifier.padding(horizontal = 4.dp))
    }
    nova?.let { uri ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { nova = null }, containerColor = PopupBg,
            title = { Text("Import from Nova?", fontSize = 18.sp) },
            text = { Text("Your home pages, dock, folders and drawer groups are replaced by Nova's, along with the settings that match. " +
                "Widgets are added again one by one; Android may ask to allow each, and some open their own settings. The launcher restarts.", color = Muted) },
            confirmButton = {
                Text("Import", color = Accent, fontSize = 15.sp, modifier = Modifier.clip(CircleShape).clickable {
                    nova = null
                    scope.launch {
                        runCatching { importNova(context, uri, apps) }
                            .onSuccess { android.widget.Toast.makeText(context, it, android.widget.Toast.LENGTH_LONG).show(); restart(context) }
                            .onFailure { android.widget.Toast.makeText(context, it.message ?: "Not a Nova backup", android.widget.Toast.LENGTH_LONG).show() }
                    }
                }.padding(12.dp))
            },
            dismissButton = { Text("Cancel", color = Muted, fontSize = 15.sp, modifier = Modifier.clip(CircleShape).clickable { nova = null }.padding(12.dp)) },
        )
    }
    restoring?.let { json ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { restoring = null }, containerColor = PopupBg,
            title = { Text("Replace your setup?", fontSize = 18.sp) },
            text = { Text("Your current layout and settings are replaced by the backup. The launcher restarts.", color = Muted) },
            confirmButton = {
                Text("Restore", color = Accent, fontSize = 15.sp, modifier = Modifier.clip(CircleShape).clickable {
                    restoring = null
                    scope.launch {
                        runCatching { withContext(Dispatchers.IO) { restoreJson(json) } }
                            .onSuccess { restart(context) }
                            .onFailure { android.widget.Toast.makeText(context, "Not a valid backup file", android.widget.Toast.LENGTH_LONG).show() }
                    }
                }.padding(12.dp))
            },
            dismissButton = { Text("Cancel", color = Muted, fontSize = 15.sp, modifier = Modifier.clip(CircleShape).clickable { restoring = null }.padding(12.dp)) },
        )
    }
}
