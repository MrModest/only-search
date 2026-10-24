package app.onlysearch

import android.app.ActivityOptions
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.content.pm.ShortcutInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import android.provider.Settings
import android.view.View
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.content.edit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class App(val label: String, val component: ComponentName, val user: UserHandle) {
    val key = "${component.flattenToString()}#${user.hashCode()}"
}

/** Where a launch starts on screen, so the app window zooms out of that icon. */
class LaunchSource(private val view: View, val bounds: Rect) {
    fun options(): Bundle =
        ActivityOptions.makeScaleUpAnimation(view, bounds.left, bounds.top, bounds.width(), bounds.height()).toBundle()
}

/** Something on a home page grid: an app key, or "w:<id>" for a widget. Position and size are in cells. */
data class Cell(val key: String, val x: Int, val y: Int, val w: Int = 1, val h: Int = 1) {
    val widgetId get() = if (key.startsWith("w:")) key.substring(2).toIntOrNull() else null
}

/** One home screen page. [id] only lives in memory, so moved pages keep their composables. */
data class HomePage(val items: List<Cell> = emptyList(), val id: Long = nextPageId++)
private var nextPageId = 0L

/** A drawer tab: a named set of app keys. */
data class AppGroup(val name: String, val apps: List<String>)

data class Shortcut(val label: String, val app: App, val info: ShortcutInfo)

/** Installed apps, icons, and the user's pins / launch counts. All state is Compose-observable. */
class Apps(private val context: Context) {
    private val launcher = context.getSystemService(LauncherApps::class.java)
    private val sp = context.getSharedPreferences("launcher", Context.MODE_PRIVATE)

    val all = mutableStateListOf<App>()
    val icons = mutableStateMapOf<String, ImageBitmap>()
    val pages = mutableStateListOf<HomePage>().apply { addAll(loadPages()) }
    var defaultPage by mutableIntStateOf(sp.getInt("defaultPage", 0).coerceIn(pages.indices))
    /** The page on screen; new pins and widgets land here. */
    var currentPage by mutableIntStateOf(defaultPage)
    val dock = mutableStateListOf<String>().apply { addAll(sp.list("dock")) }
    val history = mutableStateListOf<Pair<String, String>>().apply {
        addAll(sp.list("history").mapNotNull { it.split('\t').takeIf { p -> p.size == 2 }?.let { p -> p[0] to p[1] } })
    }
    val recent = mutableStateListOf<String>().apply { addAll(sp.list("recent")) }
    val hidden = mutableStateListOf<String>().apply { addAll(sp.list("hidden")) }
    /** Apps shown in the drawer and search: everything not hidden. */
    val visible get() = all.filter { it.key !in hidden }
    private val systemLabels = mutableMapOf<String, String>()
    val groups = mutableStateListOf<AppGroup>().apply {
        val arr = JSONArray(sp.getString("groups", "[]"))
        addAll((0 until arr.length()).map { i ->
            arr.getJSONObject(i).run { val a = getJSONArray("apps"); AppGroup(getString("name"), (0 until a.length()).map(a::getString)) }
        })
    }
    private val counts = mutableStateMapOf<String, Int>()
    val loaded = mutableStateOf(false)

    fun byKey(key: String) = all.firstOrNull { it.key == key }

    suspend fun load() {
        val (apps, newIcons) = withContext(Dispatchers.Default) {
            val users = context.getSystemService(UserManager::class.java).userProfiles
            val apps = users.flatMap { user ->
                launcher.getActivityList(null, user).map { App(it.label.toString(), it.componentName, user) to it }
            }.filter { it.first.component.packageName != context.packageName }
                .map { (app, info) -> systemLabels[app.key] = app.label; app.copy(label = sp.getString("label:${app.key}", null) ?: app.label) to info }
                .sortedBy { it.first.label.lowercase() }
            val size = (56 * context.resources.displayMetrics.density).toInt()
            apps to apps.filter { it.first.key !in icons }.associate { (app, info) -> app.key to squareIcon(info.getIcon(0), size) }
        }
        icons.putAll(newIcons)
        sp.all.forEach { (k, v) -> if (k.startsWith("count:") && v is Int) counts[k.removePrefix("count:")] = v }
        all.clear(); all.addAll(apps.map { it.first })
        if (dock.isEmpty() && pages.all { it.items.isEmpty() } && !sp.getBoolean("seeded", false)) seedDefaults()
        loaded.value = true
    }

