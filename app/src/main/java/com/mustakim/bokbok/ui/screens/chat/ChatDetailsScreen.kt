package com.mustakim.bokbok.ui.screens.chat

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PersonRemove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import com.mustakim.bokbok.data.model.User
import com.mustakim.bokbok.ui.theme.PuffyShape
import com.mustakim.bokbok.ui.theme.getMorphingShape
import com.mustakim.bokbok.data.repository.HybridGroupChatRepository
import kotlinx.coroutines.launch
import com.mustakim.bokbok.ui.shared.BokBokIconButton

// Need a way to access repository. Ideally passed via param, but for now we'll assume injection or static access isn't available easily.
// We will modify the NavGraph to pass a callback later. For now, this compiles but needs the callback.
// To avoid breaking the build, I will add the callback to the signature and make it optional or default to no-op if meaningful.
// Actually, I should update the calling code in NavGraph too.

@Composable
fun ChatDetailsScreen(
    user: User?,
    isGroup: Boolean,
    groupName: String?,
    groupImageUrl: String? = null,
    members: List<User> = emptyList(),
    creatorName: String? = null,
    // Added callback for image update
    onUpdateGroupImage: ((android.net.Uri) -> Unit)? = null,
    onRemoveGroupImage: (() -> Unit)? = null,
    onDeleteGroup: (() -> Unit)? = null,
    isUploadingImage: Boolean = false,
    uploadError: String? = null,
    onClearUploadError: () -> Unit = {},
    onBackClick: () -> Unit,
    onMuteClick: () -> Unit,
    canMute: Boolean = true,
    isMuted: Boolean = false,
    onClearHistory: () -> Unit,
    onRemoveFriend: () -> Unit,
    onAddMember: () -> Unit,
    onSeeMembers: () -> Unit
) {
    var showClearHistoryDialog by remember { mutableStateOf(false) }
    var showRemoveDialog by remember { mutableStateOf(false) }
    var showDeleteGroupDialog by remember { mutableStateOf(false) }
    var showImageOptionsDialog by remember { mutableStateOf(false) }
    
    val context = LocalContext.current
    LaunchedEffect(uploadError) {
        uploadError?.let {
            android.widget.Toast.makeText(context, it, android.widget.Toast.LENGTH_LONG).show()
            onClearUploadError()
        }
    }

    // Image Upload State
    val imageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { onUpdateGroupImage?.invoke(it) }
    }

    // Scroll State for Collapsing Header
    val scrollState = rememberScrollState()

    // Constants
    val maxHeaderHeight = 320.dp
    val minHeaderHeight = 84.dp
    val density = LocalDensity.current
    val headerHeightPx = with(density) { maxHeaderHeight.toPx() }
    val minHeaderHeightPx = with(density) { minHeaderHeight.toPx() }

    // Derived Animation Values
    val scrollY = scrollState.value
    // Value from 0.0 (Expanded) to 1.0 (Collapsed)
    // We start collapsing immediately but it finishes when we scroll the distance diff
    val collapseRange = headerHeightPx - minHeaderHeightPx
    val collapseFactor = (scrollY / collapseRange).coerceIn(0f, 1f)

    // Image Size: 160dp -> 48dp (larger when expanded)
    val imageSize = androidx.compose.ui.unit.lerp(160.dp, 48.dp, collapseFactor)

    // Image Shape: Smooth morph from Puffy (0f) -> Circle (1f)
    val imageShape = remember(collapseFactor) { getMorphingShape(collapseFactor) }

    // Text Alpha
    val expandedTextAlpha = (1f - collapseFactor * 1.5f).coerceIn(0f, 1f)
    val collapsedTextAlpha = ((collapseFactor - 0.8f) * 5f).coerceIn(0f, 1f)

    Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize()) {
            val bgImageUrl = if (isGroup) groupImageUrl else user?.profileImageUrl
            com.mustakim.bokbok.ui.shared.DynamicMeshGradientBackground(
                imageUrl = bgImageUrl,
                coverage = 1f
            )

            // 1. Content Body (Scrollable)
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(top = maxHeaderHeight) // Content starts BELOW header space
                    .padding(bottom = paddingValues.calculateBottomPadding()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                 // Info Text that fades out quickly
                 if (isGroup) {
                    Text(
                        text = "${members.size} members",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .alpha(expandedTextAlpha)
                            .padding(top = 8.dp)
                    )
                    
                    if (creatorName != null) {
                         Text(
                             text = "Created by $creatorName",
                             style = MaterialTheme.typography.labelMedium,
                             color = MaterialTheme.colorScheme.outline,
                             modifier = Modifier
                                 .alpha(expandedTextAlpha)
                                 .padding(top = 4.dp)
                         )
                    }
                 }

                 Spacer(modifier = Modifier.height(32.dp))

                 // Action Buttons
                 Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                     ExpressiveActionButton(
                         modifier = Modifier.weight(1f),
                         icon = Icons.Default.Call,
                         label = "Audio",
                         containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f),
                         contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                     ) { /* TODO */ }

                     if (isGroup) {
                         ExpressiveActionButton(
                             modifier = Modifier.weight(1f),
                             icon = Icons.Default.PersonAdd,
                             label = "Add",
                             containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.6f),
                             contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                         ) { onAddMember() }
                     } else {
                         ExpressiveActionButton(
                             modifier = Modifier.weight(1f),
                             icon = Icons.Default.Search,
                             label = "Search",
                             containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.6f),
                             contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                         ) { /* TODO */ }
                     }

                     ExpressiveActionButton(
                         modifier = Modifier.weight(1f),
                         icon = if (isMuted) Icons.Default.NotificationsOff else Icons.Default.Notifications,
                         label = if (isMuted) "Unmute" else "Mute",
                         containerColor = if (isMuted) MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.6f) else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                         contentColor = if (isMuted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onPrimaryContainer
                     ) { onMuteClick() }
                }

                Spacer(modifier = Modifier.height(32.dp))

                // Options Section
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "Options",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 8.dp)
                    )

                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.6f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column {
                            if (isGroup) {
                                ExpressiveOptionItem(
                                    icon = Icons.Default.Group,
                                    label = "See chat members",
                                    onClick = { onSeeMembers() }
                                )
                                HorizontalDivider(color = MaterialTheme.colorScheme.surface, thickness = 1.dp)
                            }

                            if (isGroup && user == null) {
                                // Owner: Show delete group option
                                // We check if user is null (meaning we are viewing the group itself, not a member details)
                                // But actual check should be based on groupInfo ownership which is passed separately.
                                // For now, we assume if we have onDeleteGroup callback, we show it.
                                if (onDeleteGroup != null) {
                                     ExpressiveOptionItem(
                                        icon = Icons.Default.Delete,
                                        label = "Delete group",
                                        onClick = { showDeleteGroupDialog = true },
                                        color = MaterialTheme.colorScheme.error
                                    )
                                    HorizontalDivider(color = MaterialTheme.colorScheme.surface, thickness = 1.dp)
                                }
                            }

                            ExpressiveOptionItem(
                                icon = Icons.Default.Delete,
                                label = "Delete chat history",
                                onClick = { showClearHistoryDialog = true },
                                color = MaterialTheme.colorScheme.error
                            )

                            HorizontalDivider(color = MaterialTheme.colorScheme.surface, thickness = 1.dp)

                            if (isGroup) {
                                ExpressiveOptionItem(
                                    icon = Icons.Default.Block,
                                    label = "Leave group",
                                    onClick = { showRemoveDialog = true },
                                    color = MaterialTheme.colorScheme.error
                                )
                            } else {
                                ExpressiveOptionItem(
                                    icon = Icons.Default.PersonRemove,
                                    label = "Remove friend",
                                    onClick = { showRemoveDialog = true },
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(100.dp))
            }

            // 2. Collapsing Header (Fixed at top, changes height)
            Surface(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .height(max(minHeaderHeight, maxHeaderHeight - with(density) { scrollY.toDp() }))
                    .zIndex(1f),
                color = MaterialTheme.colorScheme.surface.copy(alpha = if (collapseFactor > 0.9f) 0.8f else 0f),
                shadowElevation = if (collapseFactor > 0.9f) 4.dp else 0.dp
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    // Back Button (Always visible)
                     BokBokIconButton(
                         onClick = onBackClick,
                         modifier = Modifier
                             .align(Alignment.TopStart)
                             .statusBarsPadding()
                             .padding(start = 4.dp)
                             .zIndex(2f)
                     ) {
                         Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                     }

                     // Central Content (Image + Name)
                     Column(
                         modifier = Modifier
                             .align(Alignment.Center),
                         horizontalAlignment = Alignment.CenterHorizontally
                     ) {
                         // Image Container
                         val displayName = if (isGroup) groupName ?: "Group" else user?.displayName ?: "Unknown"
                         val imageUrl = if (isGroup) groupImageUrl else user?.profileImageUrl
                         
                         var isImageLoaded by remember(imageUrl) { mutableStateOf(false) }

                         Box(
                             modifier = Modifier
                                 .size(imageSize)
                                 .clip(imageShape)
                                 .background(
                                     brush = if (!isImageLoaded || imageUrl == null) {
                                         androidx.compose.ui.graphics.Brush.linearGradient(
                                             colors = listOf(
                                                 MaterialTheme.colorScheme.primaryContainer,
                                                 MaterialTheme.colorScheme.tertiaryContainer
                                             )
                                         )
                                     } else {
                                         androidx.compose.ui.graphics.Brush.linearGradient(
                                             colors = listOf(Color.Transparent, Color.Transparent)
                                         )
                                     }
                                 )
                                 .border(
                                     width = 3.dp,
                                     color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                                     shape = imageShape
                                 )
                                 .clickable(enabled = isGroup) {
                                     if (isGroup && !imageUrl.isNullOrEmpty()) {
                                         // If image exists, show options
                                         showImageOptionsDialog = true
                                     } else {
                                         // No image (or not group - though we disabled for not group), open picker
                                         imageLauncher.launch("image/*")
                                     }
                                 },
                             contentAlignment = Alignment.Center
                         ) {
                             if (!imageUrl.isNullOrEmpty()) {
                                 AsyncImage(
                                     model = imageUrl,
                                     contentDescription = null,
                                     contentScale = ContentScale.Crop,
                                     modifier = Modifier.fillMaxSize(),
                                     onSuccess = { isImageLoaded = true },
                                     onError = { isImageLoaded = false }
                                 )
                             } else {
                                 // Placeholder / Upload Icon
                                 if (isGroup) {
                                     Icon(
                                         imageVector = Icons.Default.CameraAlt,
                                         contentDescription = "Upload",
                                         tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                         modifier = Modifier.size(40.dp)
                                     )
                                 } else {
                                     Text(
                                         text = displayName.take(1).uppercase(),
                                         style = MaterialTheme.typography.displayMedium,
                                         fontWeight = FontWeight.Bold,
                                         color = MaterialTheme.colorScheme.onPrimaryContainer
                                     )
                                 }
                             }
                             
                             if (isUploadingImage) {
                                 CircularProgressIndicator(
                                     color = MaterialTheme.colorScheme.onPrimaryContainer,
                                     modifier = Modifier.size(32.dp)
                                 )
                             }
                         }
                         
                         Spacer(modifier = Modifier.height(16.dp))
                         
                         // Name (fades out when collapsed)
                         if (collapseFactor < 0.6f) {
                             Text(
                                 text = displayName,
                                 style = MaterialTheme.typography.headlineMedium,
                                 fontWeight = FontWeight.Bold,
                                 textAlign = TextAlign.Center,
                                 modifier = Modifier
                                     .alpha(expandedTextAlpha)
                                     .padding(horizontal = 24.dp)
                             )
                         }
                     }

                     // Collapsed Title (Appears in TopAppbar style)
                     if (collapseFactor > 0.6f) {
                         Text(
                             text = if (isGroup) groupName ?: "Group" else user?.displayName ?: "Unknown",
                             style = MaterialTheme.typography.titleMedium,
                             fontWeight = FontWeight.Bold,
                             modifier = Modifier
                                 .align(Alignment.TopCenter)
                                 .padding(top = 44.dp) // Align with back button center
                                 .alpha(collapsedTextAlpha)
                         )
                     }
                }
            }
        }
    }

    if (showClearHistoryDialog) {
        AlertDialog(
            onDismissRequest = { showClearHistoryDialog = false },
            title = { Text("Delete chat history?") },
            text = { Text("This will clear the chat history for YOU only. Other participants will still see the messages.") },
            confirmButton = {
                Button(
                    onClick = {
                        onClearHistory()
                        showClearHistoryDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearHistoryDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showRemoveDialog) {
        AlertDialog(
            onDismissRequest = { showRemoveDialog = false },
            title = { Text(if (isGroup) "Leave Group?" else "Remove Friend?") },
            text = {
                Text(if (isGroup)
                    "Are you sure you want to leave this group?"
                else
                    "Are you sure you want to remove this friend? This will also clear your chat history with them.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        onRemoveFriend()
                        showRemoveDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(if (isGroup) "Leave" else "Remove")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRemoveDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
    if (showDeleteGroupDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteGroupDialog = false },
            title = { Text("Delete Group?") },
            text = { Text("Are you sure you want to delete this group? This action cannot be undone and will remove the group for all members.") },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteGroup?.invoke()
                        showDeleteGroupDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteGroupDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showImageOptionsDialog) {
        ModalBottomSheet(
            onDismissRequest = { showImageOptionsDialog = false },
            sheetState = rememberModalBottomSheetState(),
            containerColor = MaterialTheme.colorScheme.surface,
            dragHandle = { BottomSheetDefaults.DragHandle() },
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 48.dp), // Extra padding for navigation bar
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Group Photo",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                // Change Photo Option
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable {
                            showImageOptionsDialog = false
                            imageLauncher.launch("image/*")
                        }
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.CameraAlt,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        text = "Change Photo",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                // Remove Photo Option
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable {
                            showImageOptionsDialog = false
                            onRemoveGroupImage?.invoke()
                        }
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        text = "Remove Photo",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@Composable
fun ExpressiveActionButton(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    label: String,
    containerColor: Color,
    contentColor: Color,
    onClick: () -> Unit
) {
    Column(
        modifier = modifier.clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(containerColor),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = contentColor,
                modifier = Modifier.size(32.dp)
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun ExpressiveOptionItem(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    color: Color = MaterialTheme.colorScheme.onSurface
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            color = color,
            fontWeight = FontWeight.Medium
        )
    }
}