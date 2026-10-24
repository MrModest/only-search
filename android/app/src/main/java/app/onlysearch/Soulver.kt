package app.onlysearch

import android.content.Context
import android.system.Os
import java.io.File
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.Executors
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** SoulverCore over JNI. Every native call runs on one thread, the Swift side keeps no locks. */
object Soulver {
    private val thread = Executors.newSingleThreadExecutor { Thread(it, "soulver") }.asCoroutineDispatcher()
    @Volatile private var ready = false
    @Volatile var initMillis = 0L
        private set

    private external fun nativeInit(resourcesPath: String, locale: String): Boolean
    private external fun nativeEval(expression: String): String?
    private external fun nativeSetUnitSystem(system: String)
    private external fun nativeSetRates(rates: String)
    private external fun nativeRateLog(): String

    /** Recent rate requests from the engine (diagnostics for SoulverTest). */
    fun rateLogBlocking(): String = runCatching { nativeRateLog() }.getOrDefault("")
    fun setRatesBlocking(rates: String) { if (ready) runCatching { nativeSetRates(rates) } }

    /** Blocking; call off the main thread or through [evaluate]. */
    fun initBlocking(context: Context): Boolean {
        if (ready) return true
        val start = System.nanoTime()
        val dir = copyResources(context)
        Os.setenv("TZ", TimeZone.getDefault().id, true)
        System.loadLibrary("SoulverBridge")
        ready = nativeInit(dir.absolutePath, Locale.getDefault().toLanguageTag().replace('-', '_'))
        // The first evaluation loads unit, currency and date tables; pay that here rather than on the first keystroke.
        if (ready) listOf("1 kg in lb", "\$10 + 15%", "3pm + 2 hours", "today + 1 week").forEach { nativeEval(it) }
        initMillis = (System.nanoTime() - start) / 1_000_000
        return ready
    }

    fun evalBlocking(expression: String): String = if (ready) nativeEval(expression).orEmpty() else ""

    suspend fun init(context: Context) = withContext(thread) { initBlocking(context) }

    suspend fun evaluate(expression: String): String = withContext(thread) { evalBlocking(expression) }

    // A bridge library older than these calls (e.g. a local build with CI-extracted libs) just keeps the defaults.
    suspend fun setUnitSystem(system: String) = withContext(thread) { if (ready) runCatching { nativeSetUnitSystem(system) } }

    suspend fun setRates(rates: String) = withContext(thread) { if (ready) runCatching { nativeSetRates(rates) } }

    /**
     * Live fiat (open.er-api.com) and crypto (CoinGecko) rates, refetched when older than 6 h. The last good set is kept,
     * so an offline start still uses recent rates; with none at all SoulverCore's built-in standard rates apply.
     */
    suspend fun refreshRates() {
        val sp = prefs.sp
        var data = sp.getString("rates", null)
        if (data == null || System.currentTimeMillis() - sp.getLong("ratesAt", 0) > 6 * 3_600_000L) {
            withContext(Dispatchers.IO) { runCatching { fetchRates() } }.getOrNull()?.let {
                data = it
                sp.edit().putString("rates", it).putLong("ratesAt", System.currentTimeMillis()).apply()
            }
        }
        data?.let { setRates(it) }
    }

    private val Crypto = mapOf(
        "bitcoin" to "BTC", "ethereum" to "ETH", "solana" to "SOL", "ripple" to "XRP", "dogecoin" to "DOGE",
        "cardano" to "ADA", "litecoin" to "LTC", "tether" to "USDT", "binancecoin" to "BNB", "polkadot" to "DOT",
    )

    private fun get(url: String): String = (URL(url).openConnection() as HttpURLConnection).run {
        connectTimeout = 8000; readTimeout = 8000
        try { inputStream.bufferedReader().readText() } finally { disconnect() }
    }

    private fun fetchRates(): String {
        val fiat = JSONObject(get("https://open.er-api.com/v6/latest/USD")).getJSONObject("rates")
        val perUsd = fiat.keys().asSequence().associateWith { fiat.getDouble(it) }.toMutableMap()
        runCatching {
            val c = JSONObject(get("https://api.coingecko.com/api/v3/simple/price?vs_currencies=usd&ids=" + Crypto.keys.joinToString(",")))
            Crypto.forEach { (id, code) -> c.optJSONObject(id)?.optDouble("usd")?.takeIf { it > 0 }?.let { perUsd[code] = 1 / it } }
        }
        return perUsd.entries.joinToString(";") { "${it.key}=${it.value.toBigDecimal().toPlainString()}" }
    }

    // The engine reads its resources bundle from a real directory, so unpack it from assets once per install/update.
    private fun copyResources(context: Context): File {
        val name = "SoulverCore_SoulverCore.resources"
        val dir = File(context.filesDir, name)
        val stamp = File(context.filesDir, "$name.stamp")
        val version = context.packageManager.getPackageInfo(context.packageName, 0).lastUpdateTime.toString()
        if (stamp.exists() && stamp.readText() == version) return dir
        dir.deleteRecursively()
        fun copy(asset: String, target: File) {
            val children = context.assets.list(asset).orEmpty()
            if (children.isEmpty()) {
                target.parentFile?.mkdirs()
                context.assets.open(asset).use { input -> target.outputStream().use { input.copyTo(it) } }
            } else children.forEach { copy("$asset/$it", File(target, it)) }
        }
        copy("soulver/$name", dir)
        stamp.writeText(version)
        return dir
    }
}
