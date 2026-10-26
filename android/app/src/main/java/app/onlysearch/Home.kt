package app.onlysearch

import android.appwidget.AppWidgetProviderInfo
import android.content.Intent
import android.os.Build
import android.speech.RecognizerIntent
import android.text.format.DateFormat
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import android.appwidget.AppWidgetHostView
import android.os.Bundle
import android.util.SizeF
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.unit.round
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Date
import kotlin.math.roundToInt

internal class HomeActions(
    val onSearch: () -> Unit, val onVoice: (String) -> Unit, val onDrag: (Float) -> Unit, val onDragEnd: (Float) -> Unit, val onPickWidget: () -> Unit,
    val onSettings: (String) -> Unit, val onEdit: () -> Unit, val onGesture: (String) -> Unit,
)

@Composable
internal fun HomeScreen(apps: Apps, widgets: Widgets, pager: PagerState, actions: HomeActions) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    var menu by remember { mutableStateOf(false) }
    var removing by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<String?>(null) }
    val dnd = LocalDragDrop.current
    LaunchedEffect(prefs.columns, prefs.rows) { apps.fitGrid() }
    LaunchedEffect(pager.currentPage) { selected = null }
    var menuAt by remember { mutableStateOf(IntOffset.Zero) }
    // Swipes: "Swipe up" set to the drawer follows the finger; anything else fires on release past [swipe].
    val swipe = with(LocalDensity.current) { 60.dp.toPx() }
    val travel = remember { floatArrayOf(0f) }
    val multi = remember { booleanArrayOf(false) }
    Box(
        Modifier.fillMaxSize()
            .pointerInput(Unit) { detectMultiTouch(onMulti = { multi[0] = it }, onPinch = { actions.onGesture("gPinch") }, onTwoDown = { actions.onGesture("gTwoDown") }) }
            .draggable(
                rememberDraggableState { d -> travel[0] += d; if (gesture("gUp") == "drawer" && !multi[0]) actions.onDrag(d) }, Orientation.Vertical,
                onDragStarted = { travel[0] = 0f },
                onDragStopped = { v ->
                    val up = gesture("gUp")
                    if (up == "drawer") actions.onDragEnd(if (multi[0]) 1000f else v)
                    when {
                        multi[0] -> {}
                        travel[0] > swipe && v >= 0f -> actions.onGesture("gDown")
                        up != "drawer" && travel[0] < -swipe && v <= 0f -> actions.onGesture("gUp")
                    }
                },
            )
            .pointerInput(Unit) {
                detectTapGestures(onTap = { selected = null }, onDoubleTap = { if (dnd.item == null) actions.onGesture("gDoubleTap") }, onLongPress = {
                    // A held app or widget handles its own long press.
                    if (!DragDropPressing.value && dnd.item == null && selected == null) { haptic.performHapticFeedback(HapticFeedbackType.LongPress); menuAt = it.round(); menu = true }
                })
            }
            .testTag("home")
    ) {
    Column(Modifier.fillMaxSize().systemBarsPadding().padding(top = 8.dp, bottom = 12.dp)) {
        val pill = @Composable { m: Modifier ->
            Pill("Apps, math, units, time…", m.testTag("homeSearch"), onClick = actions.onSearch) { if (prefs.mic) MicButton(actions.onVoice) }
        }
        if (prefs.searchBar == "top") pill(Modifier.padding(start = 18.dp, end = 18.dp, bottom = 16.dp))
        HorizontalPager(pager, Modifier.weight(1f).fillMaxWidth(), beyondViewportPageCount = 1, key = { apps.pages.getOrNull(it)?.id ?: -1L }) { i ->
            val page = apps.pages.getOrNull(i) ?: return@HorizontalPager
            // Narrow side margins, like Nova's "small": widgets get nearly the full width.
            HomeGrid(apps, widgets, i, page, selected, { selected = it }, Modifier.fillMaxSize().padding(horizontal = 4.dp))
        }
        Column(Modifier.padding(horizontal = 18.dp)) {
            PageDots(apps.pages.size, pager.currentPage, Modifier.padding(top = 18.dp, bottom = 16.dp))
            if (prefs.searchBar == "above") pill(Modifier.padding(bottom = 16.dp))
            val dockTravel = remember { floatArrayOf(0f) }
            if (prefs.showDock) Box(Modifier.heightIn(min = 58.dp).onGloballyPositioned { dnd.dock = it.boundsInWindow() }
                .draggable(rememberDraggableState { dockTravel[0] += it }, Orientation.Vertical, onDragStarted = { dockTravel[0] = 0f },
                    onDragStopped = { v -> if (dockTravel[0] < -swipe && v <= 0f) actions.onGesture("gDock") })) {
                AppGrid(apps, apps.dock.take(prefs.dockSize).mapNotNull(apps::byKey), size = 58.dp * prefs.iconSize / 100, labelColor = null,
                    columns = prefs.dockSize, drag = "dock")
            } else LaunchedEffect(Unit) { dnd.dock = Rect.Zero }
            if (prefs.searchBar == "bottom") { Spacer(Modifier.height(16.dp)); pill(Modifier) }
        }
    }
    // Menus open where the finger was.
    Box(Modifier.offset { menuAt }) {
        val pageWidgets = apps.pages.getOrNull(pager.currentPage)?.items.orEmpty().mapNotNull { it.widgetId }
        DropdownMenu(menu, { menu = false }, shape = RoundedCornerShape(16.dp), containerColor = PopupBg) {
            DropdownMenuItem({ Text("Edit home") }, { menu = false; actions.onEdit() })
            DropdownMenuItem({ Text("Add widget") }, { menu = false; actions.onPickWidget() })
            if (pageWidgets.isNotEmpty()) DropdownMenuItem({ Text("Remove widget") }, { menu = false; removing = true })
            DropdownMenuItem({ Text("Wallpaper") }, { menu = false; pickWallpaper(context) })
            DropdownMenuItem({ Text("Settings") }, { menu = false; actions.onSettings("hub") })
        }
        DropdownMenu(removing, { removing = false }, shape = RoundedCornerShape(16.dp), containerColor = PopupBg) {
            pageWidgets.forEach { id ->
                val label = widgets.manager.getAppWidgetInfo(id)?.loadLabel(context.packageManager) ?: "Widget $id"
                DropdownMenuItem({ Text("Remove $label") }, { widgets.remove(id); apps.removeItem("w:$id"); removing = false })
            }
        }
    }
}
}

