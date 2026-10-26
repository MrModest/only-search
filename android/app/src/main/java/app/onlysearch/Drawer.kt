@file:OptIn(ExperimentalFoundationApi::class)

package app.onlysearch

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

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
    // Derived so scrubbing (each letter change recomposes the drawer) doesn't rebuild the lists.
    val visible by remember(apps) { derivedStateOf { apps.visible } }
    val frequent by remember(apps) { derivedStateOf { if (prefs.suggested && apps.groups.getOrNull(tab) == null) apps.frequent(prefs.drawerColumns) else emptyList() } }
    val shown by remember(apps) { derivedStateOf { apps.groups.getOrNull(tab)?.let { g -> visible.filter { it.key in g.apps } } ?: visible } }
    val scrub = prefs.scrubber && group == null
    val sections by remember(apps) { derivedStateOf { shown.groupBy { it.label.firstOrNull()?.uppercaseChar()?.takeIf(Char::isLetter) ?: '#' }.toSortedMap() } }
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
                    item(span = { GridItemSpan(cols) }, contentType = "label") { Label("Suggested", Modifier.padding(start = 6.dp, top = 6.dp)) }
                    items(frequent, key = { "f" + it.key }, contentType = { "app" }) { AppTile(apps, it, Fg, 54.dp, drag = "drawer") }
                    item(span = { GridItemSpan(cols) }, contentType = "divider") { Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFF22252A))) }
                }
                sections.forEach { (letter, group) ->
                    item(span = { GridItemSpan(cols) }, key = "h$letter", contentType = "header") {
                        Text(letter.toString(), color = Accent, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 8.dp))
                    }
                    items(group, key = { it.key }, contentType = { "app" }) { AppTile(apps, it, Fg, 54.dp, drag = "drawer") }
                }
            }
            if (group != null && shown.isEmpty()) Text("No apps in “${group.name}” yet. Long-press the tab to add some.", color = Muted, fontSize = 14.sp,
                modifier = Modifier.align(Alignment.TopCenter).padding(24.dp))
            if (scrub) {
                // Full A–Z like the mock-up; a letter with no apps jumps to the next one that has some.
                val letters = remember(headerIndex) { ('A'..'Z').toList() + headerIndex.keys.filter { it !in 'A'..'Z' } }
                val target = remember(headerIndex) { letters.associateWith { l -> headerIndex[l] ?: headerIndex.entries.firstOrNull { it.key > l }?.value ?: headerIndex.values.lastOrNull() ?: 0 } }
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
        Pill("Search ${visible.size} apps or calculate", Modifier.padding(start = 14.dp, end = 14.dp, top = 10.dp, bottom = 10.dp),
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
