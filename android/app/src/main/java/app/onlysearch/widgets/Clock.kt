package app.onlysearch.widgets

import app.onlysearch.Mono
import app.onlysearch.WallFg
import app.onlysearch.WallMuted
import app.onlysearch.shadowed
import android.text.format.DateFormat
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.util.Date

@Composable
internal fun Clock(modifier: Modifier = Modifier) = BoxWithConstraints(modifier) {
    val context = LocalContext.current
    val now by produceState(Date()) { while (true) { value = Date(); delay(60_000 - System.currentTimeMillis() % 60_000) } }
    // 92sp when there's room, smaller in a short or narrow cell block.
    val size = with(LocalDensity.current) { minOf(92.dp, (maxHeight - 30.dp).coerceAtLeast(16.dp), maxWidth / 2.8f).toSp() }
    Column(Modifier.padding(horizontal = 8.dp).align(Alignment.CenterStart), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(DateFormat.format("EEE d MMM", now).toString().uppercase(), style = shadowed(), color = WallMuted, fontSize = 12.sp,
            fontFamily = Mono, letterSpacing = 0.14.em)
        // AM/PM would not fit at this size; the status bar already shows it.
        Text(DateFormat.format(if (DateFormat.is24HourFormat(context)) "H:mm" else "h:mm", now).toString(), style = shadowed(), color = WallFg,
            fontSize = size, fontWeight = FontWeight.Light, letterSpacing = (-0.045).em, lineHeight = size)
    }
}
