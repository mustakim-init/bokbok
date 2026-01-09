package com.mustakim.bokbok.util

import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.os.Build
import timber.log.Timber

/**
 * Utility object for querying device video encoder capabilities.
 * Used to validate and filter UI options for screen recording.
 */
object EncoderCapabilities {
    
    private val codecList by lazy { MediaCodecList(MediaCodecList.REGULAR_CODECS) }
    
    data class EncoderInfo(
        val name: String,
        val mimeType: String,
        val maxWidth: Int,
        val maxHeight: Int,
        val maxFps: Int,
        val maxBitrate: Int,
        val isHardwareAccelerated: Boolean
    )
    
    /**
     * Checks if a specific MIME type encoder is available on the device.
     */
    fun isEncoderAvailable(mimeType: String): Boolean {
        return codecList.codecInfos.any { info ->
            info.isEncoder && info.supportedTypes.any { it.equals(mimeType, ignoreCase = true) }
        }
    }
    
    /**
     * Checks if HEVC (H.265) encoding is supported.
     */
    fun isHevcSupported(): Boolean = isEncoderAvailable("video/hevc")
    
    /**
     * Checks if AVC (H.264) encoding is supported.
     */
    fun isAvcSupported(): Boolean = isEncoderAvailable("video/avc")
    
    /**
     * Gets the maximum supported frame rate for a given encoder and resolution.
     */
    fun getMaxSupportedFps(mimeType: String, width: Int, height: Int): Int {
        val encoder = findEncoder(mimeType) ?: return 60 // Default fallback
        val caps = encoder.getCapabilitiesForType(mimeType)?.videoCapabilities ?: return 60
        
        return try {
            val frameRateRange = caps.getSupportedFrameRatesFor(width, height)
            frameRateRange?.upper?.toInt() ?: 60
        } catch (e: Exception) {
            Timber.w(e, "Could not get frame rate for ${width}x${height}")
            60
        }
    }
    
    /**
     * Checks if a specific resolution is supported by the encoder.
     */
    fun isResolutionSupported(mimeType: String, width: Int, height: Int): Boolean {
        val encoder = findEncoder(mimeType) ?: return false
        val caps = encoder.getCapabilitiesForType(mimeType)?.videoCapabilities ?: return false
        
        return try {
            caps.isSizeSupported(width, height)
        } catch (e: Exception) {
            Timber.w(e, "Could not check resolution support")
            false
        }
    }
    
    /**
     * Gets the max supported bitrate for a given encoder.
     */
    fun getMaxBitrate(mimeType: String): Int {
        val encoder = findEncoder(mimeType) ?: return 50_000_000
        val caps = encoder.getCapabilitiesForType(mimeType)?.videoCapabilities ?: return 50_000_000
        return caps.bitrateRange.upper
    }
    
    /**
     * Returns a list of supported FPS values for the given encoder and resolution.
     */
    fun getSupportedFpsList(mimeType: String, width: Int, height: Int): List<Int> {
        val maxFps = getMaxSupportedFps(mimeType, width, height)
        val allFps = listOf(30, 60, 90, 120)
        return allFps.filter { it <= maxFps }
    }
    
    /**
     * Returns a list of supported resolutions from the standard set.
     */
    fun getSupportedResolutions(mimeType: String): List<String> {
        val standardResolutions = mapOf(
            "480p" to (854 to 480),
            "720p" to (1280 to 720),
            "1080p" to (1920 to 1080),
            "2K" to (2560 to 1440),
            "4K" to (3840 to 2160)
        )
        
        return standardResolutions.filter { (_, dims) ->
            isResolutionSupported(mimeType, dims.first, dims.second)
        }.keys.toList()
    }
    
    /**
     * Gets detailed encoder info for logging/debugging.
     */
    fun getEncoderInfo(mimeType: String): EncoderInfo? {
        val encoder = findEncoder(mimeType) ?: return null
        val caps = encoder.getCapabilitiesForType(mimeType)?.videoCapabilities ?: return null
        
        val isHardware = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            encoder.isHardwareAccelerated
        } else {
            !encoder.name.contains("sw", ignoreCase = true)
        }
        
        return EncoderInfo(
            name = encoder.name,
            mimeType = mimeType,
            maxWidth = caps.supportedWidths.upper,
            maxHeight = caps.supportedHeights.upper,
            maxFps = (caps.supportedFrameRates?.upper ?: 60.0).toInt(),
            maxBitrate = caps.bitrateRange.upper,
            isHardwareAccelerated = isHardware
        )
    }
    
    private fun findEncoder(mimeType: String): MediaCodecInfo? {
        return codecList.codecInfos.firstOrNull { info ->
            info.isEncoder && info.supportedTypes.any { it.equals(mimeType, ignoreCase = true) }
        }
    }
}
