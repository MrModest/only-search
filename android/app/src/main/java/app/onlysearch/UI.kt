@file:OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class, ExperimentalTextApi::class)

package app.onlysearch

import android.app.Activity
import android.appwidget.AppWidgetProviderInfo
import android.content.Intent
import android.os.Build
import android.speech.RecognizerIntent
import android.text.format.DateFormat
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toAndroidRect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import android.appwidget.AppWidgetHostView
import android.os.Bundle
import android.util.SizeF
import androidx.compose.ui.layout.ContentScale
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
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.Date
import kotlin.math.max
import kotlin.math.roundToInt

// Mock-up 2026-10-24 (docs/Mock-up 2026-10-24.html): soft dark tiles and cards, sky-blue accent, Inter + JetBrains Mono.
// Motion follows Nova Launcher: finger-tracked drawer, icon zoom launches, press scale, popup shortcuts.
internal val Sheet = Color(0xFF0E0F12)
internal val Tile = Color(0xFF1E2025)
internal val TileLine = Color(0xFF30333A)
internal val FieldBg = Color(0xFF1A1C21)
internal val Card = Color(0xFF16181C)
internal val CardLine = Color(0xFF26292F)
internal val Divider = Color(0xFF23262C)
internal val PillLine = Color(0xFF34373E)
internal val ChipLine = Color(0xFF2E3138)
internal val Accent = Color(0xFF7CC4FA)
internal val OnAccent = Color(0xFF06121C)
internal val AccentCard = Color(0xFF121B24)
internal val AccentLine = Color(0xFF24405A)
internal val Fg = Color(0xFFEDEAE4)
internal val Muted = Color(0xFFA6A29A)
internal val Dim = Color(0xFF8C887F)
internal val Danger = Color(0xFFFF8A7A)
internal val Scrim = Color(0xF2111216)
internal val PopupBg = Color(0xFF1C1E23)
internal val TextShadow = Shadow(Color(0x66000000), Offset(0f, 1f), 3f)
internal val Settle = spring<Float>(dampingRatio = 0.9f, stiffness = 420f)

internal fun family(res: Int, vararg weights: Int) =
    FontFamily(weights.map { Font(res, FontWeight(it), variationSettings = FontVariation.Settings(FontVariation.weight(it))) })
internal val Inter = family(R.font.inter, 300, 400, 500, 600, 700)
internal val Mono = family(R.font.jetbrains_mono, 400, 500, 700)

