package app.onlysearch

import app.onlysearch.home.AppIcon
import app.onlysearch.settings.BackButton
import app.onlysearch.settings.Group
import app.onlysearch.settings.SettingsBg
import app.onlysearch.settings.Stepper
import app.onlysearch.settings.SwitchRow
import app.onlysearch.settings.ValueRow
import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/** Optional accessibility service: the only way a launcher may lock the screen. Reads no window content. */
class LockService : AccessibilityService() {
    override fun onServiceConnected() { instance = this }
    override fun onUnbind(intent: Intent?): Boolean { instance = null; return super.onUnbind(intent) }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    companion object {
        @SuppressLint("StaticFieldLeak") var instance: LockService? = null
    }
}

/** The home gestures, their settings keys and the mock-up's defaults. */
internal val Gestures = listOf(
    Triple("gDown", "Swipe down", "search"),
    Triple("gUp", "Swipe up", "drawer"),
    Triple("gTwoDown", "Two-finger swipe down", "notifications"),
    Triple("gDoubleTap", "Double tap", "lock"),
    Triple("gPinch", "Pinch in", "edit"),
    Triple("gHome", "Home button on home", "search"),
    Triple("gDock", "Swipe up on dock", "history"),
)

private val GestureIcons = mapOf(
    "gDown" to line("M12 4v16 M6 14l6 6 6-6"),
    "gUp" to line("M12 20V4 M6 10l6-6 6 6"),
    "gTwoDown" to line("M9 4v12 M15 4v12 M5 13l4 4 4-4 M11 13l4 4 4-4"),
    "gDoubleTap" to line("M6 11h12v10H6z M8.5 11V8a3.5 3.5 0 0 1 7 0v3"),
    "gPinch" to line("M4 4l6 6 M20 4l-6 6 M4 20l6-6 M20 20l-6-6"),
    "gHome" to line("M4 11l8-7 8 7v9H4z"),
    "gDock" to line("M3 12a9 9 0 1 0 3-6.7L3 8 M3 3v5h5 M12 7v5l3 2"),
)

internal val LauncherActions = listOf(
    "search" to "Search", "drawer" to "App drawer", "notifications" to "Notifications", "lock" to "Lock screen",
    "edit" to "Edit home", "history" to "Calculator history", "settings" to "Launcher settings", "none" to "Do nothing",
)

internal fun gesture(key: String): String = prefs.gestureOf(key) ?: Gestures.first { it.first == key }.third

internal fun actionLabel(apps: Apps, action: String): String = when {
    action.startsWith("app:") -> apps.byKey(action.removePrefix("app:"))?.label ?: "Missing app"
    action.startsWith("shortcut:") -> apps.pinnedShortcut(action.removePrefix("shortcut:"))?.let { "${it.app.label} › ${it.label}" } ?: "Missing shortcut"
    else -> LauncherActions.firstOrNull { it.first == action }?.second ?: action
}

fun lockScreen(): Boolean = LockService.instance?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN) == true

/** Pulls down the shade through the hidden StatusBarManager call; the accessibility service is the fallback. */
@SuppressLint("WrongConstant")
fun expandNotifications(context: Context) {
    runCatching {
        val bar = context.getSystemService("statusbar")
        Class.forName("android.app.StatusBarManager").getMethod("expandNotificationsPanel").invoke(bar)
    }.onFailure { LockService.instance?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS) }
}

fun openAccessibilitySettings(context: Context) =
    context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))

/** Asked the first time a lock gesture runs without the service: turn it on, keep the gesture idle, or switch it off. */
@Composable
internal fun LockPrompt(onClose: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    Dialog(onClose) {
        ProvideTextStyle(TextStyle(fontFamily = Sans, color = Fg)) {
            Column(Modifier.clip(RoundedCornerShape(22.dp)).background(PopupBg).border(1.dp, PillLine, RoundedCornerShape(22.dp)).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("Lock the screen with a gesture?", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                Text("Android only lets a launcher lock the screen through an accessibility service. Turn on “Only Search: lock screen gesture” in the next screen. It reads nothing on screen.",
                    color = Muted, fontSize = 14.sp, lineHeight = 20.sp)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End), verticalAlignment = Alignment.CenterVertically) {
                    Text("Not now", color = Muted, fontSize = 14.sp, maxLines = 1, modifier = Modifier.clip(CircleShape).clickable(onClick = onClose).padding(10.dp))
                    Box(Modifier.height(40.dp).clip(CircleShape).background(Accent).clickable { openAccessibilitySettings(context); onClose() }.padding(horizontal = 18.dp),
                        contentAlignment = Alignment.Center) { Text("Turn on", color = OnAccent, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1) }
                }
                Text("Turn the gesture off instead", color = Dim, fontSize = 13.sp, modifier = Modifier.clip(CircleShape).clickable {
                    Gestures.filter { gesture(it.first) == "lock" }.forEach { prefs.setGesture(it.first, "none") }; onClose()
                }.padding(vertical = 6.dp))
            }
        }
    }
}

