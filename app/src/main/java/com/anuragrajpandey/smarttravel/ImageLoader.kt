package com.anuragrajpandey.smarttravel

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import android.widget.ImageView
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

object ImageLoader {
    private val maxKb = (Runtime.getRuntime().maxMemory() / 1024 / 8).toInt().coerceIn(8192, 32768)
    private val cache = object : LruCache<String, Bitmap>(maxKb) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount / 1024
    }
    private val executor = Executors.newFixedThreadPool(6)
    private val mainHandler = Handler(Looper.getMainLooper())

    fun load(url: String?, imageView: ImageView, onResult: ((Boolean) -> Unit)? = null) {
        imageView.tag = url
        if (url.isNullOrBlank()) {
            imageView.setImageDrawable(null)
            onResult?.invoke(false)
            return
        }
        val cached = cache.get(url)
        if (cached != null && !cached.isRecycled) {
            imageView.setImageBitmap(cached)
            imageView.alpha = 1f
            onResult?.invoke(true)
            return
        }
        imageView.setImageDrawable(null)
        executor.execute {
            // The view may have been recycled or re-bound while this job waited in the queue.
            if (imageView.tag != url) return@execute
            val bitmap = runCatching { downloadBitmap(url) }.getOrNull()
            if (bitmap != null) {
                cache.put(url, bitmap)
            }
            mainHandler.post {
                if (imageView.tag == url) {
                    if (bitmap != null && !bitmap.isRecycled) {
                        imageView.alpha = 0f
                        imageView.setImageBitmap(bitmap)
                        imageView.animate().alpha(1f).setDuration(160).start()
                        onResult?.invoke(true)
                    } else {
                        onResult?.invoke(false)
                    }
                }
            }
        }
    }

    private fun downloadBitmap(rawUrl: String): Bitmap? {
        var currentUrl = rawUrl
        repeat(3) {
            val connection = (URL(currentUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = 7000
                readTimeout = 12000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "FlipDoor/1.0 (Android travel discovery app)")
            }
            try {
                val code = connection.responseCode
                if (code in 300..399) {
                    val next = connection.getHeaderField("Location") ?: return null
                    currentUrl = next
                    return@repeat
                }
                if (code !in 200..299) return null
                val bytes = connection.inputStream.use { it.readBytes() }
                if (bytes.isEmpty()) return null
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                var sampleSize = 1
                val maxDim = maxOf(bounds.outWidth, bounds.outHeight)
                while (maxDim / sampleSize > 900) {
                    sampleSize *= 2
                }
                val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize; inPreferredConfig = Bitmap.Config.RGB_565 }
                return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOpts)
            } finally {
                connection.disconnect()
            }
        }
        return null
    }
}
