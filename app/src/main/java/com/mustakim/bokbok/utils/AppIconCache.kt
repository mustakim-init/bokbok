package com.mustakim.bokbok.utils

import android.content.Context
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/**
 * A simple, thread-safe memory cache for App Icons.
 * Since app icons are small (~30-50KB), caching even 500 apps 
 * only takes ~20MB of RAM, which is negligible on modern devices.
 */
object AppIconCache {
    private val cache = ConcurrentHashMap<String, ImageBitmap>()

    /**
     * Get an icon from cache or load it if missing.
     */
    suspend fun getIcon(context: Context, packageName: String): ImageBitmap? {
        // Check cache first
        cache[packageName]?.let { return it }

        // Load if not in cache
        return withContext(Dispatchers.IO) {
            try {
                val icon = context.packageManager.getApplicationIcon(packageName)
                    .toBitmap()
                    .asImageBitmap()
                cache[packageName] = icon
                icon
            } catch (_: Exception) {
                null
            }
        }
    }

    /**
     * Optional: Clear cache if memory is low
     */
    fun clear() {
        cache.clear()
    }
}
