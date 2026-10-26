@file:OptIn(ExperimentalLayoutApi::class)

package app.onlysearch

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

internal data class Calc(val expression: String, val result: String, val micros: Long, val home: String? = null)

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
            // Hidden when the query is a kind the user switched off in Settings › Search & calculator.
            calc = if (r.isBlank() || r == q.trim() || !calcAllowed(q)) null
            else Calc(q.trim(), r, us, homeCurrencyQuery(q.trim())?.let { Soulver.evaluate(it) }?.takeIf { it.isNotBlank() && it != r })
        }
    }
    // A copy only when the list changes; an unchanged one compares by identity.
    val all by remember(apps) { derivedStateOf { apps.all.toList() } }
    val (matches, matchMicros) = remember(query, all, apps.hidden.size) {
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
    val topApp = top?.first
    val loadedShortcuts by produceState(topApp?.key to emptyList<Shortcut>(), topApp?.key) {
        value = topApp?.key to topApp?.let { withContext(Dispatchers.IO) { apps.shortcuts(it).take(3) } }.orEmpty()
    }
    val shortcuts = loadedShortcuts.second.takeIf { loadedShortcuts.first == topApp?.key }.orEmpty()
    val copy = { c: Calc ->
        val text = if (prefs.enterCopies == "both") "${c.expression} = ${c.result}" else c.result
        clipboard.setText(AnnotatedString(text)); apps.remember(c.expression, c.result)
        Toast.makeText(context, "Copied $text", Toast.LENGTH_SHORT).show()
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
    val keyboard = LocalSoftwareKeyboardController.current
    val openSetting = { e: SettingsEntry ->
        if (e.page != null) { keyboard?.hide(); onSettings(e.page) }
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
                        AppGrid(apps, recent, size = 52.dp, labelColor = Muted, columns = 5, drag = "search")
                    }
                }
            } else {
                if (c != null) item(key = "calc") {
                    Column(Modifier.animateItem(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(Modifier.padding(horizontal = 4.dp)) {
                            Label("Calculator", Modifier.weight(1f))
                            if (prefs.computeTime) Text("%.1f ms".format(c.micros / 1000.0), color = Muted, fontSize = 11.sp, fontFamily = Mono)
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
                        AppGrid(apps, rest, size = 52.dp, rowGap = 12.dp, drag = "search")
                    }
                }
                if (people.isNotEmpty() || wantsPeople) item(key = "people") {
                    Column(Modifier.animateItem(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Label("People", Modifier.padding(horizontal = 4.dp))
                        ResultCard {
                            if (wantsPeople) ContactsPrompt(onAllow = { askPermission(context, android.Manifest.permission.READ_CONTACTS, askContacts) }, onOff = { prefs.srcContacts = false })
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
        val total = matches.size + people.size + settingsHits.size
        val counted = "$total ${if (total == 1) "result" else "results"} · %.0f ms".format(matchMicros / 1000.0)
        val (left, right) = when {
            query.isBlank() -> "↵ open top hit" to "long-press for actions"
            c != null -> (if (prefs.enterCopies == "both") "↵ copy expression = answer" else "↵ copy answer") to "long-press app for actions"
            top != null -> "↵ open ${top.first.label}" to counted
            total > 0 -> "" to counted
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
    val dnd = LocalDragDrop.current
    val (track, source, anchor) = rememberLaunchSource()
    val coords = remember { arrayOfNulls<LayoutCoordinates>(1) }
    val shape = RoundedCornerShape(20.dp)
    Box {
        Column(
            Modifier.fillMaxWidth().clip(shape).background(AccentCard).border(1.dp, AccentLine, shape).onGloballyPositioned { coords[0] = it }
                .semantics { onClick { apps.launch(app, source()); true }; onLongClick { menu = true; true } }
                .pointerInput(app.key) {
                    // Hold and drag pins the app on home, like a tile; the ghost is the icon.
                    tapHoldDrag(
                        { coords[0] }, onTap = { apps.launch(app, source()) },
                        onLongPress = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); menu = true },
                        onDragStart = { _, at ->
                            menu = false
                            val px = 52.dp.toPx()
                            dnd.start(Dragged(app.key, prefs.appSpan, prefs.appSpan, "search", Offset(px / 2, px / 2), Size(px, px)), at)
                        },
                        onDrag = dnd::move, onDragEnd = dnd::end,
                    )
                }
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
                shortcuts.forEach { s -> Chip(s.label, Color(0xFF16232F), AccentLine, Sans, 13) { apps.launch(s, source()) } }
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
        Chip("Allow", Color(0xFF16232F), AccentLine, Sans, 13, onAllow)
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
        if (c.home != null) Text("≈ ${c.home}", color = Muted, fontSize = 15.sp, fontFamily = Mono, textAlign = TextAlign.End, modifier = Modifier.fillMaxWidth())
    }
}

internal fun highlight(label: String, range: IntRange?) = buildAnnotatedString {
    append(label)
    if (range != null && range.last < label.length) addStyle(SpanStyle(color = Accent), range.first, range.last + 1)
}
