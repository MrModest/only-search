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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import android.appwidget.AppWidgetManager
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.unit.round
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
    val widgets = line("M4 4h7v7H4z M13 4h7v7h-7z M4 13h7v7H4z M13 13h7v7h-7z")
    val edit = line("M4 20h4L19 9l-4-4L4 16z M13.5 6.5l4 4")
    val hide = line("M3 3l18 18 M10.6 5.1A10 10 0 0 1 12 5c5 0 9 4.5 10 7-0.4 1-1.2 2.3-2.4 3.5 M6.6 6.6C4.6 8 3.3 10 2 12c1 2.5 5 7 10 7 1.8 0 3.4-.5 4.8-1.3 M9.9 9.9a3 3 0 0 0 4.2 4.2")
    val grip = line("M5 9h14 M5 15h14", 1.8f)
    val monitor = line("M3 5h18v11H3z M8 20h8 M12 16v4")
    val phone = line("M5 4h4l2 5-2.5 1.5a11 11 0 0 0 5 5L15 13l5 2v4a2 2 0 0 1-2 2A16 16 0 0 1 3 6a2 2 0 0 1 2-2z")
    val message = line("M12 20a8 8 0 1 0-7-4l-1 4 4-1a8 8 0 0 0 4 1z")
    val globe = line("M12 21a9 9 0 1 0 0-18 9 9 0 0 0 0 18z M3 12h18 M12 3a14 14 0 0 1 0 18 M12 3a14 14 0 0 0 0 18")
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
    var pickFor by remember { mutableStateOf<String?>(null) }
    var settings by remember { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf(false) }
    val pager = rememberPagerState(apps.defaultPage) { apps.pages.size }
    LaunchedEffect(pager) { snapshotFlow { pager.currentPage }.collect { apps.currentPage = it } }
    val searchProgress by animateFloatAsState(if (searching) 1f else 0f, tween(180), label = "search")
    val dnd = remember { DragDrop() }

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
            onPickWidget = { pickFor = null; picking = true },
            onSettings = { settings = it },
            onEdit = { editing = true },
        )
    }

    dnd.onStart = {
        if (it.from == "drawer" || it.from == "popup") { settleDrawer(false); closeSearch() }
        if (it.from == "picker" && editing) { editing = false; scope.launch { pager.scrollToPage(apps.currentPage) } }
    }
    dnd.onDrop = { d, at ->
        val page = pager.currentPage
        val grid = apps.pages.getOrNull(page)?.let { dnd.grids[it.id] }
        val target = grid?.let { dnd.target(d, at, it) }
        when {
            (d.from == "grid" || d.from == "dock") && dnd.remove.contains(at) -> {
                d.key.removePrefix("w:").toIntOrNull()?.takeIf { d.key.startsWith("w:") }?.let(widgets::remove)
                apps.removeItem(d.key)
                if (apps.dock.remove(d.key)) apps.saveDock()
            }
            d.from == "picker" -> if (target != null && apps.fits(page, target.x, target.y, target.w, target.h)) {
                widgets.providers().firstOrNull { pickerKey(it) == d.key }?.let { apps.widgetSpot = page to target; apps.currentPage = page; widgets.add(it) }
            }
            apps.byKey(d.key) != null && dnd.dock.contains(at) ->
                apps.dropOnDock(d.key, ((at.x - dnd.dock.left) / (dnd.dock.width / 4)).toInt())
            target != null -> if (apps.moveTo(page, target) && d.from == "dock") apps.dock.remove(d.key).also { apps.saveDock() }
        }
    }
    // Dragging to a screen edge flips to the neighbouring page.
    val edgePx = with(LocalDensity.current) { 28.dp.toPx() }
    val widthPx = with(LocalDensity.current) { LocalConfiguration.current.screenWidthDp.dp.toPx() }
    LaunchedEffect(dnd.item != null) {
        while (dnd.item != null) {
            delay(500)
            val p = pager.currentPage
            if (dnd.pos.x < edgePx && p > 0) pager.animateScrollToPage(p - 1)
            else if (dnd.pos.x > widthPx - edgePx && p < apps.pages.lastIndex) pager.animateScrollToPage(p + 1)
        }
    }

    CompositionLocalProvider(LocalDragDrop provides dnd, LocalPickWidgets provides { pkg: String? -> pickFor = pkg; picking = true }) {
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
                DrawerScreen(apps, drawerScroll, onSearch = { searching = true }, onSettings = { settings = "drawer" })
            }
            AnimatedVisibility(
                searching,
                enter = fadeIn(tween(160)) + slideInVertically(tween(220)) { it / 12 },
                exit = fadeOut(tween(120)) + slideOutVertically(tween(160)) { it / 12 },
            ) {
                SearchScreen(apps, query, onSettings = { settings = it }) { query = it }
            }
            AnimatedVisibility(editing, enter = fadeIn(tween(160)) + scaleIn(tween(220), 1.06f), exit = fadeOut(tween(140)) + scaleOut(tween(180), 1.06f)) {
                EditHome(apps, widgets, onDone = { page -> editing = false; scope.launch { pager.scrollToPage(page) } },
                    onPickWidget = { pickFor = null; picking = true }, onSettings = { settings = it })
            }
            if (picking) WidgetPicker(apps, widgets, pickFor) { picking = false }
            AnimatedVisibility(
                settings != null,
                enter = fadeIn(tween(160)) + slideInHorizontally(tween(220)) { it / 8 },
                exit = fadeOut(tween(120)) + slideOutHorizontally(tween(160)) { it / 8 },
            ) {
                SettingsScreen(apps, settings ?: "hub") { settings = null }
            }
            if (dnd.item?.from.let { it == "grid" || it == "dock" }) RemoveZone(dnd)
            DragGhost(apps, dnd)
        }
    }
    }
}

