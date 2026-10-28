package app.onlysearch.search

import app.onlysearch.match
import app.onlysearch.prefs
import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.provider.Settings
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.content.edit

/** A system settings screen findable from search. [page] set = one of the launcher's own settings pages instead. */
data class SettingsEntry(val label: String, val where: String, val action: String, val keywords: String = "", val page: String? = null)

val SystemSettings = listOf(
    SettingsEntry("Wi‑Fi", "Network & internet", Settings.ACTION_WIFI_SETTINGS, "wifi wireless internet network"),
    SettingsEntry("Mobile network", "Network & internet", Settings.ACTION_DATA_ROAMING_SETTINGS, "cellular data roaming sim"),
    SettingsEntry("Data usage", "Network & internet", Settings.ACTION_DATA_USAGE_SETTINGS, "data saver"),
    SettingsEntry("Airplane mode", "Network & internet", Settings.ACTION_AIRPLANE_MODE_SETTINGS, "flight"),
    SettingsEntry("Hotspot & tethering", "Network & internet", Settings.ACTION_WIRELESS_SETTINGS, "hotspot tether"),
    SettingsEntry("VPN", "Network & internet", Settings.ACTION_VPN_SETTINGS),
    SettingsEntry("Bluetooth", "Connected devices", Settings.ACTION_BLUETOOTH_SETTINGS, "pair headphones"),
    SettingsEntry("Cast screen", "Connected devices", Settings.ACTION_CAST_SETTINGS, "chromecast mirror screen"),
    SettingsEntry("NFC", "Connected devices", Settings.ACTION_NFC_SETTINGS, "tap to pay"),
    SettingsEntry("Display", "Settings", Settings.ACTION_DISPLAY_SETTINGS, "brightness screen timeout font size"),
    SettingsEntry("Dark theme", "Display", "android.settings.DARK_THEME_SETTINGS", "night mode"),
    SettingsEntry("Sound & vibration", "Settings", Settings.ACTION_SOUND_SETTINGS, "volume ringtone vibrate"),
    SettingsEntry("Do Not Disturb", "Sound", "android.settings.ZEN_MODE_SETTINGS", "dnd silence"),
    SettingsEntry("Notifications", "Settings", "android.settings.NOTIFICATION_SETTINGS"),
    SettingsEntry("Battery", "Settings", Intent.ACTION_POWER_USAGE_SUMMARY, "power usage"),
    SettingsEntry("Battery saver", "Battery", Settings.ACTION_BATTERY_SAVER_SETTINGS, "power saving"),
    SettingsEntry("Storage", "Settings", Settings.ACTION_INTERNAL_STORAGE_SETTINGS, "space free up"),
    SettingsEntry("Apps", "Settings", Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS, "applications installed"),
    SettingsEntry("Default apps", "Apps", Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS, "browser sms phone"),
    SettingsEntry("Default home app", "Apps", Settings.ACTION_HOME_SETTINGS, "launcher"),
    SettingsEntry("Location", "Settings", Settings.ACTION_LOCATION_SOURCE_SETTINGS, "gps"),
    SettingsEntry("Security", "Settings", Settings.ACTION_SECURITY_SETTINGS, "screen lock fingerprint face unlock"),
    SettingsEntry("Privacy", "Settings", Settings.ACTION_PRIVACY_SETTINGS, "permissions"),
    SettingsEntry("Accessibility", "Settings", Settings.ACTION_ACCESSIBILITY_SETTINGS, "talkback magnification"),
    SettingsEntry("Accounts", "Settings", Settings.ACTION_SYNC_SETTINGS, "sync google"),
    SettingsEntry("Languages", "System", Settings.ACTION_LOCALE_SETTINGS, "language locale"),
    SettingsEntry("Keyboard", "System", Settings.ACTION_INPUT_METHOD_SETTINGS, "input gboard"),
    SettingsEntry("Date & time", "System", Settings.ACTION_DATE_SETTINGS, "clock time zone"),
    SettingsEntry("Developer options", "System", Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS, "usb debugging"),
    SettingsEntry("About phone", "Settings", Settings.ACTION_DEVICE_INFO_SETTINGS, "model android version build"),
    SettingsEntry("Wallpaper", "Display", Intent.ACTION_SET_WALLPAPER, "background"),
    // The launcher's own pages, under "Launcher".
    SettingsEntry("Launcher settings", "Launcher", "", "only search", page = "hub"),
    SettingsEntry("Home screen", "Launcher", "", "grid columns rows icon size shape labels", page = "home"),
    SettingsEntry("Search & calculator", "Launcher", "", "currency units math", page = "search"),
    SettingsEntry("Sources & ranking", "Launcher", "", "contacts web results", page = "sources"),
    SettingsEntry("App drawer & folders", "Launcher", "", "groups hidden apps", page = "drawer"),
    SettingsEntry("Dock & search bar", "Launcher", "", "dock", page = "dock"),
    SettingsEntry("Gestures", "Launcher", "", "swipe double tap", page = "gestures"),
    SettingsEntry("Look & feel", "Launcher", "", "accent theme icons", page = "look"),
    SettingsEntry("Backup & restore", "Launcher", "", "export import", page = "backup"),
)