internal fun pickWallpaper(context: android.content.Context) =
    context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SET_WALLPAPER), "Wallpaper"))

/**
 * Two-finger gestures, watched without consuming so single-finger ones still work: a pinch in, or both fingers
 * moving down. [onMulti] says whether the current gesture has had two fingers, so the swipe handler can ignore it.
 */
internal suspend fun PointerInputScope.detectMultiTouch(onMulti: (Boolean) -> Unit, onPinch: () -> Unit, onTwoDown: () -> Unit) = awaitEachGesture {
    awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
    onMulti(false)
    var startDist = 0f
    var startY = 0f
    var fired = false
    val travel = 80.dp.toPx()
    while (true) {
        val e = awaitPointerEvent(PointerEventPass.Initial)
        val down = e.changes.filter { it.pressed }
        if (down.isEmpty()) break
        if (down.size < 2 || fired) continue
        onMulti(true)
        val d = (down[0].position - down[1].position).getDistance()
        val y = (down[0].position.y + down[1].position.y) / 2
        if (startDist == 0f) { startDist = d; startY = y; continue }
        if (d < startDist * 0.65f) { fired = true; onPinch() }
        else if (y - startY > travel && d > startDist * 0.75f) { fired = true; onTwoDown() }
    }
}

@Composable
internal fun PageDots(count: Int, current: Int, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().height(6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally)) {
        if (count > 1) repeat(count) { i ->
            val on = i == current
            Box(Modifier.size(if (on) 18.dp else 6.dp, 6.dp).clip(CircleShape).background(if (on) Accent else Color(0x66EDEAE4)))
        }
    }
}

