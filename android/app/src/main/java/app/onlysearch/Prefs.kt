package app.onlysearch

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

    private inner class P<T>(private val key: String, def: T, clamp: (T) -> T = { it }) : ReadWriteProperty<Any?, T> {
        @Suppress("UNCHECKED_CAST")
        private val state = mutableStateOf(clamp((sp.all[key] as? T) ?: def))
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
    var columns by P("set.columns", 4) { it.coerceIn(3, MaxColumns) }
    var rows by P("set.rows", 6) { it.coerceIn(1, MaxRows) }
    var iconSize by P("set.iconSize", 100)
    var iconShape by P("set.iconShape", "squircle")
    var labels by P("set.labels", true)
    var lockLayout by P("set.lockLayout", false)
    /** Cells an app takes each way; 2 on a grid imported from Nova's half-cell layout. */
    var appSpan by P("set.appSpan", 1)
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

    // Sources & ranking
    var srcContacts by P("set.srcContacts", true)
    var srcSettings by P("set.srcSettings", true)
    var srcWeb by P("set.srcWeb", true)
    var boostFrequent by P("set.boostFrequent", true)
    var fuzzy by P("set.fuzzy", true)
    var engine by P("set.engine", "Google")

    // Gestures: actions keyed by gesture ("gDown" → "search", "app:<key>", "shortcut:<key>")
    private val gestureMap = mutableStateMapOf<String, String>().apply {
        sp.all.forEach { (k, v) -> if (k.startsWith("set.g") && v is String) put(k.removePrefix("set."), v) }
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
        sp.all.forEach { (k, v) -> if (k.startsWith("cals:") && v is String) put(k.removePrefix("cals:"), v.split(",").mapNotNull { it.toLongOrNull() }.toSet()) }
    }
    fun hiddenCalendars(key: String) = hiddenCals[key].orEmpty()
    fun setHiddenCalendars(key: String, ids: Set<Long>) { hiddenCals[key] = ids; sp.edit { putString("cals:$key", ids.joinToString(",")) } }

    // Per Day widget, by widget key: hours shown before and after the current hour, and the hours of the day it skips
    private fun hourPairs(prefix: String) = mutableStateMapOf<String, Pair<Int, Int>>().apply {
        sp.all.forEach { (k, v) ->
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

// The Nova import can need a 10 × 14 grid (Nova's 5 × 7 with half cells).
internal const val MaxColumns = 10
internal const val MaxRows = 14

/** Every launcher pref as typed JSON ({"key": {"t": "s|i|l|b|f", "v": …}}), minus cached currency rates. */
internal fun backupJson(): String = backupJson(prefs.sp.all)

internal fun backupJson(all: Map<String, *>): String {
    val out = org.json.JSONObject().put("app", "only-search").put("version", 1)
    val data = org.json.JSONObject()
    all.forEach { (k, v) ->
        if (k == "rates" || k == "ratesAt") return@forEach
        val t = when (v) { is String -> "s"; is Int -> "i"; is Long -> "l"; is Boolean -> "b"; is Float -> "f"; else -> return@forEach }
        data.put(k, org.json.JSONObject().put("t", t).put("v", v))
    }
    return out.put("prefs", data).toString(2)
}

/** Replaces all launcher prefs with the backup's. Throws on a file that isn't one of ours. */
internal fun restoreJson(json: String) {
    val values = parseBackup(json)
    prefs.sp.edit(commit = true) {
        clear()
        values.forEach { (k, v) ->
            when (v) {
                is String -> putString(k, v)
                is Int -> putInt(k, v)
                is Long -> putLong(k, v)
                is Boolean -> putBoolean(k, v)
                is Float -> putFloat(k, v)
            }
        }
    }
}

/** The typed values of a backup; validates the file (and the layout prefs) before anything is written. */
internal fun parseBackup(json: String): Map<String, Any> {
    val root = org.json.JSONObject(json)
    require(root.optString("app") == "only-search") { "Not an Only Search backup" }
    val data = root.getJSONObject("prefs")
    val entries = data.keys().asSequence().associateWith { data.getJSONObject(it) }
    val values = entries.mapValues { (_, e) ->
        when (e.getString("t")) {
            "s" -> e.getString("v")
            "i" -> e.getInt("v")
            "l" -> e.getLong("v")
            "b" -> e.getBoolean("v")
            "f" -> e.getDouble("v").toFloat()
            else -> throw IllegalArgumentException("Unknown value type")
        }
    }
    (values["pages"] as? String)?.let(::parsePages)
    (values["folders"] as? String)?.let(::parseFolders)
    (values["groups"] as? String)?.let(::parseGroups)
    return values
}
