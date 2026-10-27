package app.onlysearch

import android.widget.Toast
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toAndroidRect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import android.appwidget.AppWidgetManager
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.unit.roundToIntRect
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.roundToInt

/** Tracks a composable's bounds so a launch can zoom out of it, and a menu can point at it (screen coordinates). */
@Composable
internal fun rememberLaunchSource(): Triple<Modifier, () -> LaunchSource, () -> Rect> {
    val view = LocalView.current
    val coords = remember { arrayOfNulls<LayoutCoordinates>(1) }
    // Read in composition by an open menu, so it stays state; launch bounds are only needed on tap.
    var screen by remember { mutableStateOf(Rect.Zero) }
    return Triple(
        Modifier.onGloballyPositioned { coords[0] = it; screen = Rect(it.localToScreen(Offset.Zero), it.size.toSize()) },
        { LaunchSource(view, (coords[0]?.takeIf { it.isAttached }?.boundsInWindow() ?: Rect.Zero).roundToIntRect().toAndroidRect()) }, { screen },
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
                            dnd.start(Dragged(app.key, AppSpan, AppSpan, drag, Offset(px / 2, px / 2), Size(px, px)), at)
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
        // On home (grid, dock, folder) the menu offers Remove instead of Hide.
        val remove = when {
            drag == "grid" || drag == "dock" -> { { apps.removeItem(app.key); if (apps.dock.remove(app.key)) apps.saveDock() } }
            drag?.startsWith("folder:") == true -> { { apps.takeFromFolder(drag.removePrefix("folder:"), app.key) } }
            else -> null
        }
        if (menu) AppPopup(apps, app, anchor(), hint = canDrag && drag != "drawer", onRemove = remove) { menu = false }
    }
}

