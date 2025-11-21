package com.mustakim.bokbok.ui.screens.notifications

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mustakim.bokbok.data.model.Notification
import com.mustakim.bokbok.data.model.NotificationType
import com.mustakim.bokbok.data.repository.NotificationRepository
import com.mustakim.bokbok.data.repository.UserRepository
import com.mustakim.bokbok.data.repository.RoomRepository
import com.mustakim.bokbok.state.RoomStateManager
import com.mustakim.bokbok.state.JoinMode
import com.mustakim.bokbok.viewmodel.LoungeViewModel
import kotlinx.coroutines.launch
import androidx.compose.ui.platform.LocalContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsScreen(
    navController: NavHostController,
    loungeViewModel: LoungeViewModel = viewModel()
) {
    val context = LocalContext.current
    val repo = remember { NotificationRepository() } // In real app, use Hilt/ViewModel
    val userRepo = remember { UserRepository(context) }
    val roomRepo = remember { RoomRepository() }
    val scope = rememberCoroutineScope()
    
    var notifications by remember { mutableStateOf<List<Notification>>(emptyList()) }

    LaunchedEffect(Unit) {
        val userId = userRepo.getCurrentUserId()
        if (userId != null) {
            repo.observeNotifications(userId).collect {
                notifications = it
            }
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Notifications") }) }
    ) { padding ->
        LazyColumn(modifier = Modifier.padding(padding)) {
            items(notifications) { notification ->
                NotificationItem(
                    notification = notification,
                    onAccept = {
                        if (notification.type == NotificationType.ROOM_INVITE) {
                            val roomId = notification.payload["roomId"]
                            if (roomId != null) {
                                // Check permissions first
                                val permissionsToCheck = mutableListOf(android.Manifest.permission.RECORD_AUDIO)
                                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                                    permissionsToCheck.add(android.Manifest.permission.POST_NOTIFICATIONS)
                                }
                                
                                val hasPermissions = permissionsToCheck.all {
                                    androidx.core.content.ContextCompat.checkSelfPermission(
                                        context, it
                                    ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                                }

                                if (!hasPermissions) {
                                    // Navigate to permissions screen or show rationale
                                    // For now, let's redirect to PermissionsScreen if available or just show a toast/snackbar
                                    // Ideally, we should use a permission launcher here, but since we are in a list item, 
                                    // it's cleaner to navigate to a dedicated permission handling flow or show a dialog.
                                    // Given the NavGraph, we can navigate to PermissionsScreen.
                                    navController.navigate(com.mustakim.bokbok.ui.navigation.NavRoutes.Permissions.route)
                                } else {
                                    // Fetch the room data and join using RoomStateManager
                                    scope.launch {
                                        val result = roomRepo.getRoom(roomId)
                                        result.onSuccess { room ->
                                            // Join as session-only (temporary join)
                                            loungeViewModel.joinRoomSessionOnly(room)
                                            RoomStateManager.joinRoom(room, JoinMode.SESSION_ONLY)
                                            
                                            // Delete notification after accepting
                                            userRepo.getCurrentUserId()?.let { uid ->
                                                repo.deleteNotification(uid, notification.id) 
                                            }
                                        }.onFailure {
                                            // Handle error - room might not exist anymore
                                            userRepo.getCurrentUserId()?.let { uid ->
                                                repo.deleteNotification(uid, notification.id)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    },
                    onReject = {
                        scope.launch {
                            userRepo.getCurrentUserId()?.let { uid ->
                                repo.deleteNotification(uid, notification.id)
                            }
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun NotificationItem(
    notification: Notification,
    onAccept: () -> Unit,
    onReject: () -> Unit
) {
    ListItem(
        headlineContent = { Text(notification.title) },
        supportingContent = { Text(notification.body) },
        trailingContent = {
            if (notification.type == NotificationType.ROOM_INVITE) {
                Row {
                    TextButton(onClick = onReject) { Text("Reject") }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = onAccept) { Text("Accept") }
                }
            }
        }
    )
}
