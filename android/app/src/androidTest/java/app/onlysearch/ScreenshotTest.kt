package app.onlysearch

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
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
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

    private val homeIcon = { label: String -> hasContentDescription(label) and hasAnyAncestor(hasTestTag("home")) }

    // Pinned apps, launch counts (for "Frequent") and calculator history, as a regular user would have.
    private fun seed() {
        val user = Process.myUserHandle()
        val keys = context.getSystemService(LauncherApps::class.java).getActivityList(null, user)
            .associate { it.label.toString() to "${it.componentName.flattenToString()}#${user.hashCode()}" }
        context.getSharedPreferences("launcher", Context.MODE_PRIVATE).edit().clear()
            .putString("home", listOf("Calendar", "Maps", "Photos", "Gmail", "Clock", "Drive", "Files", "Contacts").mapNotNull(keys::get).joinToString("\n"))
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
            a.widgets.providers().firstOrNull { it.provider.packageName.contains("deskclock") && it.configure == null }
                ?.let { a.widgets.add(it) }
        }
        compose.waitUntil(30_000) { compose.onAllNodes(homeIcon("Calendar")).fetchSemanticsNodes().isNotEmpty() }
        shot("01-home", 2500)

        // Drawer: mid-swipe (finger down) and fully open.
        compose.onNodeWithTag("home").performTouchInput {
            down(Offset(centerX, height * 0.75f)); moveTo(Offset(centerX, height * 0.62f)); moveTo(Offset(centerX, height * 0.5f))
        }
        shot("02-drawer-dragging")
        compose.onNodeWithTag("home").performTouchInput { moveTo(Offset(centerX, height * 0.25f)); up() }
        shot("03-drawer", 1500)

        compose.onNodeWithTag("scrubber").performTouchInput { down(Offset(centerX, height * 0.5f)); moveTo(Offset(centerX, height * 0.55f)) }
        shot("04-drawer-letter-scrubber")
        compose.onNodeWithTag("scrubber").performTouchInput { up() }

        scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        shot("05-back-home", 1500)

        // Long-press popup with shortcuts and quick actions.
        compose.onNode(homeIcon("Calendar")).performTouchInput { longClick() }
        shot("06-long-press-popup", 1200)
        scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()

        // Search: empty state (frequent + history), app matches, then the calculator from simple maths
        // up to time-zone conversion. Numbered so the files sort in that order.
        compose.onNodeWithTag("homeSearch").performClick()
        shot("10-search-empty", 1500)
        compose.onNodeWithTag("searchField").performTextInput("ca")
        shot("11-search-apps")

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
        scenario.close()
    }
}
