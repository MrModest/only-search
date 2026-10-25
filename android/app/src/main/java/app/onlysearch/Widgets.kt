package app.onlysearch

import android.app.Activity
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Intent

/** Hosts third-party home-screen widgets. Bind/configure results arrive via [onResult]; bound ids go to [onBound]. */
class Widgets(private val activity: Activity) {
    val host = AppWidgetHost(activity, 1024)
    val manager: AppWidgetManager = AppWidgetManager.getInstance(activity)
    var onBound: (Int) -> Unit = {}
    private var pending = -1
    /** Widgets waiting to be added one after another (a Nova import); each runs its setup just before it's added. */
    private val queue = ArrayDeque<Pair<AppWidgetProviderInfo, () -> Unit>>()

    fun addAll(items: List<Pair<AppWidgetProviderInfo, () -> Unit>>) { queue.addAll(items); if (pending == -1) next() }

    private fun next() { val (info, setup) = queue.removeFirstOrNull() ?: return; setup(); add(info) }

    fun providers(): List<AppWidgetProviderInfo> =
        manager.installedProviders.sortedBy { it.loadLabel(activity.packageManager).lowercase() }

    fun add(info: AppWidgetProviderInfo) {
        val id = host.allocateAppWidgetId()
        if (manager.bindAppWidgetIdIfAllowed(id, info.profile, info.provider, null)) return configure(id)
        pending = id
        @Suppress("DEPRECATION")
        activity.startActivityForResult(
            Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, info.provider)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER_PROFILE, info.profile),
            REQ_BIND,
        )
    }

    private fun configure(id: Int) {
        if (manager.getAppWidgetInfo(id)?.configure == null) return commit(id)
        pending = id
        runCatching { host.startAppWidgetConfigureActivityForResult(activity, id, 0, REQ_CONFIGURE, null) }
            .onFailure { commit(id) }
    }

    fun onResult(requestCode: Int, resultCode: Int) {
        if (requestCode != REQ_BIND && requestCode != REQ_CONFIGURE) return
        val id = pending.also { pending = -1 }
        if (id == -1) return
        when {
            resultCode != Activity.RESULT_OK -> { host.deleteAppWidgetId(id); next() }
            requestCode == REQ_BIND -> configure(id)
            else -> commit(id)
        }
    }

    /** Opens a placed widget's own settings again, like Nova's "Widget settings". The widget updates itself afterwards. */
    fun reconfigure(id: Int) {
        runCatching { host.startAppWidgetConfigureActivityForResult(activity, id, 0, REQ_RECONFIGURE, null) }
    }

    fun remove(id: Int) = host.deleteAppWidgetId(id)

    private fun commit(id: Int) { onBound(id); next() }

    companion object {
        const val REQ_BIND = 11
        const val REQ_CONFIGURE = 12
        const val REQ_RECONFIGURE = 13
    }
}
