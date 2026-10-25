package app.onlysearch

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import android.os.Process
import android.util.Xml
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.util.zip.ZipInputStream
import kotlin.math.roundToInt

/**
 * Imports a Nova Launcher backup (.novabackup: a zip of nova.db and nova.xml) into the launcher prefs:
 * home pages (apps, folders, widgets), dock, drawer groups and folders (as groups), hidden apps, custom names,
 * and the settings that have a counterpart here. Replaces the current layout; the caller restarts the activity.
 * Widgets can't be carried over as they are (their ids belong to Nova), so they're queued in "novaWidgets"
 * and bound again on the next start ([bindNovaWidgets]).
 * Returns a one-line summary.
 */
internal fun importNova(context: Context, uri: Uri, apps: Apps): String {
    val dir = File(context.cacheDir, "nova").apply { deleteRecursively(); mkdirs() }
    context.contentResolver.openInputStream(uri)!!.use { input ->
        ZipInputStream(input).use { zip ->
            generateSequence { zip.nextEntry }.forEach { e ->
                val name = File(e.name).name
                if (name == "nova.db" || name == "nova.xml") File(dir, name).outputStream().use { zip.copyTo(it) }
            }
        }
    }
    val dbFile = File(dir, "nova.db")
    require(dbFile.exists()) { "Not a Nova backup" }
    val novaPrefs = File(dir, "nova.xml").takeIf { it.exists() }?.let(::readPrefsXml).orEmpty()
    val db = SQLiteDatabase.openDatabase(dbFile.path, null, SQLiteDatabase.OPEN_READONLY)
    try {
        return db.use { importNova(context, it, novaPrefs, apps) }
    } finally {
        dir.deleteRecursively()
    }
}

private class NovaItem(val id: Long, val title: String, val intent: String?, val container: Long, val screen: Int,
                       val x: Double, val y: Double, val w: Double, val h: Double, val type: Int, val provider: String?, val profile: Int)

