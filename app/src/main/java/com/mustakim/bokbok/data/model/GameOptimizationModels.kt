package com.mustakim.bokbok.data.model

enum class OptimizationProfile {
    BATTERY,
    BALANCED,
    PERFORMANCE,
    CUSTOM
}

enum class TweakType {
    TOGGLE,
    SELECT, // For resolution, refresh rate, etc.
    INPUT  // For custom values
}

data class TweakDef(
    val id: String,
    val title: String,
    val description: String,
    val type: TweakType = TweakType.TOGGLE,
    val isGlobal: Boolean = false,
    val requiresAdb: Boolean = true,
    val options: List<String>? = null,
    val category: String
)

enum class CompileMode(val value: String) {
    NONE("none"),
    SPEED("speed"),
    EVERYTHING("everything")
}
