package app.onlysearch

import android.content.Intent
import android.content.pm.LauncherApps
import android.os.Bundle
import android.os.UserHandle
import android.graphics.Color
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.mutableIntStateOf
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private lateinit var apps: Apps
    lateinit var widgets: Widgets // public for the screenshot test
        private set
    private val homeTick = mutableIntStateOf(0)
    private val stopTick = mutableIntStateOf(0)
    private val resumeTick = mutableIntStateOf(0)

    private val packageCallback = object : LauncherApps.Callback() {
        private fun reload() { lifecycleScope.launch { apps.load() } }
        override fun onPackageRemoved(packageName: String, user: UserHandle) = reload()
        override fun onPackageAdded(packageName: String, user: UserHandle) = reload()
        override fun onPackageChanged(packageName: String, user: UserHandle) = reload()
        override fun onPackagesAvailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) = reload()
        override fun onPackagesUnavailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) = reload()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Light status/nav icons always: every screen is dark, whatever the system theme.
        enableEdgeToEdge(SystemBarStyle.dark(Color.TRANSPARENT), SystemBarStyle.dark(Color.TRANSPARENT))
        prefs = Prefs(this)
        apps = Apps(this)
        widgets = Widgets(this)
        widgets.onBound = { id ->
            val (w, h) = widgets.manager.getAppWidgetInfo(id)?.let { cells(it, resources.displayMetrics.density) } ?: (2 to 2)
            if (!apps.addWidget(id, w, h)) {
                widgets.remove(id)
                Toast.makeText(this, "No room for this widget on the page", Toast.LENGTH_SHORT).show()
            }
        }
        apps.pruneWidgets { widgets.manager.getAppWidgetInfo(it) != null }
        lifecycleScope.launch { apps.load() }
        lifecycleScope.launch { Soulver.init(applicationContext) }
        getSystemService(LauncherApps::class.java).registerCallback(packageCallback)
        // "query" extra opens search pre-filled; used by the CI screenshot step and handy for testing.
        val query = intent.getStringExtra("query")
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Launcher(apps, widgets, homeTick.intValue, stopTick.intValue, resumeTick.intValue, query)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.hasCategory(Intent.CATEGORY_HOME)) homeTick.intValue++
    }

    override fun onStart() { super.onStart(); widgets.host.startListening() }
    override fun onStop() { super.onStop(); runCatching { widgets.host.stopListening() }; stopTick.intValue++ }
    override fun onResume() { super.onResume(); resumeTick.intValue++ }

    override fun onDestroy() {
        getSystemService(LauncherApps::class.java).unregisterCallback(packageCallback)
        super.onDestroy()
    }

    @Deprecated("Widget bind/configure flows still report through onActivityResult")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION") super.onActivityResult(requestCode, resultCode, data)
        widgets.onResult(requestCode, resultCode)
    }
}