/** A pinned app shortcut on the home grid: its icon with a small app badge. Tap opens it; hold and drag moves it. */
@Composable
internal fun ShortcutTile(apps: Apps, key: String, labelColor: Color?, size: Dp) {
    val loaded by produceState<Pair<Shortcut?, ImageBitmap?>?>(null, key, apps.all.size) {
        value = withContext(Dispatchers.IO) { apps.pinnedShortcut(key).let { it to it?.let(apps::shortcutIcon) } }
    }
    val s = loaded?.first
    val icon = loaded?.second
    val haptic = LocalHapticFeedback.current
    val dnd = LocalDragDrop.current
    val (track, source) = rememberLaunchSource()
    val coords = remember { arrayOfNulls<LayoutCoordinates>(1) }
    val lifted = dnd.item?.key == key
    var menu by remember { mutableStateOf(false) }
    Box {
    Column(
        Modifier.fillMaxWidth().onGloballyPositioned { coords[0] = it }.graphicsLayer { alpha = if (lifted) 0.25f else 1f }
            .semantics { onClick { s?.let { apps.launch(it, source()) }; true } }
            .pointerInput(key, prefs.lockLayout, loaded) {
                tapHoldDrag(
                    { coords[0] }, onTap = { s?.let { apps.launch(it, source()) } },
                    onLongPress = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); menu = true },
                    onDragStart = if (prefs.lockLayout) null else { _, at ->
                        menu = false
                        val px = size.toPx()
                        dnd.start(Dragged(key, AppSpan, AppSpan, "grid", Offset(px / 2, px / 2), Size(px, px), icon), at)
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
        if (labelColor != null) Text(s?.label ?: if (loaded == null) "" else "Unavailable", color = labelColor, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            style = shadowed(), modifier = Modifier.padding(top = 7.dp, start = 2.dp, end = 2.dp))
    }
    DropdownMenu(menu, { menu = false }, shape = RoundedCornerShape(16.dp), containerColor = PopupBg) {
        s?.app?.let { DropdownMenuItem({ Text("App info") }, { menu = false; apps.appInfo(it) }) }
        DropdownMenuItem({ Text("Remove") }, { menu = false; apps.removeItem(key); if (apps.dock.remove(key)) apps.saveDock() })
    }
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
    val mono = if (prefs.themedIcons) apps.monoIcons[app.key] else null
    val ringed = Modifier.size(size).then(if (ring) Modifier.border(2.dp, Accent, shape) else Modifier)
    val m = ringed.clip(shape)
    Box(modifier) {
        // Icon-pack icons keep the pack's own shape.
        if (bmp != null && apps.packed[app.key] == true) Image(bmp, app.label, ringed)
        // Themed: the app's monochrome layer in the accent colour on a plain tile (Android 13+ apps that ship one).
        else if (mono != null) Image(mono, app.label, m.background(Tile), colorFilter = ColorFilter.tint(Accent))
        else if (bmp != null) Image(bmp, app.label, m) else Box(m.background(Tile).border(1.dp, TileLine, shape))
        if (app.user != android.os.Process.myUserHandle()) {
            val context = LocalContext.current
            val badge = remember(app.user) {
                // The system draws its badge in the corner of a transparent square; we overlay that square.
                val blank = android.graphics.drawable.BitmapDrawable(context.resources, android.graphics.Bitmap.createBitmap(192, 192, android.graphics.Bitmap.Config.ARGB_8888))
                badges.getOrPut(app.user) { runCatching { context.packageManager.getUserBadgedIcon(blank, app.user).toBitmap(192, 192).asImageBitmap() }.getOrNull() }
            }
            if (badge != null) Image(badge, null, Modifier.offset(size * 0.1f, size * 0.1f).size(size))
        }
    }
}

/** Work-profile badge per user, from the system (the briefcase). */
private val badges = HashMap<android.os.UserHandle, ImageBitmap?>()

internal object FullScreen : PopupPositionProvider {
    override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize) = IntOffset.Zero
}

/**
 * Long-press menu, full screen: the screen dims, the pressed icon stays lit, and the card points at it.
 * Shortcuts open on tap, or can be dragged out of the card onto home to pin them. [anchor] is in screen coordinates.
 */
@Composable
internal fun AppPopup(apps: Apps, app: App, anchor: Rect, hint: Boolean, onRemove: (() -> Unit)? = null, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val dnd = LocalDragDrop.current
    val pickWidgets = LocalPickWidgets.current
    val density = LocalDensity.current
    // The menu fades in once its shortcuts are in, so the card doesn't jump.
    val loaded by produceState<Pair<List<Pair<Shortcut, ImageBitmap?>>, Boolean>?>(null, app) {
        value = withContext(Dispatchers.IO) {
            apps.shortcuts(app).take(4).map { it to apps.shortcutIcon(it) } to
                runCatching { AppWidgetManager.getInstance(context).getInstalledProvidersForPackage(app.component.packageName, app.user).isNotEmpty() }.getOrDefault(false)
        }
    }
    val shortcuts = loaded?.first.orEmpty()
    val hasWidgets = loaded?.second == true
    val appear = remember { Animatable(0f) }
    LaunchedEffect(loaded != null) { if (loaded != null) appear.animateTo(1f, spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessMediumLow)) }
    var dragging by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(22.dp)
    Popup(popupPositionProvider = FullScreen, onDismissRequest = onDismiss, properties = PopupProperties(focusable = true, clippingEnabled = false)) {
        ProvideTextStyle(TextStyle(fontFamily = Sans, color = Fg)) {
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
                    modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 190.dp))
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
                                if (onRemove != null) PopupAction(Ic.close, "Remove", Fg) { onRemove(); onDismiss() }
                                else PopupAction(Ic.hide, if (isHidden) "Unhide" else "Hide", if (isHidden) Accent else Fg) {
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
                        val at = coords[0]?.takeIf { it.isAttached }?.let { it.localToScreen(c.position) - homeOnScreen } ?: break
                        if (!c.pressed) {
                            if (dragging) dnd.end() else apps.launch(s, source())
                            onDone(); return@awaitEachGesture
                        }
                        if (!dragging && !prefs.lockLayout && (c.position - down.position).getDistance() > viewConfiguration.touchSlop) {
                            dragging = true; onDragging(true)
                            val px = (58.dp * prefs.iconSize / 100).toPx()
                            dnd.start(Dragged(apps.shortcutKey(s), AppSpan, AppSpan, "popup", Offset(px / 2, px / 2), Size(px, px), icon), at)
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
        modifier.imePadding().padding(24.dp).fillMaxWidth().shadow(24.dp, shape).background(PopupBg, shape).border(1.dp, PillLine, shape)
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