    fun frequent(n: Int) = visible.filter { (counts[it.key] ?: 0) > 0 }.sortedByDescending { counts[it.key] }.take(n)

    /** True between launching an app and coming back home; drives the return animation. */
    var returningFromApp = false

    fun launch(app: App, from: LaunchSource? = null) {
        counts[app.key] = (counts[app.key] ?: 0) + 1
        recent.remove(app.key); recent.add(0, app.key)
        while (recent.size > 5) recent.removeAt(recent.lastIndex)
        sp.edit { putInt("count:${app.key}", counts[app.key]!!); putString("recent", recent.joinToString("\n")) }
        returningFromApp = true
        launcher.startMainActivity(app.component, app.user, from?.bounds, from?.options())
    }

    fun toggle(list: MutableList<String>, name: String, app: App, max: Int) {
        if (!list.remove(app.key) && list.size < max) list.add(app.key)
        sp.edit { putString(name, list.joinToString("\n")) }
    }
    fun onHome(app: App) = pages.any { p -> p.items.any { it.key == app.key } }

    /** True if a w×h block fits at (x, y) on [page] without overlapping anything except [ignore]. */
    fun fits(page: Int, x: Int, y: Int, w: Int, h: Int, ignore: String? = null): Boolean {
        if (x < 0 || y < 0 || x + w > prefs.columns || y + h > prefs.rows) return false
        return pages[page].items.none { it.key != ignore && x < it.x + it.w && it.x < x + w && y < it.y + it.h && it.y < y + h }
    }

    /** First free spot for a w×h block; apps fill up from the bottom row, widgets down from the top. */
    fun freeSpot(page: Int, w: Int, h: Int, fromBottom: Boolean): Pair<Int, Int>? {
        val ys = (0..prefs.rows - h).let { if (fromBottom) it.reversed() else it }
        for (y in ys) for (x in 0..prefs.columns - w) if (fits(page, x, y, w, h)) return x to y
        return null
    }

    /** Moves [cell]'s item to that spot on [page], from wherever it was. False if the spot isn't free. */
    fun place(page: Int, cell: Cell): Boolean {
        if (!fits(page, cell.x, cell.y, cell.w, cell.h, cell.key)) return false
        dropItem(cell.key)
        pages[page] = pages[page].copy(items = pages[page].items + cell)
        savePages()
        return true
    }

    private fun dropItem(key: String) {
        for (i in pages.indices) if (pages[i].items.any { it.key == key }) pages[i] = pages[i].copy(items = pages[i].items.filter { it.key != key })
    }

    fun removeItem(key: String) { dropItem(key); savePages() }

    /** Unpins the app wherever it is, or pins it to the current page (or the first one with room). */
    fun toggleHome(app: App) {
        if (onHome(app)) return removeItem(app.key)
        for (p in (listOf(currentPage) + pages.indices).filter { it in pages.indices }) {
            val (x, y) = freeSpot(p, 1, 1, fromBottom = true) ?: continue
            place(p, Cell(app.key, x, y)); return
        }
    }

    /** Where the next bound widget goes, if it was dropped on a spot. */
    var widgetSpot: Pair<Int, Cell>? = null

    /** Puts a newly bound widget on the page; false if there's no room for it. */
    fun addWidget(id: Int, w: Int, h: Int): Boolean {
        val spot = widgetSpot.also { widgetSpot = null }
        if (spot != null && place(spot.first, spot.second.copy(key = "w:$id"))) return true
        val p = currentPage.coerceIn(pages.indices)
        val cw = w.coerceAtMost(prefs.columns); val ch = h.coerceAtMost(prefs.rows)
        val (x, y) = freeSpot(p, cw, ch, fromBottom = false) ?: return false
        return place(p, Cell("w:$id", x, y, cw, ch))
    }

