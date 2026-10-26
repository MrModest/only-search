@file:OptIn(ExperimentalTextApi::class)

package app.onlysearch

import androidx.compose.animation.core.spring
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

// Mock-up 2026-10-24 (docs/Mock-up 2026-10-24.html): soft dark tiles and cards, sky-blue accent, Sans + JetBrains Mono.
// Motion follows Nova Launcher: finger-tracked drawer, icon zoom launches, press scale, popup shortcuts.
// Sheet, Scrim and the accent colours follow Settings › Look & feel; reading them in composition keeps the UI in sync.
internal val Sheet get() = if (prefs.black) Color.Black else Color(0xFF0E0F12)
internal val Tile = Color(0xFF1E2025)
internal val TileLine = Color(0xFF30333A)
internal val FieldBg = Color(0xFF1A1C21)
internal val Card = Color(0xFF16181C)
/** Widgets are near-square; only the search bar is round. */
internal val WidgetCorner = 6.dp
internal val CardLine = Color(0xFF26292F)
internal val Divider = Color(0xFF23262C)
internal val PillLine = Color(0xFF34373E)
internal val ChipLine = Color(0xFF2E3138)
internal val Accents = listOf("sky" to Color(0xFF7CC4FA), "amber" to Color(0xFFF5B04A), "mint" to Color(0xFF7FD8B0), "rose" to Color(0xFFF59AB0), "violet" to Color(0xFFB9A2F8), "white" to Color(0xFFF2F2F2))
/** A named accent, or a custom one stored as "#RRGGBB". */
internal val Accent get() = prefs.accentColor
internal val OnAccent get() = lerp(Color(0xFF06121C), Accent, 0.05f)
internal val AccentCard get() = lerp(Color(0xFF0E0F12), Accent, 0.07f)
internal val AccentLine get() = lerp(Color(0xFF0E0F12), Accent, 0.28f)
internal val Fg = Color(0xFFEDEAE4)
internal val Muted = Color(0xFFA6A29A)
internal val Dim = Color(0xFF8C887F)
internal val Danger = Color(0xFFFF8A7A)
internal val Scrim get() = if (prefs.black) Color(0xF5000000) else Color(0xF2111216)
internal val PopupBg = Color(0xFF1C1E23)
internal val TextShadow = Shadow(Color(0x66000000), Offset(0f, 1f), 3f)
internal val Settle = spring<Float>(dampingRatio = 0.9f, stiffness = 420f)

internal fun family(res: Int, vararg weights: Int) =
    FontFamily(weights.map { Font(res, FontWeight(it), variationSettings = FontVariation.Settings(FontVariation.weight(it))) })
/** The phone's own UI font (Google Sans Text on a Pixel), else plain sans-serif. */
internal val Sans: FontFamily = android.content.res.Resources.getSystem().run {
    getIdentifier("config_bodyFontFamily", "string", "android").takeIf { it != 0 }?.let(::getString)?.takeIf { it.isNotBlank() }
}?.let { FontFamily(android.graphics.Typeface.create(it, android.graphics.Typeface.NORMAL)) } ?: FontFamily.Default
internal val Mono = family(R.font.jetbrains_mono, 400, 500, 700)

/** Stroked 24×24 line icon from SVG path data, the way the mock-up draws them. */
internal fun line(d: String, width: Float = 1.7f) = ImageVector.Builder(defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
    .addPath(PathParser().parsePathString(d).toNodes(), fill = null, stroke = SolidColor(Color.White), strokeLineWidth = width,
        strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round)
    .build()

internal object Ic {
    val search = line("M11 18a7 7 0 1 0 0-14 7 7 0 0 0 0 14z M20 20l-4-4", 2f)
    val mic = line("M12 3a3 3 0 0 0-3 3v6a3 3 0 0 0 6 0V6a3 3 0 0 0-3-3z M5.5 11a6.5 6.5 0 0 0 13 0 M12 17.5V21")
    val close = line("M6 6l12 12 M18 6L6 18", 1.8f)
    val history = line("M3 12a9 9 0 1 0 3-6.7L3 8 M3 3v5h5 M12 7v5l3 2", 1.8f)
    val info = line("M12 21a9 9 0 1 0 0-18 9 9 0 0 0 0 18z M12 11v6 M12 7.5h.01")
    val home = line("M4 11l8-7 8 7v9H4z")
    val dock = line("M3 14h18v6H3z M7 10h10")
    val uninstall = line("M5 7h14 M10 7V4h4v3 M7 7l1 13h8l1-13")
    val expand = line("M6 9l6 6 6-6", 2f)
    val collapse = line("M6 15l6-6 6 6", 2f)
    val widgets = line("M4 4h7v7H4z M13 4h7v7h-7z M4 13h7v7H4z M13 13h7v7h-7z")
    val edit = line("M4 20h4L19 9l-4-4L4 16z M13.5 6.5l4 4")
    val hide = line("M3 3l18 18 M10.6 5.1A10 10 0 0 1 12 5c5 0 9 4.5 10 7-0.4 1-1.2 2.3-2.4 3.5 M6.6 6.6C4.6 8 3.3 10 2 12c1 2.5 5 7 10 7 1.8 0 3.4-.5 4.8-1.3 M9.9 9.9a3 3 0 0 0 4.2 4.2")
    val grip = line("M5 9h14 M5 15h14", 1.8f)
    val monitor = line("M3 5h18v11H3z M8 20h8 M12 16v4")
    val phone = line("M5 4h4l2 5-2.5 1.5a11 11 0 0 0 5 5L15 13l5 2v4a2 2 0 0 1-2 2A16 16 0 0 1 3 6a2 2 0 0 1 2-2z")
    val message = line("M12 20a8 8 0 1 0-7-4l-1 4 4-1a8 8 0 0 0 4 1z")
    val globe = line("M12 21a9 9 0 1 0 0-18 9 9 0 0 0 0 18z M3 12h18 M12 3a14 14 0 0 1 0 18 M12 3a14 14 0 0 0 0 18")
}

@Composable
internal fun Label(text: String, modifier: Modifier = Modifier, color: Color = Muted) =
    Text(text.uppercase(), modifier, color = color, fontSize = 11.sp, fontFamily = Mono, letterSpacing = 0.14.em, maxLines = 1)

@Composable
internal fun shadowed() = LocalTextStyle.current.copy(shadow = TextShadow)
