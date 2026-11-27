package com.anuragrajpandey.smarttravel

import android.app.Application
import android.net.http.HttpResponseCache
import android.util.LruCache
import java.io.File
import java.util.concurrent.Callable
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

/** Installs a disk HTTP cache so repeat launches and images load instantly. */
class FlipDoorApp : Application() {
    override fun onCreate() {
        super.onCreate()
        runCatching { HttpResponseCache.install(File(cacheDir, "http"), 60L * 1024 * 1024) }
    }
}

/** Shared worker pool + small in-memory response cache. */
object Net {
    val pool: ExecutorService = Executors.newFixedThreadPool(10) { r -> Thread(r).apply { isDaemon = true } }

    private val mem = LruCache<String, Pair<Long, String>>(300)

    fun <T> async(block: () -> T): Future<T> = pool.submit(Callable { block() })

    /** Waits for a future but never throws; returns [default] on error/timeout. */
    fun <T> await(f: Future<T>, timeoutMs: Long, default: T): T =
        try { f.get(timeoutMs, TimeUnit.MILLISECONDS) } catch (e: Exception) { f.cancel(true); default }

    fun cached(key: String, ttlMs: Long): String? {
        val hit = mem.get(key) ?: return null
        return if (System.currentTimeMillis() - hit.first < ttlMs) hit.second else null
    }

    fun put(key: String, value: String) { mem.put(key, System.currentTimeMillis() to value) }
}
