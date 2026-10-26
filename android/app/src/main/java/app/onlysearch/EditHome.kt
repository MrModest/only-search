package app.onlysearch

import android.text.format.DateFormat
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Date
import kotlinx.coroutines.launch

internal object EIc {
    val wallpaper = line("M4 4h16v16H4z M4 16l5-5 5 5 2-2 4 4 M15 8.5h.01")
    val widgets = line("M4 4h7v7H4z M13 4h7v7h-7z M4 13h7v7H4z M13 13h7v7h-7z")
    val layout = line("M4 4h16v16H4z M4 9.3h16 M4 14.6h16 M9.3 4v16 M14.6 4v16")
    val settings = line("M12 15a3 3 0 1 0 0-6 3 3 0 0 0 0 6z M12 2.5v3 M12 18.5v3 M4.6 4.6l2.1 2.1 M17.3 17.3l2.1 2.1 M2.5 12h3 M18.5 12h3 M4.6 19.4l2.1-2.1 M17.3 6.7l2.1-2.1")
    val plus = line("M12 5v14 M5 12h14", 1.8f)
}

/** Edit home: a carousel of page previews (drag to reorder, + to add), the default page, and shortcuts to related settings. */
@Composable
fun EditHome(apps: Apps, widgets: Widgets, onDone: (Int) -> Unit, onPickWidget: () -> Unit, onSettings: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val state = rememberPagerState(apps.currentPage) { apps.pages.size + 1 }
    val screen = LocalWindowInfo.current.containerDpSize
    val previewW = screen.width * 0.6f
    val previewH = screen.height * 0.6f
    val onPage = state.currentPage < apps.pages.size
    LaunchedEffect(state.currentPage) { if (onPage) apps.currentPage = state.currentPage }
    fun locked() = prefs.lockLayout.also { if (it) Toast.makeText(context, "Layout is locked (Settings › Home screen)", Toast.LENGTH_SHORT).show() }

    Column(Modifier.fillMaxSize().background(SettingsBg).systemBarsPadding().padding(bottom = 12.dp)) {
        Row(Modifier.fillMaxWidth().padding(start = 18.dp, end = 14.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("Edit home", fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                Text("drag pages · tap + to add", color = Muted, fontSize = 11.sp, fontFamily = Mono)
            }
            Box(Modifier.height(40.dp).clip(CircleShape).background(Accent).clickable { onDone(apps.currentPage) }.padding(horizontal = 20.dp),
                contentAlignment = Alignment.Center) { Text("Done", color = OnAccent, fontSize = 15.sp, fontWeight = FontWeight.SemiBold) }
        }
        Spacer(Modifier.weight(1f))
        HorizontalPager(
            state, Modifier.fillMaxWidth().height(previewH),
            contentPadding = PaddingValues(horizontal = (screen.width - previewW) / 2), pageSpacing = 12.dp,
            key = { apps.pages.getOrNull(it)?.id ?: -1L },
        ) { i ->
            val page = apps.pages.getOrNull(i)
            if (page == null) AddPageCard {
                if (!locked()) {
                    apps.addPage()
                    // Wait a frame so the pager knows about the new page before scrolling to it.
                    scope.launch { withFrameNanos {}; state.animateScrollToPage(apps.pages.lastIndex) }
                }
            }
            else {
                // Long-press, then drag sideways past half a card to swap with the neighbour.
                val index by rememberUpdatedState(i)
                var dx by remember { mutableFloatStateOf(0f) }
                PagePreview(apps, page, current = i == state.currentPage, modifier = Modifier
                    .graphicsLayer { translationX = dx; if (dx != 0f) { scaleX = 1.03f; scaleY = 1.03f } }
                    .pointerInput(Unit) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = { if (!locked()) haptic.performHapticFeedback(HapticFeedbackType.LongPress) },
                            onDragEnd = { dx = 0f }, onDragCancel = { dx = 0f },
                        ) { change, drag ->
                            if (prefs.lockLayout) return@detectDragGesturesAfterLongPress
                            change.consume()
                            dx += drag.x
                            val step = size.width * 0.5f
                            val to = when { dx > step -> index + 1; dx < -step -> index - 1; else -> return@detectDragGesturesAfterLongPress }
                            if (to !in apps.pages.indices) return@detectDragGesturesAfterLongPress
                            apps.movePage(index, to)
                            dx += if (to > index) -(size.width + 12.dp.toPx()) else size.width + 12.dp.toPx()
                            scope.launch { state.scrollToPage(to) }
                        }
                    })
            }
        }
        Spacer(Modifier.height(18.dp))
        Row(Modifier.fillMaxWidth().height(36.dp), horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(6.dp).clip(CircleShape).background(if (state.currentPage > 0) Dim else Color.Transparent))
            when {
                !onPage -> Text("Add a page", color = Muted, fontSize = 13.sp)
                state.currentPage == apps.defaultPage -> Row(
                    Modifier.height(34.dp).clip(CircleShape).background(AccentCard).border(1.dp, AccentLine, CircleShape).padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(Ic.home, null, tint = Accent, modifier = Modifier.size(16.dp))
                    Text("Default page", color = Accent, fontSize = 13.sp)
                }
                else -> Chip("Set as default page", FieldBg, ChipLine, Sans, 13) { apps.makeDefault(state.currentPage) }
            }
            if (onPage && apps.pages.size > 1) Box(
                Modifier.size(34.dp).clip(CircleShape).border(1.dp, ChipLine, CircleShape).semantics { contentDescription = "Delete page" }
                    .clickable { if (!locked()) apps.deletePage(state.currentPage).forEach(widgets::remove) },
                contentAlignment = Alignment.Center,
            ) { Icon(Ic.uninstall, null, tint = Danger, modifier = Modifier.size(16.dp)) }
            Box(Modifier.size(6.dp).clip(CircleShape).background(if (state.currentPage < apps.pages.size) Dim else Color.Transparent))
        }
        Spacer(Modifier.weight(1f))
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            EditButton(EIc.wallpaper, "Wallpaper") { pickWallpaper(context) }
            EditButton(EIc.widgets, "Widgets") { if (onPage) onPickWidget() }
            EditButton(EIc.layout, "Layout") { onSettings("home") }
            EditButton(EIc.settings, "Settings") { onSettings("hub") }
        }
    }
}

