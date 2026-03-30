package com.mustakim.bokbok.data.bloatware

import android.content.Context
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.google.gson.reflect.TypeToken
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Represents a bloatware app entry from the debloat.json database.
 * Based on the App Manager reference implementation.
 */
data class DebloatObject(
    val packageName: String,
    
    @SerializedName("label")
    val label: String? = null,
    
    @SerializedName("description")
    val description: String? = null,
    
    @SerializedName("removal")
    val removal: String = "Advanced",
    
    @SerializedName("list")
    val type: String = "Misc",
    
    @SerializedName("warning")
    val warning: String? = null,
    
    @SerializedName("suggestions")
    val suggestions: String? = null,
    
    @SerializedName("dependencies")
    val dependencies: List<String>? = null,
    
    @SerializedName("neededBy")
    val requiredBy: List<String>? = null,
    
    @SerializedName("labels")
    val labels: List<String>? = null,
    
    @SerializedName("web")
    val webRefs: List<String>? = null
) {
    companion object {
        const val REMOVAL_RECOMMENDED = "Recommended"
        const val REMOVAL_ADVANCED = "Advanced"
        const val REMOVAL_EXPERT = "Expert"
        const val REMOVAL_UNSAFE = "Unsafe"
        
        const val LIST_AOSP = "Aosp"
        const val LIST_CARRIER = "Carrier"
        const val LIST_GOOGLE = "Google"
        const val LIST_OEM = "Oem"
        const val LIST_MISC = "Misc"
    }
    
    /**
     * Get removal safety level as enum
     */
    fun getRemovalSafety(): RemovalSafety {
        return when (removal.replaceFirstChar { it.uppercase() }) {
            REMOVAL_RECOMMENDED -> RemovalSafety.SAFE
            REMOVAL_ADVANCED -> RemovalSafety.CAUTION
            REMOVAL_EXPERT -> RemovalSafety.UNSAFE
            REMOVAL_UNSAFE -> RemovalSafety.UNSAFE
            else -> RemovalSafety.UNKNOWN
        }
    }
    
    /**
     * Check if this app is safe to remove
     */
    fun isSafeToRemove(): Boolean {
        // Only Recommended and Advanced are considered somewhat safe for general users
        return removal.replaceFirstChar { it.uppercase() } in listOf(REMOVAL_RECOMMENDED, REMOVAL_ADVANCED)
    }
}

/**
 * Removal safety levels for UI display
 */
enum class RemovalSafety {
    SAFE,        // Green badge: Can be removed without issues
    REPLACEABLE, // Blue badge: Can be replaced with alternatives  
    CAUTION,     // Yellow badge: May affect some features
    UNSAFE,      // Red badge: Do NOT remove - will break system
    UNKNOWN      // Gray badge: User decides
}

/**
 * Singleton database of known bloatware apps.
 * Loaded from debloat.json asset file.
 */
object BloatwareDatabase {
    private const val UAD_JSON_URL = "https://raw.githubusercontent.com/Universal-Debloater-Alliance/universal-android-debloater-next-generation/main/resources/assets/uad_lists.json"
    private const val CACHE_FILE_NAME = "uad_cache.json"

    private var debloatMap: Map<String, DebloatObject>? = null
    
    /**
     * Load the bloatware database.
     * Tries cache first, then falls back to assets.
     */
    fun load(context: Context) {
        if (debloatMap != null) return
        
        try {
            val gson = Gson()
            val type = object : TypeToken<Map<String, DebloatObject>>() {}.type

            // 1. Try Cache
            val cacheFile = File(context.filesDir, CACHE_FILE_NAME)
            if (cacheFile.exists()) {
                val json = cacheFile.readText()
                debloatMap = gson.fromJson(json, type)
            }

            // 2. Fallback to Asset (if cache failed or empty)
            if (debloatMap == null || debloatMap!!.isEmpty()) {
                val json = context.assets.open("debloat.json").bufferedReader().use { it.readText() }
                
                if (json.trim().startsWith("[")) {
                     // Old list format
                     val list = gson.fromJson(json, Array<DebloatObject>::class.java).toList()
                     debloatMap = list.associateBy { it.packageName }
                } else {
                     // New map format: { "pkg.name": { ... }, ... }
                     val map = gson.fromJson<Map<String, DebloatObject>>(json, type)
                     // Re-create the map giving each object its package name from the key
                     debloatMap = map.mapValues { (pkg, obj) -> obj.copy(packageName = pkg) }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            debloatMap = emptyMap()
        }
    }

    /**
     * Download latest definitions from GitHub and update cache.
     * Call this from a background thread (e.g. ViewModel init).
     */
    fun sync(context: Context): Boolean {
        return try {
            val url = URL(UAD_JSON_URL)
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 10000
            connection.readTimeout = 10000
            connection.requestMethod = "GET"
            
            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val json = connection.inputStream.bufferedReader().use { it.readText() }
                
                // Validate JSON before saving
                val gson = Gson()
                val type = object : TypeToken<Map<String, DebloatObject>>() {}.type
                val newMap: Map<String, DebloatObject> = gson.fromJson(json, type)
                
                if (newMap.isNotEmpty()) {
                    // Re-create with package names from keys to validate and store in-memory
                    val processedMap = newMap.mapValues { (pkg, obj) -> obj.copy(packageName = pkg) }
                    
                    // Save to cache
                    val cacheFile = File(context.filesDir, CACHE_FILE_NAME)
                    cacheFile.writeText(json)
                    
                    // Update in-memory
                    debloatMap = processedMap
                    true
                } else {
                    false
                }
            } else {
                false
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
    
    /**
     * Get bloatware info for a package
     */
    fun getBloatwareInfo(context: Context, packageName: String): DebloatObject? {
        load(context)
        // UAD JSON uses package name as key, but sometimes the ID inside object is different (rare).
        // Our map is keyed by package name from JSON root.
        return debloatMap?.get(packageName)
    }
    
    /**
     * Check if a package is known bloatware
     */
    fun isBloatware(context: Context, packageName: String): Boolean {
        load(context)
        return debloatMap?.containsKey(packageName) == true
    }
    
    /**
     * Get removal safety for a package
     */
    fun getRemovalSafety(context: Context, packageName: String): RemovalSafety {
        val info = getBloatwareInfo(context, packageName)
        return info?.getRemovalSafety() ?: RemovalSafety.UNKNOWN
    }
    
    /**
     * Get all bloatware entries
     */
    fun getAllBloatware(context: Context): List<DebloatObject> {
        load(context)
        return debloatMap?.values?.toList() ?: emptyList()
    }
    
    /**
     * Get count of bloatware entries
     */
    fun getCount(context: Context): Int {
        load(context)
        return debloatMap?.size ?: 0
    }
    /**
     * Clear the in-memory database to free RAM.
     * Call this after a full scan is complete.
     */
    fun clear() {
        debloatMap = null
    }
}
