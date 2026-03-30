package com.mustakim.bokbok.data.model

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
    val category: String,
    val minSdk: Int = 1,
    val maxSdk: Int = Int.MAX_VALUE,
    val manufacturer: String? = null, // e.g. "samsung", "xiaomi"
    val warning: String? = null // For caution tweaks
)

enum class CompileMode(val value: String) {
    NONE("none"),
    SPEED("speed"),
    EVERYTHING("everything")
}