/** A home page's cell grid: apps and widgets at their cells, sized from the Home screen settings. Also the drop target. */
@Composable
internal fun HomeGrid(apps: Apps, widgets: Widgets, index: Int, page: HomePage, selected: String?, onSelect: (String?) -> Unit, modifier: Modifier) {
    val dnd = LocalDragDrop.current
    val haptic = LocalHapticFeedback.current
    var calendarsFor by remember { mutableStateOf<String?>(null) }
    calendarsFor?.let { CalendarPicker(it) { calendarsFor = null } }
    BoxWithConstraints(modifier.onGloballyPositioned { dnd.grids[page.id] = Rect(it.positionInWindow(), it.size.toSize()) }) {
        val cw = maxWidth / prefs.columns
        val ch = maxHeight / prefs.rows
        // Keep ~10dp between a label and the icon below it; small cells shrink the icon.
        val icon = minOf(58.dp * prefs.iconSize / 100, cw * prefs.appSpan - 12.dp, ch * prefs.appSpan - if (prefs.labels) 34.dp else 10.dp).coerceAtLeast(0.dp)
        val drop = dnd.item?.let { d -> dnd.grids[page.id]?.let { dnd.target(d, dnd.pos, it) }?.let { it to apps.fits(index, it.x, it.y, it.w, it.h, d.key) } }
        if (drop != null) {
            val (t, ok) = drop
            Box(Modifier.offset(cw * t.x, ch * t.y).size(cw * t.w, ch * t.h).padding(2.dp).clip(RoundedCornerShape(WidgetCorner))
                .background((if (ok) Accent else Danger).copy(alpha = 0.12f)).border(2.dp, if (ok) Accent else Danger, RoundedCornerShape(WidgetCorner)))
        }
        page.items.forEach { c ->
            key(c.key) {
                if (!c.isWidget) Box(Modifier.offset(cw * c.x, ch * c.y).size(cw * c.w, ch * c.h), contentAlignment = Alignment.Center) {
                    if (c.key.startsWith("s:")) ShortcutTile(apps, c.key, if (prefs.labels) Fg else null, icon)
                    else if (c.key.startsWith("f:")) FolderTile(apps, c.key, if (prefs.labels) Fg else null, icon)
                    else apps.byKey(c.key)?.let { AppTile(apps, it, if (prefs.labels) Fg else null, icon, drag = "grid") }
                } else {
                    val coords = remember { arrayOfNulls<LayoutCoordinates>(1) }
                    val now by rememberUpdatedState(c)
                    val framed by rememberUpdatedState(selected == c.key)
                    val lifted = dnd.item?.key == c.key
                    Box(
                        Modifier.offset(cw * c.x, ch * c.y).size(cw * c.w, ch * c.h).onGloballyPositioned { coords[0] = it }
                            .graphicsLayer { alpha = if (lifted) 0.25f else 1f }
                            .pointerInput(c.key) {
                                // Initial pass: see the touch before the widget's own view, take it over after a long press.
                                // Once framed, new touches belong to the resize handles and the widget.
                                tapHoldDrag(
                                    { coords[0] }, PointerEventPass.Initial, onTap = null,
                                    // Once framed, holds belong to the resize handles.
                                    onLongPress = { if (!prefs.lockLayout && !framed) { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onSelect(c.key) } },
                                    onDragStart = { grab, at ->
                                        if (prefs.lockLayout) return@tapHoldDrag
                                        onSelect(null)
                                        dnd.start(Dragged(now.key, now.w, now.h, "grid", grab, Size(cw.toPx() * now.w, ch.toPx() * now.h)), at)
                                    },
                                    onDrag = dnd::move, onDragEnd = { if (dnd.item != null) dnd.end() },
                                    enabled = { !framed },
                                )
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        val id = c.widgetId
                        if (id != null) WidgetView(widgets, id, cw * c.w - 4.dp, ch * c.h - 4.dp)
                        else BuiltinWidget(c, Modifier.size(cw * c.w - 4.dp, ch * c.h - 4.dp))
                        if (selected == c.key) WidgetFrame(apps, widgets, index, c, cw, ch, onCalendars = { calendarsFor = c.key }) { onSelect(null) }
                    }
                }
            }
        }
    }
}

/** Selection frame on a long-pressed widget: edge handles resize it cell by cell, × removes it. */
@Composable
private fun BoxScope.WidgetFrame(apps: Apps, widgets: Widgets, page: Int, cell: Cell, cw: Dp, ch: Dp, onCalendars: () -> Unit, onClose: () -> Unit) {
    val id = cell.widgetId
    val builtin = Builtins.firstOrNull { it.kind == cell.builtin }
    val info = id?.let { remember(it) { widgets.manager.getAppWidgetInfo(it) } }
    if (info == null && builtin == null) return
    val density = LocalDensity.current.density
    fun minCells(px: Int, minPx: Int, cellDp: Dp) = kotlin.math.ceil(((if (minPx > 0) minPx else px) / density) / cellDp.value).toInt().coerceAtLeast(1)
    val minW = builtin?.minW ?: minCells(info!!.minWidth, info.minResizeWidth, cw)
    val minH = builtin?.minH ?: minCells(info!!.minHeight, info.minResizeHeight, ch)
    val current by rememberUpdatedState(cell)
    fun resize(dx: Int, dy: Int, dw: Int, dh: Int, start: Cell) {
        val w = start.w + dw; val h = start.h + dh
        if (w < minW || h < minH) return
        val next = start.copy(x = start.x + dx, y = start.y + dy, w = w, h = h)
        if (next != current) apps.place(page, next)
    }
    Box(Modifier.matchParentSize().padding(1.dp).border(2.dp, Accent, RoundedCornerShape(WidgetCorner)))
    val horizontal = info == null || info.resizeMode and AppWidgetProviderInfo.RESIZE_HORIZONTAL != 0
    val vertical = info == null || info.resizeMode and AppWidgetProviderInfo.RESIZE_VERTICAL != 0
    @Composable
    fun Handle(align: Alignment, name: String, apply: (Float, Float, Cell) -> Unit) {
        var start by remember { mutableStateOf(cell) }
        var acc by remember { mutableStateOf(Offset.Zero) }
        Box(
            Modifier.align(align).size(36.dp).semantics { contentDescription = name }.pointerInput(Unit) {
                detectDragGestures(onDragStart = { start = current; acc = Offset.Zero }) { change, d ->
                    change.consume(); acc += d
                    apply(acc.x / cw.toPx(), acc.y / ch.toPx(), start)
                }
            },
            contentAlignment = Alignment.Center,
        ) { Box(Modifier.size(14.dp).clip(CircleShape).background(Accent).border(2.dp, OnAccent, CircleShape)) }
    }
    if (horizontal) {
        Handle(Alignment.CenterEnd, "Resize right") { fx, _, s -> resize(0, 0, fx.roundToInt().coerceAtMost(prefs.columns - s.x - s.w), 0, s) }
        Handle(Alignment.CenterStart, "Resize left") { fx, _, s -> val d = fx.roundToInt().coerceAtLeast(-s.x); resize(d, 0, -d, 0, s) }
    }
    if (vertical) {
        Handle(Alignment.BottomCenter, "Resize bottom") { _, fy, s -> resize(0, 0, 0, fy.roundToInt().coerceAtMost(prefs.rows - s.y - s.h), s) }
        Handle(Alignment.TopCenter, "Resize top") { _, fy, s -> val d = fy.roundToInt().coerceAtLeast(-s.y); resize(0, d, 0, -d, s) }
    }
    Box(
        Modifier.align(Alignment.TopEnd).padding(6.dp).size(30.dp).clip(CircleShape).background(PopupBg).border(1.dp, PillLine, CircleShape)
            .clickable { id?.let(widgets::remove); apps.removeItem(cell.key); onClose() }.semantics { contentDescription = "Remove widget" },
        contentAlignment = Alignment.Center,
    ) { Icon(Ic.close, null, tint = Danger, modifier = Modifier.size(14.dp)) }
    if (info?.configure != null || builtin?.agenda == true) Box(
        Modifier.align(Alignment.TopStart).padding(6.dp).size(30.dp).clip(CircleShape).background(PopupBg).border(1.dp, PillLine, CircleShape)
            .clickable { if (id != null) widgets.reconfigure(id) else onCalendars(); onClose() }.semantics { contentDescription = "Widget settings" },
        contentAlignment = Alignment.Center,
    ) { Icon(EIc.settings, null, tint = Fg, modifier = Modifier.size(15.dp)) }
}

@Composable
internal fun Clock(modifier: Modifier = Modifier) = BoxWithConstraints(modifier) {
    val context = LocalContext.current
    val now by produceState(Date()) { while (true) { value = Date(); delay(60_000 - System.currentTimeMillis() % 60_000) } }
    // 92sp when there's room, smaller in a short or narrow cell block.
    val size = with(LocalDensity.current) { minOf(92.dp, (maxHeight - 30.dp).coerceAtLeast(16.dp), maxWidth / 2.8f).toSp() }
    Column(Modifier.padding(horizontal = 8.dp).align(Alignment.CenterStart), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(DateFormat.format("EEE d MMM", now).toString().uppercase(), style = shadowed(), color = Muted, fontSize = 12.sp,
            fontFamily = Mono, letterSpacing = 0.14.em)
        // AM/PM would not fit at this size; the status bar already shows it.
        Text(DateFormat.format(if (DateFormat.is24HourFormat(context)) "H:mm" else "h:mm", now).toString(), style = shadowed(),
            fontSize = size, fontWeight = FontWeight.Light, letterSpacing = (-0.045).em, lineHeight = size)
    }
}

@Composable
internal fun WidgetView(widgets: Widgets, id: Int, width: Dp, height: Dp) {
    val info = remember(id) { widgets.manager.getAppWidgetInfo(id) } ?: return
    var view by remember { mutableStateOf<AppWidgetHostView?>(null) }
    // Tell the widget how big it is, the way Launcher3 (and Nova) do: portrait as it is, plus landscape, wider and
    // shorter. Widgets that size themselves by the max width then show as much as they would there.
    LaunchedEffect(view, width, height) {
        val v = view ?: return@LaunchedEffect
        val dm = v.resources.displayMetrics
        val ratio = maxOf(dm.heightPixels, dm.widthPixels) / minOf(dm.heightPixels, dm.widthPixels).toFloat()
        val (w, h) = width.value to height.value
        if (Build.VERSION.SDK_INT >= 31) v.updateAppWidgetSize(Bundle(), listOf(SizeF(w, h), SizeF(w * ratio, h / ratio)))
        else @Suppress("DEPRECATION") v.updateAppWidgetSize(null, w.toInt(), (h / ratio).toInt(), (w * ratio).toInt(), h.toInt())
    }
    AndroidView(
        factory = { ctx -> widgets.host.createView(ctx, id, info).apply { setAppWidget(id, info); setPadding(0, 0, 0, 0); view = this } },
        modifier = Modifier.size(width, height).clip(RoundedCornerShape(WidgetCorner)),
    )
}

/** Speech goes straight into the search field, so "65 kg in pounds" can be spoken. */
@Composable
internal fun MicButton(onText: (String) -> Unit) {
    val context = LocalContext.current
    val speech = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        r.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let(onText)
    }
    Box(
        Modifier.size(40.dp).clip(CircleShape).background(Color(0xFF2A2D33)).clickable {
            runCatching { speech.launch(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)) }
                .onFailure { Toast.makeText(context, "No voice input available", Toast.LENGTH_SHORT).show() }
        },
        contentAlignment = Alignment.Center,
    ) { Icon(Ic.mic, "Voice search", tint = Fg, modifier = Modifier.size(18.dp)) }
}

