package com.mustakim.bokbok.ui.screens.profile

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import com.mustakim.bokbok.data.model.User
import com.mustakim.bokbok.viewmodel.ProfileViewModel
import com.mustakim.bokbok.viewmodel.UserViewModel
import com.mustakim.bokbok.ui.shared.BokBokIconButton
import com.mustakim.bokbok.ui.screens.common.MainScaffold

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    navController: NavHostController,
    profileViewModel: ProfileViewModel = hiltViewModel(),
    userViewModel: UserViewModel = hiltViewModel()
) {
    val uiState by profileViewModel.uiState.collectAsState()

    // Image picker launcher
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { profileViewModel.uploadProfileImage(it) }
    }

    // Show snackbar for errors/success
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.error) {
        uiState.error?.let { error ->
            snackbarHostState.showSnackbar(error)
            profileViewModel.clearError()
        }
    }

    LaunchedEffect(uiState.successMessage) {
        uiState.successMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            profileViewModel.clearSuccessMessage()
        }
    }

    MainScaffold(
        navController = navController,
        title = "Profile",
        userViewModel = userViewModel,
        containerColor = Color.Transparent,
        background = {
            // Programmatic M3E Mesh gradient background layer
            val color1 = MaterialTheme.colorScheme.primary
            val color2 = MaterialTheme.colorScheme.secondary

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .drawWithCache {
                        onDrawBehind {
                            drawRect(
                                brush = Brush.radialGradient(
                                    colors = listOf(color1.copy(alpha = 0.12f), Color.Transparent),
                                    center = Offset(size.width * 0.15f, size.height * 0.1f),
                                    radius = size.width * 0.8f
                                )
                            )
                            drawRect(
                                brush = Brush.radialGradient(
                                    colors = listOf(color2.copy(alpha = 0.1f), Color.Transparent),
                                    center = Offset(size.width * 0.85f, size.height * 0.25f),
                                    radius = size.width * 0.7f
                                )
                            )
                        }
                    }
            )
        },
        customTopBar = { passedScrollBehavior ->
            androidx.compose.material3.CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = "Profile",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold
                    )
                },
                navigationIcon = {
                    BokBokIconButton(onClick = { navController.navigateUp() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    if (!uiState.isEditing) {
                        BokBokIconButton(onClick = { profileViewModel.toggleEditMode() }) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit")
                        }
                    }
                },
                scrollBehavior = passedScrollBehavior,
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
                )
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (uiState.isLoading && uiState.user == null) {
                com.mustakim.bokbok.ui.shared.shimmer.ShimmerHost(
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Box(modifier = Modifier.size(120.dp).clip(RoundedCornerShape(32.dp)).background(MaterialTheme.colorScheme.surfaceVariant))
                    Spacer(modifier = Modifier.height(16.dp))
                    com.mustakim.bokbok.ui.shared.shimmer.TextPlaceholder(modifier = Modifier.width(150.dp))
                    Spacer(modifier = Modifier.height(32.dp))
                    
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(32.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.6f),
                        border = androidx.compose.foundation.BorderStroke(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)
                        ),
                        tonalElevation = 2.dp
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            repeat(3) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(modifier = Modifier.size(24.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant))
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Column {
                                        com.mustakim.bokbok.ui.shared.shimmer.TextPlaceholder(modifier = Modifier.width(80.dp).height(12.dp))
                                        Spacer(modifier = Modifier.height(8.dp))
                                        com.mustakim.bokbok.ui.shared.shimmer.TextPlaceholder(modifier = Modifier.width(200.dp).height(16.dp))
                                    }
                                }
                                if (it < 2) HorizontalDivider()
                            }
                        }
                    }
                }
            } else if (uiState.user != null) {
                if (uiState.isEditing) {
                    EditProfileContent(
                        user = uiState.user!!,
                        onSave = { displayName, bio, phone ->
                            profileViewModel.updateProfile(displayName, bio, phone)
                        },
                        onCancel = { profileViewModel.toggleEditMode() },
                        isLoading = uiState.isLoading
                    )
                } else {
                    ViewProfileContent(
                        user = uiState.user!!,
                        isUploadingImage = uiState.isUploadingImage,
                        onPickImage = { imagePickerLauncher.launch("image/*") },
                        onDeleteImage = { profileViewModel.deleteProfileImage() }
                    )
                }
            } else {
                // Error state
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Failed to load profile")
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = { profileViewModel.loadUserProfile() }) {
                        Text("Retry")
                    }
                }
            }
        }
    }
}

// ============= VIEW MODE =============

