package app.onlysearch

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.layout.LayoutCoordinates
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.roundToInt

/** An item picked up for dragging. [from] is "grid", "dock", "drawer", "popup" or "picker"; [grab] is the finger's offset inside it. */
data class Dragged(val key: String, val w: Int, val h: Int, val from: String, val grab: Offset, val size: Size, val icon: ImageBitmap? = null)

/** Drag-and-drop state shared by every screen. Positions are window coordinates. */
class DragDrop {
    var item by mutableStateOf<Dragged?>(null)
    var pos by mutableStateOf(Offset.Zero)
    /** Grid bounds per home page id, and the dock row. */
    val grids = mutableStateMapOf<Long, Rect>()
    var dock by mutableStateOf(Rect.Zero)
    /** The "Remove" drop zone shown while dragging something off home. */
    var remove by mutableStateOf(Rect.Zero)
    var onStart: (Dragged) -> Unit = {}
    var onDrop: (Dragged, Offset) -> Unit = { _, _ -> }

    fun start(d: Dragged, at: Offset) { item = d; pos = at; onStart(d) }
    fun move(at: Offset) { pos = at }
    fun end() { item?.let { onDrop(it, pos) }; item = null }

    /** The cell block the item would land on in [grid], or null when the finger is outside it. */
    fun target(d: Dragged, at: Offset, grid: Rect): Cell? {
        if (!grid.contains(at)) return null
        val cw = grid.width / prefs.columns
        val ch = grid.height / prefs.rows
        // Apps land in the cell under the finger; a widget keeps its top-left corner where it was grabbed.
        val (x, y) = if (d.w == 1 && d.h == 1) ((at.x - grid.left) / cw).toInt() to ((at.y - grid.top) / ch).toInt()
        else ((at.x - d.grab.x - grid.left) / cw).roundToInt() to ((at.y - d.grab.y - grid.top) / ch).roundToInt()
        return Cell(d.key, x.coerceIn(0, prefs.columns - d.w), y.coerceIn(0, prefs.rows - d.h), d.w, d.h)
    }
}

val LocalDragDrop = staticCompositionLocalOf { DragDrop() }

/** Opens the widget picker, optionally for one app's package. */
val LocalPickWidgets = staticCompositionLocalOf<(String?) -> Unit> { {} }

/**
 * Tap, long-press, and long-press-then-drag in one detector. After the long press every move is consumed,
 * so pagers, scrolling lists and widget views underneath let go. [pass] Initial lets a parent (a widget frame)
 * see touches before the widget's own view does.
 */
internal suspend fun PointerInputScope.tapHoldDrag(
    coords: () -> LayoutCoordinates?,
    pass: PointerEventPass = PointerEventPass.Main,
    onPress: (Boolean) -> Unit = {},
    onTap: (() -> Unit)?,
    onLongPress: () -> Unit,
    onDragStart: ((grab: Offset, at: Offset) -> Unit)?,
    onDrag: (Offset) -> Unit = {},
    onDragEnd: () -> Unit = {},
    enabled: () -> Boolean = { true },
) = awaitEachGesture {
    val down = awaitFirstDown(requireUnconsumed = false, pass = pass)
    if (!enabled()) return@awaitEachGesture
    DragDropPressing.value = true
    try { tapHoldDragBody(down, coords, pass, onPress, onTap, onLongPress, onDragStart, onDrag, onDragEnd) } finally { DragDropPressing.value = false }
}

/** Shared flag for [DragDrop.pressingTile]; a plain holder because the detector has no DragDrop at hand. */
internal object DragDropPressing { var value = false }

private suspend fun androidx.compose.ui.input.pointer.AwaitPointerEventScope.tapHoldDragBody(
    down: androidx.compose.ui.input.pointer.PointerInputChange,
    coords: () -> LayoutCoordinates?,
    pass: PointerEventPass,
    onPress: (Boolean) -> Unit,
    onTap: (() -> Unit)?,
    onLongPress: () -> Unit,
    onDragStart: ((grab: Offset, at: Offset) -> Unit)?,
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit,
) {
    onPress(true)
    var outcome = 0 // 0 = held long enough, 1 = released, 2 = moved or cancelled
    withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
        outcome = if (pass == PointerEventPass.Main) { if (waitForUpOrCancellation() != null) 1 else 2 } else {
            var r = 0
            while (r == 0) {
                val c = awaitPointerEvent(pass).changes.firstOrNull { it.id == down.id } ?: break
                r = when { !c.pressed -> 1; (c.position - down.position).getDistance() > viewConfiguration.touchSlop -> 2; else -> 0 }
            }
            if (r == 0) 2 else r
        }
    }
    onPress(false)
    if (outcome == 1) { onTap?.invoke(); return }
    if (outcome == 2) return
    onLongPress()
    var dragging = false
    while (true) {
        val c = awaitPointerEvent(pass).changes.firstOrNull { it.id == down.id } ?: break
        c.consume()
        if (!c.pressed) break
        if (onDragStart == null) continue
        val window = coords()?.takeIf { it.isAttached }?.localToWindow(c.position) ?: continue
        if (!dragging && (c.position - down.position).getDistance() > viewConfiguration.touchSlop) {
            dragging = true
            onDragStart(down.position, window)
        }
        if (dragging) onDrag(window)
    }
    if (dragging) onDragEnd()
}
