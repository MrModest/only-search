package app.onlysearch

import app.onlysearch.drawer.DrawerScreen
import app.onlysearch.home.AppIcon
import app.onlysearch.home.DragDrop
import app.onlysearch.home.EditHome
import app.onlysearch.home.HomeActions
import app.onlysearch.home.HomeScreen
import app.onlysearch.home.LocalDragDrop
import app.onlysearch.home.LocalPickWidgets
import app.onlysearch.home.homeOnScreen
import app.onlysearch.home.iconShape
import app.onlysearch.search.SearchScreen
import app.onlysearch.settings.SettingsScreen
import app.onlysearch.widgets.WidgetPicker
import app.onlysearch.widgets.Widgets
import app.onlysearch.widgets.pickerKey
import android.app.Activity
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.roundToInt

@Composable
fun Launcher(apps: Apps, widgets: Widgets, home: Pair<Int, Boolean>, stopTick: Int, resumeTick: Int, initialQuery: String?, typed: Pair<Int, String>) {
    BackHandler(true) {} // lowest priority: Back on home must not finish the launcher
    val scope = rememberCoroutineScope()
    val activity = LocalContext.current as Activity
    val screenPx = LocalWindowInfo.current.containerSize.height.toFloat()

    val drawer = remember { Animatable(0f) } // 0 = closed, 1 = open; follows the finger while dragging
    val enter = remember { Animatable(1f) } // home "icons zoom back in" after returning from an app
    var searching by rememberSaveable { mutableStateOf(initialQuery != null) }
    var query by rememberSaveable { mutableStateOf(initialQuery.orEmpty()) }
    var picking by remember { mutableStateOf(false) }
    var pickFor by remember { mutableStateOf<String?>(null) }
    var settings by remember { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf(false) }
    val pager = rememberPagerState(apps.defaultPage) { apps.pages.size }
    LaunchedEffect(pager) { snapshotFlow { pager.currentPage }.collect { apps.currentPage = it } }
    val dnd = remember { DragDrop() }
    // An app dragged out of search results: search stays composed (it owns the gesture) but home shows through.
    // Search stays composed (just faded) while something dragged out of it is in flight; closing it would cancel the drag.
    val searchDrag = dnd.item?.from == "search" || (searching && dnd.item?.from == "popup")
    val searchProgress by animateFloatAsState(if (searching && !searchDrag) 1f else 0f, tween(180), label = "search")
    val keyboard = LocalSoftwareKeyboardController.current

    var drawerBlur by remember { mutableStateOf(false) }
    fun settleDrawer(open: Boolean) = scope.launch {
        if (!open) drawerBlur = false
        drawer.animateTo(if (open) 1f else 0f, Settle)
        if (open) drawerBlur = true
    }
    fun dragDrawer(deltaPx: Float) = scope.launch { drawer.snapTo((drawer.value - deltaPx / screenPx).coerceIn(0f, 1f)) }
    val drawerOpening by remember { derivedStateOf { drawer.targetValue > 0f } }
    fun closeSearch() { searching = false; if (prefs.resetQuery) query = "" }
    // A kept query is forgotten once search has stayed closed for the chosen time.
    LaunchedEffect(searching) { if (!searching && query.isNotEmpty()) { delay(prefs.keepQueryMinutes * 60_000L); query = "" } }
    var lockPrompt by remember { mutableStateOf(false) }
    fun run(action: String) {
        when {
            action == "search" -> searching = true
            action == "drawer" -> settleDrawer(true)
            action == "notifications" -> expandNotifications(activity)
            action == "lock" -> if (!lockScreen()) lockPrompt = true
            action == "edit" -> editing = true
            action == "history" -> { query = ""; searching = true }
            action == "settings" -> settings = "hub"
            action.startsWith("app:") -> apps.byKey(action.removePrefix("app:"))?.let { apps.launch(it) }
            action.startsWith("shortcut:") -> apps.pinnedShortcut(action.removePrefix("shortcut:"))?.let { apps.launch(it) }
        }
    }
    // A hardware key typed on home opens search with that character.
    LaunchedEffect(typed.first) { if (typed.first > 0 && !searching) { query = typed.second; searching = true } }

    // Home button: animate back. Leaving to an app: reset silently so the launch animation isn't disturbed.
    LaunchedEffect(home.first) {
        if (home.first == 0) return@LaunchedEffect
        val onHome = home.second && !searching && !picking && settings == null && !editing && drawer.value == 0f
        closeSearch(); picking = false; settings = null; editing = false; settleDrawer(false)
        // Home pressed on home: back to the default page first, then the "Home button on home" gesture.
        if (onHome) { if (pager.currentPage != apps.defaultPage) pager.animateScrollToPage(apps.defaultPage) else run(gesture("gHome")) }
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

    // Light theme sheets get dark status and nav icons; over the wallpaper (and the picker's dark scrim) they stay light.
    val view = LocalView.current
    val darkStatus by remember { derivedStateOf { isLight && (searching || settings != null || editing || drawer.value > 0.5f) } }
    val darkNav by remember { derivedStateOf { darkStatus || isLight && picking } }
    LaunchedEffect(darkStatus, darkNav) {
        WindowCompat.getInsetsController(activity.window, view).run { isAppearanceLightStatusBars = darkStatus; isAppearanceLightNavigationBars = darkNav }
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
            onGesture = { run(gesture(it)) },
        )
    }

    dnd.onStart = {
        if (it.from == "drawer" || it.from == "popup") settleDrawer(false)
        if (it.from == "search" || it.from == "popup") keyboard?.hide()
        if (it.from == "picker" && editing) { editing = false; scope.launch { pager.scrollToPage(apps.currentPage) } }
    }
    dnd.onDrop = { d, at ->
        if (d.from == "search" || d.from == "popup") closeSearch()
        // The close started on pickup can be cut short (e.g. drawer opened over search); finish it so no half-open drawer covers home.
        if (d.from == "drawer" || d.from == "popup") settleDrawer(false)
        val page = pager.currentPage
        val grid = apps.pages.getOrNull(page)?.let { dnd.grids[it.id] }
        val target = grid?.let { dnd.target(d, at, it) }
        // An app dragged out of a folder leaves the folder once it lands somewhere.
        val folder = d.from.removePrefix("folder:").takeIf { d.from.startsWith("folder:") }
        val leaveFolder = { folder?.let { apps.takeFromFolder(it, d.key) } }
        when {
            folder != null && dnd.remove.contains(at) -> leaveFolder()
            (d.from == "grid" || d.from == "dock") && dnd.remove.contains(at) -> {
                d.key.removePrefix("w:").toIntOrNull()?.takeIf { d.key.startsWith("w:") }?.let(widgets::remove)
                if (d.key.startsWith("f:")) apps.removeFolder(d.key) else apps.removeItem(d.key)
                if (apps.dock.remove(d.key)) apps.saveDock()
            }
            d.from == "picker" -> if (target != null && apps.fits(page, target.x, target.y, target.w, target.h)) {
                if (d.key.startsWith("pb:")) apps.addBuiltin(d.key.removePrefix("pb:"), page to target)
                else widgets.manager.installedProviders.firstOrNull { pickerKey(it) == d.key }?.let { apps.widgetSpot = page to target; apps.currentPage = page; widgets.add(it) }
            }
            apps.byKey(d.key) != null && dnd.dock.contains(at) -> {
                if (apps.dropOnDock(d.key, ((at.x - dnd.dock.left) / (dnd.dock.width / prefs.dockSize)).toInt())) leaveFolder()
            }
            target != null -> {
                // An app dropped on an app makes a folder; on a folder, it joins it.
                val over = apps.pages[page].items.firstOrNull { it.x == target.x && it.y == target.y && it.w == target.w && it.h == target.h && it.key != d.key && !it.isWidget }
                if (apps.byKey(d.key) != null && over != null && over.key != folder && (over.key.startsWith("f:") || apps.byKey(over.key) != null)) {
                    if (apps.dropOnto(page, over, d.key)) leaveFolder()
                } else if (apps.moveTo(page, target)) {
                    if (d.from == "dock") apps.dock.remove(d.key).also { apps.saveDock() }
                    leaveFolder()
                }
            }
        }
    }
    // Dragging to a screen edge flips to the neighbouring page.
    val edgePx = with(LocalDensity.current) { 28.dp.toPx() }
    val widthPx = LocalWindowInfo.current.containerSize.width.toFloat()
    LaunchedEffect(dnd.item != null) {
        while (dnd.item != null) {
            delay(500)
            val p = pager.currentPage
            if (dnd.pos.x < edgePx && p > 0) pager.animateScrollToPage(p - 1)
            else if (dnd.pos.x > widthPx - edgePx && p < apps.pages.lastIndex) pager.animateScrollToPage(p + 1)
        }
    }

    CompositionLocalProvider(LocalDragDrop provides dnd, LocalPickWidgets provides { pkg: String? -> pickFor = pkg; picking = true }) {
    ProvideTextStyle(TextStyle(fontFamily = Sans, color = Fg)) {
        // Follows a drag from the root: the tile that started it may be disposed mid-drag (its page scrolled away).
        Box(Modifier.fillMaxSize().onGloballyPositioned { homeOnScreen = it.localToScreen(Offset.Zero) }.pointerInput(Unit) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                while (true) {
                    val e = awaitPointerEvent(PointerEventPass.Initial)
                    val c = e.changes.firstOrNull() ?: break
                    if (dnd.item != null && dnd.item?.from != "popup" && dnd.item?.from != "picker") {
                        if (c.pressed) dnd.move(c.position) else dnd.end()
                    }
                    if (e.changes.none { it.pressed }) break
                }
            }
        }) {
            Box(Modifier.fillMaxSize().graphicsLayer {
                val p = max(drawer.value, searchProgress)
                val s = (1f - 0.06f * p) * (0.9f + 0.1f * enter.value)
                scaleX = s; scaleY = s
                alpha = (1f - p) * enter.value
            }) {
                if (prefs.dim > 0) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = prefs.dim / 100f)))
                HomeScreen(apps, widgets, pager, homeActions)
            }
            // Status icons are light over the wallpaper; this shade keeps them readable on a light wallpaper.
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
                Box(Modifier.graphicsLayer { alpha = if (searchDrag) 0f else 1f }) {
                    SearchScreen(apps, query, onSettings = { settings = it }) { query = it }
                }
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
            if (dnd.item?.from.let { it == "grid" || it == "dock" || it?.startsWith("folder:") == true }) RemoveZone(dnd)
            DragGhost(apps, dnd)
            if (lockPrompt) LockPrompt { lockPrompt = false }
        }
    }
    }
}

