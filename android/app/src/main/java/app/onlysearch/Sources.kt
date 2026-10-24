package app.onlysearch

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.provider.Settings
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap

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

