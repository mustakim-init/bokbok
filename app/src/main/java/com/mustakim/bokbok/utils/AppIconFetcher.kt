package com.mustakim.bokbok.utils

import android.content.Context
import coil.decode.DataSource
import coil.fetch.DrawableResult
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.request.Options

/**
 * A Coil Fetcher that loads an app icon from a package name.
 * Use with the "appicon://" scheme.
 * Example: "appicon://com.android.settings"
 */
class AppIconFetcher(
    private val packageName: String,
    private val context: Context
) : Fetcher {
    override suspend fun fetch(): FetchResult? {
        val pm = context.packageManager
        val icon = try {
            pm.getApplicationIcon(packageName)
        } catch (_: Exception) {
            null
        } ?: return null
        
        return DrawableResult(
            drawable = icon,
            isSampled = false,
            dataSource = DataSource.DISK
        )
    }

    class Factory(private val context: Context) : Fetcher.Factory<String> {
        override fun create(data: String, options: Options, imageLoader: coil.ImageLoader): Fetcher? {
            if (!data.startsWith("appicon://")) return null
            val packageName = data.removePrefix("appicon://")
            return AppIconFetcher(packageName, context)
        }
    }
}
