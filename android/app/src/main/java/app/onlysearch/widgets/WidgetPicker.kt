package app.onlysearch.widgets

import app.onlysearch.AppSpan
import app.onlysearch.Apps
import app.onlysearch.Card
import app.onlysearch.Cell
import app.onlysearch.ChipLine
import app.onlysearch.Field
import app.onlysearch.FieldBg
import app.onlysearch.FieldLine
import app.onlysearch.Ic
import app.onlysearch.Mono
import app.onlysearch.Muted
import app.onlysearch.Sheet
import app.onlysearch.Tile
import app.onlysearch.drawer.Handle
import app.onlysearch.home.Dragged
import app.onlysearch.home.LocalDragDrop
import app.onlysearch.home.tapHoldDrag
import app.onlysearch.isLight
import app.onlysearch.prefs
import android.appwidget.AppWidgetProviderInfo
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun WidgetPicker(apps: Apps, widgets: Widgets, only: String?, onClose: () -> Unit) {
    val context = LocalContext.current
    val pm = context.packageManager
    val density = context.resources.displayMetrics.density
    var groups by remember { mutableStateOf(emptyList<Triple<String, String, List<AppWidgetProviderInfo>>>()) }
    var filter by remember { mutableStateOf("") }
    var open by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        groups = withContext(Dispatchers.IO) {
            widgets.providers().filter { only == null || it.provider.packageName == only }.groupBy { it.provider.packageName }.map { (pkg, list) ->
                Triple(pkg, runCatching { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() }.getOrDefault(pkg), list)
            }.sortedBy { it.second.lowercase() }
        }
        open = groups.firstOrNull()?.first
    }
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
                .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)).background(Card)
                .border(1.dp, FieldLine, RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
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
                    item(key = "builtin", contentType = "group") {
                        Text("Only Search", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.heightIn(min = 40.dp).wrapContentHeight())
                    }
                    items(ours, key = { "b:" + it.kind }, contentType = { "builtin" }) { b ->
                        val (w, h) = b.size()
                        WidgetPreview(apps, "pb:${b.kind}", b.label, w, h, null, Modifier, onDragging = { dragging = true }, onDropped = onClose,
                            onClick = {
                                if (!apps.addBuiltin(b.kind)) Toast.makeText(context, "No room for this widget on the page", Toast.LENGTH_SHORT).show()
                                onClose()
                            }) {
                            // The clock, next event and month draw light text for the wallpaper; a grey stand-in keeps them readable on a light sheet.
                            val wall = isLight && b.kind in listOf("clock", "event", "month")
                            Box(if (wall) Modifier.fillMaxSize().clip(RoundedCornerShape(10.dp)).background(Color(0xFF77736C)) else Modifier) {
                                BuiltinWidget(Cell("b:${b.kind}:preview", 0, 0, w, h), Modifier.fillMaxSize(), preview = true)
                            }
                        }
                    }
                }
                shown.forEach { (pkg, label, list) ->
                    val expanded = open == pkg || filter.isNotBlank()
                    item(key = pkg, contentType = "group") {
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
                    if (expanded) items(rows, key = { it[0].provider.flattenToString() }, contentType = { "widgets" }) { row ->
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
    val preview by produceState<ImageBitmap?>(null, p) {
        value = withContext(Dispatchers.IO) {
            runCatching { (p.loadPreviewImage(context, density.densityDpi) ?: p.loadIcon(context, density.densityDpi))?.toBitmap()?.asImageBitmap() }.getOrNull()
        }
    }
    val (w, h) = cells(p, density.density)
    WidgetPreview(apps, pickerKey(p), p.loadLabel(context.packageManager), w * AppSpan, h * AppSpan, preview, modifier, onDragging, onDropped, onClick) {
        preview?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit) }
    }
}

/** A picker card: tap adds the widget, hold and drag places it. [key] is what the drop handler gets; [w] × [h] are half cells. */
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
            .pointerInput(key, preview) {
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
                .border(1.dp, FieldLine, RoundedCornerShape(16.dp)).padding(8.dp),
            contentAlignment = Alignment.Center,
        ) { sample() }
        Row(Modifier.fillMaxWidth()) {
            Text(label, color = Muted, fontSize = 12.sp, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${w / AppSpan} × ${h / AppSpan}", color = Muted, fontSize = 12.sp, fontFamily = Mono)
        }
    }
}
