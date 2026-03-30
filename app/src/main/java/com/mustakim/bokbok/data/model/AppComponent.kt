package com.mustakim.bokbok.data.model

data class AppComponent(
    val packageName: String,
    val name: String,
    val label: String,
    val isEnabled: Boolean,
    val isExported: Boolean,
    val processName: String? = null
)
