package app.onlysearch

import app.onlysearch.home.iconShape
import app.onlysearch.search.anyDecimal
import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.*
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.graphics.Color
import androidx.core.content.edit
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

/** User settings. Compose-observable; each change is saved to the launcher prefs straight away. */
class Prefs(context: Context) {
    val sp: SharedPreferences = context.getSharedPreferences("launcher", Context.MODE_PRIVATE)
    // One copy for all the initial reads, after a layout from before the half-cell grid is converted to it.
    private val saved = sp.all.let { all ->
        fineGrid(all).also { fine -> if (fine != all) sp.edit(commit = true) { fine.forEach { (k, v) -> if (all[k] != v) put(k, v) } } }
    }

    private inner class P<T>(private val key: String, def: T, clamp: (T) -> T = { it }) : ReadWriteProperty<Any?, T> {
        @Suppress("UNCHECKED_CAST")
        private val state = mutableStateOf(clamp((saved[key] as? T) ?: def))
        override fun getValue(thisRef: Any?, property: KProperty<*>) = state.value
        override fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
            state.value = value
            sp.edit {
                when (value) {
                    is Boolean -> putBoolean(key, value)
                    is Int -> putInt(key, value)
                    is String -> putString(key, value)
                }
            }
        }
    }

    // Home screen
    /** Grid size in half cells; an app takes [AppSpan] × [AppSpan]. */
    var columns by P("set.columns", 4 * AppSpan) { it.coerceIn(3 * AppSpan, MaxColumns) }
    var rows by P("set.rows", 6 * AppSpan) { it.coerceIn(AppSpan, MaxRows) }
    var iconSize by P("set.iconSize", 100)
    var iconShape by P("set.iconShape", "squircle")
    var labels by P("set.labels", true)
    var lockLayout by P("set.lockLayout", false)
    var nextEvent by P("set.nextEvent", false)
    var todayCard by P("set.todayCard", false)

    // Search & calculator
    var calcMath by P("set.calcMath", true)
    var calcUnits by P("set.calcUnits", true)
    var calcCurrency by P("set.calcCurrency", true)
    var calcDates by P("set.calcDates", true)
    var calcFinance by P("set.calcFinance", true)
    var calcNatural by P("set.calcNatural", true)
    var computeTime by P("set.computeTime", false)
    var homeCurrency by P("set.homeCurrency", runCatching { java.util.Currency.getInstance(java.util.Locale.getDefault()).currencyCode }.getOrDefault("USD"))
    var unitSystem by P("set.unitSystem", when (java.util.Locale.getDefault().country) { "US", "LR", "MM" -> "customaryUS"; "GB" -> "imperial"; else -> "metric" })
    var enterCopies by P("set.enterCopies", "answer")
    var anyDecimal by P("set.anyDecimal", true)

    // Sources & ranking
    var srcContacts by P("set.srcContacts", true)
    var srcSettings by P("set.srcSettings", true)
    var srcWeb by P("set.srcWeb", true)
    var boostFrequent by P("set.boostFrequent", true)
    var fuzzy by P("set.fuzzy", true)
    var engine by P("set.engine", "Google")

    // Gestures: actions keyed by gesture ("gDown" → "search", "app:<key>", "shortcut:<key>")
    private val gestureMap = mutableStateMapOf<String, String>().apply {
        saved.forEach { (k, v) -> if (k.startsWith("set.g") && v is String) put(k.removePrefix("set."), v) }
    }
    fun gestureOf(key: String): String? = gestureMap[key]
    fun setGesture(key: String, action: String) { gestureMap[key] = action; sp.edit { putString("set.$key", action) } }
    var typeToSearch by P("set.typeToSearch", true)
    var resetQuery by P("set.resetQuery", false)
    var keepQueryMinutes by P("set.keepQueryMinutes", 5)

    // Dock & search bar
    var dockSize by P("set.dockSize", 4)
    var showDock by P("set.showDock", true)
    var searchBar by P("set.searchBar", "bottom")
    var mic by P("set.mic", true)

    // Look & feel
    /** "system", "light" or "dark". */
    var theme by P("set.theme", "system")
    var accent by P("set.accent", "sky")
    /** [accent] as a colour, parsed once per change. */
    val accentColor by derivedStateOf {
        accent.takeIf { it.startsWith("#") }?.let { runCatching { Color(android.graphics.Color.parseColor(it)) }.getOrNull() }
            ?: Accents.firstOrNull { it.first == accent }?.second ?: Accents[0].second
    }
    var black by P("set.black", false)
    var themedIcons by P("set.themedIcons", false)
    /** Package of the icon pack in use, or "" for the apps' own icons. */
    var iconPack by P("set.iconPack", "")
    var dim by P("set.dim", 0)

    // Calendars each built-in agenda widget leaves out, by widget key
    private val hiddenCals = mutableStateMapOf<String, Set<Long>>().apply {
        saved.forEach { (k, v) -> if (k.startsWith("cals:") && v is String) put(k.removePrefix("cals:"), v.split(",").mapNotNull { it.toLongOrNull() }.toSet()) }
    }
    fun hiddenCalendars(key: String) = hiddenCals[key].orEmpty()
    fun setHiddenCalendars(key: String, ids: Set<Long>) { hiddenCals[key] = ids; sp.edit { putString("cals:$key", ids.joinToString(",")) } }

    // Per Day widget, by widget key: hours shown before and after the current hour, and the hours of the day it skips
    private fun hourPairs(prefix: String) = mutableStateMapOf<String, Pair<Int, Int>>().apply {
        saved.forEach { (k, v) ->
            val (a, b) = (v as? String)?.split(",")?.mapNotNull { it.toIntOrNull() }?.takeIf { k.startsWith(prefix) && it.size == 2 } ?: return@forEach
            put(k.removePrefix(prefix), a to b)
        }
    }
    private val dayRanges = hourPairs("range:")
    private val dayTrims = hourPairs("trim:")
    fun dayRange(key: String) = dayRanges[key] ?: (4 to 20)
    fun setDayRange(key: String, r: Pair<Int, Int>) { dayRanges[key] = r; sp.edit { putString("range:$key", "${r.first},${r.second}") } }
    /** Hours of the day (from, until) the widget collapses when nothing's on, or null. */
    fun dayTrim(key: String) = dayTrims[key]
    fun setDayTrim(key: String, t: Pair<Int, Int>?) {
        if (t == null) { dayTrims.remove(key); sp.edit { remove("trim:$key") } }
        else { dayTrims[key] = t; sp.edit { putString("trim:$key", "${t.first},${t.second}") } }
    }

    // App drawer
    var drawerColumns by P("set.drawerColumns", 4)
    var suggested by P("set.suggested", true)
    var scrubber by P("set.scrubber", true)
}

