package com.mustakim.bokbok.data.model

data class AppPermissionDetail(
    val permission: String,
    val label: String,
    val isGranted: Boolean,
    val isRuntime: Boolean
)