@Composable
internal fun WidgetPicker(apps: Apps, widgets: Widgets, only: String?, onClose: () -> Unit) {
    val context = LocalContext.current
    val pm = context.packageManager
    val density = context.resources.displayMetrics.density
    val groups = remember {
        widgets.providers().filter { only == null || it.provider.packageName == only }.groupBy { it.provider.packageName }.map { (pkg, list) ->
            Triple(pkg, runCatching { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() }.getOrDefault(pkg), list)
        }.sortedBy { it.second.lowercase() }
    }
    var filter by remember { mutableStateOf("") }
    var open by remember { mutableStateOf(groups.firstOrNull()?.first) }
    // While a preview is dragged onto home the sheet stays composed (it owns the gesture) but invisible.
    var dragging by remember { mutableStateOf(false) }
    val shown = groups.mapNotNull { (pkg, label, list) ->
        val hits = if (filter.isBlank() || label.contains(filter, true)) list else list.filter { it.loadLabel(pm).contains(filter, true) }
        if (hits.isEmpty()) null else Triple(pkg, label, hits)
    }
    Box(Modifier.fillMaxSize().graphicsLayer { alpha = if (dragging) 0f else 1f }.background(Color(0xB3000000))
        .clickable(interactionSource = null, indication = null, onClick = onClose)) {
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().fillMaxHeight(0.86f)
                .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)).background(Color(0xFF15171B))
                .border(1.dp, Color(0xFF2A2D33), RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                .clickable(interactionSource = null, indication = null) {}
                .navigationBarsPadding().imePadding().padding(start = 16.dp, end = 16.dp, top = 10.dp)
        ) {
            Handle(Modifier.padding(bottom = 12.dp))
            Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Widgets", fontSize = 22.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Text("hold & drag to place", color = Muted, fontSize = 11.sp, fontFamily = Mono)
            }
            Row(
                Modifier.fillMaxWidth().height(46.dp).clip(RoundedCornerShape(14.dp)).background(Tile)
                    .border(1.dp, ChipLine, RoundedCornerShape(14.dp)).padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(Ic.search, null, tint = Muted, modifier = Modifier.size(18.dp))
                Field(filter, { filter = it }, "Search widgets", Modifier.weight(1f), autoFocus = false, fontSize = 15) {}
            }
            LazyColumn(Modifier.weight(1f).padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                val ours = Builtins.filter { only == null && (filter.isBlank() || it.label.contains(filter, true) || "only search".contains(filter, true)) }
                if (ours.isNotEmpty()) {
                    item(key = "builtin") {
                        Text("Only Search", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.heightIn(min = 40.dp).wrapContentHeight())
                    }
                    items(ours, key = { "b:" + it.kind }) { b ->
                        val (w, h) = b.size()
                        WidgetPreview(apps, "pb:${b.kind}", b.label, w, h, null, Modifier, onDragging = { dragging = true }, onDropped = onClose,
                            onClick = {
                                if (!apps.addBuiltin(b.kind)) Toast.makeText(context, "No room for this widget on the page", Toast.LENGTH_SHORT).show()
                                onClose()
                            }) {
                            BuiltinWidget(Cell("b:${b.kind}:preview", 0, 0, w, h), Modifier.fillMaxSize(), preview = true)
                        }
                    }
                }
                shown.forEach { (pkg, label, list) ->
                    val expanded = open == pkg || filter.isNotBlank()
                    item(key = pkg) {
                        Row(Modifier.fillMaxWidth().heightIn(min = 40.dp).clickable { open = if (open == pkg) null else pkg },
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            val icon = apps.all.firstOrNull { it.component.packageName == pkg }?.let { apps.icons[it.key] }
                            Box(Modifier.size(28.dp).clip(RoundedCornerShape(9.dp)).background(Tile)) { if (icon != null) Image(icon, null, Modifier.fillMaxSize()) }
                            Text(label, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(if (list.size == 1) "1 widget" else "${list.size} widgets", color = Muted, fontSize = 12.sp)
                            Icon(if (expanded) Ic.collapse else Ic.expand, null, tint = Muted, modifier = Modifier.size(16.dp))
                        }
                    }
                    // Small widgets (≤ 2 cells wide) pair up two per row, wide ones take the full width.
                    val rows = mutableListOf<MutableList<AppWidgetProviderInfo>>()
                    list.forEach { p ->
                        val last = rows.lastOrNull()
                        if (cells(p, density).first <= 2 && last?.size == 1 && cells(last[0], density).first <= 2) last.add(p) else rows.add(mutableListOf(p))
                    }
                    if (expanded) items(rows, key = { it[0].provider.flattenToString() }) { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            row.forEach { p ->
                                WidgetPreview(apps, p, Modifier.weight(1f), onDragging = { dragging = true }, onDropped = onClose) { widgets.add(p); onClose() }
                            }
                            if (row.size == 1 && cells(row[0], density).first <= 2) Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

internal fun pickerKey(p: AppWidgetProviderInfo) = "p:${p.profile.hashCode()}:${p.provider.flattenToString()}"

/** Width × height in launcher cells (~70dp each); Android 12+ widgets state their own size. */
internal fun cells(p: AppWidgetProviderInfo, density: Float): Pair<Int, Int> {
    fun of(px: Int) = ((px / density + 30) / 70).toInt().coerceAtLeast(1)
    return if (Build.VERSION.SDK_INT >= 31 && p.targetCellWidth > 0) p.targetCellWidth to p.targetCellHeight.coerceAtLeast(1)
    else of(p.minWidth) to of(p.minHeight)
}

@Composable
internal fun WidgetPreview(apps: Apps, p: AppWidgetProviderInfo, modifier: Modifier, onDragging: () -> Unit, onDropped: () -> Unit, onClick: () -> Unit) {
    val context = LocalContext.current
    val density = context.resources.displayMetrics
    val preview = remember(p) {
        runCatching { (p.loadPreviewImage(context, density.densityDpi) ?: p.loadIcon(context, density.densityDpi))?.toBitmap()?.asImageBitmap() }.getOrNull()
    }
    val (w, h) = cells(p, density.density)
    WidgetPreview(apps, pickerKey(p), p.loadLabel(context.packageManager), w, h, preview, modifier, onDragging, onDropped, onClick) {
        if (preview != null) Image(preview, null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
    }
}

/** A picker card: tap adds the widget, hold and drag places it. [key] is what the drop handler gets. */
@Composable
internal fun WidgetPreview(
    apps: Apps, key: String, label: String, w: Int, h: Int, preview: ImageBitmap?, modifier: Modifier,
    onDragging: () -> Unit, onDropped: () -> Unit, onClick: () -> Unit, sample: @Composable () -> Unit,
) {
    val dnd = LocalDragDrop.current
    val haptic = LocalHapticFeedback.current
    val coords = remember { arrayOfNulls<LayoutCoordinates>(1) }
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Sheet).onGloballyPositioned { coords[0] = it }
            .semantics { onClick { onClick(); true } }
            .pointerInput(key) {
                tapHoldDrag(
                    { coords[0] }, onTap = onClick, onLongPress = { haptic.performHapticFeedback(HapticFeedbackType.LongPress) },
                    onDragStart = { _, at ->
                        // The ghost takes the widget's size on the current page's grid.
                        val grid = apps.pages.getOrNull(apps.currentPage)?.let { dnd.grids[it.id] } ?: return@tapHoldDrag
                        val cw = w.coerceAtMost(prefs.columns); val ch = h.coerceAtMost(prefs.rows)
                        val size = Size(grid.width / prefs.columns * cw, grid.height / prefs.rows * ch)
                        onDragging()
                        dnd.start(Dragged(key, cw, ch, "picker", Offset(size.width / 2, size.height / 2), size, preview), at)
                    },
                    onDrag = dnd::move, onDragEnd = { dnd.end(); onDropped() },
                )
            }
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            Modifier.fillMaxWidth().height(96.dp).clip(RoundedCornerShape(16.dp)).background(FieldBg)
                .border(1.dp, Color(0xFF2A2D33), RoundedCornerShape(16.dp)).padding(8.dp),
            contentAlignment = Alignment.Center,
        ) { sample() }
        Row(Modifier.fillMaxWidth()) {
            Text(label, color = Muted, fontSize = 12.sp, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("$w × $h", color = Muted, fontSize = 12.sp, fontFamily = Mono)
        }
    }
}