lateinit var prefs: Prefs

/** Half cells an app takes each way: the home grid is twice as fine as its app cells, so widgets can move and size by half cells. */
internal const val AppSpan = 2
internal const val MaxColumns = 10 * AppSpan
internal const val MaxRows = 14 * AppSpan

/**
 * Layouts saved before every grid had half cells ("set.appSpan" missing or 1) become half-cell ones: grid size and every
 * home item ×2, so they look the same. "set.appSpan" = 2 marks the result; values already marked come back as they are.
 */
internal fun fineGrid(values: Map<String, Any?>): Map<String, Any?> {
    if (values["set.appSpan"] == AppSpan) return values
    val out = values.toMutableMap()
    out["set.columns"] = ((values["set.columns"] as? Int) ?: 4) * AppSpan
    out["set.rows"] = ((values["set.rows"] as? Int) ?: 6) * AppSpan
    (values["pages"] as? String)?.let { json ->
        val pages = org.json.JSONArray(json)
        for (i in 0 until pages.length()) {
            val items = pages.getJSONObject(i).getJSONArray("items")
            for (j in 0 until items.length()) items.getJSONObject(j).let { o -> listOf("x", "y", "w", "h").forEach { o.put(it, o.getInt(it) * AppSpan) } }
        }
        out["pages"] = pages.toString()
    }
    out["set.appSpan"] = AppSpan
    return out
}

