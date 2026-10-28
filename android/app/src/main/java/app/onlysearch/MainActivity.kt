package app.onlysearch

import app.onlysearch.imports.bindNovaWidgets
import app.onlysearch.search.Soulver
import app.onlysearch.widgets.Widgets
import app.onlysearch.widgets.cells
import android.content.Intent
import android.content.res.Configuration
import android.content.pm.LauncherApps
import android.os.Bundle
import android.os.SystemClock
import android.os.UserHandle
import android.graphics.Color
import android.widget.Toast
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private lateinit var apps: Apps
    lateinit var widgets: Widgets // public for the screenshot test
        private set
    /** Home intents: a count, and whether the launcher was already in front (Home pressed on home, not returning from an app). */
    private val home = mutableStateOf(0 to false)
    private var startedAt = 0L
    private val stopTick = mutableIntStateOf(0)
    private val resumeTick = mutableIntStateOf(0)
    private val typed = androidx.compose.runtime.mutableStateOf(0 to "")
    private var awaitingWidget = false

    private val packageCallback = object : LauncherApps.Callback() {
        private fun reload(vararg pkgs: String) { lifecycleScope.launch { apps.load(pkgs.toSet()) } }
        override fun onPackageRemoved(packageName: String, user: UserHandle) = reload(packageName)
        override fun onPackageAdded(packageName: String, user: UserHandle) = reload(packageName)
        override fun onPackageChanged(packageName: String, user: UserHandle) = reload(packageName)
        override fun onPackagesAvailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) = reload(*packageNames)
        override fun onPackagesUnavailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) = reload(*packageNames)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Light status/nav icons over the wallpaper; Launcher darkens them over light theme sheets.
        enableEdgeToEdge(SystemBarStyle.dark(Color.TRANSPARENT), SystemBarStyle.dark(Color.TRANSPARENT))
        prefs = Prefs(this)
        systemDark = isNight(resources.configuration)
        apps = Apps(this)
        widgets = Widgets(this)
        widgets.onBound = { id ->
            val (w, h) = widgets.manager.getAppWidgetInfo(id)?.let { cells(it, resources.displayMetrics.density) } ?: (2 to 2)
            if (!apps.addWidget(id, w, h)) {
                widgets.remove(id)
                Toast.makeText(this, "No room for this widget on the page", Toast.LENGTH_SHORT).show()
            }
        }
        awaitingWidget = widgets.pending != -1
        apps.pruneWidgets { widgets.manager.getAppWidgetInfo(it) != null }
        // Ids no page uses any more (a restore or Nova import replaced the layout) are released.
        val used = apps.pages.flatMap { p -> p.items.mapNotNull { it.widgetId } }.toSet()
        widgets.host.appWidgetIds.filter { it !in used && it != widgets.pending }.forEach(widgets::remove)
        bindNovaWidgets(this, apps, widgets)
        lifecycleScope.launch { apps.load() }
        lifecycleScope.launch {
            Soulver.init(applicationContext)
            Soulver.setUnitSystem(prefs.unitSystem)
        }
        getSystemService(LauncherApps::class.java).registerCallback(packageCallback)
        // "query" extra opens search pre-filled; used by the CI screenshot step and handy for testing.
        val query = intent.getStringExtra("query")
        setContent {
            MaterialTheme(colorScheme = if (isLight) lightColorScheme() else darkColorScheme()) {
                Launcher(apps, widgets, home.value, stopTick.intValue, resumeTick.intValue, query, typed.value)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Like Launcher3: focused and not brought back from an app. On some devices the swipe-home animation hands focus
        // to the launcher early, so a launcher started a moment ago doesn't count either.
        // ponytail: 500 ms is a guess at the transition length; raise it if a home gesture still triggers the action.
        val inFront = hasWindowFocus() && intent.flags and Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT == 0 && SystemClock.uptimeMillis() - startedAt > 500
        if (intent.hasCategory(Intent.CATEGORY_HOME)) home.value = home.value.first + 1 to inFront
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        systemDark = isNight(newConfig)
    }

    private fun isNight(c: Configuration) = (c.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

    override fun onStart() { super.onStart(); startedAt = SystemClock.uptimeMillis(); widgets.host.startListening() }
    override fun onStop() { super.onStop(); runCatching { widgets.host.stopListening() }; stopTick.intValue++ }
    override fun onResume() {
        super.onResume(); resumeTick.intValue++
        lifecycleScope.launch { Soulver.refreshRates() }
    }

    // "Type anywhere to search": a printable key that nothing on screen took opens search with it.
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        val c = event.unicodeChar
        if (prefs.typeToSearch && c > 0 && !Character.isISOControl(c) && !event.isCtrlPressed) {
            typed.value = typed.value.first + 1 to String(Character.toChars(c)); return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onDestroy() {
        getSystemService(LauncherApps::class.java).unregisterCallback(packageCallback)
        super.onDestroy()
    }

    @Deprecated("Widget bind/configure flows still report through onActivityResult")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION") super.onActivityResult(requestCode, resultCode, data)
        widgetResult(requestCode, resultCode)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        // Back on top with no result for an add from before the process died (e.g. force-stopped): it's dropped.
        if (hasFocus && awaitingWidget) { awaitingWidget = false; widgetResult(Widgets.REQ_CONFIGURE, RESULT_CANCELED) }
    }

    private fun widgetResult(requestCode: Int, resultCode: Int) {
        // Relaunched after the process died while the widget's setup was open, we get a cancel first and the real result later.
        if (awaitingWidget && resultCode != RESULT_OK) return
        awaitingWidget = false
        if (resultCode != RESULT_OK) apps.widgetSpot = null
        widgets.onResult(requestCode, resultCode)
    }
}
