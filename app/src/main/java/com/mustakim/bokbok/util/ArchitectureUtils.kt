package com.mustakim.bokbok.util

import android.os.Build

/**
 * Utility to handle architecture-specific feature availability.
 */
object ArchitectureUtils {
    /**
     * Checks if the current device is running a 64-bit architecture.
     * Used to disable features that lack 32-bit native libraries.
     */
    fun is64Bit(): Boolean {
        return Build.SUPPORTED_64_BIT_ABIS.isNotEmpty()
    }
}
