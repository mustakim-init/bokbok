package com.mustakim.bokbok.data.model

import android.os.Build
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.vector.ImageVector


@Immutable
data class AppPermission(
    val permission: String,
    val title: String,
    val description: String,
    val icon: ImageVector,
    val isRequired: Boolean
)

object PermissionsList {
    val MICROPHONE = AppPermission(
        permission = android.Manifest.permission.RECORD_AUDIO,
        title = "Microphone",
        description = "Essential for voice calls and audio communication",
        icon = Icons.Default.Mic,
        isRequired = true
    )

    val NOTIFICATIONS = AppPermission(
        permission = android.Manifest.permission.POST_NOTIFICATIONS,
        title = "Notifications",
        description = "Get notified about incoming calls and messages",
        icon = Icons.Default.Notifications,
        isRequired = true
    )

    val CAMERA = AppPermission(
        permission = android.Manifest.permission.CAMERA,
        title = "Camera",
        description = "Optional for video calls and profile pictures",
        icon = Icons.Default.Videocam,
        isRequired = false
    )

    val CONTACTS = AppPermission(
        permission = android.Manifest.permission.READ_CONTACTS,
        title = "Contacts",
        description = "Optional to find friends already using BokBok",
        icon = Icons.Default.Contacts,
        isRequired = false
    )

    fun getAllPermissions(): List<AppPermission> {
        val permissions = mutableListOf(MICROPHONE, CAMERA, CONTACTS)

        // Only add POST_NOTIFICATIONS on Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(1, NOTIFICATIONS) // Add after microphone
        }

        return permissions
    }

    fun getRequiredPermissions(): List<AppPermission> {
        return getAllPermissions().filter { it.isRequired }
    }
}
