package app.onlysearch

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.pm.LauncherApps
import android.graphics.Bitmap
import android.os.Process
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Not a correctness test: drives the launcher through its main features with seeded data and saves
 * full-screen PNGs to <external files>/shots for design work. Run by .github/workflows/screenshots.yml.
 */
@RunWith(AndroidJUnit4::class)
class ScreenshotTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val dir by lazy { File(context.getExternalFilesDir(null), "shots").apply { deleteRecursively(); mkdirs() } }

    private fun shot(name: String, settleMs: Long = 900) {
        compose.waitForIdle()
        Thread.sleep(settleMs)
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    // Some expressions may legitimately show no calculator card; screenshot whatever is there.
    private fun softWait(condition: () -> Boolean) = runCatching { compose.waitUntil(20_000, condition) }

    // System Home, like the real button: the launcher animates back to its home screen.
    private fun goHome() {
        instrumentation.uiAutomation.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
        compose.waitUntil(20_000) { runCatching { compose.onAllNodes(homeIcon("Calendar")).fetchSemanticsNodes().isNotEmpty() }.getOrDefault(false) }
        compose.waitForIdle()
    }

    private val homeIcon = { label: String -> hasContentDescription(label) and hasAnyAncestor(hasTestTag("home")) }

    // Pinned apps, launch counts (for "Frequent") and calculator history, as a regular user would have.
    private fun seed() {
        val user = Process.myUserHandle()
        val keys = context.getSystemService(LauncherApps::class.java).getActivityList(null, user)
            .associate { it.label.toString() to "${it.componentName.flattenToString()}#${user.hashCode()}" }
        context.getSharedPreferences("launcher", Context.MODE_PRIVATE).edit().clear()
            .putString("dock", listOf("Phone", "Messages", "Chrome", "Camera").mapNotNull(keys::get).joinToString("\n"))
            .putBoolean("seeded", true)
            .putString("home", listOf("Calendar", "Maps", "Photos", "Gmail", "Clock", "Drive", "Files", "Contacts").mapNotNull(keys::get).joinToString("\n"))
            .putString("groups", JSONArray(listOf(
                "Work" to listOf("Gmail", "Calendar", "Drive", "Files", "Contacts"),
                "Media" to listOf("Photos", "Camera", "YouTube", "YouTube Music"),
            ).map { (name, labels) -> JSONObject().put("name", name).put("apps", JSONArray(labels.mapNotNull(keys::get))) }).toString())
            .apply { listOf("Chrome" to 12, "Messages" to 9, "Maps" to 7, "Camera" to 5, "Gmail" to 4).forEach { (l, n) -> keys[l]?.let { putInt("count:$it", n) } } }
            .putString("history", listOf(
                "9:35am in New York to Japan\t10:35 PM",
                "\$10 for lunch + 15% tip\t\$11.50",
                "January 30 2020 + 3 months 2 weeks 5 days\tMay 19, 2020",
            ).joinToString("\n"))
            .commit()
    }

    @Test
    fun showcase() {
        seed()
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        scenario.onActivity { a ->
            val usable = a.widgets.providers().filter { it.configure == null }
            (usable.firstOrNull { it.provider.packageName.contains("deskclock") } ?: usable.firstOrNull())?.let { a.widgets.add(it) }
        }
        compose.waitUntil(30_000) { compose.onAllNodes(homeIcon("Calendar")).fetchSemanticsNodes().isNotEmpty() }
        shot("01-home", 2500)

        // Drawer: mid-swipe (finger down) and fully open.
        compose.onNodeWithTag("home").performTouchInput {
            down(Offset(centerX, height * 0.75f)); moveTo(Offset(centerX, height * 0.62f)); moveTo(Offset(centerX, height * 0.5f))
        }
        shot("02-drawer-dragging")
        // A gesture can't always be continued across performTouchInput calls; finish with a clean swipe if needed.
        runCatching { compose.onNodeWithTag("home").performTouchInput { moveTo(Offset(centerX, height * 0.25f)); up() } }
            .onFailure { compose.onNodeWithTag("home").performTouchInput { swipeUp(startY = height * 0.8f, endY = height * 0.2f, durationMillis = 400) } }
        shot("03-drawer", 1500)
        compose.onNodeWithText("Work").performClick()
        shot("03a-drawer-group")
        compose.onNodeWithText("All").performClick()

        compose.onNodeWithTag("scrubber").performTouchInput { down(Offset(centerX, height * 0.5f)); moveTo(Offset(centerX, height * 0.55f)) }
        shot("04-drawer-letter-scrubber")
        runCatching { compose.onNodeWithTag("scrubber").performTouchInput { up() } }

        goHome()
        shot("05-back-home", 1500)

        // Long-press popup with shortcuts and quick actions.
        compose.onNode(homeIcon("Calendar")).performTouchInput { longClick() }
        shot("06-long-press-popup", 1200)
        instrumentation.uiAutomation.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
        goHome()

        // Search: empty state (frequent + history), app matches, then the calculator from simple maths
        // up to time-zone conversion. Numbered so the files sort in that order.
        compose.waitUntil(20_000) {
            if (compose.onAllNodes(hasTestTag("searchField")).fetchSemanticsNodes().isEmpty()) {
                runCatching { compose.onNodeWithTag("homeSearch").performClick() }; false
            } else true
        }
        shot("10-search-empty", 1500)
        // App search: prefix, several matches, fuzzy letters, and an app with its shortcuts listed.
        listOf("ca" to "prefix-ca", "ma" to "matches-ma", "gml" to "fuzzy-gml", "phts" to "fuzzy-phts", "chrome" to "shortcuts-chrome", "set" to "settings")
            .forEachIndexed { i, (q, name) ->
                compose.onNodeWithTag("searchField").performTextClearance()
                compose.onNodeWithTag("searchField").performTextInput(q)
                shot("%02d-apps-%s".format(11 + i, name))
            }

        listOf(
            "arithmetic" to "2+2",
            "arithmetic" to "(128 * 3.5) / 4 + 12^2",
            "percent" to "20% of 450",
            "percent" to "40 is what % of 90",
            "percent" to "\$150 is 25% on what",
            "money" to "\$10 for lunch + 15% tip",
            "money" to "\$25/hour * 14 hours of work",
            "money" to "\$25k over 10 years at 7.5%",
            "units" to "65 kg in pounds",
            "units" to "5 km in miles",
            "units" to "72 F in C",
            "currency" to "100 USD in EUR",
            "dates" to "today + 3 weeks",
            "dates" to "January 30 2020 + 3 months 2 weeks 5 days",
            "time" to "3:45pm + 4 hr 10 min",
            "timezone" to "9:35am in New York to Japan",
            "timezone" to "time in Tokyo",
            "timezone" to "2pm London in Los Angeles",
        ).forEachIndexed { i, (topic, expression) ->
            val field = compose.onNodeWithTag("searchField")
            field.performTextClearance()
            softWait { compose.onAllNodes(hasTestTag("calcCard")).fetchSemanticsNodes().isEmpty() }
            field.performTextInput(expression)
            softWait { compose.onAllNodes(hasTestTag("calcCard")).fetchSemanticsNodes().isNotEmpty() }
            shot("%02d-%s-%s".format(20 + i, topic, expression.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')))
        }
        goHome()

        // Long-press beside the clock opens the home menu.
        val homeMenu = { item: String ->
            compose.onNodeWithTag("home").performTouchInput { longClick(Offset(width * 0.85f, height * 0.2f)) }
            compose.onNodeWithText(item).performClick()
        }
        homeMenu("Edit home")
        shot("40-edit-home", 1500)
        instrumentation.uiAutomation.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
        goHome()
        homeMenu("Add widget")
        shot("41-widget-picker", 1500)
        instrumentation.uiAutomation.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
        goHome()

        homeMenu("Settings")
        shot("50-settings")
        listOf("Search & calculator", "Sources & ranking", "Home screen", "Dock & search bar", "App drawer & folders", "Gestures", "Look & feel", "Backup & restore")
            .forEachIndexed { i, title ->
                compose.onNodeWithText(title).performScrollTo().performClick()
                shot("%02d-settings-%s".format(51 + i, title.lowercase().replace(Regex("[^a-z]+"), "-").trim('-')))
                instrumentation.uiAutomation.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
                compose.waitForIdle()
            }
        scenario.close()
    }
}
