package app.onlysearch

import android.app.Activity
import android.appwidget.AppWidgetProviderInfo
import android.content.Intent
import android.os.Build
import android.text.format.DateFormat
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Star
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
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toAndroidRect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.Date
import kotlin.math.max
import kotlin.math.roundToInt

// Design D (docs/DESIGN.md): tight padding, near-square corners, amber accent on a dark sheet.
// Motion follows Nova Launcher: finger-tracked drawer, icon zoom launches, press scale, popup shortcuts.
private val Sheet = Color(0xFF17191C)
private val BarBg = Color(0xFF1C1F22)
private val Line = Color(0xFF2C3135)
private val Selected = Color(0xFF23272B)
private val Accent = Color(0xFFF5B04A)
private val Muted = Color(0xFF7D868E)
private val Fg = Color(0xFFE8EAED)
private val Scrim = Color(0xD90E1116)
private val PopupBg = Color(0xF21E2124)
private val Mono = FontFamily.Monospace
private val Small = RoundedCornerShape(5.dp)
private val TextShadow = Shadow(Color(0x66000000), Offset(0f, 1f), 3f)
private val Settle = spring<Float>(dampingRatio = 0.9f, stiffness = 420f)

@Composable
fun Launcher(apps: Apps, widgets: Widgets, homeTick: Int, stopTick: Int, resumeTick: Int, initialQuery: String?) {
    val scope = rememberCoroutineScope()
    val activity = LocalContext.current as Activity
    val screenPx = with(LocalDensity.current) { LocalConfiguration.current.screenHeightDp.dp.toPx() }

    val drawer = remember { Animatable(0f) } // 0 = closed, 1 = open; follows the finger while dragging
    val enter = remember { Animatable(1f) } // home "icons zoom back in" after returning from an app
    var searching by remember { mutableStateOf(initialQuery != null) }
    var query by remember { mutableStateOf(initialQuery.orEmpty()) }
    var picking by remember { mutableStateOf(false) }
    val searchProgress by animateFloatAsState(if (searching) 1f else 0f, tween(180), label = "search")

    var drawerBlur by remember { mutableStateOf(false) }
    fun settleDrawer(open: Boolean) = scope.launch {
        if (!open) drawerBlur = false
        drawer.animateTo(if (open) 1f else 0f, Settle)
        if (open) drawerBlur = true
    }
    fun dragDrawer(deltaPx: Float) = scope.launch { drawer.snapTo((drawer.value - deltaPx / screenPx).coerceIn(0f, 1f)) }
    val drawerOpening by remember { derivedStateOf { drawer.targetValue > 0f } }
    fun closeSearch() { searching = false; query = "" }

    // Home button: animate back. Leaving to an app: reset silently so the launch animation isn't disturbed.
    LaunchedEffect(homeTick) { if (homeTick > 0) { closeSearch(); picking = false; settleDrawer(false) } }
    LaunchedEffect(stopTick) { if (stopTick > 0) { closeSearch(); picking = false; drawerBlur = false; drawer.snapTo(0f) } }
    LaunchedEffect(resumeTick) {
        if (apps.returningFromApp) {
            apps.returningFromApp = false
            enter.snapTo(0f); enter.animateTo(1f, spring(dampingRatio = 0.8f, stiffness = 300f))
        }
    }
    BackHandler(searching || picking || drawerOpening) {
        when { picking -> picking = false; searching -> closeSearch(); else -> settleDrawer(false) }
    }

    // Wallpaper blurs behind the open drawer / search (Android 12+, needs cross-window blur enabled).
    // Only changed while no finger is down: updating window attributes mid-gesture cancels the touch.
    val blurred = drawerBlur || searching
    LaunchedEffect(blurred) {
        if (Build.VERSION.SDK_INT < 31) return@LaunchedEffect
        val from = if (blurred) 0 else 48
        val to = 48 - from
        repeat(6) { i -> activity.window.setBackgroundBlurRadius(from + (to - from) * (i + 1) / 6); delay(16) }
    }

    // Pull down at the top of the drawer to drag it closed; fling decides where it settles.
    val drawerScroll = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (drawer.value < 1f && available.y < 0) { dragDrawer(available.y); return available.copy(x = 0f) }
                return Offset.Zero
            }
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (available.y > 0 && source == NestedScrollSource.UserInput) { dragDrawer(available.y); return available.copy(x = 0f) }
                return Offset.Zero
            }
            override suspend fun onPreFling(available: Velocity): Velocity {
                if (drawer.value >= 1f) return Velocity.Zero
                settleDrawer(available.y < -600f || (available.y < 600f && drawer.value > 0.7f))
                return available
            }
        }
    }

    // Built once: if HomeScreen got new lambdas while the drawer starts to show, it would recompose
    // mid-swipe and the drag gesture would be cancelled.
    val homeActions = remember(screenPx) {
        HomeActions(
            onSearch = { searching = true },
            onDrawer = { settleDrawer(true) },
            onDrag = { dragDrawer(it) },
            onDragEnd = { v -> settleDrawer(v < -600f || (v < 600f && drawer.value > 0.3f)) },
            onPickWidget = { picking = true },
        )
    }

    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().graphicsLayer {
            val p = max(drawer.value, searchProgress)
            val s = (1f - 0.06f * p) * (0.9f + 0.1f * enter.value)
            scaleX = s; scaleY = s
            alpha = (1f - p) * enter.value
        }) {
            HomeScreen(apps, widgets, homeActions)
        }
        // Always composed and parked below the screen: adding it under the finger mid-swipe cancels the drag.
        Box(Modifier.fillMaxSize().graphicsLayer {
            translationY = (1f - drawer.value) * size.height
            alpha = (drawer.value * 1.6f).coerceAtMost(1f)
        }) {
            DrawerScreen(apps, drawerScroll, onSearch = { query = it; searching = true })
        }
        AnimatedVisibility(
            searching,
            enter = fadeIn(tween(160)) + slideInVertically(tween(220)) { it / 12 },
            exit = fadeOut(tween(120)) + slideOutVertically(tween(160)) { it / 12 },
        ) {
            SearchScreen(apps, query) { query = it }
        }
        if (picking) WidgetPicker(widgets) { picking = false }
    }
}