/** Drop here to take an app, shortcut or widget off home. */
@Composable
private fun BoxScope.RemoveZone(dnd: DragDrop) {
    val over = dnd.remove.contains(dnd.pos)
    val slack = with(LocalDensity.current) { 24.dp.toPx() }
    Row(
        Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 10.dp).height(48.dp)
            .onGloballyPositioned { dnd.remove = it.boundsInWindow().inflate(slack) }
            .clip(CircleShape).background(if (over) Danger.copy(alpha = 0.25f) else PopupBg).border(1.dp, if (over) Danger else PillLine, CircleShape)
            .padding(horizontal = 22.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(Ic.close, null, tint = Danger, modifier = Modifier.size(18.dp))
        Text("Remove", color = if (over) Danger else Fg, fontSize = 14.sp)
    }
}

/** What's under the finger while dragging: the app icon, or an outline the size of the widget. */
@Composable
private fun DragGhost(apps: Apps, dnd: DragDrop) {
    val d = dnd.item ?: return
    val density = LocalDensity.current
    val w = with(density) { d.size.width.toDp() }
    val h = with(density) { d.size.height.toDp() }
    Box(Modifier.offset { IntOffset((dnd.pos.x - d.grab.x).roundToInt(), (dnd.pos.y - d.grab.y).roundToInt()) }.size(w, h)
        .graphicsLayer { scaleX = 1.08f; scaleY = 1.08f }) {
        val app = apps.byKey(d.key)
        if (d.from == "picker") Box(Modifier.fillMaxSize().padding(4.dp).clip(RoundedCornerShape(24.dp)).background(Card.copy(alpha = 0.85f))
            .border(2.dp, Accent, RoundedCornerShape(24.dp)).padding(10.dp)) {
            d.icon?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit) }
        }
        else if (d.icon != null) Image(d.icon, null, Modifier.fillMaxSize().clip(iconShape(prefs.iconShape, w)))
        else if (app != null) AppIcon(apps, app, w)
        else Box(Modifier.fillMaxSize().clip(RoundedCornerShape(24.dp)).background(Accent.copy(alpha = 0.18f)).border(2.dp, Accent, RoundedCornerShape(24.dp)))
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
    var selected by remember { mutableStateOf<String?>(null) }
    val dnd = LocalDragDrop.current
    LaunchedEffect(prefs.columns, prefs.rows) { apps.fitGrid() }
    LaunchedEffect(pager.currentPage) { selected = null }
    var menuAt by remember { mutableStateOf(IntOffset.Zero) }
    Box(
        Modifier.fillMaxSize()
            .pointerInput(Unit) { detectPinchIn(actions.onEdit) }
            .draggable(rememberDraggableState(actions.onDrag), Orientation.Vertical, onDragStopped = { actions.onDragEnd(it) })
            .pointerInput(Unit) {
                detectTapGestures(onTap = { selected = null }, onLongPress = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress); menuAt = it.round(); menu = true
                })
            }
            .testTag("home")
    ) {
    Column(Modifier.fillMaxSize().systemBarsPadding().padding(top = 24.dp, bottom = 12.dp)) {
        HorizontalPager(pager, Modifier.weight(1f).fillMaxWidth(), key = { apps.pages.getOrNull(it)?.id ?: -1L }) { i ->
            val page = apps.pages.getOrNull(i) ?: return@HorizontalPager
            Column(Modifier.fillMaxSize().padding(horizontal = 18.dp)) {
                Clock()
                Spacer(Modifier.height(24.dp))
                HomeGrid(apps, widgets, i, page, selected, { selected = it }, Modifier.weight(1f).fillMaxWidth())
            }
        }
        Column(Modifier.padding(horizontal = 18.dp)) {
            PageDots(apps.pages.size, pager.currentPage, Modifier.padding(top = 18.dp, bottom = 16.dp))
            Box(Modifier.onGloballyPositioned { dnd.dock = it.boundsInWindow() }) {
                AppGrid(apps, apps.dock.mapNotNull(apps::byKey), size = 58.dp * prefs.iconSize / 100, labelColor = null, drag = "dock")
            }
            Spacer(Modifier.height(16.dp))
            Pill("Apps, math, units, time…", Modifier.testTag("homeSearch"), onClick = actions.onSearch) { MicButton(actions.onVoice) }
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

/** A home page's cell grid: apps and widgets at their cells, sized from the Home screen settings. Also the drop target. */
@Composable
internal fun HomeGrid(apps: Apps, widgets: Widgets, index: Int, page: HomePage, selected: String?, onSelect: (String?) -> Unit, modifier: Modifier) {
    val dnd = LocalDragDrop.current
    val haptic = LocalHapticFeedback.current
    BoxWithConstraints(modifier.onGloballyPositioned { dnd.grids[page.id] = Rect(it.positionInWindow(), it.size.toSize()) }) {
        val cw = maxWidth / prefs.columns
        val ch = maxHeight / prefs.rows
        // Keep ~10dp between a label and the icon below it; small cells shrink the icon.
        val icon = minOf(58.dp * prefs.iconSize / 100, cw - 12.dp, ch - if (prefs.labels) 34.dp else 10.dp)
        val drop = dnd.item?.let { d -> dnd.grids[page.id]?.let { dnd.target(d, dnd.pos, it) }?.let { it to apps.fits(index, it.x, it.y, it.w, it.h, d.key) } }
        if (drop != null) {
            val (t, ok) = drop
            Box(Modifier.offset(cw * t.x, ch * t.y).size(cw * t.w, ch * t.h).padding(4.dp).clip(RoundedCornerShape(20.dp))
                .background((if (ok) Accent else Danger).copy(alpha = 0.12f)).border(2.dp, if (ok) Accent else Danger, RoundedCornerShape(20.dp)))
        }
        page.items.forEach { c ->
            key(c.key) {
                val id = c.widgetId
                if (id == null) Box(Modifier.offset(cw * c.x, ch * c.y).size(cw * c.w, ch * c.h), contentAlignment = Alignment.Center) {
                    if (c.key.startsWith("s:")) ShortcutTile(apps, c.key, if (prefs.labels) Fg else null, icon)
                    else apps.byKey(c.key)?.let { AppTile(apps, it, if (prefs.labels) Fg else null, icon, drag = "grid") }
                } else {
                    val coords = remember { arrayOfNulls<LayoutCoordinates>(1) }
                    val now by rememberUpdatedState(c)
                    val lifted = dnd.item?.key == c.key
                    Box(
                        Modifier.offset(cw * c.x, ch * c.y).size(cw * c.w, ch * c.h).onGloballyPositioned { coords[0] = it }
                            .graphicsLayer { alpha = if (lifted) 0.25f else 1f }
                            .pointerInput(c.key) {
                                // Initial pass: see the touch before the widget's own view, take it over after a long press.
                                tapHoldDrag(
                                    { coords[0] }, PointerEventPass.Initial, onTap = null,
                                    onLongPress = { if (!prefs.lockLayout) { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onSelect(c.key) } },
                                    onDragStart = { grab, at ->
                                        if (prefs.lockLayout) return@tapHoldDrag
                                        onSelect(null)
                                        dnd.start(Dragged(now.key, now.w, now.h, "grid", grab, Size(cw.toPx() * now.w, ch.toPx() * now.h)), at)
                                    },
                                    onDrag = dnd::move, onDragEnd = { if (dnd.item != null) dnd.end() },
                                )
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        WidgetView(widgets, id, cw * c.w - 8.dp, ch * c.h - 8.dp)
                        if (selected == c.key) WidgetFrame(apps, widgets, index, c, cw, ch) { onSelect(null) }
                    }
                }
            }
        }
    }
}

/** Selection frame on a long-pressed widget: edge handles resize it cell by cell, × removes it. */
@Composable
private fun BoxScope.WidgetFrame(apps: Apps, widgets: Widgets, page: Int, cell: Cell, cw: Dp, ch: Dp, onClose: () -> Unit) {
    val id = cell.widgetId ?: return
    val info = remember(id) { widgets.manager.getAppWidgetInfo(id) } ?: return
    val density = LocalDensity.current.density
    fun minCells(px: Int, minPx: Int, cellDp: Dp) = kotlin.math.ceil(((if (minPx > 0) minPx else px) / density) / cellDp.value).toInt().coerceAtLeast(1)
    val minW = minCells(info.minWidth, info.minResizeWidth, cw)
    val minH = minCells(info.minHeight, info.minResizeHeight, ch)
    val current by rememberUpdatedState(cell)
    fun resize(dx: Int, dy: Int, dw: Int, dh: Int, start: Cell) {
        val w = start.w + dw; val h = start.h + dh
        if (w < minW || h < minH) return
        val next = start.copy(x = start.x + dx, y = start.y + dy, w = w, h = h)
        if (next != current) apps.place(page, next)
    }
    Box(Modifier.matchParentSize().padding(2.dp).border(2.dp, Accent, RoundedCornerShape(24.dp)))
    val horizontal = info.resizeMode and AppWidgetProviderInfo.RESIZE_HORIZONTAL != 0
    val vertical = info.resizeMode and AppWidgetProviderInfo.RESIZE_VERTICAL != 0
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
            .clickable { widgets.remove(id); apps.removeItem(cell.key); onClose() }.semantics { contentDescription = "Remove widget" },
        contentAlignment = Alignment.Center,
    ) { Icon(Ic.close, null, tint = Danger, modifier = Modifier.size(14.dp)) }
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
    val dnd = LocalDragDrop.current
    val haptic = LocalHapticFeedback.current
    val preview = remember(p) {
        runCatching { (p.loadPreviewImage(context, density.densityDpi) ?: p.loadIcon(context, density.densityDpi))?.toBitmap()?.asImageBitmap() }.getOrNull()
    }
    val (w, h) = cells(p, density.density)
    val coords = remember { arrayOfNulls<LayoutCoordinates>(1) }
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Sheet).onGloballyPositioned { coords[0] = it }
            .semantics { onClick { onClick(); true } }
            .pointerInput(p) {
                tapHoldDrag(
                    { coords[0] }, onTap = onClick, onLongPress = { haptic.performHapticFeedback(HapticFeedbackType.LongPress) },
                    onDragStart = { _, at ->
                        // The ghost takes the widget's size on the current page's grid.
                        val grid = apps.pages.getOrNull(apps.currentPage)?.let { dnd.grids[it.id] } ?: return@tapHoldDrag
                        val cw = w.coerceAtMost(prefs.columns); val ch = h.coerceAtMost(prefs.rows)
                        val size = Size(grid.width / prefs.columns * cw, grid.height / prefs.rows * ch)
                        onDragging()
                        dnd.start(Dragged(pickerKey(p), cw, ch, "picker", Offset(size.width / 2, size.height / 2), size, preview), at)
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
        ) { if (preview != null) Image(preview, null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit) }
        Row(Modifier.fillMaxWidth()) {
            Text(p.loadLabel(context.packageManager), color = Muted, fontSize = 12.sp, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("$w × $h", color = Muted, fontSize = 12.sp, fontFamily = Mono)
        }
    }
}

// ---------- Apps ----------

/** Tracks a composable's bounds so a launch can zoom out of it, and a menu can point at it (screen coordinates). */
@Composable
internal fun rememberLaunchSource(): Triple<Modifier, () -> LaunchSource, () -> Rect> {
    val view = LocalView.current
    var bounds by remember { mutableStateOf(Rect.Zero) }
    var screen by remember { mutableStateOf(Rect.Zero) }
    return Triple(
        Modifier.onGloballyPositioned { bounds = it.boundsInWindow(); screen = Rect(it.localToScreen(Offset.Zero), it.size.toSize()) },
        { LaunchSource(view, bounds.toAndroidRect()) }, { screen },
    )
}

@Composable
internal fun AppGrid(apps: Apps, list: List<App>, size: Dp, labelColor: Color? = Fg, columns: Int = 4, rowGap: Dp = 22.dp, drag: String? = null) {
    if (list.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(rowGap)) {
        list.chunked(columns).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { AppTile(apps, it, labelColor, size, Modifier.weight(1f), drag) }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
internal fun AppTile(apps: Apps, app: App, labelColor: Color?, size: Dp, modifier: Modifier = Modifier, drag: String? = null) {
    var menu by remember { mutableStateOf(false) }
    var pressed by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current
    val dnd = LocalDragDrop.current
    val lifted = dnd.item?.let { it.key == app.key && it.from == drag } == true
    val scale by animateFloatAsState(if (pressed) 0.88f else if (menu) 1.1f else 1f, spring(dampingRatio = 0.6f, stiffness = 700f), label = "press")
    val (track, source, anchor) = rememberLaunchSource()
    val coords = remember { arrayOfNulls<LayoutCoordinates>(1) }
    val canDrag = drag != null && !prefs.lockLayout
    Box(modifier) {
        Column(
            Modifier.fillMaxWidth().onGloballyPositioned { coords[0] = it }
                .graphicsLayer { alpha = if (lifted) 0.25f else 1f }
                .semantics {
                    onClick { apps.launch(app, source()); true }
                    onLongClick { menu = true; true }
                }
                .pointerInput(app.key, canDrag) {
                    tapHoldDrag(
                        { coords[0] }, onPress = { pressed = it }, onTap = { apps.launch(app, source()) },
                        onLongPress = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); menu = true },
                        onDragStart = if (!canDrag) null else { _, at ->
                            menu = false
                            val px = size.toPx()
                            dnd.start(Dragged(app.key, 1, 1, drag!!, Offset(px / 2, px / 2), Size(px, px)), at)
                        },
                        onDrag = dnd::move, onDragEnd = dnd::end,
                    )
                },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AppIcon(apps, app, size, track.graphicsLayer { scaleX = scale; scaleY = scale }, ring = menu)
            if (labelColor != null) Text(app.label, color = labelColor, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = shadowed(), modifier = Modifier.padding(top = 7.dp, start = 2.dp, end = 2.dp))
        }
        if (menu) AppPopup(apps, app, anchor(), hint = canDrag && drag != "drawer") { menu = false }
    }
}

/** A pinned app shortcut on the home grid: its icon with a small app badge. Tap opens it; hold and drag moves it. */
@Composable
internal fun ShortcutTile(apps: Apps, key: String, labelColor: Color?, size: Dp) {
    val s = remember(key, apps.all.size) { apps.pinnedShortcut(key) }
    val icon = remember(s) { s?.let(apps::shortcutIcon) }
    val haptic = LocalHapticFeedback.current
    val dnd = LocalDragDrop.current
    val (track, source) = rememberLaunchSource()
    val coords = remember { arrayOfNulls<LayoutCoordinates>(1) }
    val lifted = dnd.item?.key == key
    Column(
        Modifier.fillMaxWidth().onGloballyPositioned { coords[0] = it }.graphicsLayer { alpha = if (lifted) 0.25f else 1f }
            .semantics { onClick { s?.let { apps.launch(it, source()) }; true } }
            .pointerInput(key, prefs.lockLayout) {
                tapHoldDrag(
                    { coords[0] }, onTap = { s?.let { apps.launch(it, source()) } },
                    onLongPress = { haptic.performHapticFeedback(HapticFeedbackType.LongPress) },
                    onDragStart = if (prefs.lockLayout) null else { _, at ->
                        val px = size.toPx()
                        dnd.start(Dragged(key, 1, 1, "grid", Offset(px / 2, px / 2), Size(px, px), icon), at)
                    },
                    onDrag = dnd::move, onDragEnd = dnd::end,
                )
            },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(track.size(size)) {
            val shape = iconShape(prefs.iconShape, size)
            if (icon != null) Image(icon, s?.label, Modifier.fillMaxSize().clip(shape).background(Tile))
            else Box(Modifier.fillMaxSize().clip(shape).background(Tile).border(1.dp, TileLine, shape))
            s?.app?.let { AppIcon(apps, it, size * 0.4f, Modifier.align(Alignment.BottomEnd).offset(4.dp, 4.dp)) }
        }
        if (labelColor != null) Text(s?.label ?: "Unavailable", color = labelColor, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            style = shadowed(), modifier = Modifier.padding(top = 7.dp, start = 2.dp, end = 2.dp))
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

private object FullScreen : PopupPositionProvider {
    override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize) = IntOffset.Zero
}

/**
 * Long-press menu, full screen: the screen dims, the pressed icon stays lit, and the card points at it.
 * Shortcuts open on tap, or can be dragged out of the card onto home to pin them. [anchor] is in screen coordinates.
 */
@Composable
internal fun AppPopup(apps: Apps, app: App, anchor: Rect, hint: Boolean, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val dnd = LocalDragDrop.current
    val pickWidgets = LocalPickWidgets.current
    val density = LocalDensity.current
    val shortcuts = remember(app) { apps.shortcuts(app).take(4).map { it to apps.shortcutIcon(it) } }
    val hasWidgets = remember(app) {
        runCatching { AppWidgetManager.getInstance(context).getInstalledProvidersForPackage(app.component.packageName, app.user).isNotEmpty() }.getOrDefault(false)
    }
    val appear = remember { Animatable(0f) }
    LaunchedEffect(Unit) { appear.animateTo(1f, spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessMediumLow)) }
    var dragging by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(22.dp)
    Popup(popupPositionProvider = FullScreen, onDismissRequest = onDismiss, properties = PopupProperties(focusable = true, clippingEnabled = false)) {
        ProvideTextStyle(TextStyle(fontFamily = Inter, color = Fg)) {
            var root by remember { mutableStateOf<LayoutCoordinates?>(null) }
            Box(Modifier.fillMaxSize().onGloballyPositioned { root = it }.graphicsLayer { alpha = if (dragging) 0f else 1f }) {
                val r = root?.takeIf { it.isAttached }
                val a = if (r == null) Rect.Zero else Rect(r.screenToLocal(anchor.topLeft), r.screenToLocal(anchor.bottomRight))
                Box(Modifier.fillMaxSize().graphicsLayer { alpha = appear.value.coerceIn(0f, 1f) }.background(Color(0x99000000))
                    .clickable(interactionSource = null, indication = null, onClick = onDismiss))
                if (r == null) return@Box
                if (renaming) {
                    RenameCard(apps, app, Modifier.align(Alignment.Center)) { renaming = false; onDismiss() }
                    return@Box
                }
                // The pressed icon, lit above the dim.
                Box(Modifier.offset { IntOffset(a.left.roundToInt(), a.top.roundToInt()) }) { AppIcon(apps, app, with(density) { a.width.toDp() }, ring = true) }
                if (hint) Text("drag to move · drag a shortcut out to pin it", color = Muted, fontSize = 11.sp, fontFamily = Mono,
                    modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 96.dp))
                Layout(
                    content = {
                        Box(Modifier.size(16.dp).graphicsLayer { rotationZ = 45f }.background(PopupBg).border(1.dp, PillLine))
                        Column(Modifier.width(288.dp).shadow(24.dp, shape).background(PopupBg, shape).border(1.dp, PillLine, shape).padding(6.dp)) {
                            Text(app.label, color = Muted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 11.dp))
                            shortcuts.forEach { (s, icon) ->
                                ShortcutRow(apps, s, icon, onDragging = { dragging = it }, onDone = onDismiss)
                            }
                            if (shortcuts.isNotEmpty()) Box(Modifier.padding(6.dp).fillMaxWidth().height(1.dp).background(ChipLine))
                            Row(Modifier.fillMaxWidth()) {
                                PopupAction(Ic.info, "Info", Fg) { apps.appInfo(app); onDismiss() }
                                if (hasWidgets) PopupAction(Ic.widgets, "Widgets", Fg) { pickWidgets(app.component.packageName); onDismiss() }
                                PopupAction(Ic.edit, "Edit", Fg) { renaming = true }
                                val isHidden = app.key in apps.hidden
                                PopupAction(Ic.hide, if (isHidden) "Unhide" else "Hide", if (isHidden) Accent else Fg) {
                                    apps.hide(app, !isHidden)
                                    if (!isHidden) Toast.makeText(context, "${app.label} hidden from the drawer and search", Toast.LENGTH_SHORT).show()
                                    onDismiss()
                                }
                                PopupAction(Ic.uninstall, "Uninstall", Danger) { apps.uninstall(app); onDismiss() }
                            }
                        }
                    },
                    modifier = Modifier.fillMaxSize().graphicsLayer {
                        val v = appear.value
                        scaleX = 0.92f + 0.08f * v; scaleY = 0.92f + 0.08f * v; alpha = v.coerceIn(0f, 1f)
                        transformOrigin = TransformOrigin(a.center.x / size.width.coerceAtLeast(1f), a.top / size.height.coerceAtLeast(1f))
                    },
                ) { ms, c ->
                    val tail = ms[0].measure(Constraints())
                    val card = ms[1].measure(Constraints(maxWidth = c.maxWidth))
                    val margin = 12.dp.roundToPx(); val gap = 14.dp.roundToPx()
                    val x = (a.center.x - card.width / 2).roundToInt().coerceIn(margin, max(margin, c.maxWidth - card.width - margin))
                    val aboveY = a.top.roundToInt() - gap - card.height
                    val above = aboveY >= 48.dp.roundToPx()
                    val y = if (above) aboveY else a.bottom.roundToInt() + gap
                    layout(c.maxWidth, c.maxHeight) {
                        val inset = 22.dp.roundToPx()
                        val tx = (a.center.x - tail.width / 2).roundToInt().coerceIn(x + inset, max(x + inset, x + card.width - inset - tail.width))
                        tail.place(tx, if (above) y + card.height - tail.height / 2 else y - tail.height / 2)
                        card.place(x, y)
                    }
                }
            }
        }
    }
}

/** A shortcut in the menu. Tap opens it; dragging it past the touch slop picks it up to pin on home. */
@Composable
private fun ShortcutRow(apps: Apps, s: Shortcut, icon: ImageBitmap?, onDragging: (Boolean) -> Unit, onDone: () -> Unit) {
    val dnd = LocalDragDrop.current
    val (track, source) = rememberLaunchSource()
    val coords = remember { arrayOfNulls<LayoutCoordinates>(1) }
    Row(
        Modifier.fillMaxWidth().height(48.dp).then(track).onGloballyPositioned { coords[0] = it }.clip(RoundedCornerShape(14.dp))
            .semantics { onClick { apps.launch(s, source()); onDone(); true } }
            .pointerInput(s) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    var dragging = false
                    while (true) {
                        val c = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                        val at = coords[0]?.takeIf { it.isAttached }?.localToScreen(c.position) ?: break
                        if (!c.pressed) {
                            if (dragging) dnd.end() else apps.launch(s, source())
                            onDone(); return@awaitEachGesture
                        }
                        if (!dragging && !prefs.lockLayout && (c.position - down.position).getDistance() > viewConfiguration.touchSlop) {
                            dragging = true; onDragging(true)
                            val px = (58.dp * prefs.iconSize / 100).toPx()
                            dnd.start(Dragged(apps.shortcutKey(s), 1, 1, "popup", Offset(px / 2, px / 2), Size(px, px), icon), at)
                        }
                        if (dragging) { c.consume(); dnd.move(at) }
                    }
                    if (dragging) { dnd.item = null; onDone() }
                }
            }
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        val m = Modifier.size(22.dp).clip(RoundedCornerShape(6.dp))
        if (icon != null) Image(icon, null, m) else Box(m.background(Tile))
        Text(s.label, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Icon(Ic.grip, "Drag to pin", tint = Dim, modifier = Modifier.size(16.dp))
    }
}

@Composable
private fun RenameCard(apps: Apps, app: App, modifier: Modifier, onDone: () -> Unit) {
    var name by remember { mutableStateOf(app.label) }
    val shape = RoundedCornerShape(22.dp)
    Column(
        modifier.padding(24.dp).fillMaxWidth().shadow(24.dp, shape).background(PopupBg, shape).border(1.dp, PillLine, shape)
            .clickable(interactionSource = null, indication = null) {}.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Rename", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        Row(Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(14.dp)).background(FieldBg).border(1.5.dp, Accent, RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Field(name, { name = it }, apps.systemLabel(app), Modifier.weight(1f), autoFocus = true) { apps.rename(app, name); onDone() }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (app.label != apps.systemLabel(app)) Text("Reset", color = Accent, fontSize = 14.sp,
                modifier = Modifier.clip(CircleShape).clickable { apps.rename(app, ""); onDone() }.padding(horizontal = 10.dp, vertical = 8.dp))
            Spacer(Modifier.weight(1f))
            Text("Cancel", color = Muted, fontSize = 14.sp, modifier = Modifier.clip(CircleShape).clickable(onClick = onDone).padding(horizontal = 12.dp, vertical = 8.dp))
            Box(Modifier.height(38.dp).clip(CircleShape).background(Accent).clickable { apps.rename(app, name); onDone() }.padding(horizontal = 18.dp),
                contentAlignment = Alignment.Center) { Text("Save", color = OnAccent, fontSize = 14.sp, fontWeight = FontWeight.SemiBold) }
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
internal fun DrawerScreen(apps: Apps, scroll: NestedScrollConnection, onSearch: () -> Unit, onSettings: () -> Unit) {
    val grid = rememberLazyGridState()
    val scope = rememberCoroutineScope()
    var tab by remember { mutableIntStateOf(-1) } // -1 = All, else a group index
    var editGroup by remember { mutableStateOf<Int?>(null) }
    var options by remember { mutableStateOf(false) }
    if (tab >= apps.groups.size) tab = -1
    val cols = prefs.drawerColumns
    val group = apps.groups.getOrNull(tab)
    val frequent = if (prefs.suggested && group == null) apps.frequent(cols) else emptyList()
    val shown = group?.let { g -> apps.visible.filter { it.key in g.apps } } ?: apps.visible
    val scrub = prefs.scrubber && group == null
    val sections = shown.groupBy { it.label.firstOrNull()?.uppercaseChar()?.takeIf(Char::isLetter) ?: '#' }.toSortedMap()
    // Grid index of each section header, for the letter scrubber.
    val headerIndex = remember(sections, frequent) {
        var i = if (frequent.isEmpty()) 0 else 2 + frequent.size
        sections.map { (letter, group) -> (letter to i).also { i += 1 + group.size } }.toMap()
    }
    var scrubLetter by remember { mutableStateOf<Char?>(null) }
    var scrubY by remember { mutableFloatStateOf(0f) }
    var scrubTop by remember { mutableFloatStateOf(0f) }
    Column(Modifier.fillMaxSize().background(Scrim).systemBarsPadding().testTag("drawer")) {
        Handle(Modifier.padding(top = 8.dp, bottom = 12.dp))
        Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 6.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically) {
                GroupTab("All", tab == -1) { tab = -1; scope.launch { grid.scrollToItem(0) } }
                apps.groups.forEachIndexed { i, g -> GroupTab(g.name, tab == i, onLongClick = { editGroup = i }) { tab = i; scope.launch { grid.scrollToItem(0) } } }
                Box(Modifier.size(36.dp).clip(CircleShape).border(1.dp, Color(0xFF3A3D45), CircleShape).clickable { editGroup = -1 }
                    .semantics { contentDescription = "New group" }, contentAlignment = Alignment.Center) {
                    Icon(GIc.plus, null, tint = Muted, modifier = Modifier.size(18.dp))
                }
            }
            Box {
                Box(Modifier.size(40.dp).clip(CircleShape).clickable { options = true }.semantics { contentDescription = "Drawer options" },
                    contentAlignment = Alignment.Center) { Icon(GIc.more, null, tint = Muted, modifier = Modifier.size(20.dp)) }
                DropdownMenu(options, { options = false }, shape = RoundedCornerShape(16.dp), containerColor = PopupBg) {
                    DropdownMenuItem({ Text("New group") }, { options = false; editGroup = -1 })
                    if (group != null) DropdownMenuItem({ Text("Edit “${group.name}”") }, { options = false; editGroup = tab })
                    DropdownMenuItem({ Text(if (prefs.suggested) "Hide suggested apps" else "Show suggested apps") }, { options = false; prefs.suggested = !prefs.suggested })
                    DropdownMenuItem({ Text("Drawer settings") }, { options = false; onSettings() })
                }
            }
        }
        Box(Modifier.weight(1f)) {
            LazyVerticalGrid(GridCells.Fixed(cols), Modifier.fillMaxSize().padding(start = 12.dp, end = if (scrub) 28.dp else 12.dp).nestedScroll(scroll), grid,
                verticalArrangement = Arrangement.spacedBy(10.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (frequent.isNotEmpty()) {
                    item(span = { GridItemSpan(cols) }) { Label("Suggested", Modifier.padding(start = 6.dp, top = 6.dp)) }
                    items(frequent, key = { "f" + it.key }) { AppTile(apps, it, Fg, 54.dp, drag = "drawer") }
                    item(span = { GridItemSpan(cols) }) { Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFF22252A))) }
                }
                sections.forEach { (letter, group) ->
                    item(span = { GridItemSpan(cols) }, key = "h$letter") {
                        Text(letter.toString(), color = Accent, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 8.dp))
                    }
                    items(group, key = { it.key }) { AppTile(apps, it, Fg, 54.dp, drag = "drawer") }
                }
            }
            if (group != null && shown.isEmpty()) Text("No apps in “${group.name}” yet. Long-press the tab to add some.", color = Muted, fontSize = 14.sp,
                modifier = Modifier.align(Alignment.TopCenter).padding(24.dp))
            if (scrub) {
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
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFF22252A)))
        Pill("Search ${apps.visible.size} apps or calculate", Modifier.padding(start = 14.dp, end = 14.dp, top = 10.dp, bottom = 10.dp),
            height = 52.dp, onClick = onSearch)
    }
    editGroup?.let { i -> GroupEditor(apps, i) { editGroup = null; if (i == -1 && apps.groups.isNotEmpty()) tab = apps.groups.lastIndex } }
}