/** Best settings matches for [query]: label matches first, then keyword hits. */
fun searchSettings(query: String, fuzzy: Boolean): List<Pair<SettingsEntry, IntRange?>> {
    val q = query.trim()
    if (q.length < 2) return emptyList()
    return SystemSettings.mapNotNull { e ->
        val m = match(e.label, q, fuzzy)
        when {
            m != null -> Triple(e, m.first, m.second)
            e.keywords.split(' ').any { it.startsWith(q, ignoreCase = true) } -> Triple(e, 20, null)
            else -> null
        }
    }.sortedByDescending { it.second }.take(3).map { it.first to it.third }
}

data class Person(val id: Long, val name: String, val label: String, val number: String, val photo: ImageBitmap?)

fun hasContacts(context: Context) = context.checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED

/** Asks for [permission]; once it is permanently denied the system shows no dialog, so open the app's settings instead. */
fun askPermission(context: Context, permission: String, ask: ManagedActivityResultLauncher<String, Boolean>) {
    val key = "asked.$permission"
    if (prefs.sp.getBoolean(key, false) && !(context as Activity).shouldShowRequestPermissionRationale(permission)) {
        context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)))
    } else {
        prefs.sp.edit { putBoolean(key, true) }
        ask.launch(permission)
    }
}

/** Contacts with a phone number whose name matches [query]. Blocking; call off the main thread. */
fun searchContacts(context: Context, query: String): List<Person> {
    if (query.isBlank() || !hasContacts(context)) return emptyList()
    val uri = Uri.withAppendedPath(Phone.CONTENT_FILTER_URI, Uri.encode(query.trim()))
    val cols = arrayOf(Phone.CONTACT_ID, Phone.DISPLAY_NAME, Phone.NUMBER, Phone.TYPE, Phone.LABEL, Phone.PHOTO_THUMBNAIL_URI)
    val people = linkedMapOf<Long, Person>()
    runCatching {
        context.contentResolver.query(uri, cols, null, null, null)?.use { c ->
            while (c.moveToNext() && people.size < 3) {
                val id = c.getLong(0)
                if (id in people) continue
                val label = Phone.getTypeLabel(context.resources, c.getInt(3), c.getString(4)).toString()
                val photo = c.getString(5)?.let { p ->
                    runCatching { context.contentResolver.openInputStream(Uri.parse(p))?.use { BitmapFactory.decodeStream(it)?.asImageBitmap() } }.getOrNull()
                }
                people[id] = Person(id, c.getString(1).orEmpty(), label, c.getString(2).orEmpty(), photo)
            }
        }
    }
    return people.values.toList()
}

fun openContact(context: Context, p: Person) = context.startActivity(
    Intent(Intent.ACTION_VIEW, Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_URI, p.id.toString())).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
)

fun webSearchUrl(query: String): String = when (prefs.engine) {
    "DuckDuckGo" -> "https://duckduckgo.com/?q="
    "Bing" -> "https://www.bing.com/search?q="
    "Startpage" -> "https://www.startpage.com/do/search?q="
    else -> "https://www.google.com/search?q="
} + Uri.encode(query.trim())