// ---------- Home ----------

private class HomeActions(
    val onSearch: () -> Unit, val onDrawer: () -> Unit, val onDrag: (Float) -> Unit, val onDragEnd: (Float) -> Unit, val onPickWidget: () -> Unit,
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HomeScreen(apps: Apps, widgets: Widgets, actions: HomeActions) {
    val (onSearch, onDrawer, onDrag) = Triple(actions.onSearch, actions.onDrawer, actions.onDrag)
    val (onDragEnd, onPickWidget) = actions.onDragEnd to actions.onPickWidget
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    var menu by remember { mutableStateOf(false) }
    var removing by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxSize()
            .draggable(rememberDraggableState(onDrag), Orientation.Vertical, onDragStopped = { onDragEnd(it) })
            .combinedClickable(interactionSource = null, indication = null, onLongClick = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress); menu = true
            }) {}
            .systemBarsPadding().padding(horizontal = 6.dp).testTag("home")
    ) {
        Clock()
        widgets.ids.forEach { id -> WidgetView(widgets, id); Spacer(Modifier.height(6.dp)) }
        Spacer(Modifier.weight(1f))
        AppGrid(apps, apps.home.mapNotNull(apps::byKey), labels = true)
        Spacer(Modifier.height(8.dp))
        Bar(Modifier.testTag("homeSearch").clickable(onClick = onSearch)) {
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

/** Tracks a composable's on-screen bounds so a launch can zoom out of it. */
@Composable
private fun rememberLaunchSource(): Pair<Modifier, () -> LaunchSource> {
    val view = LocalView.current
    var bounds by remember { mutableStateOf(androidx.compose.ui.geometry.Rect.Zero) }
    return Modifier.onGloballyPositioned { bounds = it.boundsInWindow() } to { LaunchSource(view, bounds.toAndroidRect()) }
}

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
private fun AppTile(apps: Apps, app: App, label: Boolean, size: Dp, modifier: Modifier = Modifier) {
    var menu by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed || menu) 0.88f else 1f, spring(dampingRatio = 0.6f, stiffness = 700f), label = "press")
    val (track, source) = rememberLaunchSource()
    Box(modifier) {
        Column(
            Modifier.fillMaxWidth().combinedClickable(
                interactionSource = interaction, indication = null,
                onLongClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); menu = true },
            ) { apps.launch(app, source()) },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(apps, app, size, track.graphicsLayer { scaleX = scale; scaleY = scale })
            if (label) Text(app.label, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Medium, maxLines = 1,
                overflow = TextOverflow.Ellipsis, style = TextStyle(shadow = TextShadow), modifier = Modifier.padding(top = 3.dp, start = 2.dp, end = 2.dp))
        }
        if (menu) AppPopup(apps, app) { menu = false }
    }
}

