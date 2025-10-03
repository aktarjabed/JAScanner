package com.jascanner.core

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.collection.LruCache
import timber.log.Timber
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MemoryManager @Inject constructor() {

    private val bitmapCache: LruCache<String, Bitmap>

    init {
        val maxMemory = (Runtime.getRuntime().maxMemory() / 1024).toInt()
        val cacheSize = maxMemory / 8 // Use 1/8th of the available memory for this cache.
        bitmapCache = object : LruCache<String, Bitmap>(cacheSize) {
            override fun sizeOf(key: String, bitmap: Bitmap): Int {
                // The cache size will be measured in kilobytes rather than number of items.
                return bitmap.byteCount / 1024
            }
        }
        Timber.d("MemoryManager initialized with cache size: %d KB", cacheSize)
    }

    fun addBitmapToCache(key: String, bitmap: Bitmap) {
        if (getBitmapFromCache(key) == null) {
            bitmapCache.put(key, bitmap)
            Timber.d("Bitmap added to cache. Key: %s", key)
        }
    }

    fun getBitmapFromCache(key: String): Bitmap? {
        return bitmapCache.get(key)
    }

    fun removeBitmapFromCache(key: String) {
        bitmapCache.remove(key)
        Timber.d("Bitmap removed from cache. Key: %s", key)
    }

    fun clearCache() {
        bitmapCache.evictAll()
        Timber.d("Bitmap cache cleared.")
    }

    @Throws(IOException::class)
    fun loadBitmapSafely(context: Context, uri: Uri): Bitmap {
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, options)
        }

        options.inSampleSize = calculateInSampleSize(options, 1024, 1024)

        options.inJustDecodeBounds = false
        return context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, options)
        } ?: throw IOException("Could not open input stream for URI: $uri")
    }

    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val (height: Int, width: Int) = options.run { outHeight to outWidth }
        var inSampleSize = 1

        if (height > reqHeight || width > reqWidth) {
            val halfHeight: Int = height / 2
            val halfWidth: Int = width / 2
            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }
}