private fun importNova(context: Context, db: SQLiteDatabase, novaPrefs: Map<String, String>, apps: Apps): String {
    val me = Process.myUserHandle().hashCode()
    // Nova stores the main profile as -1.
    fun userOf(profile: Int) = if (profile < 0) me else profile
    fun keyOf(cn: ComponentName?, pkg: String?, profile: Int): String? {
        val p = cn?.packageName ?: pkg ?: return null
        val ofPkg = apps.all.filter { it.component.packageName == p }
        val user = userOf(profile)
        return (ofPkg.firstOrNull { it.component == cn && it.user.hashCode() == user } ?: ofPkg.firstOrNull { it.component == cn }
            ?: ofPkg.firstOrNull { it.user.hashCode() == user } ?: ofPkg.firstOrNull())?.key
    }
    fun keyOfIntent(intent: String?, profile: Int): String? {
        val i = runCatching { Intent.parseUri(intent ?: return null, 0) }.getOrNull() ?: return null
        return keyOf(i.component, i.`package`, profile)
    }
    // "pkg/cls#profile", as Nova's app groups store it.
    fun keyOfComponent(s: String): String? {
        val cn = ComponentName.unflattenFromString(s.substringBefore('#')) ?: return null
        return keyOf(cn, null, s.substringAfter('#', "-1").toIntOrNull() ?: -1)
    }

    val items = mutableListOf<NovaItem>()
    db.rawQuery("SELECT _id, title, intent, container, screen, cellX, cellY, spanX, spanY, itemType, appWidgetProvider, profileId FROM favorites", null).use { c ->
        while (c.moveToNext()) items += NovaItem(c.getLong(0), c.getString(1).orEmpty(), c.getString(2), c.getLong(3), c.getInt(4),
            c.getDouble(5), c.getDouble(6), c.getDouble(7), c.getDouble(8), c.getInt(9), c.getString(10), c.getInt(11))
    }
    val desktop = items.filter { it.container == -100L }
    // "7x5 subgrid" = rows × columns. Half-cell positions need a grid twice as fine (the user's choice).
    val (rows, cols) = novaPrefs["desktop_grid"]?.let { Regex("(\\d+)x(\\d+)").find(it)?.destructured }?.let { (r, c) -> r.toInt() to c.toInt() } ?: (prefs.rows to prefs.columns)
    val half = desktop.any { listOf(it.x, it.y, it.w, it.h).any { v -> v % 1.0 != 0.0 } }
    val f = if (half) 2 else 1
    fun cell(v: Double) = Math.round(v * f).toInt()

    // Folders: an itemType 2 row on the desktop; its apps have container = the folder's _id.
    val folders = mutableMapOf<String, Folder>()
    fun folderApps(id: Long) = items.filter { it.container == id && it.type != 2 }.sortedWith(compareBy({ it.y }, { it.x }))
        .mapNotNull { keyOfIntent(it.intent, it.profile) }.distinct()

    val screens = desktop.map { it.screen }.distinct().sorted()
    val widgetQueue = JSONArray()
    val pages = screens.mapIndexed { page, screen ->
        JSONObject().put("items", JSONArray().apply {
            desktop.filter { it.screen == screen }.forEach { it ->
                val x = cell(it.x); val y = cell(it.y)
                val key = when (it.type) {
                    0, 1 -> keyOfIntent(it.intent, it.profile)
                    2 -> folderApps(it.id).let { list ->
                        when (list.size) {
                            0 -> null
                            1 -> list[0]
                            else -> "f:nova${it.id}".also { k -> folders[k] = Folder(it.title.trim().ifEmpty { "Folder" }, list) }
                        }
                    }
                    4 -> {
                        it.provider?.let { p -> widgetQueue.put(JSONObject().put("provider", p).put("profile", userOf(it.profile)).put("page", page)
                            .put("x", x).put("y", y).put("w", cell(it.w).coerceAtLeast(1)).put("h", cell(it.h).coerceAtLeast(1))) }
                        null
                    }
                    else -> null
                } ?: return@forEach
                val (w, h) = if (it.type == 4) cell(it.w) to cell(it.h) else f to f
                put(JSONObject().put("k", key).put("x", x).put("y", y).put("w", w).put("h", h))
            }
        })
    }.ifEmpty { listOf(JSONObject().put("items", JSONArray())) }

    val dock = items.filter { it.container == -101L }.sortedBy { it.x }.mapNotNull { keyOfIntent(it.intent, it.profile) }

    // Drawer tabs and drawer folders both become drawer groups.
    val groups = JSONArray()
    db.rawQuery("SELECT _id, title FROM drawer_groups WHERE groupType IN ('TAB_APP_GROUP', 'FOLDER_APP_GROUP') ORDER BY tabOrder, _id", null).use { c ->
        while (c.moveToNext()) {
            val keys = db.rawQuery("SELECT component FROM appgroups WHERE groupId = ?", arrayOf(c.getLong(0).toString())).use { a ->
                generateSequence { if (a.moveToNext()) a.getString(0) else null }.toList()
            }.mapNotNull(::keyOfComponent).distinct()
            if (keys.isNotEmpty()) groups.put(JSONObject().put("name", c.getString(1).orEmpty().trim().ifEmpty { "Group" }).put("apps", JSONArray(keys)))
        }
    }
    val hidden = db.rawQuery("SELECT component FROM appgroups WHERE groupId = -1", null).use { a ->
        generateSequence { if (a.moveToNext()) a.getString(0) else null }.toList()
    }.mapNotNull(::keyOfComponent).distinct()
    val labels = db.rawQuery("SELECT componentName, profileId, title FROM allapps WHERE title IS NOT NULL", null).use { c ->
        generateSequence { if (c.moveToNext()) Triple(c.getString(0), c.getInt(1), c.getString(2)) else null }.toList()
    }.mapNotNull { (cn, profile, title) -> keyOf(ComponentName.unflattenFromString(cn), null, profile)?.let { it to title } }

    // The current layout goes: home pages, folders, dock and groups are replaced, widgets released on the next start.
    val sp = prefs.sp
    sp.edit {
        putString("pages", JSONArray(pages).toString())
        putInt("defaultPage", (novaPrefs["desktop_default_page"]?.toIntOrNull() ?: 0).coerceIn(0, pages.size - 1))
        putString("folders", JSONObject().apply { folders.forEach { (k, v) -> put(k, JSONObject().put("name", v.name).put("apps", JSONArray(v.apps))) } }.toString())
        putString("dock", dock.joinToString("\n"))
        putString("groups", groups.toString())
        putString("hidden", hidden.joinToString("\n"))
        labels.forEach { (k, title) -> putString("label:$k", title) }
        putString("novaWidgets", widgetQueue.toString())
        putBoolean("builtins", true)
        putBoolean("seeded", true)
    }
    prefs.columns = (cols * f).coerceIn(3, MaxColumns)
    prefs.rows = (rows * f).coerceIn(1, MaxRows)
    prefs.appSpan = f
    novaPrefs["dock_grid_cols"]?.toIntOrNull()?.let { prefs.dockSize = it.coerceIn(3, 6) }
    novaPrefs["dock_enable"]?.let { prefs.showDock = it == "true" }
    novaPrefs["searchbar_placement"]?.let { prefs.searchBar = when { it.startsWith("DOCK_ABOVE") -> "above"; it.startsWith("DOCK_BELOW") -> "bottom"; it.contains("TOP") -> "top"; else -> prefs.searchBar } }
    novaPrefs["adaptive_icon_shape"]?.let { prefs.iconShape = when (it) { "CIRCLE" -> "circle"; "SQUARE", "ROUNDED_SQUARE" -> "square"; "TEARDROP" -> "teardrop"; else -> "squircle" } }
    // "1.25:true:13.0:…" = icon scale : show labels : …
    novaPrefs["desktop_cellspecs"]?.split(":")?.let { s ->
        // Nova's 100% icon is ~42 dp, ours 58 dp: 125% in Nova ≈ 90% here.
        s.getOrNull(0)?.toFloatOrNull()?.let { prefs.iconSize = (it * 42 / 58 * 100 / 5).roundToInt().times(5).coerceIn(70, 130) }
        s.getOrNull(1)?.let { prefs.labels = it == "true" }
    }
    novaPrefs["drawer_grid"]?.let { Regex("\\d+x(\\d+)").find(it)?.groupValues?.get(1)?.toIntOrNull() }?.let { prefs.drawerColumns = it.coerceIn(3, 6) }
    novaPrefs["drawer_predictive_apps"]?.let { prefs.suggested = it == "true" }
    mapOf("gesture_swipe_up" to "gUp", "gesture_swipe_down" to "gDown", "gesture_two_finger_scroll_down" to "gTwoDown",
        "gesture_double_tap" to "gDoubleTap", "gesture_pinch" to "gPinch").forEach { (nova, ours) ->
        novaPrefs[nova]?.let { novaAction(it) { intent -> keyOfIntent(intent, -1) } }?.let { prefs.setGesture(ours, it) }
    }
    return "Imported ${pages.size} pages, ${dock.size} dock apps, ${folders.size} folders, ${groups.length()} groups, ${widgetQueue.length()} widgets"
}