@Composable
private fun Icon(apps: Apps, app: App, size: Dp, modifier: Modifier = Modifier) {
    val bmp = apps.icons[app.key]
    val m = modifier.size(size).clip(RoundedCornerShape(size * 0.2f))
    if (bmp != null) Image(bmp, app.label, m) else Box(m.background(Line))
}

/** Places the popup above its anchor (below if there's no room), horizontally centred and kept on screen. */
private object AboveAnchor : PopupPositionProvider {
    override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset {
        val x = (anchorBounds.center.x - popupContentSize.width / 2).coerceIn(0, max(0, windowSize.width - popupContentSize.width))
        val above = anchorBounds.top - popupContentSize.height
        return IntOffset(x, if (above >= 0) above else anchorBounds.bottom)
    }
}

/** Nova-style long-press card: the app's shortcuts, then a row of quick actions. Scales in from the icon. */
@Composable
private fun AppPopup(apps: Apps, app: App, onDismiss: () -> Unit) {
    val shortcuts = remember(app) { apps.shortcuts(app).take(4).map { it to apps.shortcutIcon(it) } }
    val appear = remember { Animatable(0f) }
    LaunchedEffect(Unit) { appear.animateTo(1f, spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessMediumLow)) }
    Popup(popupPositionProvider = AboveAnchor, onDismissRequest = onDismiss, properties = PopupProperties(focusable = true)) {
        Column(
            Modifier.padding(6.dp).width(220.dp)
                .graphicsLayer {
                    val v = appear.value
                    scaleX = 0.85f + 0.15f * v; scaleY = 0.85f + 0.15f * v; alpha = v.coerceIn(0f, 1f)
                    transformOrigin = TransformOrigin(0.5f, 1f)
                }
                .clip(RoundedCornerShape(6.dp)).background(PopupBg).border(1.dp, Line, RoundedCornerShape(6.dp))
        ) {
            Text(app.label, color = Muted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 10.dp, end = 10.dp, top = 8.dp, bottom = 4.dp))
            shortcuts.forEach { (s, icon) ->
                val (track, source) = rememberLaunchSource()
                Row(Modifier.fillMaxWidth().then(track).clickable { apps.launch(s, source()); onDismiss() }.padding(horizontal = 10.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    val m = Modifier.size(22.dp).clip(RoundedCornerShape(4.dp))
                    if (icon != null) Image(icon, null, m) else Box(m.background(Line))
                    Text(s.label, color = Fg, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 10.dp))
                }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(Line))
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                val onHome = app.key in apps.home
                val onDock = app.key in apps.dock
                PopupAction(if (onHome) Icons.Filled.Home else Icons.Outlined.Home, if (onHome) "Unpin" else "Home") { apps.toggleHome(app); onDismiss() }
                PopupAction(if (onDock) Icons.Filled.Star else Icons.Outlined.Star, if (onDock) "Undock" else "Dock") { apps.toggleDock(app); onDismiss() }
                PopupAction(Icons.Outlined.Info, "Info") { apps.appInfo(app); onDismiss() }
                PopupAction(Icons.Outlined.Delete, "Uninstall") { apps.uninstall(app); onDismiss() }
            }
        }
    }
}

@Composable
private fun RowScope.PopupAction(icon: ImageVector, label: String, onClick: () -> Unit) {
    Column(Modifier.weight(1f).clip(RoundedCornerShape(4.dp)).clickable(onClick = onClick).padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        androidx.compose.material3.Icon(icon, label, tint = Fg, modifier = Modifier.size(20.dp))
        Text(label, color = Muted, fontSize = 9.5.sp, modifier = Modifier.padding(top = 2.dp))
    }
}

