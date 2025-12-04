package com.mustakim.bokbok.ui.screens.notifications

import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import com.mustakim.bokbok.data.model.FriendRequest
import com.mustakim.bokbok.data.model.Notification
import com.mustakim.bokbok.data.model.NotificationType
import com.mustakim.bokbok.data.repository.RoomRepository
import com.mustakim.bokbok.state.JoinMode
import com.mustakim.bokbok.state.RoomStateManager
import com.mustakim.bokbok.viewmodel.LoungeViewModel
import com.mustakim.bokbok.viewmodel.NotificationFilter
import com.mustakim.bokbok.viewmodel.NotificationUiItem
import com.mustakim.bokbok.viewmodel.NotificationViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.cos
import kotlin.math.sin

// --- Custom "Expressive" Shape (Scalloped/Star) ---
class ExpressiveStarShape(private val points: Int = 12) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val path = Path()
        val centerX = size.width / 2f
        val centerY = size.height / 2f
        val radius = size.width / 2f
        val innerRadius = radius * 0.85f // Soft scallop depth

        for (i in 0 until points * 2) {
            val angle = Math.PI * i / points
            val r = if (i % 2 == 0) radius else innerRadius
            val x = centerX + r * sin(angle).toFloat()
            val y = centerY - r * cos(angle).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        return Outline.Generic(path)
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun NotificationsScreen(
    navController: NavHostController,
    loungeViewModel: LoungeViewModel = viewModel(),
    viewModel: NotificationViewModel
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val roomRepo = remember { RoomRepository() }
    val scope = rememberCoroutineScope()

    // Scroll behavior for LargeTopAppBar
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

    val uiItems by viewModel.uiItems.collectAsState()
    val currentFilter by viewModel.filter.collectAsState()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            Column {
                LargeTopAppBar(
                    title = {
                        Text(
                            "Inbox",
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-0.5).sp
                        )
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = { navController.navigateUp() },
                            modifier = Modifier
                                .padding(start = 8.dp)
                                .background(
                                    MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f),
                                    CircleShape
                                )
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                        navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                        titleContentColor = MaterialTheme.colorScheme.onSurface,
                        actionIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    scrollBehavior = scrollBehavior
                )

                // Filter Chips
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(bottom = 8.dp)
                ) {
                    item {
                        FilterChip(
                            selected = currentFilter == NotificationFilter.ALL,
                            onClick = { viewModel.setFilter(NotificationFilter.ALL) },
                            label = { Text("All") },
                            leadingIcon = if (currentFilter == NotificationFilter.ALL) {
                                { Icon(Icons.Default.Check, null, Modifier.size(16.dp)) }
                            } else null
                        )
                    }
                    item {
                        FilterChip(
                            selected = currentFilter == NotificationFilter.INVITES,
                            onClick = { viewModel.setFilter(NotificationFilter.INVITES) },
                            label = { Text("Invites") }
                        )
                    }
                    item {
                        FilterChip(
                            selected = currentFilter == NotificationFilter.REQUESTS,
                            onClick = { viewModel.setFilter(NotificationFilter.REQUESTS) },
                            label = { Text("Requests") }
                        )
                    }
                }
            }
        }
    ) { padding ->
        if (uiItems.isEmpty()) {
            EmptyStateExpressive(modifier = Modifier.padding(padding))
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    top = padding.calculateTopPadding() + 8.dp,
                    bottom = 24.dp,
                    start = 16.dp,
                    end = 16.dp
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(
                    items = uiItems,
                    key = { it.id }
                ) { item ->
                    Box(
                        modifier = Modifier.animateItem(
                            placementSpec = spring(
                                dampingRatio = 0.7f,
                                stiffness = 300f
                            )
                        )
                    ) {
                        when (item) {
                            is NotificationUiItem.Standard -> {
                                ExpressiveNotificationCard(
                                    notification = item.notification,
                                    onAccept = {
                                        haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                        if (item.notification.type == NotificationType.ROOM_INVITE) {
                                            val roomId = item.notification.payload["roomId"]
                                            if (roomId != null) {
                                                // Check permissions
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
                                                    navController.navigate(com.mustakim.bokbok.ui.navigation.NavRoutes.Permissions.route)
                                                } else {
                                                    scope.launch {
                                                        val result = roomRepo.getRoom(roomId)
                                                        result.onSuccess { room ->
                                                            loungeViewModel.joinRoomSessionOnly(room)
                                                            RoomStateManager.joinRoom(room, JoinMode.SESSION_ONLY)
                                                            navController.navigateUp()
                                                            viewModel.deleteNotification(item.notification.id)
                                                        }.onFailure {
                                                            viewModel.deleteNotification(item.notification.id)
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    },
                                    onReject = {
                                        haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                                        viewModel.deleteNotification(item.notification.id)
                                    }
                                )
                            }
                            is NotificationUiItem.Request -> {
                                ExpressiveFriendRequestCard(
                                    request = item.request,
                                    onAccept = {
                                        haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                        viewModel.acceptFriendRequest(item.request.friendship.id)
                                    },
                                    onDecline = {
                                        haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                                        viewModel.declineFriendRequest(item.request.friendship.id)
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ExpressiveFriendRequestCard(
    request: FriendRequest,
    onAccept: () -> Unit,
    onDecline: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        onClick = { }
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Box(modifier = Modifier.size(56.dp)) {
                    AsyncImage(
                        model = request.sender.profileImageUrl,
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(18.dp)),
                        contentScale = ContentScale.Crop
                    )

                    // Badge
                    Surface(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .offset(x = 6.dp, y = 6.dp),
                        shape = ExpressiveStarShape(12),
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        border = androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.surfaceContainerLow)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PersonAdd,
                            contentDescription = null,
                            modifier = Modifier
                                .padding(6.dp)
                                .size(14.dp),
                            tint = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = request.sender.displayName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Surface(
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f),
                            shape = CircleShape
                        ) {
                            Text(
                                text = getTimeAgo(request.friendship.createdAt.toDate().time),
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            )
                        }
                    }

                    Text(
                        text = "@${request.sender.username}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(Modifier.height(4.dp))

                    Text(
                        text = "Sent you a friend request",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = onDecline,
                    modifier = Modifier.weight(1f).height(50.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f),
                        contentColor = MaterialTheme.colorScheme.onSurface
                    ),
                    shape = CircleShape,
                    elevation = null
                ) {
                    Icon(Icons.Default.Close, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Decline")
                }

                Button(
                    onClick = onAccept,
                    modifier = Modifier.weight(1f).height(50.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondary,
                        contentColor = MaterialTheme.colorScheme.onSecondary
                    ),
                    shape = CircleShape
                ) {
                    Icon(Icons.Default.Check, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Accept")
                }
            }
        }
    }
}

@Composable
fun ExpressiveNotificationCard(
    notification: Notification,
    onAccept: () -> Unit,
    onReject: () -> Unit
) {
    val isRoomInvite = notification.type == NotificationType.ROOM_INVITE
    val isUnread = !notification.isRead

    // "Expressive" cards use high-surface containers and very round corners
    val backgroundColor = if (isUnread)
        MaterialTheme.colorScheme.surfaceContainerHighest
    else
        MaterialTheme.colorScheme.surface

    val contentColor = MaterialTheme.colorScheme.onSurface


    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp), // Extra large corners
        color = backgroundColor,
        onClick = { /* Expand details? */ }
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Row(verticalAlignment = Alignment.Top) {
                // Avatar with "Squircle" or custom shape
                Box(modifier = Modifier.size(56.dp)) {
                    if (notification.senderImageUrl.isNotEmpty()) {
                        AsyncImage(
                            model = notification.senderImageUrl,
                            contentDescription = null,
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(18.dp)), // Squircle-ish
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    MaterialTheme.colorScheme.primaryContainer,
                                    RoundedCornerShape(18.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = notification.senderName.take(1).uppercase(),
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }

                    // Expressive Icon Badge (Starburst)
                    if (isRoomInvite) {
                        Surface(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .offset(x = 6.dp, y = 6.dp),
                            shape = ExpressiveStarShape(12), // Custom Star Shape
                            color = MaterialTheme.colorScheme.tertiaryContainer,
                            border = androidx.compose.foundation.BorderStroke(2.dp, backgroundColor)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Call,
                                contentDescription = null,
                                modifier = Modifier
                                    .padding(6.dp)
                                    .size(14.dp),
                                tint = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    // Header Row
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = notification.senderName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = contentColor
                        )

                        // Time Badge
                        Surface(
                            color = contentColor.copy(alpha = 0.1f),
                            shape = CircleShape
                        ) {
                            Text(
                                text = getTimeAgo(notification.createdAt),
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                color = contentColor.copy(alpha = 0.7f)
                            )
                        }
                    }

                    Spacer(Modifier.height(4.dp))

                    Text(
                        text = notification.title,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color = contentColor.copy(alpha = 0.9f)
                    )

                    if (notification.body.isNotEmpty()) {
                        Text(
                            text = notification.body,
                            style = MaterialTheme.typography.bodyMedium,
                            color = contentColor.copy(alpha = 0.7f),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // Action Buttons (Full width, pill shaped)
            if (isRoomInvite) {
                Spacer(Modifier.height(20.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    // Decline Button
                    Button(
                        onClick = onReject,
                        modifier = Modifier.weight(1f).height(50.dp), // Taller touch target
                        colors = ButtonDefaults.buttonColors(
                            containerColor = contentColor.copy(alpha = 0.1f),
                            contentColor = contentColor
                        ),
                        shape = CircleShape, // Pill shape
                        elevation = null
                    ) {
                        Icon(Icons.Default.Close, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Decline")
                    }

                    // Accept Button
                    Button(
                        onClick = onAccept,
                        modifier = Modifier.weight(1f).height(50.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        shape = CircleShape
                    ) {
                        Icon(Icons.Default.Check, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Accept")
                    }
                }
            }
        }
    }
}

@Composable
fun EmptyStateExpressive(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Large Expressive Icon
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(140.dp)
                .background(
                    MaterialTheme.colorScheme.surfaceContainerHigh,
                    ExpressiveStarShape(16)
                )
        ) {
            Icon(
                imageVector = Icons.Rounded.NotificationsOff,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(Modifier.height(32.dp))

        Text(
            text = "All Caught Up!",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = "Your notification tray is empty for now.\nTime to start a conversation?",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}


fun getTimeAgo(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp

    return when {
        diff < TimeUnit.MINUTES.toMillis(1) -> "Just now"
        diff < TimeUnit.HOURS.toMillis(1) -> {
            val minutes = TimeUnit.MILLISECONDS.toMinutes(diff)
            "${minutes}m ago"
        }
        diff < TimeUnit.DAYS.toMillis(1) -> {
            val hours = TimeUnit.MILLISECONDS.toHours(diff)
            "${hours}h ago"
        }
        diff < TimeUnit.DAYS.toMillis(7) -> {
            val days = TimeUnit.MILLISECONDS.toDays(diff)
            "${days}d ago"
        }
        else -> {
            val dateFormat = SimpleDateFormat("MMM dd", Locale.getDefault())
            dateFormat.format(Date(timestamp))
        }
    }
}