    fun pruneWidgets(valid: (Int) -> Boolean) {
        val bad = pages.flatMap { p -> p.items.mapNotNull { it.widgetId }.filterNot(valid) }
        if (bad.isEmpty()) return
        bad.forEach { dropItem("w:$it") }
        savePages()
    }

    /** After the grid shrinks: shrink widgets that are too big and move what's off the grid to a free spot. */
    fun fitGrid() {
        var changed = false
        for (p in pages.indices) {
            val items = pages[p].items
            pages[p] = pages[p].copy(items = emptyList())
            items.forEach { c ->
                val s = c.copy(w = c.w.coerceAtMost(prefs.columns), h = c.h.coerceAtMost(prefs.rows))
                if (s == c && fits(p, s.x, s.y, s.w, s.h)) { pages[p] = pages[p].copy(items = pages[p].items + s); return@forEach }
                changed = true
                val (x, y) = freeSpot(p, s.w, s.h, fromBottom = s.widgetId == null) ?: return@forEach
                pages[p] = pages[p].copy(items = pages[p].items + s.copy(x = x, y = y))
            }
        }
        if (changed) savePages()
    }

    fun addPage() { pages.add(HomePage()); savePages() }

    /** Returns the deleted page's widget ids so the caller can release them. */
    fun deletePage(i: Int): List<Int> {
        val gone = pages.removeAt(i)
        if (defaultPage > i || defaultPage == pages.size) defaultPage--
        defaultPage = defaultPage.coerceIn(pages.indices)
        currentPage = currentPage.coerceIn(pages.indices)
        savePages()
        return gone.items.mapNotNull { it.widgetId }
    }

    fun movePage(from: Int, to: Int) {
        val def = pages[defaultPage].id
        pages.add(to, pages.removeAt(from))
        defaultPage = pages.indexOfFirst { it.id == def }
        savePages()
    }

    fun makeDefault(i: Int) { defaultPage = i; savePages() }

    private fun savePages() = syncPins().also { sp.edit {
        putString("pages", JSONArray(pages.map { p ->
            JSONObject().put("items", JSONArray(p.items.map { JSONObject().put("k", it.key).put("x", it.x).put("y", it.y).put("w", it.w).put("h", it.h) }))
        }).toString())
        putInt("defaultPage", defaultPage)
    } }

    // Before pages there was one "home" list (apps sat in the bottom rows) and one "widgets" list (stacked at the top).
    private fun loadPages(): List<HomePage> {
        val json = sp.getString("pages", null) ?: run {
            val cols = prefs.columns; val rows = prefs.rows
            val apps = sp.list("home").take(cols * rows)
            val top = rows - (apps.size + cols - 1) / cols
            val widgets = sp.getString("widgets", "")!!.split(",").mapNotNull { it.toIntOrNull() }
                .take(top / 2).mapIndexed { i, id -> Cell("w:$id", 0, i * 2, cols, 2) }
            return listOf(HomePage(widgets + apps.mapIndexed { i, k -> Cell(k, i % cols, top + i / cols) }))
        }
        val arr = JSONArray(json)
        return (0 until arr.length()).map { i ->
            val items = arr.getJSONObject(i).getJSONArray("items")
            HomePage((0 until items.length()).map { j ->
                items.getJSONObject(j).run { Cell(getString("k"), getInt("x"), getInt("y"), getInt("w"), getInt("h")) }
            })
        }.ifEmpty { listOf(HomePage()) }
    }

    fun toggleDock(app: App) = toggle(dock, "dock", app, 4)

    fun saveDock() = sp.edit { putString("dock", dock.joinToString("\n")) }

    /** Moves an app into the dock at [index] (reordering if it's already there); takes it off the home grid. */
    fun dropOnDock(key: String, index: Int) {
        if (key !in dock && dock.size >= 4) return
        dock.remove(key)
        dock.add(index.coerceIn(0, dock.size), key)
        dropItem(key); savePages(); saveDock()
    }