// ---------- Drawer ----------

@Composable
private fun DrawerScreen(apps: Apps, scroll: NestedScrollConnection, onSearch: (String) -> Unit) {
    val grid = rememberLazyGridState()
    val scope = rememberCoroutineScope()
    val frequent = apps.frequent(5)
    val sections = apps.all.groupBy { it.label.firstOrNull()?.uppercaseChar()?.takeIf(Char::isLetter) ?: '#' }.toSortedMap()
    // Grid index of each section header, for the letter scrubber.
    val headerIndex = remember(sections, frequent) {
        var i = if (frequent.isEmpty()) 0 else 1 + frequent.size
        sections.map { (letter, group) -> (letter to i).also { i += 1 + group.size } }.toMap()
    }
    var scrubLetter by remember { mutableStateOf<Char?>(null) }
    var scrubY by remember { mutableFloatStateOf(0f) }
    var scrubTop by remember { mutableFloatStateOf(0f) }
    Column(Modifier.fillMaxSize().background(Scrim).systemBarsPadding().imePadding().testTag("drawer")) {
        Box(Modifier.weight(1f)) {
            LazyVerticalGrid(GridCells.Fixed(5), Modifier.fillMaxSize().padding(end = 14.dp).nestedScroll(scroll), grid,
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (frequent.isNotEmpty()) {
                    item(span = { GridItemSpan(5) }) { Section("Frequent") }
                    items(frequent, key = { "f" + it.key }) { AppTile(apps, it, true, 40.dp) }
                }
                sections.forEach { (letter, group) ->
                    item(span = { GridItemSpan(5) }, key = "h$letter") { Section(letter.toString()) }
                    items(group, key = { it.key }) { AppTile(apps, it, true, 40.dp) }
                }
            }
            val letters = headerIndex.keys.toList()
            Column(Modifier.align(Alignment.CenterEnd).width(14.dp).testTag("scrubber")
                .onGloballyPositioned { scrubTop = it.positionInParent().y }
                .pointerInput(headerIndex) {
                    fun jump(y: Float) {
                        val l = letters[(y / size.height * letters.size).toInt().coerceIn(0, letters.lastIndex)]
                        scrubY = y
                        if (l != scrubLetter) { scrubLetter = l; scope.launch { grid.scrollToItem(headerIndex[l]!!) } }
                    }
                    detectVerticalDragGestures(
                        onDragStart = { jump(it.y) },
                        onDragEnd = { scrubLetter = null },
                        onDragCancel = { scrubLetter = null },
                    ) { change, _ -> jump(change.position.y) }
                }) {
                letters.forEach { l ->
                    Text(l.toString(), color = if (l == scrubLetter) Accent else Color(0xFF9AA3AB), fontSize = 9.sp, fontFamily = Mono,
                        fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().clickable { scope.launch { grid.scrollToItem(headerIndex[l]!!) } })
                }
            }
            // Fast-scroll bubble next to the finger.
            scrubLetter?.let { l ->
                val bubble = 52.dp
                val bubblePx = with(LocalDensity.current) { bubble.toPx() }
                Box(
                    Modifier.align(Alignment.TopEnd).offset { IntOffset(-(bubblePx * 0.9f).roundToInt(), (scrubTop + scrubY - bubblePx / 2).roundToInt()) }
                        .size(bubble).clip(RoundedCornerShape(6.dp)).background(Accent),
                    contentAlignment = Alignment.Center,
                ) { Text(l.toString(), color = Color(0xFF17191C), fontSize = 26.sp, fontWeight = FontWeight.Bold) }
            }
        }
        Spacer(Modifier.height(6.dp))
        Bar {
            Text("⌕", color = Muted, fontSize = 15.sp)
            // Same search as home: the first keystroke hands over to the search screen (apps + calculator).
            Field("", { if (it.isNotEmpty()) onSearch(it) }, "Search apps, math, dates", Modifier.weight(1f), autoFocus = false) {}
        }
        Spacer(Modifier.height(6.dp))
    }
}

