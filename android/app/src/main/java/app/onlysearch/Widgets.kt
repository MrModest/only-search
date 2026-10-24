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
            resultCode != Activity.RESULT_OK -> host.deleteAppWidgetId(id)
            requestCode == REQ_BIND -> configure(id)
            else -> commit(id)
        }
    }

    fun remove(id: Int) = host.deleteAppWidgetId(id)

    private fun commit(id: Int) = onBound(id)

    companion object {
        const val REQ_BIND = 11
        const val REQ_CONFIGURE = 12
    }
}
