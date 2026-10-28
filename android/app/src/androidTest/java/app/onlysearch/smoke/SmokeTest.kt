package app.onlysearch.smoke

import android.content.Intent
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Home shows its search bar; tapping it opens search, and a typed sum gets an answer. Read through the accessibility tree, no UI test library.
 * ponytail: this package sorts after search.SoulverTest, which needs a process where the launcher hasn't started the engine yet;
 * CI runs each class in its own process. Use the test orchestrator if more instrumented tests start sharing state.
 */
@RunWith(AndroidJUnit4::class)
class SmokeTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    private fun find(timeoutMs: Long = 15_000, match: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo? {
        val until = SystemClock.uptimeMillis() + timeoutMs
        while (SystemClock.uptimeMillis() < until) {
            instrumentation.uiAutomation.rootInActiveWindow?.let { root -> walk(root, match)?.let { return it } }
            SystemClock.sleep(250)
        }
        return null
    }

    private fun walk(n: AccessibilityNodeInfo, match: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo? {
        if (match(n)) return n
        for (i in 0 until n.childCount) n.getChild(i)?.let { c -> walk(c, match)?.let { return it } }
        return null
    }

    private fun AccessibilityNodeInfo.text() = text?.toString().orEmpty()

    @Test
    fun homeSearchAndCalculator() {
        val context = instrumentation.targetContext
        context.startActivity(Intent().setClassName(context, "app.onlysearch.MainActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))

        val bar = find { it.text().startsWith("Apps, math") }
        assertNotNull("home search bar", bar)
        var tap: AccessibilityNodeInfo? = bar
        while (tap != null && !tap.isClickable) tap = tap.parent
        tap!!.performAction(AccessibilityNodeInfo.ACTION_CLICK)

        assertNotNull("search field", find { it.isEditable })
        // Typed again if the field was still settling (focus, keyboard) the first time.
        val answer = (1..3).firstNotNullOfOrNull {
            find { it.isEditable }?.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, android.os.Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, "65 kg in pounds")
            })
            find(5_000) { !it.isEditable && it.text().contains("143") }
        }
        assertNotNull("calculator answer", answer)
    }
}