@Composable
fun ViewProfileContent(
    user: User,
    isUploadingImage: Boolean,
    onPickImage: () -> Unit,
    onDeleteImage: () -> Unit
) {
    var showImageOptions by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Profile Picture Section
        item {
            ProfileImageSection(
                imageUrl = user.profileImageUrl,
                displayName = user.displayName.ifEmpty { user.username },
                isUploading = isUploadingImage,
                onClick = { showImageOptions = true }
            )
        }

        // User Info Card
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(32.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.6f),
                border = androidx.compose.foundation.BorderStroke(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)
                ),
                tonalElevation = 2.dp
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ProfileInfoItem(
                        icon = Icons.Default.Person,
                        label = "Display Name",
                        value = user.displayName.ifEmpty { "Not set" }
                    )

                    HorizontalDivider()

                    ProfileInfoItem(
                        icon = Icons.Default.AlternateEmail,
                        label = "Username",
                        value = "@${user.username}"
                    )

                    HorizontalDivider()

                    ProfileInfoItem(
                        icon = Icons.Default.Email,
                        label = "Email",
                        value = user.email
                    )

                    if (user.phoneNumber.isNotEmpty()) {
                        HorizontalDivider()
                        ProfileInfoItem(
                            icon = Icons.Default.Phone,
                            label = "Phone",
                            value = user.phoneNumber
                        )
                    }

                    if (user.bio.isNotEmpty()) {
                        HorizontalDivider()
                        ProfileInfoItem(
                            icon = Icons.Default.Info,
                            label = "Bio",
                            value = user.bio
                        )
                    }
                }
            }
        }
    }

    // Image Options Bottom Sheet
    if (showImageOptions) {
        ModalBottomSheet(
            onDismissRequest = { showImageOptions = false }
        ) {
            Column(
                modifier = Modifier.padding(bottom = 32.dp)
            ) {
                ListItem(
                    headlineContent = { Text("Choose from gallery") },
                    leadingContent = {
                        Icon(Icons.Default.Image, contentDescription = null)
                    },
                    modifier = Modifier.clickable {
                        showImageOptions = false
                        onPickImage()
                    }
                )

                if (user.profileImageUrl.isNotEmpty()) {
                    ListItem(
                        headlineContent = { Text("Remove picture") },
                        leadingContent = {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error
                            )
                        },
                        modifier = Modifier.clickable {
                            showImageOptions = false
                            onDeleteImage()
                        }
                    )
                }
            }
        }
    }
}

// ============= EDIT MODE =============

@Composable
fun EditProfileContent(
    user: User,
    onSave: (String, String, String) -> Unit,
    onCancel: () -> Unit,
    isLoading: Boolean
) {
    var displayName by remember { mutableStateOf(user.displayName) }
    var bio by remember { mutableStateOf(user.bio) }
    var phoneNumber by remember { mutableStateOf(user.phoneNumber) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "Edit Profile",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        }

        item {
            OutlinedTextField(
                value = displayName,
                onValueChange = { displayName = it },
                label = { Text("Display Name") },
                leadingIcon = {
                    Icon(Icons.Default.Person, contentDescription = null)
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
        }

        item {
            OutlinedTextField(
                value = bio,
                onValueChange = { bio = it },
                label = { Text("Bio") },
                leadingIcon = {
                    Icon(Icons.Default.Info, contentDescription = null)
                },
                modifier = Modifier.fillMaxWidth(),
                maxLines = 3,
                supportingText = { Text("${bio.length}/150") }
            )
        }

        item {
            OutlinedTextField(
                value = phoneNumber,
                onValueChange = { phoneNumber = it },
                label = { Text("Phone Number") },
                leadingIcon = {
                    Icon(Icons.Default.Phone, contentDescription = null)
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onCancel,
                    modifier = Modifier.weight(1f),
                    enabled = !isLoading
                ) {
                    Text("Cancel")
                }

                Button(
                    onClick = { onSave(displayName, bio, phoneNumber) },
                    modifier = Modifier.weight(1f),
                    enabled = !isLoading
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("Save")
                    }
                }
            }
        }
    }
}

// ============= HELPER COMPOSABLES =============

@Composable
fun ProfileImageSection(
    imageUrl: String,
    displayName: String,
    isUploading: Boolean,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(120.dp)
                .clickable(onClick = onClick)
        ) {
            if (imageUrl.isNotEmpty()) {
                AsyncImage(
                    model = imageUrl,
                    contentDescription = "Profile picture",
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(32.dp)),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(32.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = displayName.firstOrNull()?.uppercase() ?: "?",
                        style = MaterialTheme.typography.displayLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            // Upload indicator
            if (isUploading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                            RoundedCornerShape(32.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            // Camera icon
            Icon(
                imageVector = Icons.Default.PhotoCamera,
                contentDescription = "Change picture",
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(32.dp)
                    .background(
                        MaterialTheme.colorScheme.primary,
                        RoundedCornerShape(12.dp)
                    )
                    .padding(6.dp),
                tint = MaterialTheme.colorScheme.onPrimary
            )
        }

        Text(
            text = displayName,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun ProfileInfoItem(
    icon: ImageVector,
    label: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}