/** Stroked 24×24 line icon from SVG path data, the way the mock-up draws them. */
internal fun line(d: String, width: Float = 1.7f) = ImageVector.Builder(defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
    .addPath(PathParser().parsePathString(d).toNodes(), fill = null, stroke = SolidColor(Color.White), strokeLineWidth = width,
        strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round)
    .build()

internal object Ic {
    val search = line("M11 18a7 7 0 1 0 0-14 7 7 0 0 0 0 14z M20 20l-4-4", 2f)
    val mic = line("M12 3a3 3 0 0 0-3 3v6a3 3 0 0 0 6 0V6a3 3 0 0 0-3-3z M5.5 11a6.5 6.5 0 0 0 13 0 M12 17.5V21")
    val close = line("M6 6l12 12 M18 6L6 18", 1.8f)
    val history = line("M3 12a9 9 0 1 0 3-6.7L3 8 M3 3v5h5 M12 7v5l3 2", 1.8f)
    val info = line("M12 21a9 9 0 1 0 0-18 9 9 0 0 0 0 18z M12 11v6 M12 7.5h.01")
    val home = line("M4 11l8-7 8 7v9H4z")
    val dock = line("M3 14h18v6H3z M7 10h10")
    val uninstall = line("M5 7h14 M10 7V4h4v3 M7 7l1 13h8l1-13")
    val expand = line("M6 9l6 6 6-6", 2f)
    val collapse = line("M6 15l6-6 6 6", 2f)
}

@Composable
internal fun Label(text: String, modifier: Modifier = Modifier, color: Color = Muted) =
    Text(text.uppercase(), modifier, color = color, fontSize = 11.sp, fontFamily = Mono, letterSpacing = 0.14.em, maxLines = 1)

@Composable
internal fun shadowed() = LocalTextStyle.current.copy(shadow = TextShadow)

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
    var settings by remember { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf(false) }
    val pager = rememberPagerState(apps.defaultPage) { apps.pages.size }
    LaunchedEffect(pager) { snapshotFlow { pager.currentPage }.collect { apps.currentPage = it } }
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
    LaunchedEffect(homeTick) {
        if (homeTick == 0) return@LaunchedEffect
        val onHome = !searching && !picking && settings == null && !editing && drawer.value == 0f
        closeSearch(); picking = false; settings = null; editing = false; settleDrawer(false)
        if (onHome) pager.animateScrollToPage(apps.defaultPage)
    }
    LaunchedEffect(stopTick) { if (stopTick > 0) { closeSearch(); picking = false; drawerBlur = false; drawer.snapTo(0f) } }
    LaunchedEffect(resumeTick) {
        if (apps.returningFromApp) {
            apps.returningFromApp = false
            enter.snapTo(0f); enter.animateTo(1f, spring(dampingRatio = 0.8f, stiffness = 300f))
        }
    }
    BackHandler(searching || picking || editing || drawerOpening) {
        when { picking -> picking = false; searching -> closeSearch(); editing -> editing = false; else -> settleDrawer(false) }
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
            onVoice = { query = it; searching = true },
            onDrag = { dragDrawer(it) },
            onDragEnd = { v -> settleDrawer(v < -600f || (v < 600f && drawer.value > 0.3f)) },
            onPickWidget = { picking = true },
            onSettings = { settings = it },
            onEdit = { editing = true },
        )
    }

    ProvideTextStyle(TextStyle(fontFamily = Inter, color = Fg)) {
        Box(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().graphicsLayer {
                val p = max(drawer.value, searchProgress)
                val s = (1f - 0.06f * p) * (0.9f + 0.1f * enter.value)
                scaleX = s; scaleY = s
                alpha = (1f - p) * enter.value
            }) {
                HomeScreen(apps, widgets, pager, homeActions)
            }
            // Status icons are always light (MainActivity); this shade keeps them readable on a light wallpaper.
            Box(Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Color(0x73000000), Color.Transparent)))
                .windowInsetsPadding(WindowInsets.statusBars).height(16.dp))
            // Always composed and parked below the screen: adding it under the finger mid-swipe cancels the drag.
            Box(Modifier.fillMaxSize().graphicsLayer {
                translationY = (1f - drawer.value) * size.height
                alpha = (drawer.value * 1.6f).coerceAtMost(1f)
            }) {
                DrawerScreen(apps, drawerScroll, onSearch = { searching = true })
            }
            AnimatedVisibility(
                searching,
                enter = fadeIn(tween(160)) + slideInVertically(tween(220)) { it / 12 },
                exit = fadeOut(tween(120)) + slideOutVertically(tween(160)) { it / 12 },
            ) {
                SearchScreen(apps, query) { query = it }
            }
            AnimatedVisibility(editing, enter = fadeIn(tween(160)) + scaleIn(tween(220), 1.06f), exit = fadeOut(tween(140)) + scaleOut(tween(180), 1.06f)) {
                EditHome(apps, widgets, onDone = { page -> editing = false; scope.launch { pager.scrollToPage(page) } },
                    onPickWidget = { picking = true }, onSettings = { settings = it })
            }
            if (picking) WidgetPicker(apps, widgets) { picking = false }
            AnimatedVisibility(
                settings != null,
                enter = fadeIn(tween(160)) + slideInHorizontally(tween(220)) { it / 8 },
                exit = fadeOut(tween(120)) + slideOutHorizontally(tween(160)) { it / 8 },
            ) {
                SettingsScreen(apps, settings ?: "hub") { settings = null }
            }
        }
    }
}

// ---------- Home ----------

internal class HomeActions(
    val onSearch: () -> Unit, val onVoice: (String) -> Unit, val onDrag: (Float) -> Unit, val onDragEnd: (Float) -> Unit, val onPickWidget: () -> Unit,
    val onSettings: (String) -> Unit, val onEdit: () -> Unit,
)

@Composable
internal fun HomeScreen(apps: Apps, widgets: Widgets, pager: PagerState, actions: HomeActions) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    var menu by remember { mutableStateOf(false) }
    var removing by remember { mutableStateOf(false) }
    LaunchedEffect(prefs.columns, prefs.rows) { apps.fitGrid() }
    Column(
        Modifier.fillMaxSize()
            .pointerInput(Unit) { detectPinchIn(actions.onEdit) }
            .draggable(rememberDraggableState(actions.onDrag), Orientation.Vertical, onDragStopped = { actions.onDragEnd(it) })
            .combinedClickable(interactionSource = null, indication = null, onLongClick = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress); menu = true
            }) {}
            .systemBarsPadding().padding(top = 24.dp, bottom = 12.dp).testTag("home")
    ) {
        HorizontalPager(pager, Modifier.weight(1f).fillMaxWidth(), key = { apps.pages.getOrNull(it)?.id ?: -1L }) { i ->
            val page = apps.pages.getOrNull(i) ?: return@HorizontalPager
            Column(Modifier.fillMaxSize().padding(horizontal = 18.dp)) {
                Clock()
                Spacer(Modifier.height(24.dp))
                HomeGrid(apps, widgets, page, Modifier.weight(1f).fillMaxWidth())
            }
        }
        Column(Modifier.padding(horizontal = 18.dp)) {
            PageDots(apps.pages.size, pager.currentPage, Modifier.padding(vertical = 14.dp))
            AppGrid(apps, apps.dock.mapNotNull(apps::byKey), size = 58.dp * prefs.iconSize / 100, labelColor = null)
            Spacer(Modifier.height(16.dp))
            Pill("Apps, math, units, time…", Modifier.testTag("homeSearch"), onClick = actions.onSearch) { MicButton(actions.onVoice) }
        }
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

internal fun pickWallpaper(context: android.content.Context) =
    context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SET_WALLPAPER), "Wallpaper"))

