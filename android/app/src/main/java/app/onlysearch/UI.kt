package app.onlysearch

import android.appwidget.AppWidgetProviderInfo
import android.content.Intent
import android.text.format.DateFormat
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.Date

// Design D (docs/DESIGN.md): tight padding, near-square corners, amber accent on a dark sheet.
private val Sheet = Color(0xFF17191C)
private val BarBg = Color(0xFF1C1F22)
private val Line = Color(0xFF2C3135)
private val Selected = Color(0xFF23272B)
private val Accent = Color(0xFFF5B04A)
private val Muted = Color(0xFF7D868E)
private val Fg = Color(0xFFE8EAED)
private val Glass = Color(0x6B12161E)
private val Scrim = Color(0xD90E1116)
private val Mono = FontFamily.Monospace
private val Small = RoundedCornerShape(5.dp)
private val TextShadow = Shadow(Color(0x66000000), Offset(0f, 1f), 3f)

enum class Screen { Home, Drawer, Search }

@Composable
fun Launcher(apps: Apps, widgets: Widgets, resetTick: Int, initialQuery: String?) {
    var screen by remember { mutableStateOf(if (initialQuery != null) Screen.Search else Screen.Home) }
    var query by remember { mutableStateOf(initialQuery.orEmpty()) }
    var picking by remember { mutableStateOf(false) }
    val home = { screen = Screen.Home; query = ""; picking = false }
    LaunchedEffect(resetTick) { if (resetTick > 0) home() }
    BackHandler(screen != Screen.Home || picking) { home() }

    Box(Modifier.fillMaxSize()) {
        when (screen) {
            Screen.Home -> HomeScreen(apps, widgets, onSearch = { screen = Screen.Search }, onDrawer = { screen = Screen.Drawer }, onPickWidget = { picking = true })
            Screen.Drawer -> DrawerScreen(apps, onClose = home)
            Screen.Search -> SearchScreen(apps, query, { query = it }, onDone = home)
        }
        if (picking) WidgetPicker(widgets) { picking = false }
    }
}

// ---------- Home ----------

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HomeScreen(apps: Apps, widgets: Widgets, onSearch: () -> Unit, onDrawer: () -> Unit, onPickWidget: () -> Unit) {
    val context = LocalContext.current
    var menu by remember { mutableStateOf(false) }
    var removing by remember { mutableStateOf(false) }
    var dragged by remember { mutableFloatStateOf(0f) }
    Column(
        Modifier.fillMaxSize()
            .draggable(
                rememberDraggableState { dragged += it }, Orientation.Vertical,
                onDragStarted = { dragged = 0f },
                onDragStopped = { velocity -> if (dragged < -100 || velocity < -800) onDrawer() },
            )
            .combinedClickable(interactionSource = null, indication = null, onLongClick = { menu = true }) {}
            .systemBarsPadding().padding(horizontal = 6.dp)
    ) {
        Clock()
        widgets.ids.forEach { id -> WidgetView(widgets, id); Spacer(Modifier.height(6.dp)) }
        Spacer(Modifier.weight(1f))
        AppGrid(apps, apps.home.mapNotNull(apps::byKey), labels = true)
        Spacer(Modifier.height(8.dp))
        Bar(Modifier.clickable(onClick = onSearch)) {
            Text("⌕", color = Muted, fontSize = 15.sp)
            Text("Search apps, math, dates", color = Muted, fontSize = 14.sp, modifier = Modifier.weight(1f))
            Kbd("↑ apps", Modifier.clickable(onClick = onDrawer))
        }
        Spacer(Modifier.height(4.dp))
        AppGrid(apps, apps.dock.mapNotNull(apps::byKey), labels = false)
        Spacer(Modifier.height(6.dp))
        DropdownMenu(menu, { menu = false }) {
            DropdownMenuItem({ Text("Add widget") }, { menu = false; onPickWidget() })
            if (widgets.ids.isNotEmpty()) DropdownMenuItem({ Text("Remove widget") }, { menu = false; removing = true })
            DropdownMenuItem({ Text("Wallpaper") }, {
                menu = false
                context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SET_WALLPAPER), "Wallpaper"))
            })
            DropdownMenuItem({ Text("Default home app") }, { menu = false; apps.homeSettings() })
        }
        DropdownMenu(removing, { removing = false }) {
            widgets.ids.toList().forEach { id ->
                val label = widgets.manager.getAppWidgetInfo(id)?.loadLabel(context.packageManager) ?: "Widget $id"
                DropdownMenuItem({ Text("Remove $label") }, { widgets.remove(id); removing = false })
            }
        }
    }
}

