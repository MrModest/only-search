package app.onlysearch

import android.content.Context
import android.system.Os
import java.io.File
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.Executors
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext

/** SoulverCore over JNI. Every native call runs on one thread, the Swift side keeps no locks. */
object Soulver {
    private val thread = Executors.newSingleThreadExecutor { Thread(it, "soulver") }.asCoroutineDispatcher()
    @Volatile private var ready = false
    @Volatile var initMillis = 0L
        private set

    private external fun nativeInit(resourcesPath: String, locale: String): Boolean
    private external fun nativeEval(expression: String): String?

    /** Blocking; call off the main thread or through [evaluate]. */
    fun initBlocking(context: Context): Boolean {
        if (ready) return true
        val start = System.nanoTime()
        val dir = copyResources(context)
        Os.setenv("TZ", TimeZone.getDefault().id, true)
        System.loadLibrary("SoulverBridge")
        ready = nativeInit(dir.absolutePath, Locale.getDefault().toLanguageTag().replace('-', '_'))
        initMillis = (System.nanoTime() - start) / 1_000_000
        return ready
    }

    fun evalBlocking(expression: String): String = if (ready) nativeEval(expression).orEmpty() else ""

    suspend fun init(context: Context) = withContext(thread) { initBlocking(context) }

    suspend fun evaluate(expression: String): String = withContext(thread) { evalBlocking(expression) }

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