    /** [place], or for an app dropped on another app: the two swap cells. */
    fun moveTo(page: Int, target: Cell): Boolean {
        if (place(page, target)) return true
        if (target.w != 1 || target.h != 1) return false
        val other = pages[page].items.firstOrNull { it.x == target.x && it.y == target.y && it.w == 1 && it.h == 1 } ?: return false
        val fromPage = pages.indexOfFirst { p -> p.items.any { it.key == target.key } }
        if (fromPage < 0) return false
        val from = pages[fromPage].items.first { it.key == target.key }
        dropItem(other.key); dropItem(target.key)
        pages[page] = pages[page].copy(items = pages[page].items + target)
        pages[fromPage] = pages[fromPage].copy(items = pages[fromPage].items + other.copy(x = from.x, y = from.y))
        savePages()
        return true
    }

    fun hide(app: App, hide: Boolean) {
        hidden.remove(app.key); if (hide) hidden.add(app.key)
        sp.edit { putString("hidden", hidden.joinToString("\n")) }
    }

    /** Adds the group at [index] (-1 = new) or replaces it. */
    fun saveGroup(index: Int, group: AppGroup) {
        if (index in groups.indices) groups[index] = group else groups.add(group)
        saveGroups()
    }

    fun deleteGroup(index: Int) { groups.removeAt(index); saveGroups() }

    private fun saveGroups() = sp.edit {
        putString("groups", JSONArray(groups.map { JSONObject().put("name", it.name).put("apps", JSONArray(it.apps)) }).toString())
    }

    fun systemLabel(app: App) = systemLabels[app.key] ?: app.label

    /** A custom name for the app; blank goes back to the app's own name. */
    fun rename(app: App, label: String) {
        val name = label.trim().ifEmpty { systemLabel(app) }
        sp.edit { if (name == systemLabel(app)) remove("label:${app.key}") else putString("label:${app.key}", name) }
        val i = all.indexOfFirst { it.key == app.key }
        if (i >= 0) all[i] = all[i].copy(label = name)
        all.sortBy { it.label.lowercase() }
    }

    // Pinned shortcuts live on the grid as "s:<user>:<package>/<id>".
    fun shortcutKey(s: Shortcut) = "s:${s.app.user.hashCode()}:${s.info.`package`}/${s.info.id}"

    private fun parseShortcut(key: String): Triple<Int, String, String>? {
        val (user, rest) = key.removePrefix("s:").split(":", limit = 2).takeIf { it.size == 2 } ?: return null
        val (pkg, id) = rest.split("/", limit = 2).takeIf { it.size == 2 } ?: return null
        return Triple(user.toIntOrNull() ?: return null, pkg, id)
    }

    private val shortcutCache = mutableMapOf<String, Shortcut?>()

    fun pinnedShortcut(key: String): Shortcut? = shortcutCache.getOrPut(key) {
        val (user, pkg, id) = parseShortcut(key) ?: return null
        val app = all.firstOrNull { it.component.packageName == pkg && it.user.hashCode() == user } ?: return null
        if (!launcher.hasShortcutHostPermission()) return null
        val q = LauncherApps.ShortcutQuery().setPackage(pkg).setShortcutIds(listOf(id)).setQueryFlags(
            LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST or LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED
        )
        runCatching { launcher.getShortcuts(q, app.user) }.getOrNull()?.firstOrNull()?.let { Shortcut((it.shortLabel ?: it.longLabel ?: "").toString(), app, it) }
    }

    private var pinned = pages.flatMap { p -> p.items.map { it.key } }.filter { it.startsWith("s:") }.toSet()

    // The system only keeps a shortcut alive while a launcher pins it; pinShortcuts replaces a package's whole set.
    private fun syncPins() {
        val now = pages.flatMap { p -> p.items.map { it.key } }.filter { it.startsWith("s:") }.toSet()
        if (now == pinned) return
        (now + pinned).mapNotNull(::parseShortcut).map { it.first to it.second }.distinct().forEach { (user, pkg) ->
            val handle = all.firstOrNull { it.component.packageName == pkg && it.user.hashCode() == user }?.user ?: return@forEach
            val ids = now.mapNotNull(::parseShortcut).filter { it.first == user && it.second == pkg }.map { it.third }
            runCatching { launcher.pinShortcuts(pkg, ids, handle) }
        }
        pinned = now
    }