/** Fires once when two fingers pinch together. Only watches; never consumes, so other gestures still work. */
internal suspend fun PointerInputScope.detectPinchIn(onPinch: () -> Unit) = awaitEachGesture {
    awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
    var start = 0f
    while (true) {
        val e = awaitPointerEvent(PointerEventPass.Initial)
        val down = e.changes.filter { it.pressed }
        if (down.isEmpty()) break
        if (down.size < 2) continue
        val d = (down[0].position - down[1].position).getDistance()
        if (start == 0f) start = d else if (d < start * 0.65f) { onPinch(); break }
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

/** A home page's cell grid: apps and widgets at their cells, sized from the Home screen settings. */
@Composable
internal fun HomeGrid(apps: Apps, widgets: Widgets, page: HomePage, modifier: Modifier) {
    BoxWithConstraints(modifier) {
        val cw = maxWidth / prefs.columns
        val ch = maxHeight / prefs.rows
        val icon = minOf(58.dp * prefs.iconSize / 100, cw - 12.dp, ch - if (prefs.labels) 28.dp else 6.dp)
        page.items.forEach { c ->
            key(c.key) {
                Box(Modifier.offset(cw * c.x, ch * c.y).size(cw * c.w, ch * c.h), contentAlignment = Alignment.Center) {
                    val id = c.widgetId
                    if (id != null) WidgetView(widgets, id, cw * c.w - 8.dp, ch * c.h - 8.dp)
                    else apps.byKey(c.key)?.let { AppTile(apps, it, if (prefs.labels) Fg else null, icon) }
                }
            }
        }
    }
}

@Composable
internal fun Clock() {
    val context = LocalContext.current
    val now by produceState(Date()) { while (true) { value = Date(); delay(60_000 - System.currentTimeMillis() % 60_000) } }
    Column(Modifier.padding(horizontal = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(DateFormat.format("EEE d MMM", now).toString().uppercase(), style = shadowed(), color = Muted, fontSize = 12.sp,
            fontFamily = Mono, letterSpacing = 0.14.em)
        // AM/PM would not fit at this size; the status bar already shows it.
        Text(DateFormat.format(if (DateFormat.is24HourFormat(context)) "H:mm" else "h:mm", now).toString(), style = shadowed(),
            fontSize = 92.sp, fontWeight = FontWeight.Light, letterSpacing = (-0.045).em, lineHeight = 92.sp)
    }
}

@Composable
internal fun WidgetView(widgets: Widgets, id: Int, width: Dp, height: Dp) {
    val info = remember(id) { widgets.manager.getAppWidgetInfo(id) } ?: return
    var view by remember { mutableStateOf<AppWidgetHostView?>(null) }
    // Tell the widget how big it really is, so it can pick a layout that fits.
    LaunchedEffect(view, width, height) {
        val v = view ?: return@LaunchedEffect
        if (Build.VERSION.SDK_INT >= 31) v.updateAppWidgetSize(Bundle(), listOf(SizeF(width.value, height.value)))
        else @Suppress("DEPRECATION") v.updateAppWidgetSize(null, width.value.toInt(), height.value.toInt(), width.value.toInt(), height.value.toInt())
    }
    AndroidView(
        factory = { ctx -> widgets.host.createView(ctx, id, info).apply { setAppWidget(id, info); view = this } },
        modifier = Modifier.size(width, height).clip(RoundedCornerShape(24.dp)),
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
internal fun WidgetPicker(apps: Apps, widgets: Widgets, onClose: () -> Unit) {
    val context = LocalContext.current
    val pm = context.packageManager
    val density = context.resources.displayMetrics.density
    val groups = remember {
        widgets.providers().groupBy { it.provider.packageName }.map { (pkg, list) ->
            Triple(pkg, runCatching { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() }.getOrDefault(pkg), list)
        }.sortedBy { it.second.lowercase() }
    }
    var filter by remember { mutableStateOf("") }
    var open by remember { mutableStateOf(groups.firstOrNull()?.first) }
    val shown = groups.mapNotNull { (pkg, label, list) ->
        val hits = if (filter.isBlank() || label.contains(filter, true)) list else list.filter { it.loadLabel(pm).contains(filter, true) }
        if (hits.isEmpty()) null else Triple(pkg, label, hits)
    }
    Box(Modifier.fillMaxSize().background(Color(0xB3000000)).clickable(interactionSource = null, indication = null, onClick = onClose)) {
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
                Text("tap to place", color = Muted, fontSize = 11.sp, fontFamily = Mono)
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
                            row.forEach { p -> WidgetPreview(p, Modifier.weight(1f)) { widgets.add(p); onClose() } }
                            if (row.size == 1 && cells(row[0], density).first <= 2) Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

/** Width × height in launcher cells (~70dp each); Android 12+ widgets state their own size. */
internal fun cells(p: AppWidgetProviderInfo, density: Float): Pair<Int, Int> {
    fun of(px: Int) = ((px / density + 30) / 70).toInt().coerceAtLeast(1)
    return if (Build.VERSION.SDK_INT >= 31 && p.targetCellWidth > 0) p.targetCellWidth to p.targetCellHeight.coerceAtLeast(1)
    else of(p.minWidth) to of(p.minHeight)
}

@Composable
internal fun WidgetPreview(p: AppWidgetProviderInfo, modifier: Modifier, onClick: () -> Unit) {
    val context = LocalContext.current
    val density = context.resources.displayMetrics
    val preview = remember(p) {
        runCatching { (p.loadPreviewImage(context, density.densityDpi) ?: p.loadIcon(context, density.densityDpi))?.toBitmap()?.asImageBitmap() }.getOrNull()
    }
    val (w, h) = cells(p, density.density)
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Sheet).clickable(onClick = onClick).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            Modifier.fillMaxWidth().height(96.dp).clip(RoundedCornerShape(16.dp)).background(FieldBg)
                .border(1.dp, Color(0xFF2A2D33), RoundedCornerShape(16.dp)).padding(8.dp),
            contentAlignment = Alignment.Center,
        ) { if (preview != null) Image(preview, null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit) }
        Row(Modifier.fillMaxWidth()) {
            Text(p.loadLabel(context.packageManager), color = Muted, fontSize = 12.sp, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("$w × $h", color = Muted, fontSize = 12.sp, fontFamily = Mono)
        }
    }
}

// ---------- Apps ----------

/** Tracks a composable's on-screen bounds so a launch can zoom out of it. */
@Composable
internal fun rememberLaunchSource(): Pair<Modifier, () -> LaunchSource> {
    val view = LocalView.current
    var bounds by remember { mutableStateOf(androidx.compose.ui.geometry.Rect.Zero) }
    return Modifier.onGloballyPositioned { bounds = it.boundsInWindow() } to { LaunchSource(view, bounds.toAndroidRect()) }
}

@Composable
internal fun AppGrid(apps: Apps, list: List<App>, size: Dp, labelColor: Color? = Fg, columns: Int = 4, rowGap: Dp = 22.dp) {
    if (list.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(rowGap)) {
        list.chunked(columns).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { AppTile(apps, it, labelColor, size, Modifier.weight(1f)) }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
internal fun AppTile(apps: Apps, app: App, labelColor: Color?, size: Dp, modifier: Modifier = Modifier) {
    var menu by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.88f else if (menu) 1.1f else 1f, spring(dampingRatio = 0.6f, stiffness = 700f), label = "press")
    val (track, source) = rememberLaunchSource()
    Box(modifier) {
        Column(
            Modifier.fillMaxWidth().combinedClickable(
                interactionSource = interaction, indication = null,
                onLongClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); menu = true },
            ) { apps.launch(app, source()) },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AppIcon(apps, app, size, track.graphicsLayer { scaleX = scale; scaleY = scale }, ring = menu)
            if (labelColor != null) Text(app.label, color = labelColor, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = shadowed(), modifier = Modifier.padding(top = 7.dp, start = 2.dp, end = 2.dp))
        }
        if (menu) AppPopup(apps, app) { menu = false }
    }
}

internal val IconShapes = listOf("squircle", "circle", "square", "teardrop")

internal fun iconShape(name: String, size: Dp): Shape = when (name) {
    "circle" -> CircleShape
    "square" -> RoundedCornerShape(size / 7)
    "teardrop" -> RoundedCornerShape(50, 50, 14, 50)
    else -> RoundedCornerShape(size / 3)
}

/** App icon in the chosen shape (default: soft square, corner = a third of the side); [ring] marks the long-pressed one. */
@Composable
internal fun AppIcon(apps: Apps, app: App, size: Dp, modifier: Modifier = Modifier, ring: Boolean = false) {
    val bmp = apps.icons[app.key]
    val shape = iconShape(prefs.iconShape, size)
    val m = modifier.size(size).then(if (ring) Modifier.border(2.dp, Accent, shape) else Modifier).clip(shape)
    if (bmp != null) Image(bmp, app.label, m) else Box(m.background(Tile).border(1.dp, TileLine, shape))
}

/** Places the popup above its anchor (below if there's no room), horizontally centred and kept on screen. */
internal object AboveAnchor : PopupPositionProvider {
    override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset {
        val x = (anchorBounds.center.x - popupContentSize.width / 2).coerceIn(0, max(0, windowSize.width - popupContentSize.width))
        val above = anchorBounds.top - popupContentSize.height
        return IntOffset(x, if (above >= 0) above else anchorBounds.bottom)
    }
}

/** Long-press card: the app's shortcuts, then a row of quick actions. Scales in from the icon. */
@Composable
internal fun AppPopup(apps: Apps, app: App, onDismiss: () -> Unit) {
    val shortcuts = remember(app) { apps.shortcuts(app).take(4).map { it to apps.shortcutIcon(it) } }
    val appear = remember { Animatable(0f) }
    LaunchedEffect(Unit) { appear.animateTo(1f, spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessMediumLow)) }
    val shape = RoundedCornerShape(22.dp)
    Popup(popupPositionProvider = AboveAnchor, onDismissRequest = onDismiss, properties = PopupProperties(focusable = true)) {
        ProvideTextStyle(TextStyle(fontFamily = Inter, color = Fg)) {
            Column(
                Modifier.padding(10.dp).width(288.dp)
                    .graphicsLayer {
                        val v = appear.value
                        scaleX = 0.85f + 0.15f * v; scaleY = 0.85f + 0.15f * v; alpha = v.coerceIn(0f, 1f)
                        transformOrigin = TransformOrigin(0.5f, 1f)
                    }
                    .shadow(24.dp, shape).background(PopupBg, shape).border(1.dp, PillLine, shape).padding(6.dp)
            ) {
                Text(app.label, color = Muted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 11.dp))
                shortcuts.forEach { (s, icon) ->
                    val (track, source) = rememberLaunchSource()
                    Row(Modifier.fillMaxWidth().height(48.dp).then(track).clip(RoundedCornerShape(14.dp))
                        .clickable { apps.launch(s, source()); onDismiss() }.padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        val m = Modifier.size(22.dp).clip(RoundedCornerShape(6.dp))
                        if (icon != null) Image(icon, null, m) else Box(m.background(Tile))
                        Text(s.label, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                if (shortcuts.isNotEmpty()) Box(Modifier.padding(6.dp).fillMaxWidth().height(1.dp).background(ChipLine))
                Row(Modifier.fillMaxWidth()) {
                    val onHome = apps.onHome(app)
                    val onDock = app.key in apps.dock
                    PopupAction(Ic.info, "Info", Fg) { apps.appInfo(app); onDismiss() }
                    val context = LocalContext.current
                    fun unlocked(action: () -> Unit) {
                        if (prefs.lockLayout) Toast.makeText(context, "Layout is locked (Settings › Home screen)", Toast.LENGTH_SHORT).show() else action()
                        onDismiss()
                    }
                    PopupAction(Ic.home, if (onHome) "Unpin" else "Home", if (onHome) Accent else Fg) { unlocked { apps.toggleHome(app) } }
                    PopupAction(Ic.dock, if (onDock) "Undock" else "Dock", if (onDock) Accent else Fg) { unlocked { apps.toggleDock(app) } }
                    PopupAction(Ic.uninstall, "Uninstall", Danger) { apps.uninstall(app); onDismiss() }
                }
            }
        }
    }
}

@Composable
internal fun RowScope.PopupAction(icon: ImageVector, label: String, color: Color, onClick: () -> Unit) {
    Column(Modifier.weight(1f).height(62.dp).clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically)) {
        Icon(icon, label, tint = color, modifier = Modifier.size(20.dp))
        Text(label, color = color, fontSize = 11.sp)
    }
}

// ---------- Drawer ----------

@Composable
internal fun Handle(modifier: Modifier = Modifier) = Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
    Box(Modifier.size(36.dp, 4.dp).clip(CircleShape).background(Color(0xFF3A3D45)))
}

@Composable
internal fun DrawerScreen(apps: Apps, scroll: NestedScrollConnection, onSearch: () -> Unit) {
    val grid = rememberLazyGridState()
    val scope = rememberCoroutineScope()
    val frequent = apps.frequent(4)
    val sections = apps.all.groupBy { it.label.firstOrNull()?.uppercaseChar()?.takeIf(Char::isLetter) ?: '#' }.toSortedMap()
    // Grid index of each section header, for the letter scrubber.
    val headerIndex = remember(sections, frequent) {
        var i = if (frequent.isEmpty()) 0 else 2 + frequent.size
        sections.map { (letter, group) -> (letter to i).also { i += 1 + group.size } }.toMap()
    }
    var scrubLetter by remember { mutableStateOf<Char?>(null) }
    var scrubY by remember { mutableFloatStateOf(0f) }
    var scrubTop by remember { mutableFloatStateOf(0f) }
    Column(Modifier.fillMaxSize().background(Scrim).systemBarsPadding().testTag("drawer")) {
        Handle(Modifier.padding(top = 8.dp, bottom = 14.dp))
        Box(Modifier.weight(1f)) {
            LazyVerticalGrid(GridCells.Fixed(4), Modifier.fillMaxSize().padding(start = 12.dp, end = 28.dp).nestedScroll(scroll), grid,
                verticalArrangement = Arrangement.spacedBy(10.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (frequent.isNotEmpty()) {
                    item(span = { GridItemSpan(4) }) { Label("Suggested", Modifier.padding(start = 6.dp, top = 6.dp)) }
                    items(frequent, key = { "f" + it.key }) { AppTile(apps, it, Fg, 54.dp) }
                    item(span = { GridItemSpan(4) }) { Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFF22252A))) }
                }
                sections.forEach { (letter, group) ->
                    item(span = { GridItemSpan(4) }, key = "h$letter") {
                        Text(letter.toString(), color = Accent, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 8.dp))
                    }
                    items(group, key = { it.key }) { AppTile(apps, it, Fg, 54.dp) }
                }
            }
            // Full A–Z like the mock-up; a letter with no apps jumps to the next one that has some.
            val letters = ('A'..'Z').toList() + headerIndex.keys.filter { it !in 'A'..'Z' }
            val target = letters.associateWith { l -> headerIndex[l] ?: headerIndex.entries.firstOrNull { it.key > l }?.value ?: headerIndex.values.lastOrNull() ?: 0 }
            Column(Modifier.align(Alignment.CenterEnd).width(28.dp).fillMaxHeight().padding(top = 8.dp, bottom = 16.dp).testTag("scrubber")
                .onGloballyPositioned { scrubTop = it.positionInParent().y }
                .pointerInput(headerIndex) {
                    fun jump(y: Float) {
                        val l = letters[(y / size.height * letters.size).toInt().coerceIn(0, letters.lastIndex)]
                        scrubY = y
                        if (l != scrubLetter) { scrubLetter = l; scope.launch { grid.scrollToItem(target.getValue(l)) } }
                    }
                    detectVerticalDragGestures(
                        onDragStart = { jump(it.y) },
                        onDragEnd = { scrubLetter = null },
                        onDragCancel = { scrubLetter = null },
                    ) { change, _ -> jump(change.position.y) }
                },
                verticalArrangement = Arrangement.SpaceBetween) {
                letters.forEach { l ->
                    Text(l.toString(), color = if (l == scrubLetter) Accent else Dim, fontSize = 10.sp, fontFamily = Mono,
                        fontWeight = if (l == scrubLetter) FontWeight.Bold else FontWeight.Normal, textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().clickable { scope.launch { grid.scrollToItem(target.getValue(l)) } })
                }
            }
            // Fast-scroll bubble next to the finger, its point aimed at the scrubber.
            scrubLetter?.let { l ->
                val bubble = 56.dp
                val (bubblePx, gapPx) = with(LocalDensity.current) { bubble.toPx() to 44.dp.toPx() }
                Box(
                    Modifier.align(Alignment.TopEnd).offset { IntOffset(-gapPx.roundToInt(), (scrubTop + scrubY - bubblePx / 2).roundToInt()) }
                        .size(bubble).clip(RoundedCornerShape(28.dp, 28.dp, 6.dp, 28.dp)).background(Accent),
                    contentAlignment = Alignment.Center,
                ) { Text(l.toString(), color = OnAccent, fontSize = 26.sp, fontWeight = FontWeight.Bold) }
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFF22252A)))
        Pill("Search ${apps.all.size} apps or calculate", Modifier.padding(start = 14.dp, end = 14.dp, top = 10.dp, bottom = 10.dp),
            height = 52.dp, onClick = onSearch)
    }
}

