package app.onlysearch

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.drawable.Drawable
import android.util.Xml
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import org.xmlpull.v1.XmlPullParser

/** Icon packs in the ADW / Nova format (what Icon Pack Studio exports): installed apps that answer these intents. */
private val PackIntents = listOf("org.adw.launcher.THEMES", "com.novalauncher.THEME", "com.teslacoilsw.launcher.THEME")

/** Installed icon packs as package → name. */
fun iconPacks(context: Context): List<Pair<String, String>> {
    val pm = context.packageManager
    return PackIntents.flatMap { pm.queryIntentActivities(Intent(it), 0) }
        .map { it.activityInfo.packageName to it.loadLabel(pm).toString() }.distinctBy { it.first }.sortedBy { it.second.lowercase() }
}

/**
 * One icon pack's appfilter.xml: drawables per activity, plus the back / mask / front layers and scale
 * it applies to apps it has no icon for.
 */
class IconPack(context: Context, private val pkg: String) {
    private val res: Resources = context.packageManager.getResourcesForApplication(pkg)
    private val density = context.resources.displayMetrics.densityDpi
    private val icons = mutableMapOf<String, String>()
    private var back: String? = null
    private var mask: String? = null
    private var front: String? = null
    private var scale = 1f

    init {
        val id = res.getIdentifier("appfilter", "xml", pkg)
        val parser: XmlPullParser = if (id != 0) res.getXml(id) else Xml.newPullParser().apply { setInput(res.assets.open("appfilter.xml"), null) }
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType != XmlPullParser.START_TAG) continue
            when (parser.name) {
                "item" -> {
                    // "ComponentInfo{pkg/cls}"; the class may be written relative (".Main").
                    val c = parser.getAttributeValue(null, "component")?.removePrefix("ComponentInfo{")?.removeSuffix("}")
                    val d = parser.getAttributeValue(null, "drawable")
                    val cn = c?.let(ComponentName::unflattenFromString)
                    if (cn != null && d != null) icons.putIfAbsent(cn.flattenToString(), d)
                }
                "iconback" -> back = parser.getAttributeValue(null, "img1")
                "iconmask" -> mask = parser.getAttributeValue(null, "img1")
                "iconupon" -> front = parser.getAttributeValue(null, "img1")
                "scale" -> scale = parser.getAttributeValue(null, "factor")?.toFloatOrNull() ?: 1f
            }
        }
    }

    private fun drawable(name: String?): Drawable? {
        val id = name?.let { res.getIdentifier(it, "drawable", pkg) } ?: 0
        return if (id == 0) null else runCatching { res.getDrawableForDensity(id, density, null) }.getOrNull()
    }

    /** The pack's own icon for [component], or null. Pack icons come shaped, so they're drawn as they are. */
    fun icon(component: ComponentName, size: Int): ImageBitmap? =
        drawable(icons[component.flattenToString()])?.toBitmap(size, size)?.asImageBitmap()

    /** An app the pack doesn't cover, styled the pack's way (scaled onto its back, cut by its mask); null if it has no back. */
    fun fallback(original: Drawable, size: Int): ImageBitmap? {
        val b = drawable(back) ?: return null
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        b.setBounds(0, 0, size, size); b.draw(c)
        // The app's icon, scaled, with the mask cut out of it (not out of the back).
        val layer = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val lc = Canvas(layer)
        val inner = (size * scale).toInt(); val o = ((size - inner) / 2).toFloat()
        lc.drawBitmap(original.toBitmap(inner, inner), o, o, null)
        drawable(mask)?.let { m -> lc.drawBitmap(m.toBitmap(size, size), 0f, 0f, Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT) }) }
        c.drawBitmap(layer, 0f, 0f, null)
        drawable(front)?.let { it.setBounds(0, 0, size, size); it.draw(c) }
        return bmp.asImageBitmap()
    }
}
