package com.mustakim.bokbok.ui.screens.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.google.firebase.firestore.FirebaseFirestore
import com.mustakim.bokbok.data.model.User
import com.mustakim.bokbok.data.repository.FriendsRepository
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * Dialog to add members to a group chat.
 * Matches VoiceRoom's AddParticipantsDialog with:
 * - Two tabs: Friends and Global Search
 * - Multi-select with checkboxes
 * - Bulk add with count display
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddMemberDialog(
    currentMembers: List<User>,
    friendsRepository: FriendsRepository,
    onDismiss: () -> Unit,
    onAddMembers: (List<String>) -> Unit // userId list for bulk add
) {
    val scope = rememberCoroutineScope()
    var friends by remember { mutableStateOf<List<User>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    
    var selectedTab by remember { mutableIntStateOf(0) }
    val selectedUserIds = remember { mutableStateListOf<String>() }
    var searchQuery by remember { mutableStateOf("") }
    
    // Global Search State
    var globalSearchResults by remember { mutableStateOf<List<User>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    
    val firestore = remember { FirebaseFirestore.getInstance() }
    
    // Fetch friends on dialog open
    LaunchedEffect(Unit) {
        scope.launch {
            isLoading = true
            friendsRepository.observeFriends().collect { friendStatusList ->
                // Filter out current members
                val memberIds = currentMembers.map { it.uid }.toSet()
                friends = friendStatusList
                    .map { it.user }
                    .filter { it.uid !in memberIds }
                isLoading = false
            }
        }
    }
    
    // Friend filter logic
    val filteredFriends = remember(friends, searchQuery, selectedTab) {
        if (selectedTab == 0 && searchQuery.isNotBlank()) {
            friends.filter {
                it.username.contains(searchQuery, ignoreCase = true) ||
                it.displayName.contains(searchQuery, ignoreCase = true)
            }
        } else friends
    }
    
    // Global search when tab is 1
    LaunchedEffect(searchQuery, selectedTab) {
        if (selectedTab == 1 && searchQuery.isNotBlank()) {
            isSearching = true
            try {
                val memberIds = currentMembers.map { it.uid }.toSet()
                val snapshot = firestore.collection("users")
                    .whereGreaterThanOrEqualTo("username", searchQuery.lowercase())
                    .whereLessThanOrEqualTo("username", searchQuery.lowercase() + "\uf8ff")
                    .limit(20)
                    .get()
                    .await()
                
                globalSearchResults = snapshot.documents.mapNotNull { doc ->
                    val uid = doc.id
                    if (uid in memberIds) return@mapNotNull null // Skip existing members
                    
                    User(
                        uid = uid,
                        username = doc.getString("username") ?: "",
                        displayName = doc.getString("displayName") ?: "",
                        profileImageUrl = doc.getString("profileImageUrl") ?: ""
                    )
                }
            } catch (e: Exception) {
                globalSearchResults = emptyList()
            }
            isSearching = false
        } else if (selectedTab == 1) {
            globalSearchResults = emptyList()
        }
    }
    
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 600.dp)
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Icon(
                    imageVector = Icons.Default.PersonAdd,
                    contentDescription = null,
                    modifier = Modifier
                        .size(32.dp)
                        .align(Alignment.CenterHorizontally),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Add Members",
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                PrimaryTabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = Color.Transparent,
                    divider = {}
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0; searchQuery = "" },
                        text = { Text("Friends") }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1; searchQuery = "" },
                        text = { Text("Global Search") }
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Search Bar
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { 
                        Text(if (selectedTab == 0) "Search friends..." else "Search by username...") 
                    },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    singleLine = true
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Box(modifier = Modifier.weight(1f, fill = false)) {
                    when {
                        isLoading && selectedTab == 0 -> {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator()
                            }
                        }
                        selectedTab == 0 -> {
                            // FRIENDS LIST
                            if (filteredFriends.isEmpty()) {
                                Box(
                                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = if (friends.isEmpty()) "All friends are already members" else "No friends found",
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            } else {
                                LazyColumn(
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    items(filteredFriends) { user ->
                                        val isSelected = selectedUserIds.contains(user.uid)
                                        UserListItem(
                                            user = user,
                                            isSelected = isSelected,
                                            onClick = {
                                                if (isSelected) selectedUserIds.remove(user.uid)
                                                else selectedUserIds.add(user.uid)
                                            }
                                        )
                                    }
                                }
                            }
                        }
                        selectedTab == 1 -> {
                            // GLOBAL SEARCH LIST
                            when {
                                isSearching -> {
                                    Box(
                                        modifier = Modifier.fillMaxWidth().padding(24.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        CircularProgressIndicator()
                                    }
                                }
                                searchQuery.isBlank() -> {
                                    Box(
                                        modifier = Modifier.fillMaxWidth().padding(24.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("Type to search...", style = MaterialTheme.typography.bodyMedium)
                                    }
                                }
                                globalSearchResults.isEmpty() -> {
                                    Box(
                                        modifier = Modifier.fillMaxWidth().padding(24.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("No users found", style = MaterialTheme.typography.bodyMedium)
                                    }
                                }
                                else -> {
                                    LazyColumn(
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        items(globalSearchResults) { user ->
                                            val isSelected = selectedUserIds.contains(user.uid)
                                            UserListItem(
                                                user = user,
                                                isSelected = isSelected,
                                                onClick = {
                                                    if (isSelected) selectedUserIds.remove(user.uid)
                                                    else selectedUserIds.add(user.uid)
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                Row(
                    horizontalArrangement = Arrangement.End,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = { onAddMembers(selectedUserIds.toList()) },
                        enabled = selectedUserIds.isNotEmpty()
                    ) {
                        Text("Add (${selectedUserIds.size})")
                    }
                }
            }
        }
    }
}

@Composable
private fun UserListItem(
    user: User,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    ListItem(
        headlineContent = { Text(user.displayName.ifBlank { user.username }) },
        supportingContent = { Text("@${user.username}") },
        leadingContent = {
            val imageModifier = Modifier.size(40.dp).clip(CircleShape)
            if (!user.profileImageUrl.isNullOrEmpty()) {
                AsyncImage(
                    model = user.profileImageUrl,
                    contentDescription = null,
                    modifier = imageModifier,
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = imageModifier.background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = (user.displayName.ifBlank { user.username }).take(1).uppercase(),
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        },
        trailingContent = {
            Checkbox(
                checked = isSelected,
                onCheckedChange = { onClick() }
            )
        },
        colors = ListItemDefaults.colors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f) else Color.Transparent
        ),
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
    )
}