    fun remember(expression: String, result: String) {
        history.removeAll { it.first == expression }
        history.add(0, expression to result)
        while (history.size > 20) history.removeAt(history.lastIndex)
        sp.edit { putString("history", history.joinToString("\n") { "${it.first}\t${it.second}" }) }
    }

    fun clearHistory() { history.clear(); sp.edit { remove("history") } }

    fun shortcuts(app: App): List<Shortcut> {
        if (!launcher.hasShortcutHostPermission()) return emptyList()
        val q = LauncherApps.ShortcutQuery().setPackage(app.component.packageName).setQueryFlags(
            LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST or LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED
        )
        return runCatching { launcher.getShortcuts(q, app.user).orEmpty() }.getOrDefault(emptyList())
            .map { Shortcut((it.shortLabel ?: it.longLabel ?: "").toString(), app, it) }
    }

    fun launch(s: Shortcut, from: LaunchSource? = null) {
        returningFromApp = true
        launcher.startShortcut(s.info, from?.bounds, from?.options())
    }

    fun shortcutIcon(s: Shortcut): ImageBitmap? = runCatching {
        val size = (40 * context.resources.displayMetrics.density).toInt()
        squareIcon(launcher.getShortcutIconDrawable(s.info, context.resources.displayMetrics.densityDpi)!!, size)
    }.getOrNull()

    fun appInfo(app: App) = launcher.startAppDetailsActivity(app.component, app.user, null, null)

    fun uninstall(app: App) = context.startActivity(
        Intent(Intent.ACTION_DELETE, Uri.fromParts("package", app.component.packageName, null)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    )

    fun homeSettings() = context.startActivity(Intent(Settings.ACTION_HOME_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))

    // Dock gets the default phone, SMS, browser and camera apps on first run.
    private fun seedDefaults() {
        val pm = context.packageManager
        listOf(
            Intent(Intent.ACTION_DIAL),
            Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:")),
            Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com")),
            Intent("android.media.action.STILL_IMAGE_CAMERA"),
        ).mapNotNull { pm.resolveActivity(it, 0)?.activityInfo?.packageName }
            .mapNotNull { pkg -> all.firstOrNull { it.component.packageName == pkg && it.user == Process.myUserHandle() } }
            .distinct().forEach { dock.add(it.key) }
        sp.edit { putString("dock", dock.joinToString("\n")); putBoolean("seeded", true) }
    }

    private fun android.content.SharedPreferences.list(name: String) =
        getString(name, "")!!.split("\n").filter { it.isNotEmpty() }
}

/** Renders adaptive icons full-bleed (no system mask) so the UI can clip them to its own near-square shape. */
fun squareIcon(d: Drawable, size: Int): ImageBitmap {
    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    if (d is AdaptiveIconDrawable) {
        val inset = size / 4
        listOfNotNull(d.background, d.foreground).forEach { it.setBounds(-inset, -inset, size + inset, size + inset); it.draw(c) }
    } else {
        d.setBounds(0, 0, size, size); d.draw(c)
    }
    return bmp.asImageBitmap()
}

/** Score for fuzzy app matching; null = no match. Higher is better. Returns the matched range for highlighting. */
fun match(label: String, query: String): Pair<Int, IntRange?>? {
    val l = label.lowercase(); val q = query.lowercase().trim()
    if (q.isEmpty()) return null
    if (l.startsWith(q)) return 100 to q.indices
    val word = Regex("\\b${Regex.escape(q)}").find(l)
    if (word != null) return 80 to word.range
    val i = l.indexOf(q)
    if (i >= 0) return 60 to (i until i + q.length)
    var j = 0
    for (ch in l) if (j < q.length && ch == q[j]) j++
    return if (j == q.length && q.length > 1) 30 to null else null
}
