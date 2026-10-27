package app.onlysearch.home

import app.onlysearch.AppSpan
import app.onlysearch.Apps
import app.onlysearch.Fg
import app.onlysearch.Field
import app.onlysearch.PillLine
import app.onlysearch.PopupBg
import app.onlysearch.Sans
import app.onlysearch.Tile
import app.onlysearch.TileLine
import app.onlysearch.WidgetCorner
import app.onlysearch.prefs
import app.onlysearch.shadowed
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties

/** A home folder: up to four of its apps in a tile. Tap opens it; hold and drag moves it. */
@Composable
internal fun FolderTile(apps: Apps, key: String, labelColor: Color?, size: Dp) {
    val folder = apps.folders[key] ?: return
    var open by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current
    val dnd = LocalDragDrop.current
    val coords = remember { arrayOfNulls<LayoutCoordinates>(1) }
    val lifted = dnd.item?.key == key
    Column(
        Modifier.fillMaxWidth().onGloballyPositioned { coords[0] = it }.graphicsLayer { alpha = if (lifted) 0.25f else 1f }
            .semantics { onClick { open = true; true } }
            .pointerInput(key, prefs.lockLayout) {
                tapHoldDrag(
                    { coords[0] }, onTap = { open = true }, onLongPress = { haptic.performHapticFeedback(HapticFeedbackType.LongPress) },
                    onDragStart = if (prefs.lockLayout) null else { _, at ->
                        val px = size.toPx()
                        dnd.start(Dragged(key, AppSpan, AppSpan, "grid", Offset(px / 2, px / 2), Size(px, px)), at)
                    },
                    onDrag = dnd::move, onDragEnd = dnd::end,
                )
            },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val shape = iconShape(prefs.iconShape, size)
        Box(Modifier.size(size).clip(shape).background(Tile.copy(alpha = 0.85f)).border(1.dp, TileLine, shape).padding(size * 0.14f)) {
            val mini = ((size * 0.72f - 4.dp) / 2).coerceAtLeast(0.dp)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                folder.apps.mapNotNull(apps::byKey).take(4).chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) { row.forEach { AppIcon(apps, it, mini) } }
                }
            }
        }
        if (labelColor != null) Text(folder.name, color = labelColor, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            style = shadowed(), modifier = Modifier.padding(top = 7.dp, start = 2.dp, end = 2.dp))
        // Inside the tile's column so the sheet is placed over the tile.
        if (open) FolderSheet(apps, key, size, with(LocalDensity.current) { (coords[0]?.size?.width ?: 0).toDp() }) { open = false }
    }
}

/** Centred on the folder's tile, like Nova, but kept on screen. */
private object OverAnchor : PopupPositionProvider {
    override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset {
        val margin = (windowSize.width * 0.02f).toInt()
        fun place(center: Int, size: Int, window: Int) = (center - size / 2).coerceAtMost(window - margin - size).coerceAtLeast(margin)
        return IntOffset(place(anchorBounds.center.x, popupContentSize.width, windowSize.width), place(anchorBounds.center.y, popupContentSize.height, windowSize.height))
    }
}

/** An open folder over its tile: its apps, then its name (editable). Dragging an app out takes it out of the folder. */
@Composable
private fun FolderSheet(apps: Apps, key: String, icon: Dp, cell: Dp, onClose: () -> Unit) {
    val folder = apps.folders[key] ?: return onClose()
    val dnd = LocalDragDrop.current
    val from = "folder:$key"
    var name by remember { mutableStateOf(folder.name) }
    // The sheet stays composed while one of its apps is dragged (it owns the gesture), but invisible; it closes on the drop.
    val dragging = dnd.item?.from == from
    var dragged by remember { mutableStateOf(false) }
    LaunchedEffect(dragging) { if (dragging) dragged = true else if (dragged) onClose() }
    val save = { if (name.isNotBlank() && name != folder.name) apps.renameFolder(key, name.trim()) }
    // Opening an app from the folder leaves home; coming back shows home, not the folder.
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, e -> if (e == androidx.lifecycle.Lifecycle.Event.ON_STOP) { save(); onClose() } }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    val maxHeight = LocalWindowInfo.current.containerDpSize.height * 0.7f
    BackHandler { save(); onClose() }
    Popup(popupPositionProvider = OverAnchor, onDismissRequest = { save(); onClose() }, properties = PopupProperties(focusable = true)) {
        ProvideTextStyle(TextStyle(fontFamily = Sans, color = Fg)) {
            val list = folder.apps.mapNotNull(apps::byKey)
            val columns = when { list.size <= 3 -> list.size.coerceAtLeast(1); list.size <= 9 -> 3; else -> 4 }
            Column(
                Modifier.graphicsLayer { alpha = if (dragging) 0f else 1f }.width(cell.coerceAtLeast(icon + 28.dp) * columns + 24.dp)
                    .clip(RoundedCornerShape(WidgetCorner * 3)).background(PopupBg).border(1.dp, PillLine, RoundedCornerShape(WidgetCorner * 3))
                    .padding(start = 12.dp, end = 12.dp, top = 16.dp, bottom = 6.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Long-press drags out; an immediate vertical swipe scrolls. The name field stays below, outside the scroll.
                Box(Modifier.heightIn(max = maxHeight).verticalScroll(rememberScrollState())) {
                    AppGrid(apps, list, icon, Fg, columns = columns, rowGap = 16.dp, drag = from)
                }
                Field(name, { name = it }, "Folder name", Modifier.fillMaxWidth(), autoFocus = false, fontSize = 17, align = TextAlign.Center) { save() }
            }
        }
    }
}
