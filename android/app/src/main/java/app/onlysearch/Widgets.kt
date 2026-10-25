package app.onlysearch

import android.app.Activity
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.os.Build
import android.graphics.Outline
import android.graphics.drawable.Drawable
import android.graphics.drawable.DrawableContainer
import android.graphics.drawable.DrawableWrapper
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.view.ViewGroup
import android.widget.ImageView
import kotlin.math.abs
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.RemoteViews

/** Hosts third-party home-screen widgets. Bind/configure results arrive via [onResult]; bound ids go to [onBound]. */
class Widgets(private val activity: Activity) {
    val host = object : AppWidgetHost(activity, 1024) {
        override fun onCreateView(context: Context, appWidgetId: Int, appWidget: AppWidgetProviderInfo?): AppWidgetHostView = CornerHostView(context)
    }
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

/**
 * Widgets draw their corners with the system widget radius (~24–28 dp), which clipping can't make smaller. After each
 * update, rounded-rect drawables using the system background or inner radius are redrawn at our widget corner (scaled
 * alike), and the background view's outline, if it rounds itself, is replaced too.
 */
private class CornerHostView(context: Context) : AppWidgetHostView(context) {
    // The system widget radii only exist from Android 12.
    private val bgRadius = if (Build.VERSION.SDK_INT >= 31) Resources.getSystem().getDimension(android.R.dimen.system_app_widget_background_radius) else 0f
    private val innerRadius = if (Build.VERSION.SDK_INT >= 31) Resources.getSystem().getDimension(android.R.dimen.system_app_widget_inner_radius) else 0f
    private val radius = WidgetCorner.value * context.resources.displayMetrics.density
    private val outline = object : ViewOutlineProvider() {
        override fun getOutline(view: View, outline: Outline) = outline.setRoundRect(0, 0, view.width, view.height, radius)
    }

    override fun updateAppWidget(remoteViews: RemoteViews?) {
        super.updateAppWidget(remoteViews)
        if (bgRadius <= radius) return
        walk(this)
        val bg = findViewById<View>(android.R.id.background) ?: getChildAt(0)?.takeIf { childCount == 1 } ?: return
        if (bg.clipToOutline) bg.outlineProvider = outline
    }

    private fun walk(v: View) {
        v.background?.let(::fix)
        (v as? ImageView)?.drawable?.let(::fix)
        if (v is ViewGroup) for (i in 0 until v.childCount) walk(v.getChildAt(i))
    }

    private fun near(r: Float, target: Float) = target > 0 && abs(r - target) < 1.5f

    private fun fix(d: Drawable) {
        when (d) {
            is GradientDrawable -> {
                val r = d.cornerRadius
                if (near(r, bgRadius) || near(r, innerRadius)) d.mutate().let { (it as GradientDrawable).cornerRadius = r * radius / bgRadius }
            }
            is LayerDrawable -> for (i in 0 until d.numberOfLayers) fix(d.getDrawable(i))
            is DrawableWrapper -> d.drawable?.let(::fix)
            is DrawableContainer -> d.current?.let(::fix)
        }
    }
}