@Composable
private fun GroupTab(name: String, on: Boolean, onLongClick: (() -> Unit)? = null, onClick: () -> Unit) {
    Box(
        Modifier.height(40.dp).clip(CircleShape).background(if (on) Accent else Color.Transparent).border(1.dp, if (on) Accent else ChipLine, CircleShape)
            .combinedClickable(onLongClick = onLongClick, onClick = onClick).padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) { Text(name, color = if (on) OnAccent else Fg, fontSize = 15.sp, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1) }
}

// ---------- Search + calculator ----------

internal data class Calc(val expression: String, val result: String, val micros: Long)

internal val Examples = listOf("20% of 450", "5 km in mi", "time in Tokyo", "\$48 + 15% tip", "3:45pm + 4h 10m", "sqrt(2)^8")

@Composable
internal fun SearchScreen(apps: Apps, query: String, onSettings: (String) -> Unit, onQuery: (String) -> Unit) {
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
    val (matches, matchMicros) = remember(query, apps.all.toList(), apps.hidden.size) {
        val t0 = System.nanoTime()
        // Frequently opened apps get up to +10 on the match score, enough to win a tie, not to beat a better match.
        apps.visible.mapNotNull { a -> match(a.label, query, prefs.fuzzy)?.let { Triple(a, it.first + if (prefs.boostFrequent) minOf(apps.count(a), 10) else 0, it.second) } }
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
    val settingsHits = remember(query, prefs.srcSettings, prefs.fuzzy) { if (prefs.srcSettings) searchSettings(query, prefs.fuzzy) else emptyList() }
    var contactsOk by remember { mutableStateOf(hasContacts(context)) }
    val askContacts = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        contactsOk = ok
        if (!ok) {
            prefs.srcContacts = false
            Toast.makeText(context, "People results are off. Turn them on in Settings › Sources & ranking", Toast.LENGTH_LONG).show()
        }
    }
    var people by remember { mutableStateOf(emptyList<Person>()) }
    LaunchedEffect(query, contactsOk, prefs.srcContacts) {
        people = if (!contactsOk || !prefs.srcContacts || query.trim().length < 2) emptyList()
        else { delay(60); withContext(Dispatchers.IO) { searchContacts(context, query) } }
    }
    val wantsPeople = prefs.srcContacts && !contactsOk && query.trim().length >= 2 && query.any(Char::isLetter)
    val openSetting = { e: SettingsEntry ->
        if (e.page != null) onSettings(e.page)
        else runCatching { context.startActivity(Intent(e.action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            .onFailure { context.startActivity(Intent(android.provider.Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        Unit
    }

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
                if (people.isNotEmpty() || wantsPeople) item(key = "people") {
                    Column(Modifier.animateItem(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Label("People", Modifier.padding(horizontal = 4.dp))
                        ResultCard {
                            if (wantsPeople) ContactsPrompt(onAllow = { askContacts.launch(android.Manifest.permission.READ_CONTACTS) }, onOff = { prefs.srcContacts = false })
                            people.forEachIndexed { i, p -> if (i > 0) RowDivider(); PersonRow(p, query) }
                        }
                    }
                }
                if (settingsHits.isNotEmpty()) item(key = "settings") {
                    Column(Modifier.animateItem(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Label("Settings", Modifier.padding(horizontal = 4.dp))
                        ResultCard {
                            settingsHits.forEachIndexed { i, (e, range) ->
                                if (i > 0) RowDivider()
                                Row(Modifier.fillMaxWidth().height(50.dp).clickable { openSetting(e) }.padding(horizontal = 14.dp),
                                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Icon(if (e.page != null) SIc.sources else Ic.monitor, null, tint = Muted, modifier = Modifier.size(18.dp))
                                    Text(highlight(e.label, range), fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                    Text(e.where, color = Muted, fontSize = 13.sp, maxLines = 1)
                                }
                            }
                        }
                    }
                }
                if (prefs.srcWeb) item(key = "web") {
                    Row(Modifier.animateItem().fillMaxWidth().height(46.dp).clip(RoundedCornerShape(16.dp)).border(1.dp, CardLine, RoundedCornerShape(16.dp))
                        .clickable {
                            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(webSearchUrl(query))).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                        }.padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(Ic.globe, null, tint = Muted, modifier = Modifier.size(18.dp))
                        Text("Search ${prefs.engine} for “${query.trim()}”", color = Muted, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
        val (left, right) = when {
            query.isBlank() -> "↵ open top hit" to "long-press for actions"
            c != null -> "↵ copy answer" to "long-press app for actions"
            top != null -> "↵ open ${top.first.label}" to "${matches.size + people.size + settingsHits.size} results · %.0f ms".format(matchMicros / 1000.0)
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
    val (track, source, anchor) = rememberLaunchSource()
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
                    val last = apps.lastLaunch(app)
                    Text(if (last > 0) "Top hit · opened ${ago(last)}" else "Top hit", color = Muted, fontSize = 12.sp)
                }
                Text("↵", color = Accent, fontSize = 11.sp, fontFamily = Mono, modifier = Modifier
                    .border(1.dp, Color(0xFF2C4E6C), RoundedCornerShape(6.dp)).padding(horizontal = 6.dp, vertical = 3.dp))
            }
            if (shortcuts.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                shortcuts.forEach { s -> Chip(s.label, Color(0xFF16232F), AccentLine, Inter, 13) { apps.launch(s, source()) } }
            }
        }
        if (menu) AppPopup(apps, app, anchor(), hint = false) { menu = false }
    }
}

@Composable
private fun ResultCard(content: @Composable ColumnScope.() -> Unit) =
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Card).border(1.dp, CardLine, RoundedCornerShape(18.dp)), content = content)

@Composable
private fun RowDivider() = Box(Modifier.fillMaxWidth().height(1.dp).background(Divider))

private val Avatars = listOf(Color(0xFF2E3A4A) to Color(0xFFB9D3F2), Color(0xFF3E3424) to Color(0xFFF2D7A8), Color(0xFF2F3E33) to Color(0xFFBFE3C6))

@Composable
private fun PersonRow(p: Person, query: String) {
    val context = LocalContext.current
    fun go(i: Intent) = runCatching { context.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    Row(Modifier.fillMaxWidth().height(58.dp).clickable { openContact(context, p) }.padding(start = 12.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        val (bg, fg) = Avatars[(p.id % Avatars.size).toInt()]
        Box(Modifier.size(36.dp).clip(CircleShape).background(bg), contentAlignment = Alignment.Center) {
            if (p.photo != null) Image(p.photo, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            else Text(p.name.split(' ').filter { it.isNotEmpty() }.take(2).joinToString("") { it.take(1) }.uppercase(), color = fg, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(highlight(p.name, match(p.name, query)?.second), fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(p.label, color = Muted, fontSize = 12.sp, maxLines = 1)
        }
        Box(Modifier.size(40.dp).clip(CircleShape).clickable { go(Intent(Intent.ACTION_DIAL, android.net.Uri.parse("tel:${p.number}"))) }
            .semantics { contentDescription = "Call ${p.name}" }, contentAlignment = Alignment.Center) { Icon(Ic.phone, null, tint = Fg, modifier = Modifier.size(18.dp)) }
        Box(Modifier.size(40.dp).clip(CircleShape).clickable { go(Intent(Intent.ACTION_SENDTO, android.net.Uri.parse("smsto:${p.number}"))) }
            .semantics { contentDescription = "Message ${p.name}" }, contentAlignment = Alignment.Center) { Icon(Ic.message, null, tint = Fg, modifier = Modifier.size(18.dp)) }
    }
}

/** Shown in place of People results until contacts access is granted; ✕ turns the source off instead. */
@Composable
private fun ContactsPrompt(onAllow: () -> Unit, onOff: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 58.dp).padding(start = 14.dp, end = 6.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Find people in your contacts", fontSize = 14.sp, color = Muted, modifier = Modifier.weight(1f))
        Chip("Allow", Color(0xFF16232F), AccentLine, Inter, 13, onAllow)
        Box(Modifier.size(40.dp).clip(CircleShape).clickable(onClick = onOff).semantics { contentDescription = "Turn off people results" },
            contentAlignment = Alignment.Center) { Icon(Ic.close, null, tint = Dim, modifier = Modifier.size(16.dp)) }
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