/** Drop here to take an app, shortcut or widget off home. */
@Composable
private fun BoxScope.RemoveZone(dnd: DragDrop) {
    val over by remember { derivedStateOf { dnd.remove.contains(dnd.pos) } }
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
        else Box(Modifier.fillMaxSize().clip(RoundedCornerShape(WidgetCorner)).background(Accent.copy(alpha = 0.18f)).border(2.dp, Accent, RoundedCornerShape(WidgetCorner)))
    }
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
internal fun Field(value: String, onValue: (String) -> Unit, hint: String, modifier: Modifier, autoFocus: Boolean, fontSize: Int = 16,
    align: TextAlign = TextAlign.Start, onGo: () -> Unit) {
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    if (autoFocus) LaunchedEffect(Unit) { focus.requestFocus(); keyboard?.show() }
    // Text set from outside (history tap, chip, voice) puts the cursor at the end.
    var field by remember { mutableStateOf(TextFieldValue(value, TextRange(value.length))) }
    if (field.text != value) field = TextFieldValue(value, TextRange(value.length))
    BasicTextField(
        field, { field = it; if (it.text != value) onValue(it.text) }, modifier.focusRequester(focus),
        textStyle = TextStyle(color = Fg, fontSize = fontSize.sp, fontFamily = Sans, textAlign = align),
        singleLine = true,
        cursorBrush = SolidColor(Accent),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go, autoCorrectEnabled = false),
        keyboardActions = KeyboardActions(onGo = { onGo() }),
        decorationBox = { inner -> Box(contentAlignment = if (align == TextAlign.Center) Alignment.Center else Alignment.CenterStart) { if (value.isEmpty()) Text(hint, color = Muted, fontSize = fontSize.sp, maxLines = 1); inner() } },
    )
}