// ---------- Search + calculator ----------

private data class Calc(val expression: String, val result: String, val micros: Long)

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SearchScreen(apps: Apps, query: String, onQuery: (String) -> Unit) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val haptic = LocalHapticFeedback.current
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
            top != null -> apps.launch(top)
            else -> {}
        }
    }

    Column(Modifier.fillMaxSize().background(Sheet).systemBarsPadding().imePadding()) {
        LazyColumn(Modifier.weight(1f)) {
            calc?.let { c ->
                item(key = "calc") {
                    Column(Modifier.animateItem()) {
                        Section("Calculator", "%.1f ms".format(c.micros / 1000.0))
                        CalcCard(c) { copy(c) }
                    }
                }
            }
            if (matches.isNotEmpty()) {
                item(key = "apps") { Box(Modifier.animateItem()) { Section("Apps", "${matches.size}") } }
                matches.forEachIndexed { i, (app, _, range) ->
                    item(key = app.key) {
                        var menu by remember { mutableStateOf(false) }
                        val (track, source) = rememberLaunchSource()
                        Box(Modifier.animateItem()) {
                            ResultRow(apps, app, highlight(app.label, range), selected = i == 0 && calc == null,
                                trailing = if (i == 0 && calc == null) "↵ Open" else "App",
                                modifier = track.combinedClickable(onLongClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress); menu = true
                                }) { apps.launch(app, source()) })
                            if (menu) AppPopup(apps, app) { menu = false }
                        }
                    }
                }
            }
            if (shortcuts.isNotEmpty()) {
                item(key = "sc") { Box(Modifier.animateItem()) { Section("Shortcuts") } }
                items(shortcuts, key = { "s" + it.info.id + it.app.key }) { s ->
                    val (track, source) = rememberLaunchSource()
                    Box(Modifier.animateItem()) {
                        ResultRow(apps, s.app, AnnotatedString(s.label), false, s.app.label, track.clickable { apps.launch(s, source()) })
                    }
                }
            }
            if ((calc != null || query.isBlank()) && apps.history.isNotEmpty()) {
                item(key = "hist") { Box(Modifier.animateItem()) { Section("History") } }
                items(apps.history.take(6), key = { "h" + it.first }) { (e, r) ->
                    Row(Modifier.animateItem().fillMaxWidth().clickable { onQuery(e) }.padding(horizontal = 10.dp, vertical = 5.dp)) {
                        Text(e, color = Color(0xFF9AA3AB), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        Text(r, color = Fg, fontSize = 12.sp, fontFamily = Mono, modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
            if (query.isBlank()) {
                val freq = apps.frequent(5)
                if (freq.isNotEmpty()) {
                    item(key = "freq") { Box(Modifier.animateItem()) { Section("Frequent") } }
                    items(freq, key = { "q" + it.key }) { app ->
                        val (track, source) = rememberLaunchSource()
                        Box(Modifier.animateItem()) {
                            ResultRow(apps, app, AnnotatedString(app.label), false, "App", track.clickable { apps.launch(app, source()) })
                        }
                    }
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
            Field(query, onQuery, "Search apps, math, dates", Modifier.weight(1f).testTag("searchField"), autoFocus = true, onGo = go)
            if (query.isNotEmpty()) Text("✕", color = Muted, fontSize = 13.sp, modifier = Modifier.clickable { onQuery("") }.padding(horizontal = 4.dp))
        }
        Spacer(Modifier.height(6.dp))
    }
}

@Composable
private fun CalcCard(c: Calc, onCopy: () -> Unit) {
    Row(
        Modifier.testTag("calcCard").fillMaxWidth().padding(horizontal = 6.dp, vertical = 4.dp).clip(RoundedCornerShape(4.dp))
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
    // Text set from outside (drawer hand-off, history tap) puts the cursor at the end.
    var field by remember { mutableStateOf(TextFieldValue(value, TextRange(value.length))) }
    if (field.text != value) field = TextFieldValue(value, TextRange(value.length))
    BasicTextField(
        field, { field = it; if (it.text != value) onValue(it.text) }, modifier.focusRequester(focus),
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