@Composable
private fun RowScope.EditButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    Column(
        Modifier.weight(1f).height(80.dp).clip(RoundedCornerShape(18.dp)).background(FieldBg).border(1.dp, GroupLine, RoundedCornerShape(18.dp))
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
    ) {
        Icon(icon, null, tint = Fg, modifier = Modifier.size(20.dp))
        Text(label, fontSize = 13.sp)
    }
}

@Composable
private fun AddPageCard(onClick: () -> Unit) {
    val line = Knob
    Box(
        Modifier.fillMaxSize().clip(RoundedCornerShape(30.dp)).drawBehind {
            drawRoundRect(line, cornerRadius = CornerRadius(30.dp.toPx()),
                style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 6.dp.toPx()))))
        }.clickable(onClick = onClick).semantics { contentDescription = "Add page" },
        contentAlignment = Alignment.Center,
    ) { Icon(EIc.plus, null, tint = Muted, modifier = Modifier.size(28.dp)) }
}

/** A page drawn small: widget placeholders and real app icons at their cells. */
@Composable
private fun PagePreview(apps: Apps, page: HomePage, current: Boolean, modifier: Modifier) {
    val shape = RoundedCornerShape(30.dp)
    Column(
        modifier.fillMaxSize().clip(shape).background(Sheet).border(if (current) 2.dp else 1.dp, if (current) Accent else FieldLine, shape)
            .padding(start = 14.dp, end = 14.dp, top = 22.dp, bottom = 14.dp)
    ) {
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val cw = maxWidth / prefs.columns
            val ch = maxHeight / prefs.rows
            val icon = minOf(cw, ch) * 0.62f
            page.items.forEach { c ->
                Box(Modifier.offset(cw * c.x, ch * c.y).size(cw * c.w, ch * c.h).padding(3.dp), contentAlignment = Alignment.Center) {
                    if (c.builtin == "clock") Text(DateFormat.format("h:mm", Date()).toString(), fontSize = 40.sp, fontWeight = FontWeight.Light,
                        lineHeight = 44.sp, modifier = Modifier.fillMaxWidth())
                    else if (c.isWidget) Column(
                        Modifier.fillMaxSize().clip(RoundedCornerShape(14.dp)).background(Card).border(1.dp, CardLine, RoundedCornerShape(14.dp)).padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(7.dp),
                    ) {
                        Box(Modifier.fillMaxWidth(0.35f).height(4.dp).clip(CircleShape).background(Accent))
                        Box(Modifier.fillMaxWidth(0.9f).height(3.dp).clip(CircleShape).background(Knob))
                        Box(Modifier.fillMaxWidth(0.7f).height(3.dp).clip(CircleShape).background(Knob))
                    } else {
                        val bmp = apps.byKey(apps.folders[c.key]?.apps?.firstOrNull() ?: c.key)?.let { apps.icons[it.key] }
                        val s = iconShape(prefs.iconShape, icon)
                        if (bmp != null) Image(bmp, null, Modifier.size(icon).clip(s)) else Box(Modifier.size(icon).clip(s).background(Tile))
                    }
                }
            }
        }
        Box(Modifier.padding(top = 10.dp).fillMaxWidth().height(36.dp).clip(CircleShape).background(Tile).border(1.dp, PillLine, CircleShape))
    }
}
