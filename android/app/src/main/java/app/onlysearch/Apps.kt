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

data class Shortcut(val label: String, val app: App, val info: ShortcutInfo)

/** Installed apps, icons, and the user's pins / launch counts. All state is Compose-observable. */
class Apps(private val context: Context) {
    private val launcher = context.getSystemService(LauncherApps::class.java)
    private val prefs = context.getSharedPreferences("launcher", Context.MODE_PRIVATE)

    val all = mutableStateListOf<App>()
    val icons = mutableStateMapOf<String, ImageBitmap>()
    val home = mutableStateListOf<String>().apply { addAll(prefs.list("home")) }
    val dock = mutableStateListOf<String>().apply { addAll(prefs.list("dock")) }
    val history = mutableStateListOf<Pair<String, String>>().apply {
        addAll(prefs.list("history").mapNotNull { it.split('\t').takeIf { p -> p.size == 2 }?.let { p -> p[0] to p[1] } })
    }
    val recent = mutableStateListOf<String>().apply { addAll(prefs.list("recent")) }
    private val counts = mutableStateMapOf<String, Int>()
    val loaded = mutableStateOf(false)

    fun byKey(key: String) = all.firstOrNull { it.key == key }

    suspend fun load() {
        val (apps, newIcons) = withContext(Dispatchers.Default) {
            val users = context.getSystemService(UserManager::class.java).userProfiles
            val apps = users.flatMap { user ->
                launcher.getActivityList(null, user).map { App(it.label.toString(), it.componentName, user) to it }
            }.filter { it.first.component.packageName != context.packageName }
                .sortedBy { it.first.label.lowercase() }
            val size = (56 * context.resources.displayMetrics.density).toInt()
            apps to apps.filter { it.first.key !in icons }.associate { (app, info) -> app.key to squareIcon(info.getIcon(0), size) }
        }
        icons.putAll(newIcons)
        prefs.all.forEach { (k, v) -> if (k.startsWith("count:") && v is Int) counts[k.removePrefix("count:")] = v }
        all.clear(); all.addAll(apps.map { it.first })
        if (dock.isEmpty() && home.isEmpty() && !prefs.getBoolean("seeded", false)) seedDefaults()
        loaded.value = true
    }

    fun frequent(n: Int) = all.filter { (counts[it.key] ?: 0) > 0 }.sortedByDescending { counts[it.key] }.take(n)

    /** True between launching an app and coming back home; drives the return animation. */
    var returningFromApp = false

    fun launch(app: App, from: LaunchSource? = null) {
        counts[app.key] = (counts[app.key] ?: 0) + 1
        recent.remove(app.key); recent.add(0, app.key)
        while (recent.size > 5) recent.removeAt(recent.lastIndex)
        prefs.edit { putInt("count:${app.key}", counts[app.key]!!); putString("recent", recent.joinToString("\n")) }
        returningFromApp = true
        launcher.startMainActivity(app.component, app.user, from?.bounds, from?.options())
    }

    fun toggle(list: MutableList<String>, name: String, app: App, max: Int) {
        if (!list.remove(app.key) && list.size < max) list.add(app.key)
        prefs.edit { putString(name, list.joinToString("\n")) }
    }
    fun toggleHome(app: App) = toggle(home, "home", app, 8)
    fun toggleDock(app: App) = toggle(dock, "dock", app, 4)

    fun remember(expression: String, result: String) {
        history.removeAll { it.first == expression }
        history.add(0, expression to result)
        while (history.size > 20) history.removeAt(history.lastIndex)
        prefs.edit { putString("history", history.joinToString("\n") { "${it.first}\t${it.second}" }) }
    }

    fun clearHistory() { history.clear(); prefs.edit { remove("history") } }

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
        prefs.edit { putString("dock", dock.joinToString("\n")); putBoolean("seeded", true) }
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
