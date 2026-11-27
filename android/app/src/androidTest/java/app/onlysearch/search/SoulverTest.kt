package app.onlysearch.search

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.TimeZone

/** Runs the documented SoulverCore examples through the real JNI bridge and logs results + timings. Expects en-DE formatting (decimal comma, 24 h clock, € suffix), pinned in [examples]. */
@RunWith(AndroidJUnit4::class)
class SoulverTest {
    private val cases = listOf(
        "65 kg in pounds" to "143",
        "40 is what % of 90" to "44",
        "\$150 is 25% on what" to "120",
        "3:45pm + 4 hr 10 min" to "19:55",
        "9:35am in New York to Japan" to "",
        "January 30 2020 + 3 months 2 weeks 5 days" to "2020",
        "\$25k over 10 years at 7.5%" to "",
        "\$10 for lunch + 15% tip" to "11,50",
        "\$25/hour * 14 hours of work" to "350",
        "100 USD in EUR" to "",
        "today + 3 weeks" to "",
        // Home-unit conversions as Search builds them (homeQuery).
        "5 ft in m" to "1,52 m",
        "5 ft 10 in in m" to "1,78 m",
        "10 lb in kg" to "4,54 kg",
        "3 km in mi" to "1,86 mi",
        "70 F in celsius" to "21,11",
    )

    @Test
    fun examples() {
        Locale.setDefault(Locale("en", "DE"))
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
        Soulver.evalBlocking("0,5 BTC in USD").let { if (it.isNotBlank()) failures += "no BTC rate yet: 0,5 BTC in USD => '$it' (want blank)" }
        // Our rate provider: fixed rates in, conversions out (live ones are fetched by the app, not here).
        Soulver.setRatesBlocking("USD=1;EUR=0.5;BTC=0.00001;ETH=0.0004")
        listOf("100 USD in EUR" to "50", "0,5 BTC in USD" to "50.000", "1 ETH in EUR" to "1.250", "1000 USD in BTC" to "0,01", "0,5 btc in usd" to "50.000", "2 bitcoin in usd" to "200.000").forEach { (expr, expect) ->
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

    @Test
    fun numberFormatting() {
        Locale.setDefault(Locale("en", "DE"))
        assertTrue("init failed", Soulver.initBlocking(InstrumentationRegistry.getInstrumentation().targetContext))
        // Fixed rates deliberately leave many decimals before currency rounding.
        Soulver.setRatesBlocking("USD=1;EUR=0.911313;JPY=153.456789;KWD=0.307654321;BHD=0.376543219;BTC=0.0000167297")
        try {
            listOf(
                "1 usd in eur" to "0,91 €",
                "1 usd in jpy" to "¥153",
                "1 usd in kwd" to "0,308 KWD",
                "1 usd in bhd" to "0,377 BHD",
                "5 btc" to "5 btc",
                "5,123456789 btc" to "5,12345679 btc",
                "5 bitcoin" to "5 bitcoin",
                "5 btc in eur" to "272.363,82 €",
                "0,001 btc in eur" to "54,47 €",
                "1234567" to "1.234.567",
                "1e15" to "1P",
                "1e30" to "1e30",
                "50 eur in btc" to "0,00091789 btc",
                "1 usd in btc" to "0,00001673 btc",
                "0,001 btc in eur" to "54,47 €", // Crypto precision must not leak to the next query.
                "0,999999 eur" to "1,00 €",
                "0,004 eur" to "0,00 €",
                "1,234567 kwd" to "1,235 KWD",
                "1,234567 eur" to "1,23 €",
                "0,1 + 0,2" to "0,3",
                "1/3" to "0,33333333",
                "-0,0001" to "-0,0001",
                "0" to "0",
            ).forEach { (expr, expected) ->
                assertEquals(expr, expected, Soulver.evalBlocking(expr))
            }
        } finally {
            Soulver.setRatesBlocking("")
        }
    }

    @Test
    fun homeTimeZone() {
        val saved = TimeZone.getDefault()
        try {
            Locale.setDefault(Locale("en", "DE"))
            TimeZone.setDefault(TimeZone.getTimeZone("Europe/Berlin"))
            assertTrue("init failed", Soulver.initBlocking(InstrumentationRegistry.getInstrumentation().targetContext))
            fun at(zone: String, hour: Int) = ZonedDateTime.now(ZoneId.of(zone)).withHour(hour).withMinute(0).withZoneSameInstant(ZoneId.of("Europe/Berlin"))
                .format(DateTimeFormatter.ofPattern("HH:mm"))
            val minutes = ZoneId.of("Europe/Berlin").rules.getOffset(java.time.Instant.now()).totalSeconds / 60
            val target = " in UTC+%d:%02d:00".format(minutes / 60, minutes % 60)
            // Queries as Search builds them: homeQuery appends the home-zone target to a bare "<time> <place>".
            val failures = mutableListOf<String>()
            fun check(q: String, ok: (String) -> Boolean) = Soulver.evalBlocking(q).let { if (!ok(it)) failures += "$q => '$it'" }
            check("14:00 Moscow"+target) { it == at("Europe/Moscow", 14) }
            check("14:00 Moscow time"+target) { it == at("Europe/Moscow", 14) }
            check("2pm London"+target) { it == at("Europe/London", 14) }
            check("14:00 Berlin"+target) { it == "14:00" }
            check("12:00 Berlin in Moscow") { it == ZonedDateTime.now(ZoneId.of("Europe/Berlin")).withHour(12).withMinute(0).withZoneSameInstant(ZoneId.of("Europe/Moscow")).format(DateTimeFormatter.ofPattern("HH:mm")) }
            assertTrue(mskOk("29. October 2026 at 00:01", "00:01"))
            assertFalse(mskOk("29. October 2026 at 00:05", "00:01"))
            // Soulver prefixes the date when Moscow's day differs from the device zone's day.
            check("msk time") { r -> (0..1).any { mskOk(r, ZonedDateTime.now(ZoneId.of("Europe/Moscow")).minusMinutes(it.toLong()).format(DateTimeFormatter.ofPattern("HH:mm"))) } }
            check("13:00") { it == "13:00" }
            check("1 pm") { it == "13:00" }
            check("12:00 + 1h 35m") { it == "13:35" }
            check("12 USD in EUR") { it.contains("€") }
            check("5 ft in m") { it.contains("1,52 m") }
            assertTrue(failures.joinToString("\n"), failures.isEmpty())
        } finally {
            TimeZone.setDefault(saved)
        }
    }

    private fun mskOk(result: String, hhmm: String) = Regex("""(.+ at )?$hhmm""").matches(result)

    companion object { const val TAG = "SoulverTest" }
}