// ---------- Search + calculator ----------

internal data class Calc(val expression: String, val result: String, val micros: Long)

internal val Examples = listOf("20% of 450", "5 km in mi", "time in Tokyo", "\$48 + 15% tip", "3:45pm + 4h 10m", "sqrt(2)^8")

@Composable
internal fun SearchScreen(apps: Apps, query: String, onQuery: (String) -> Unit) {
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
    val (matches, matchMicros) = remember(query, apps.all.size) {
        val t0 = System.nanoTime()
        apps.all.mapNotNull { a -> match(a.label, query)?.let { Triple(a, it.first, it.second) } }
            .sortedWith(compareByDescending<Triple<App, Int, IntRange?>> { it.second }.thenBy { it.first.label.length })
            .take(9) to (System.nanoTime() - t0) / 1000
    }
    val c = calc
    // The calculator answer takes the top-hit slot; otherwise the best app match does.
    val top = if (c == null) matches.firstOrNull() else null
    val rest = matches.drop(if (top != null) 1 else 0).map { it.first }.take(8)
    val shortcuts = remember(top?.first?.key) { top?.let { apps.shortcuts(it.first).take(3) }.orEmpty() }
    val copy = { c: Calc ->
        clipboard.setText(AnnotatedString(c.result)); apps.remember(c.expression, c.result)
        Toast.makeText(context, "Copied ${c.result}", Toast.LENGTH_SHORT).show()
    }
    val go = {
        when {
            c != null -> copy(c)
            top != null -> apps.launch(top.first)
            else -> {}
        }
    }
    val recent = (apps.recent.mapNotNull(apps::byKey).ifEmpty { apps.frequent(5) }).take(5)

    Column(Modifier.fillMaxSize().background(Sheet).systemBarsPadding().imePadding()) {
        // Reversed so results stack up from the search bar, the best one closest to the thumb.
        LazyColumn(Modifier.weight(1f), reverseLayout = true, verticalArrangement = Arrangement.spacedBy(if (query.isBlank()) 22.dp else 18.dp, Alignment.Bottom),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 14.dp)) {
            if (query.isBlank()) {
                item(key = "try") {
                    Column(Modifier.animateItem().padding(horizontal = 4.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Label("Try typing")
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Examples.forEach { Chip(it, FieldBg, ChipLine, Mono, 12) { onQuery(it) } }
                        }
                    }
                }
                if (apps.history.isNotEmpty()) item(key = "hist") {
                    Column(Modifier.animateItem().padding(horizontal = 4.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Label("Calculation history", Modifier.weight(1f))
                            Text("Clear", color = Accent, fontSize = 12.sp, modifier = Modifier.clickable { apps.clearHistory() })
                        }
                        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Card).border(1.dp, CardLine, RoundedCornerShape(18.dp))) {
                            apps.history.take(4).forEachIndexed { i, (e, r) ->
                                if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(Divider))
                                Row(Modifier.fillMaxWidth().height(46.dp).clickable { onQuery(e) }.padding(horizontal = 14.dp),
                                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Icon(Ic.history, null, tint = Dim, modifier = Modifier.size(16.dp))
                                    Text(e, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                    Text(r, color = Accent, fontSize = 14.sp, fontFamily = Mono, fontWeight = FontWeight.Medium, maxLines = 1)
                                }
                            }
                        }
                    }
                }
                if (recent.isNotEmpty()) item(key = "recent") {
                    Column(Modifier.animateItem().padding(horizontal = 4.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Label("Recent apps")
                        AppGrid(apps, recent, size = 52.dp, labelColor = Muted, columns = 5)
                    }
                }
            } else {
                if (c != null) item(key = "calc") {
                    Column(Modifier.animateItem(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(Modifier.padding(horizontal = 4.dp)) {
                            Label("Calculator", Modifier.weight(1f))
                            Text("%.1f ms".format(c.micros / 1000.0), color = Muted, fontSize = 11.sp, fontFamily = Mono)
                        }
                        CalcCard(c) { copy(c) }
                    }
                }
                if (top != null) item(key = "top") {
                    Box(Modifier.animateItem()) { TopHit(apps, top.first, top.third, shortcuts) }
                }
                if (rest.isNotEmpty()) item(key = "apps") {
                    Column(Modifier.animateItem(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Label("Apps", Modifier.padding(horizontal = 4.dp))
                        AppGrid(apps, rest, size = 52.dp, rowGap = 12.dp)
                    }
                }
            }
        }
        val (left, right) = when {
            query.isBlank() -> "↵ open top hit" to "long-press for actions"
            c != null -> "↵ copy answer" to "long-press app for actions"
            top != null -> "↵ open ${top.first.label}" to "${matches.size} results · %.0f ms".format(matchMicros / 1000.0)
            else -> "no matches" to ""
        }
        Row(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFF22252A))) {}
        Row(Modifier.fillMaxWidth().height(34.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(left, color = Dim, fontSize = 11.sp, fontFamily = Mono, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Text(right, color = Dim, fontSize = 11.sp, fontFamily = Mono, maxLines = 1)
        }
        Row(
            Modifier.padding(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 12.dp).fillMaxWidth().height(52.dp)
                .clip(RoundedCornerShape(16.dp)).background(FieldBg).border(1.5.dp, Accent, RoundedCornerShape(16.dp))
                .padding(start = 16.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(Ic.search, null, tint = Accent, modifier = Modifier.size(20.dp))
            Field(query, onQuery, "Apps, math, units, time…", Modifier.weight(1f).testTag("searchField"), autoFocus = true, onGo = go)
            if (query.isEmpty()) MicButton(onQuery)
            else Box(Modifier.size(44.dp).clip(CircleShape).clickable { onQuery("") }, contentAlignment = Alignment.Center) {
                Icon(Ic.close, "Clear search", tint = Muted, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
internal fun TopHit(apps: Apps, app: App, range: IntRange?, shortcuts: List<Shortcut>) {
    var menu by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current
    val (track, source) = rememberLaunchSource()
    val shape = RoundedCornerShape(20.dp)
    Box {
        Column(
            Modifier.fillMaxWidth().clip(shape).background(AccentCard).border(1.dp, AccentLine, shape)
                .combinedClickable(onLongClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); menu = true }) { apps.launch(app, source()) }
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AppIcon(apps, app, 52.dp, track)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(highlight(app.label, range), fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("Top hit", color = Muted, fontSize = 12.sp)
                }
                Text("↵", color = Accent, fontSize = 11.sp, fontFamily = Mono, modifier = Modifier
                    .border(1.dp, Color(0xFF2C4E6C), RoundedCornerShape(6.dp)).padding(horizontal = 6.dp, vertical = 3.dp))
            }
            if (shortcuts.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                shortcuts.forEach { s -> Chip(s.label, Color(0xFF16232F), AccentLine, Inter, 13) { apps.launch(s, source()) } }
            }
        }
        if (menu) AppPopup(apps, app) { menu = false }
    }
}

@Composable
internal fun Chip(text: String, bg: Color, border: Color, font: FontFamily, size: Int, onClick: () -> Unit) {
    Box(Modifier.height(34.dp).clip(CircleShape).background(bg).border(1.dp, border, CircleShape).clickable(onClick = onClick).padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center) {
        Text(text, fontSize = size.sp, fontFamily = font, maxLines = 1)
    }
}

@Composable
internal fun CalcCard(c: Calc, onCopy: () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    Column(
        Modifier.testTag("calcCard").fillMaxWidth().clip(shape).background(AccentCard).border(1.dp, AccentLine, shape)
            .clickable(onClick = onCopy).padding(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(c.expression, color = Color(0xFFD6DEE6), fontSize = 15.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
        // Long answers (dates, multi-unit results) step down so they stay on one or two lines.
        val big = c.result.length <= 13
        Text(c.result, color = Accent, fontSize = if (big) 40.sp else 26.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.03).em,
            lineHeight = if (big) 46.sp else 31.sp, textAlign = TextAlign.End, modifier = Modifier.fillMaxWidth())
    }
}

internal fun highlight(label: String, range: IntRange?) = buildAnnotatedString {
    append(label)
    if (range != null && range.last < label.length) addStyle(SpanStyle(color = Accent), range.first, range.last + 1)
}

// ---------- Shared bits ----------

/** The round search pill on home and in the drawer; tapping it opens search. */
@Composable
internal fun Pill(hint: String, modifier: Modifier = Modifier, height: Dp = 56.dp, onClick: () -> Unit, trailing: @Composable () -> Unit = {}) {
    Row(
        modifier.fillMaxWidth().height(height).clip(CircleShape).background(Tile).border(1.dp, PillLine, CircleShape)
            .clickable(onClick = onClick).padding(start = 18.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(Ic.search, null, tint = Accent, modifier = Modifier.size(20.dp))
        Text(hint, color = Muted, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        trailing()
    }
}

@Composable
internal fun Field(value: String, onValue: (String) -> Unit, hint: String, modifier: Modifier, autoFocus: Boolean, fontSize: Int = 16, onGo: () -> Unit) {
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    if (autoFocus) LaunchedEffect(Unit) { focus.requestFocus(); keyboard?.show() }
    // Text set from outside (history tap, chip, voice) puts the cursor at the end.
    var field by remember { mutableStateOf(TextFieldValue(value, TextRange(value.length))) }
    if (field.text != value) field = TextFieldValue(value, TextRange(value.length))
    BasicTextField(
        field, { field = it; if (it.text != value) onValue(it.text) }, modifier.focusRequester(focus),
        textStyle = TextStyle(color = Fg, fontSize = fontSize.sp, fontFamily = Inter),
        singleLine = true,
        cursorBrush = SolidColor(Accent),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go, autoCorrectEnabled = false),
        keyboardActions = KeyboardActions(onGo = { onGo() }),
        decorationBox = { inner -> Box { if (value.isEmpty()) Text(hint, color = Muted, fontSize = fontSize.sp, maxLines = 1); inner() } },
    )
}
