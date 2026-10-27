package app.onlysearch.home

import app.onlysearch.Accent
import app.onlysearch.AppSpan
import app.onlysearch.Apps
import app.onlysearch.Cell
import app.onlysearch.Danger
import app.onlysearch.Fg
import app.onlysearch.FieldLine
import app.onlysearch.HomePage
import app.onlysearch.Ic
import app.onlysearch.OnAccent
import app.onlysearch.Pill
import app.onlysearch.PillLine
import app.onlysearch.PopupBg
import app.onlysearch.WallFg
import app.onlysearch.WidgetCorner
import app.onlysearch.drawer.Handle
import app.onlysearch.gesture
import app.onlysearch.prefs
import app.onlysearch.widgets.BuiltinWidget
import app.onlysearch.widgets.Builtins
import app.onlysearch.widgets.CalendarPicker
import app.onlysearch.widgets.WidgetView
import app.onlysearch.widgets.Widgets
import android.appwidget.AppWidgetProviderInfo
import android.content.Intent
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
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
        val icon = minOf(58.dp * prefs.iconSize / 100, cw * AppSpan - 12.dp, ch * AppSpan - if (prefs.labels) 34.dp else 10.dp).coerceAtLeast(0.dp)
        // Derived so a pointer move only recomposes the page when the target cell changes.
        val target by remember(page.id) { derivedStateOf { dnd.item?.let { d -> dnd.grids[page.id]?.let { dnd.target(d, dnd.pos, it) } } } }
        val drop = target?.let { it to apps.fits(index, it.x, it.y, it.w, it.h, it.key) }
        if (drop != null) {
            val (t, ok) = drop
            Box(Modifier.offset(cw * t.x, ch * t.y).size(cw * t.w, ch * t.h).padding(2.dp).clip(RoundedCornerShape(WidgetCorner))
                .background((if (ok) Accent else Danger).copy(alpha = 0.12f)).border(2.dp, if (ok) Accent else Danger, RoundedCornerShape(WidgetCorner)))
        }
        page.items.forEach { c ->
            key(c.key) {
                if (!c.isWidget) Box(Modifier.offset(cw * c.x, ch * c.y).size(cw * c.w, ch * c.h), contentAlignment = Alignment.Center) {
                    if (c.key.startsWith("s:")) ShortcutTile(apps, c.key, if (prefs.labels) WallFg else null, icon)
                    else if (c.key.startsWith("f:")) FolderTile(apps, c.key, if (prefs.labels) WallFg else null, icon)
                    else apps.byKey(c.key)?.let { AppTile(apps, it, if (prefs.labels) WallFg else null, icon, drag = "grid") }
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

/** Speech goes straight into the search field, so "65 kg in pounds" can be spoken. */
@Composable
internal fun MicButton(onText: (String) -> Unit) {
    val context = LocalContext.current
    val speech = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        r.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let(onText)
    }
    Box(
        Modifier.size(40.dp).clip(CircleShape).background(FieldLine).clickable {
            runCatching { speech.launch(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)) }
                .onFailure { Toast.makeText(context, "No voice input available", Toast.LENGTH_SHORT).show() }
        },
        contentAlignment = Alignment.Center,
    ) { Icon(Ic.mic, "Voice search", tint = Fg, modifier = Modifier.size(18.dp)) }
}
