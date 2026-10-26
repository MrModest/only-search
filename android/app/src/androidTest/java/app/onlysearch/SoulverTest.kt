package app.onlysearch

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Runs the documented SoulverCore examples through the real JNI bridge and logs results + timings. */
@RunWith(AndroidJUnit4::class)
class SoulverTest {
    private val cases = listOf(
        "65 kg in pounds" to "143",
        "40 is what % of 90" to "44",
        "\$150 is 25% on what" to "120",
        "3:45pm + 4 hr 10 min" to "7:55",
        "9:35am in New York to Japan" to "",
        "January 30 2020 + 3 months 2 weeks 5 days" to "2020",
        "\$25k over 10 years at 7.5%" to "",
        "\$10 for lunch + 15% tip" to "11.5",
        "\$25/hour * 14 hours of work" to "350",
        "100 USD in EUR" to "",
        "today + 3 weeks" to "",
        // Home-unit conversions as Search builds them (homeQuery).
        "5 ft in m" to "1.52 m",
        "5 ft 10 in in m" to "1.78 m",
        "10 lb in kg" to "4.54 kg",
        "3 km in mi" to "1.86 mi",
        "70 F in celsius" to "21.11",
    )

    @Test
    fun examples() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        assertTrue("init failed", Soulver.initBlocking(ctx))
        Log.i(TAG, "init ${Soulver.initMillis} ms")
        val failures = mutableListOf<String>()
        for ((expr, expect) in cases) {
            val t0 = System.nanoTime()
            val r = Soulver.evalBlocking(expr)
            Log.i(TAG, "%-45s => %-28s %.2f ms".format(expr, r, (System.nanoTime() - t0) / 1e6))
            if (r.isBlank() || !r.contains(expect)) failures += "$expr => '$r'"
        }
        Soulver.evalBlocking("0.5 BTC in USD").let { if (it.isNotBlank()) failures += "no BTC rate yet: 0.5 BTC in USD => '$it' (want blank)" }
        // Our rate provider: fixed rates in, conversions out (live ones are fetched by the app, not here).
        Soulver.setRatesBlocking("USD=1;EUR=0.5;BTC=0.00001;ETH=0.0004")
        listOf("100 USD in EUR" to "50", "0.5 BTC in USD" to "50,000", "1 ETH in EUR" to "1,250", "1000 USD in BTC" to "0.01", "0.5 btc in usd" to "50,000", "2 bitcoin in usd" to "200").forEach { (expr, expect) ->
            val r = Soulver.evalBlocking(expr)
            Log.i(TAG, "provider: %-30s => %s".format(expr, r))
            if (!r.contains(expect)) failures += "provider: $expr => '$r' (want $expect)"
        }
        Log.i(TAG, "rate requests:\n" + Soulver.rateLogBlocking())
        val n = 500
        val t0 = System.nanoTime()
        repeat(n) { Soulver.evalBlocking(cases[it % cases.size].first) }
        Log.i(TAG, "warm average %.3f ms over $n evals".format((System.nanoTime() - t0) / 1e6 / n))
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    companion object { const val TAG = "SoulverTest" }
}