@Composable
internal fun GestureSettings(apps: Apps) {
    var picking by remember { mutableStateOf<String?>(null) }
    Text("Every gesture can open search, an app, an app shortcut, or a launcher action.", color = Muted, fontSize = 13.sp, lineHeight = 19.sp,
        modifier = Modifier.padding(start = 4.dp, end = 4.dp, bottom = 18.dp))
    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Group("On the home screen", Gestures.map { (key, title, _) ->
            @Composable {
                val action = gesture(key)
                ValueRow(title, actionLabel(apps, action), if (action == "search") Accent else Muted, GestureIcons.getValue(key)) { picking = key }
            }
        })
        Group("Search", listOfNotNull<@Composable () -> Unit>(
            { SwitchRow("Type anywhere to search", "With a hardware keyboard attached", prefs.typeToSearch) { prefs.typeToSearch = it } },
            { SwitchRow("Reset query on close", "Off keeps your last calculation for a while", prefs.resetQuery) { prefs.resetQuery = it } },
            if (prefs.resetQuery) null else { { Stepper("Keep it for (minutes)", prefs.keepQueryMinutes, 1..120) { prefs.keepQueryMinutes = it } } },
        ))
        if (LockService.instance == null && Gestures.any { gesture(it.first) == "lock" }) Text(
            "Lock screen needs the accessibility service; you'll be asked the first time you use it.",
            color = Dim, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 4.dp),
        )
    }
    picking?.let { key -> ActionPicker(apps, Gestures.first { it.first == key }.second, gesture(key), onPick = { prefs.setGesture(key, it); picking = null }) { picking = null } }
}

/** Launcher actions first, then every app; an app with shortcuts can be expanded to pick one of them. */
@Composable
private fun ActionPicker(apps: Apps, title: String, current: String, onPick: (String) -> Unit, onClose: () -> Unit) {
    var filter by remember { mutableStateOf("") }
    var open by remember { mutableStateOf<String?>(null) }
    Dialog(onClose, DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        ProvideTextStyle(TextStyle(fontFamily = Sans, color = Fg)) {
            Column(Modifier.fillMaxSize().background(SettingsBg).systemBarsPadding().imePadding().padding(horizontal = 16.dp)) {
                Row(Modifier.padding(top = 8.dp).offset(x = (-8).dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    BackButton(onClose)
                    Text(title, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                }
                LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(0.dp)) {
                    item { Label("Launcher actions", Modifier.padding(start = 4.dp, bottom = 8.dp)) }
                    items(LauncherActions, key = { it.first }) { (id, name) -> PickRow(apps, name, id == current, null) { onPick(id) } }
                    item {
                        Label("Apps and shortcuts", Modifier.padding(start = 4.dp, top = 18.dp, bottom = 8.dp))
                        Row(Modifier.fillMaxWidth().padding(bottom = 8.dp).height(44.dp).clip(RoundedCornerShape(14.dp)).background(FieldBg)
                            .border(1.dp, FieldLine, RoundedCornerShape(14.dp)).padding(horizontal = 14.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Icon(Ic.search, null, tint = Muted, modifier = Modifier.size(18.dp))
                            Field(filter, { filter = it }, "Find an app", Modifier.weight(1f), autoFocus = false, fontSize = 15) {}
                        }
                    }
                    items(apps.visible.filter { filter.isBlank() || it.label.contains(filter.trim(), true) }, key = { it.key }) { app ->
                        Column {
                            PickRow(apps, app.label, current == "app:${app.key}", app, trailing = {
                                Box(Modifier.size(40.dp).clip(CircleShape).clickable { open = if (open == app.key) null else app.key }, contentAlignment = Alignment.Center) {
                                    Icon(if (open == app.key) Ic.collapse else Ic.expand, "Shortcuts", tint = Muted, modifier = Modifier.size(16.dp))
                                }
                            }) { onPick("app:${app.key}") }
                            if (open == app.key) {
                                val shortcuts = remember(app) { apps.shortcuts(app) }
                                if (shortcuts.isEmpty()) Text("No shortcuts", color = Dim, fontSize = 13.sp, modifier = Modifier.padding(start = 60.dp, bottom = 8.dp))
                                shortcuts.forEach { s ->
                                    val key = "shortcut:${apps.shortcutKey(s)}"
                                    Text(s.label, color = if (key == current) Accent else Fg, fontSize = 14.sp, modifier = Modifier.fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp)).clickable { onPick(key) }.padding(start = 60.dp, top = 10.dp, bottom = 10.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PickRow(apps: Apps, name: String, on: Boolean, app: App?, trailing: @Composable () -> Unit = {}, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 50.dp).clip(RoundedCornerShape(14.dp)).background(if (on) AccentCard else Color.Transparent)
        .clickable(onClick = onClick).padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        if (app != null) AppIcon(apps, app, 34.dp)
        Text(name, color = if (on) Accent else Fg, fontSize = 15.sp, modifier = Modifier.weight(1f))
        trailing()
    }
}