/** "just now", "5 min ago", "2 h ago", "yesterday", "3 days ago". */
fun ago(millis: Long, now: Long = System.currentTimeMillis()): String {
    val m = (now - millis) / 60_000
    return when {
        m < 1 -> "just now"
        m < 60 -> "$m min ago"
        m < 24 * 60 -> "${m / 60} h ago"
        m < 48 * 60 -> "yesterday"
        else -> "${m / (24 * 60)} days ago"
    }
}


private val CurrencyRx = Regex("""[$€£¥₹₽₩₺₴₸₾₪฿]|\b(usd|eur|gbp|jpy|chf|cad|aud|cny|inr|rub|sek|nok|dkk|pln|czk|try|brl|mxn|krw|sgd|hkd|nzd|zar|uah|btc|eth|sol|xrp|doge|dollars?|euros?|pounds?|yen|rubles?|bitcoins?)\b""", RegexOption.IGNORE_CASE)
private val DateRx = Regex("""\b(today|tomorrow|yesterday|now|time|am|pm|days?|weeks?|months?|years?|hours?|minutes?|seconds?|mins?|hrs?|h|m|monday|tuesday|wednesday|thursday|friday|saturday|sunday|jan(uary)?|feb(ruary)?|mar(ch)?|apr(il)?|may|june?|july?|aug(ust)?|sep(tember)?|oct(ober)?|nov(ember)?|dec(ember)?|utc|gmt|est|pst|cet)\b|\d:\d\d""", RegexOption.IGNORE_CASE)
private val FinanceRx = Regex("""%|\b(tip|interest|compound(ed)?|tax|discount|off|percent|loan|mortgage|apr|roi)\b""", RegexOption.IGNORE_CASE)
private val UnitRx = Regex("""\d\s*(km|m|cm|mm|mi|miles?|ft|feet|foot|in|inch(es)?|yd|yards?|kg|g|mg|lb|lbs|oz|ounces?|stone|st|l|ml|liters?|litres?|gal|gallons?|cups?|tbsp|tsp|mph|kph|km/h|kb|mb|gb|tb|°|c|f|celsius|fahrenheit|kelvin|kw|w|kwh|hz|ghz|pa|psi|bar|acres?|ha)\b|\b(in|to)\s+(km|m|cm|mi|miles|ft|feet|inches|kg|g|lb|lbs|oz|liters|gallons|c|f|celsius|fahrenheit)\b""", RegexOption.IGNORE_CASE)
private val WordRx = Regex("""[a-z]{3,}""", RegexOption.IGNORE_CASE)
private val MathWords = setOf("sqrt", "sin", "cos", "tan", "log", "ln", "abs", "round", "floor", "ceil", "pi", "mod", "and", "or", "xor", "of", "in", "to", "as", "sum", "avg", "average", "min", "max")

/** Which kinds of calculation a query looks like; used to honour the "Understand queries" switches. */
fun calcKinds(q: String): Set<String> {
    val kinds = mutableSetOf<String>()
    if (CurrencyRx.containsMatchIn(q)) kinds += "currency"
    if (DateRx.containsMatchIn(q)) kinds += "dates"
    if (FinanceRx.containsMatchIn(q)) kinds += "finance"
    if (UnitRx.containsMatchIn(q)) kinds += "units"
    // Words that none of the patterns above know are natural language ("$10 for lunch").
    val known = listOf(CurrencyRx, DateRx, FinanceRx, UnitRx)
    // Capitalised words are usually places ("time in Tokyo"), not prose.
    if (WordRx.findAll(q).any { w ->
            !w.value[0].isUpperCase() && w.value.lowercase() !in MathWords && known.none { it.containsMatchIn(w.value) || it.containsMatchIn("1${w.value}") }
        }) kinds += "natural"
    if (kinds.isEmpty()) kinds += "math"
    return kinds
}

