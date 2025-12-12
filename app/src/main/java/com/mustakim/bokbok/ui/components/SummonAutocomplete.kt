package com.mustakim.bokbok.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.mustakim.bokbok.data.model.User

/**
 * Autocomplete dropdown for the Summon feature.
 * Shows available users when the text ends with `\summon @` pattern.
 * 
 * @param text Current message text
 * @param availableUsers List of users that can be summoned
 * @param isGroup Whether this is a group chat (enables @everyone option)
 * @param onSuggestionSelected Called when a user suggestion is selected
 */
@Composable
fun SummonAutocomplete(
    text: String,
    availableUsers: List<User>,
    isGroup: Boolean = false,
    onSuggestionSelected: (String) -> Unit, // Passes the completed text
    modifier: Modifier = Modifier
) {
    // Check if we should show autocomplete
    val showAutocomplete = remember(text) {
        text.contains("\\summon @", ignoreCase = true) && 
        !text.endsWith(" ") && 
        text.substringAfterLast("\\summon @", "").let { partial ->
            // Don't show if the summon command is already complete
            partial.isNotEmpty() && !partial.contains(" ")
        }.not() || text.endsWith("\\summon @")
    }
    
    // Get the partial username being typed
    val partialUsername = remember(text) {
        if (text.lowercase().contains("\\summon @")) {
            text.substringAfterLast("@", "").lowercase()
        } else ""
    }
    
    // Filter suggestions based on partial input
    val suggestions = remember(partialUsername, availableUsers, isGroup) {
        val filtered = mutableListOf<SummonSuggestion>()
        
        // Add @everyone option for groups
        if (isGroup && "everyone".startsWith(partialUsername)) {
            filtered.add(SummonSuggestion.Everyone)
        }
        
        // Add matching users
        availableUsers
            .filter { it.displayName.lowercase().startsWith(partialUsername) }
            .take(5) // Limit to 5 suggestions
            .forEach { 
                filtered.add(SummonSuggestion.UserSuggestion(it)) 
            }
        
        filtered
    }
    
    // Don't show if no suggestions or autocomplete not triggered
    if (!showAutocomplete || suggestions.isEmpty()) return
    
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
        ) {
            Text(
                text = "Summon",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
            )
            
            suggestions.forEach { suggestion ->
                SuggestionItem(
                    suggestion = suggestion,
                    onClick = {
                        // Replace the partial @username with the selected name
                        val baseText = text.substringBeforeLast("@")
                        val completedText = when (suggestion) {
                            is SummonSuggestion.Everyone -> "${baseText}@everyone "
                            is SummonSuggestion.UserSuggestion -> "${baseText}@${suggestion.user.displayName} "
                        }
                        onSuggestionSelected(completedText)
                    }
                )
            }
        }
    }
}

@Composable
private fun SuggestionItem(
    suggestion: SummonSuggestion,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        when (suggestion) {
            is SummonSuggestion.Everyone -> {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(
                            MaterialTheme.colorScheme.primaryContainer,
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "@",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "@everyone",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "Summon all members",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            is SummonSuggestion.UserSuggestion -> {
                val user = suggestion.user
                if (!user.profileImageUrl.isNullOrEmpty()) {
                    AsyncImage(
                        model = user.profileImageUrl,
                        contentDescription = null,
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .background(
                                MaterialTheme.colorScheme.secondaryContainer,
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = user.displayName.take(1).uppercase(),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "@${user.displayName}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

private sealed class SummonSuggestion {
    data object Everyone : SummonSuggestion()
    data class UserSuggestion(val user: User) : SummonSuggestion()
}