/** "Label$#Intent;…" → one of our gesture actions, or null when there's no counterpart (e.g. Recent apps). */
private fun novaAction(value: String, keyOf: (String) -> String?): String? {
    val label = value.substringBefore("$#Intent")
    val intent = value.substringAfter("$", "")
    return when (label) {
        "App drawer" -> "drawer"
        "Expand notifications", "Notifications" -> "notifications"
        "Nova Search", "Search" -> "search"
        "Desktop previews", "Overview" -> "edit"
        "Screen lock", "Lock screen" -> "lock"
        "Nova Settings" -> "settings"
        "None", "Nothing" -> "none"
        else -> if (intent.contains("com.teslacoilsw.launcher.ACTION")) null else keyOf(intent)?.let { "app:$it" }
    }
}

/** SharedPreferences XML (<map><string name=…>…</string><boolean name=… value=…/>…</map>) as plain strings. */
private fun readPrefsXml(file: File): Map<String, String> {
    val out = mutableMapOf<String, String>()
    file.inputStream().use { input ->
        val p = Xml.newPullParser().apply { setInput(input, null) }
        while (p.next() != XmlPullParser.END_DOCUMENT) {
            if (p.eventType != XmlPullParser.START_TAG || p.name == "map") continue
            val name = p.getAttributeValue(null, "name") ?: continue
            out[name] = p.getAttributeValue(null, "value") ?: if (p.name == "string") p.nextText() else continue
        }
    }
    return out
}

/** Binds the widgets a Nova import queued, one after another (each may ask to allow binding or to configure). */
internal fun bindNovaWidgets(context: Context, apps: Apps, widgets: Widgets) {
    val json = prefs.sp.getString("novaWidgets", null) ?: return
    prefs.sp.edit { remove("novaWidgets") }
    val providers = AppWidgetManager.getInstance(context).installedProviders
    val arr = JSONArray(json)
    val queue = (0 until arr.length()).mapNotNull { i ->
        val o = arr.getJSONObject(i)
        val cn = ComponentName.unflattenFromString(o.getString("provider")) ?: return@mapNotNull null
        val info = providers.firstOrNull { it.provider == cn && it.profile.hashCode() == o.getInt("profile") } ?: providers.firstOrNull { it.provider == cn }
            ?: return@mapNotNull null
        val page = o.getInt("page").coerceIn(apps.pages.indices)
        info to { apps.widgetSpot = page to Cell("", o.getInt("x"), o.getInt("y"), o.getInt("w"), o.getInt("h")) }
    }
    widgets.addAll(queue)
}