fun calcAllowed(q: String): Boolean = calcKinds(q).all {
    when (it) {
        "currency" -> prefs.calcCurrency
        "dates" -> prefs.calcDates
        "finance" -> prefs.calcFinance
        "units" -> prefs.calcUnits
        "natural" -> prefs.calcNatural
        else -> prefs.calcMath
    }
}

private val TargetRx = Regex("""\b(in|to|as|into)\s+[\p{L}$€£¥°]""", RegexOption.IGNORE_CASE)
private val UnitTokenRx = Regex("""\d\s*(km/h|kph|mph|km|cm|mm|m|mi|miles?|ft|feet|foot|in|inch(es)?|yd|yards?|kg|g|lbs?|oz|ounces?|st|stones?|l|liters?|litres?|gal|gallons?|°?c|°?f|celsius|fahrenheit)\b""", RegexOption.IGNORE_CASE)
private val Metric = mapOf("km" to "mi", "m" to "ft", "cm" to "inches", "mm" to "inches", "kg" to "lb", "g" to "oz", "l" to "gal", "c" to "fahrenheit", "km/h" to "mph")
private val Imperial = mapOf("mi" to "km", "ft" to "m", "in" to "cm", "yd" to "m", "lb" to "kg", "oz" to "g", "st" to "kg", "gal" to "L", "f" to "celsius", "mph" to "km/h")
private val Aliases = mapOf("kph" to "km/h", "miles" to "mi", "mile" to "mi", "feet" to "ft", "foot" to "ft", "inch" to "in", "inches" to "in", "yard" to "yd", "yards" to "yd",
    "lbs" to "lb", "ounce" to "oz", "ounces" to "oz", "stone" to "st", "stones" to "st", "liter" to "l", "liters" to "l", "litre" to "l", "litres" to "l",
    "gallon" to "gal", "gallons" to "gal", "°c" to "c", "celsius" to "c", "°f" to "f", "fahrenheit" to "f")

// A clock time followed by a place or zone name: "14:00 Moscow", "2pm London", "9:30 PST", "14:00 Moscow time".
private val PlaceTimeRx = Regex("""^(\d{1,2}:\d\d\s*([ap]m)?|\d{1,2}\s*[ap]m)\s+(?!(\p{L}+day|tomorrow|now|am|pm)\b)\p{L}[\p{L} .'-]*$""", RegexOption.IGNORE_CASE)

/**
 * [q] converted to the home currency, unit system or time zone ("12 USD" → "12 USD in EUR", "5 ft" → "5 ft in m" when metric,
 * "14:00 Moscow" → "14:00 Moscow in UTC+2:00:00"), or null when it names its own target or has nothing to convert.
 * Units follow the first one in the query. Zone names and IANA ids are unreliable targets in SoulverCore, so the zone is given as a UTC offset.
 */
fun homeQuery(q: String, currency: String, system: String, zone: java.time.ZoneId = java.time.ZoneId.systemDefault()): String? {
    if (TargetRx.containsMatchIn(q)) return null
    if (PlaceTimeRx.matches(q)) {
        val minutes = zone.rules.getOffset(java.time.Instant.now()).totalSeconds / 60
        return "$q in UTC%s%d:%02d:00".format(if (minutes < 0) "-" else "+", Math.abs(minutes) / 60, Math.abs(minutes) % 60)
    }
    if (CurrencyRx.containsMatchIn(q)) return "$q in $currency"
    // "4h 10m" is a duration, not metres.
    if (DateRx.containsMatchIn(q.replace(Regex("""\d\s*m\b"""), ""))) return null
    val unit = UnitTokenRx.find(q)?.groupValues?.get(1)?.lowercase()?.let { Aliases[it] ?: it } ?: return null
    return (if (system == "metric") Imperial[unit] else Metric[unit])?.let { "$q in $it" }
}

/** "12.5" and "12,5" both as a decimal number, written with the locale's decimal point. "26.10.2026" or "1,000,000" stay as they are. */
fun anyDecimal(q: String, point: Char = java.text.DecimalFormatSymbols.getInstance().decimalSeparator): String =
    Regex("""(?<![\d.,])(\d+)[.,](\d+)(?![.,]?\d)""").replace(q) { "${it.groupValues[1]}$point${it.groupValues[2]}" }