private fun SharedPreferences.Editor.put(k: String, v: Any?) {
    when (v) {
        is String -> putString(k, v)
        is Int -> putInt(k, v)
        is Long -> putLong(k, v)
        is Boolean -> putBoolean(k, v)
        is Float -> putFloat(k, v)
    }
}

/** Every launcher pref as typed JSON ({"key": {"t": "s|i|l|b|f", "v": …}}), minus cached currency rates. */
internal fun backupJson(): String = backupJson(prefs.sp.all)

internal fun backupJson(all: Map<String, *>): String {
    val out = org.json.JSONObject().put("app", "only-search").put("version", 1)
    val data = org.json.JSONObject()
    all.forEach { (k, v) ->
        if (k in setOf("rates", "ratesAt", "pendingWidget", "widgetSpot")) return@forEach
        val t = when (v) { is String -> "s"; is Int -> "i"; is Long -> "l"; is Boolean -> "b"; is Float -> "f"; else -> return@forEach }
        data.put(k, org.json.JSONObject().put("t", t).put("v", v))
    }
    return out.put("prefs", data).toString(2)
}

/** Replaces all launcher prefs with the backup's (an older one's layout moved to half cells). Throws on a file that isn't one of ours. */
internal fun restoreJson(json: String) {
    val values = fineGrid(parseBackup(json))
    prefs.sp.edit(commit = true) {
        clear()
        values.forEach { (k, v) -> put(k, v) }
    }
}

/** The typed values of a backup; validates the file (and the layout prefs) before anything is written. */
internal fun parseBackup(json: String): Map<String, Any> {
    val root = org.json.JSONObject(json)
    require(root.optString("app") == "only-search") { "Not an Only Search backup" }
    val data = root.getJSONObject("prefs")
    val entries = data.keys().asSequence().associateWith { data.getJSONObject(it) }
    val values = entries.mapValues { (k, e) ->
        val t = e.getString("t")
        backupType(k)?.let { require(it == t) { "Wrong type for $k" } }
        when (t) {
            "s" -> e.getString("v")
            "i" -> e.getInt("v")
            "l" -> e.getLong("v")
            "b" -> e.getBoolean("v")
            "f" -> e.getDouble("v").toFloat()
            else -> throw IllegalArgumentException("Unknown value type")
        }
    }
    backupRanges.forEach { (k, r) -> (values[k] as? Int)?.let { require(it in r) { "$k out of range" } } }
    (values["pages"] as? String)?.let(::parsePages)
    (values["folders"] as? String)?.let(::parseFolders)
    (values["groups"] as? String)?.let(::parseGroups)
    return values
}

/** Ranges Settings allows for the numbers the launcher uses unclamped (columns and rows are clamped on load). */
private val backupRanges = mapOf("set.iconSize" to 70..130, "set.keepQueryMinutes" to 1..120, "set.dockSize" to 3..6, "set.dim" to 0..60, "set.drawerColumns" to 3..6)
private val backupInts = backupRanges.keys + setOf("set.columns", "set.rows", "set.appSpan", "defaultPage")
private val backupBools = ("labels lockLayout nextEvent todayCard calcMath calcUnits calcCurrency calcDates calcFinance calcNatural computeTime anyDecimal " +
    "srcContacts srcSettings srcWeb boostFrequent fuzzy typeToSearch resetQuery showDock mic black themedIcons suggested scrubber").split(" ").map { "set.$it" } +
    listOf("seeded", "builtins")
private val backupStrings = "iconShape homeCurrency unitSystem enterCopies engine searchBar theme accent iconPack".split(" ").map { "set.$it" } +
    listOf("pages", "folders", "groups", "dock", "history", "recent", "hidden", "home", "widgets", "novaWidgets")

/** The type tag the launcher reads [key] as, or null for keys it doesn't know (kept as they are). */
private fun backupType(key: String) = when {
    key in backupInts || key.startsWith("count:") -> "i"
    key == "lastBackup" || key.startsWith("last:") -> "l"
    key in backupBools || key.startsWith("asked.") -> "b"
    key in backupStrings || listOf("set.g", "label:", "cals:", "range:", "trim:").any(key::startsWith) -> "s"
    else -> null
}
