package com.mustakim.bokbok.utils

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import coil.ImageLoader
import coil.decode.DataSource
import coil.fetch.DrawableResult
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.key.Keyer
import coil.request.Options
import coil.request.ImageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * A Coil Fetcher that loads App Icons safely.
 * It handles the "AccessDeniedException" thrown by Vivo's theme engine.
 * 
 * Usage: AsyncImage(model = AppIcon(packageName), ...)
 */
data class AppIcon(val packageName: String)

class AppIconFetcher(
    private val context: Context,
    private val data: AppIcon
) : Fetcher {

    override suspend fun fetch(): FetchResult? = withContext(Dispatchers.IO) {
        try {
            val pm = context.packageManager
            val icon = pm.getApplicationIcon(data.packageName)
            
            val isSampled = false // Icons are small, usually don't need downsampling
            
            DrawableResult(
                drawable = icon,
                isSampled = isSampled,
                dataSource = DataSource.DISK
            )
        } catch (e: Exception) {
            // Log silently or return null to fall back to error placeholder
            // This handles the Vivo "Access Denied" crash
            null
        }
    }

    class Factory(private val context: Context) : Fetcher.Factory<AppIcon> {
        override fun create(data: AppIcon, options: Options, imageLoader: ImageLoader): Fetcher {
            return AppIconFetcher(context, data)
        }
    }
}

class AppIconKeyer : Keyer<AppIcon> {
    override fun key(data: AppIcon, options: Options): String {
        // Key by package name. 
        // Note: If an app updates its icon, the package name stays the same.
        // For perfect validity we could append version code, but for icons, 
        // package name is usually sufficient and faster to generate.
        return "app_icon:${data.packageName}"
    }
}