@Composable
private fun Clock() {
    val context = LocalContext.current
    val now by produceState(Date()) { while (true) { value = Date(); delay(60_000 - System.currentTimeMillis() % 60_000) } }
    val shadow = TextStyle(shadow = TextShadow, color = Color.White)
    Row(Modifier.fillMaxWidth().padding(start = 6.dp, end = 6.dp, top = 6.dp, bottom = 10.dp), verticalAlignment = Alignment.Bottom) {
        Text(DateFormat.getTimeFormat(context).format(now), style = shadow, fontSize = 52.sp, fontWeight = FontWeight.SemiBold, lineHeight = 52.sp, modifier = Modifier.weight(1f))
        Text(DateFormat.format("EEE, MMM d", now).toString(), style = shadow, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun WidgetView(widgets: Widgets, id: Int) {
    val info = remember(id) { widgets.manager.getAppWidgetInfo(id) } ?: return
    val density = LocalContext.current.resources.displayMetrics.density
    val height = maxOf(info.minHeight / density, 64f).dp
    AndroidView(
        factory = { ctx -> widgets.host.createView(ctx, id, info).apply { setAppWidget(id, info) } },
        modifier = Modifier.fillMaxWidth().height(height).clip(Small),
    )
}

@Composable
private fun WidgetPicker(widgets: Widgets, onClose: () -> Unit) {
    val context = LocalContext.current
    val providers = remember { widgets.providers() }
    Column(Modifier.fillMaxSize().background(Sheet).systemBarsPadding()) {
        Section("Add widget", "${providers.size}")
        LazyColumn(Modifier.weight(1f)) {
            items(providers, key = { it.provider.flattenToString() }) { p: AppWidgetProviderInfo ->
                val app = remember(p) { runCatching { context.packageManager.getApplicationLabel(context.packageManager.getApplicationInfo(p.provider.packageName, 0)) }.getOrNull() ?: "" }
                Row(Modifier.fillMaxWidth().clickable { widgets.add(p); onClose() }.padding(horizontal = 10.dp, vertical = 7.dp)) {
                    Text(p.loadLabel(context.packageManager), color = Fg, fontSize = 13.sp, modifier = Modifier.weight(1f))
                    Text(app.toString(), color = Muted, fontSize = 11.sp)
                }
            }
        }
    }
}

// ---------- Apps ----------

@Composable
private fun AppGrid(apps: Apps, list: List<App>, labels: Boolean) {
    if (list.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        list.chunked(4).forEach { row ->
            Row(Modifier.fillMaxWidth()) {
                row.forEach { AppTile(apps, it, labels, 44.dp, Modifier.weight(1f)) }
                repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AppTile(apps: Apps, app: App, label: Boolean, size: Dp, modifier: Modifier = Modifier, onLaunched: () -> Unit = {}) {
    var menu by remember { mutableStateOf(false) }
    Box(modifier) {
        Column(
            Modifier.fillMaxWidth().combinedClickable(onLongClick = { menu = true }) { apps.launch(app); onLaunched() },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(apps, app, size)
            if (label) Text(app.label, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Medium, maxLines = 1,
                overflow = TextOverflow.Ellipsis, style = TextStyle(shadow = TextShadow), modifier = Modifier.padding(top = 3.dp, start = 2.dp, end = 2.dp))
        }
        AppMenu(apps, app, menu) { menu = false }
    }
}

@Composable
private fun Icon(apps: Apps, app: App, size: Dp) {
    val bmp = apps.icons[app.key]
    val m = Modifier.size(size).clip(RoundedCornerShape(size * 0.2f))
    if (bmp != null) Image(bmp, app.label, m) else Box(m.background(Line))
}

@Composable
private fun AppMenu(apps: Apps, app: App, expanded: Boolean, onDismiss: () -> Unit) {
    DropdownMenu(expanded, onDismiss) {
        DropdownMenuItem({ Text(if (app.key in apps.home) "Remove from home" else "Add to home") }, { apps.toggleHome(app); onDismiss() })
        DropdownMenuItem({ Text(if (app.key in apps.dock) "Remove from dock" else "Add to dock") }, { apps.toggleDock(app); onDismiss() })
        DropdownMenuItem({ Text("App info") }, { apps.appInfo(app); onDismiss() })
        DropdownMenuItem({ Text("Uninstall") }, { apps.uninstall(app); onDismiss() })
    }
}

// ---------- Drawer ----------

@Composable
private fun DrawerScreen(apps: Apps, onClose: () -> Unit) {
    var filter by remember { mutableStateOf("") }
    val grid = rememberLazyGridState()
    val scope = rememberCoroutineScope()
    val list = apps.all.filter { filter.isBlank() || match(it.label, filter) != null }
    val frequent = if (filter.isBlank()) apps.frequent(5) else emptyList()
    val sections = list.groupBy { it.label.firstOrNull()?.uppercaseChar()?.takeIf(Char::isLetter) ?: '#' }.toSortedMap()
    // Grid index of each section header, for the letter scrubber.
    val headerIndex = remember(sections, frequent) {
        var i = if (frequent.isEmpty()) 0 else 1 + frequent.size
        sections.map { (letter, group) -> (letter to i).also { i += 1 + group.size } }.toMap()
    }
    val pullToClose = remember {
        object : NestedScrollConnection {
            var pulled = 0f
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (available.y > 0) pulled += available.y else pulled = 0f
                if (pulled > 250) { pulled = 0f; onClose() }
                return Offset.Zero
            }
        }
    }
    Column(Modifier.fillMaxSize().background(Scrim).systemBarsPadding().imePadding()) {
        Box(Modifier.weight(1f)) {
            LazyVerticalGrid(GridCells.Fixed(5), Modifier.fillMaxSize().padding(end = 14.dp).nestedScroll(pullToClose), grid,
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (frequent.isNotEmpty()) {
                    item(span = { GridItemSpan(5) }) { Section("Frequent") }
                    items(frequent, key = { "f" + it.key }) { AppTile(apps, it, true, 40.dp, onLaunched = onClose) }
                }
                sections.forEach { (letter, group) ->
                    item(span = { GridItemSpan(5) }, key = "h$letter") { Section(letter.toString()) }
                    items(group, key = { it.key }) { AppTile(apps, it, true, 40.dp, onLaunched = onClose) }
                }
            }
            Column(Modifier.align(Alignment.CenterEnd).width(14.dp).pointerInput(headerIndex) {
                detectVerticalDragGestures { change, _ ->
                    val letters = headerIndex.keys.toList()
                    val i = (change.position.y / size.height * letters.size).toInt().coerceIn(0, letters.lastIndex)
                    scope.launch { grid.scrollToItem(headerIndex[letters[i]]!!) }
                }
            }) {
                headerIndex.keys.forEach { l ->
                    Text(l.toString(), color = Color(0xFF9AA3AB), fontSize = 9.sp, fontFamily = Mono, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.fillMaxWidth().clickable { scope.launch { grid.scrollToItem(headerIndex[l]!!) } }, textAlign = TextAlign.Center)
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Bar {
            Text("⌕", color = Muted, fontSize = 15.sp)
            Field(filter, { filter = it }, "Filter ${apps.all.size} apps", Modifier.weight(1f), autoFocus = false) {
                list.firstOrNull()?.let { apps.launch(it); onClose() }
            }
        }
        Spacer(Modifier.height(6.dp))
    }
}

// ---------- Search + calculator ----------

private data class Calc(val expression: String, val result: String, val micros: Long)

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SearchScreen(apps: Apps, query: String, onQuery: (String) -> Unit, onDone: () -> Unit) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var calc by remember { mutableStateOf<Calc?>(null) }
    val currentQuery by rememberUpdatedState(query)
    LaunchedEffect(Unit) {
        snapshotFlow { currentQuery }.collectLatest { q ->
            if (q.isBlank()) { calc = null; return@collectLatest }
            val t0 = System.nanoTime()
            val r = Soulver.evaluate(q)
            val us = (System.nanoTime() - t0) / 1000
            calc = if (r.isBlank() || r == q.trim()) null else Calc(q.trim(), r, us)
        }
    }
    val matches = remember(query, apps.all.size) {
        apps.all.mapNotNull { a -> match(a.label, query)?.let { Triple(a, it.first, it.second) } }
            .sortedWith(compareByDescending<Triple<App, Int, IntRange?>> { it.second }.thenBy { it.first.label.length })
            .take(8)
    }
    val shortcuts = remember(matches.take(2).map { it.first.key }) { matches.take(2).flatMap { apps.shortcuts(it.first).take(3) }.take(4) }
    val copy = { c: Calc ->
        clipboard.setText(AnnotatedString(c.result)); apps.remember(c.expression, c.result)
        Toast.makeText(context, "Copied ${c.result}", Toast.LENGTH_SHORT).show()
    }
    val go = {
        val c = calc; val top = matches.firstOrNull()?.first
        when {
            c != null -> copy(c)
            top != null -> { apps.launch(top); onDone() }
            else -> {}
        }
    }

    Column(Modifier.fillMaxSize().background(Sheet).systemBarsPadding().imePadding()) {
        LazyColumn(Modifier.weight(1f)) {
            calc?.let { c ->
                item(key = "calc") {
                    Section("Calculator", "%.1f ms".format(c.micros / 1000.0))
                    CalcCard(c) { copy(c) }
                }
            }
            if (matches.isNotEmpty()) {
                item(key = "apps") { Section("Apps", "${matches.size}") }
                matches.forEachIndexed { i, (app, _, range) ->
                    item(key = app.key) {
                        var menu by remember { mutableStateOf(false) }
                        Box {
                            ResultRow(apps, app, highlight(app.label, range), selected = i == 0 && calc == null,
                                trailing = if (i == 0 && calc == null) "↵ Open" else "App",
                                modifier = Modifier.combinedClickable(onLongClick = { menu = true }) { apps.launch(app); onDone() })
                            AppMenu(apps, app, menu) { menu = false }
                        }
                    }
                }
            }
            if (shortcuts.isNotEmpty()) {
                item(key = "sc") { Section("Shortcuts") }
                items(shortcuts, key = { "s" + it.info.id + it.app.key }) { s ->
                    ResultRow(apps, s.app, AnnotatedString(s.label), false, s.app.label, Modifier.clickable { apps.launch(s); onDone() })
                }
            }
            if ((calc != null || query.isBlank()) && apps.history.isNotEmpty()) {
                item(key = "hist") { Section("History") }
                items(apps.history.take(6), key = { "h" + it.first }) { (e, r) ->
                    Row(Modifier.fillMaxWidth().clickable { onQuery(e) }.padding(horizontal = 10.dp, vertical = 5.dp)) {
                        Text(e, color = Color(0xFF9AA3AB), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        Text(r, color = Fg, fontSize = 12.sp, fontFamily = Mono, modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
            if (query.isBlank()) {
                val freq = apps.frequent(5)
                if (freq.isNotEmpty()) {
                    item(key = "freq") { Section("Frequent") }
                    items(freq, key = { "q" + it.key }) { app -> ResultRow(apps, app, AnnotatedString(app.label), false, "App", Modifier.clickable { apps.launch(app); onDone() }) }
                }
            }
        }
        if (calc != null) Row(Modifier.fillMaxWidth().border(1.dp, Line).padding(horizontal = 10.dp, vertical = 5.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("↵ Copy answer", color = Color(0xFF9AA3AB), fontSize = 11.sp)
            Text("Long-press app for actions", color = Color(0xFF9AA3AB), fontSize = 11.sp)
        }
        Spacer(Modifier.height(4.dp))
        Bar {
            Text("⌕", color = Muted, fontSize = 15.sp)
            Field(query, onQuery, "Search apps, math, dates", Modifier.weight(1f), autoFocus = true, onGo = go)
            if (query.isNotEmpty()) Text("✕", color = Muted, fontSize = 13.sp, modifier = Modifier.clickable { onQuery("") }.padding(horizontal = 4.dp))
        }
        Spacer(Modifier.height(6.dp))
    }
}

@Composable
private fun CalcCard(c: Calc, onCopy: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 4.dp).clip(RoundedCornerShape(4.dp))
            .background(Color(0xFF1D1B17)).border(1.dp, Color(0xFF3A3222), RoundedCornerShape(4.dp))
            .clickable(onClick = onCopy).padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(c.expression, color = Fg, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), maxLines = 3)
        Text("→", color = Muted, modifier = Modifier.padding(horizontal = 8.dp))
        Text(c.result, color = Accent, fontSize = 21.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f),
            textAlign = TextAlign.End)
    }
}

@Composable
private fun ResultRow(apps: Apps, app: App, label: AnnotatedString, selected: Boolean, trailing: String, modifier: Modifier) {
    Row(
        modifier.fillMaxWidth().background(if (selected) Selected else Color.Transparent),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(2.dp).height(36.dp).background(if (selected) Accent else Color.Transparent))
        Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(apps, app, 28.dp)
            Text(label, color = Fg, fontSize = 13.sp, modifier = Modifier.padding(start = 8.dp).weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (selected) Kbd(trailing) else Text(trailing, color = Muted, fontSize = 10.5.sp)
        }
    }
}

private fun highlight(label: String, range: IntRange?) = buildAnnotatedString {
    append(label)
    if (range != null && range.last < label.length) addStyle(SpanStyle(textDecoration = TextDecoration.Underline), range.first, range.last + 1)
}

// ---------- Shared bits ----------

@Composable
private fun Bar(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    Row(
        modifier.padding(horizontal = 6.dp).fillMaxWidth().clip(RoundedCornerShape(6.dp)).background(BarBg)
            .border(1.dp, Line, RoundedCornerShape(6.dp)).padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}

@Composable
private fun Field(value: String, onValue: (String) -> Unit, hint: String, modifier: Modifier, autoFocus: Boolean, onGo: () -> Unit) {
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    if (autoFocus) LaunchedEffect(Unit) { focus.requestFocus(); keyboard?.show() }
    BasicTextField(
        value, onValue, modifier.focusRequester(focus),
        textStyle = TextStyle(color = Fg, fontSize = 14.sp),
        singleLine = true,
        cursorBrush = SolidColor(Accent),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go, autoCorrectEnabled = false),
        keyboardActions = KeyboardActions(onGo = { onGo() }),
        decorationBox = { inner -> Box { if (value.isEmpty()) Text(hint, color = Muted, fontSize = 14.sp); inner() } },
    )
}

@Composable
private fun Section(title: String, right: String = "") {
    Row(Modifier.fillMaxWidth().padding(start = 10.dp, end = 10.dp, top = 7.dp, bottom = 3.dp)) {
        Text(title.uppercase(), color = Muted, fontSize = 9.5.sp, fontFamily = Mono, letterSpacing = 0.8.sp, modifier = Modifier.weight(1f))
        Text(right, color = Muted, fontSize = 9.5.sp, fontFamily = Mono)
    }
}

@Composable
private fun Kbd(text: String, modifier: Modifier = Modifier) {
    Text(text, color = Color(0xFF9AA3AB), fontSize = 10.sp, fontFamily = Mono,
        modifier = modifier.clip(RoundedCornerShape(3.dp)).background(Color(0xFF16191B)).border(1.dp, Color(0xFF3A4045), RoundedCornerShape(3.dp)).padding(horizontal = 4.dp, vertical = 1.dp))
}
