package app.onlysearch

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
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
                        dnd.start(Dragged(key, prefs.appSpan, prefs.appSpan, "grid", Offset(px / 2, px / 2), Size(px, px)), at)
                    },
                    onDrag = dnd::move, onDragEnd = dnd::end,
                )
            },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val shape = iconShape(prefs.iconShape, size)
        Box(Modifier.size(size).clip(shape).background(Tile.copy(alpha = 0.85f)).border(1.dp, TileLine, shape).padding(size * 0.14f)) {
            val mini = (size * 0.72f - 4.dp) / 2
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                folder.apps.mapNotNull(apps::byKey).take(4).chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) { row.forEach { AppIcon(apps, it, mini) } }
                }
            }
        }
        if (labelColor != null) Text(folder.name, color = labelColor, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            style = shadowed(), modifier = Modifier.padding(top = 7.dp, start = 2.dp, end = 2.dp))
    }
    if (open) FolderSheet(apps, key) { open = false }
}

/** An open folder over dimmed home: its name (editable) and apps. Dragging an app out takes it out of the folder. */
@Composable
private fun FolderSheet(apps: Apps, key: String, onClose: () -> Unit) {
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
    BackHandler { save(); onClose() }
    Popup(popupPositionProvider = FullScreen, onDismissRequest = { save(); onClose() }, properties = PopupProperties(focusable = true, clippingEnabled = false)) {
        ProvideTextStyle(TextStyle(fontFamily = Inter, color = Fg)) {
            Box(Modifier.fillMaxSize().graphicsLayer { alpha = if (dragging) 0f else 1f }.background(Color(0x99000000))
                .clickable(interactionSource = null, indication = null) { save(); onClose() }) {
                Column(
                    Modifier.align(Alignment.Center).padding(horizontal = 24.dp).fillMaxWidth().clip(RoundedCornerShape(WidgetCorner * 3)).background(PopupBg)
                        .border(1.dp, PillLine, RoundedCornerShape(WidgetCorner * 3)).clickable(interactionSource = null, indication = null) {}.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Field(name, { name = it }, "Folder name", Modifier.fillMaxWidth(), autoFocus = false, fontSize = 18) { save() }
                    AppGrid(apps, folder.apps.mapNotNull(apps::byKey), 52.dp, Fg, columns = 4, rowGap = 16.dp, drag = from)
                    Text("drag an app out to take it out of the folder", color = Muted, fontSize = 11.sp, fontFamily = Mono)
                }
            }
        }
    }
}
