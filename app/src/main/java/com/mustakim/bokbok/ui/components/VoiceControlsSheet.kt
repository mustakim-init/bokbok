package com.mustakim.bokbok.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp

@Composable
fun VoiceControlsSheet(
    isMuted: Boolean,
    isSpeakerOn: Boolean,
    isA2dpModeOn: Boolean,
    micVolume: Float,
    outputVolume: Float,
    expansionFraction: Float,
    screenHeight: Dp,
    onToggleMic: () -> Unit,
    onToggleSpeaker: () -> Unit,
    onOpenChat: () -> Unit,
    onOpenVoiceEffects: () -> Unit,
    onToggleAudioMode: () -> Unit,
    onMoreClick: () -> Unit,
    onLeaveRoom: () -> Unit,
    onMicVolumeChange: (Float) -> Unit,
    onOutputVolumeChange: (Float) -> Unit
) {
    val statusBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    // 1. Interpolate Padding & Dimensions
    val horizontalPadding = lerp(16.dp, 0.dp, expansionFraction)

    // Floating gap at bottom: 24dp (floating) -> 0dp (docked)
    // We reduce this slightly so it doesn't look like it's flying too high
    val bottomGap = lerp(24.dp, 0.dp, expansionFraction)

    // Corner Radius: 32dp (Pill) -> 0dp (Full Sheet at bottom) / 24dp (Sheet at top)
    val topCornerRadius = lerp(32.dp, 24.dp, expansionFraction)
    val bottomCornerRadius = lerp(32.dp, 0.dp, expansionFraction)

    // 2. Height Calculation
    // Collapsed: Just enough to show the row (approx 110dp)
    // Expanded: Full screen height
    val collapsedHeight = 110.dp
    val targetHeight = screenHeight - bottomGap // Subtract gap so it doesn't push off screen
    val currentHeight = lerp(collapsedHeight, targetHeight, expansionFraction)

    // 3. Content Padding (Top)
    // When expanded, we need to push content down to avoid status bar
    val topContentPadding = lerp(0.dp, statusBarHeight, expansionFraction)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = bottomGap) // This creates the "float" effect
            .height(currentHeight) // Use simple height lerp
    ) {
        // --- BACKGROUND CARD ---
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = horizontalPadding)
                .shadow(
                    elevation = if (expansionFraction < 0.95f) 8.dp else 0.dp,
                    shape = RoundedCornerShape(
                        topStart = topCornerRadius,
                        topEnd = topCornerRadius,
                        bottomStart = bottomCornerRadius,
                        bottomEnd = bottomCornerRadius
                    ),
                    clip = false
                )
                .clip(
                    RoundedCornerShape(
                        topStart = topCornerRadius,
                        topEnd = topCornerRadius,
                        bottomStart = bottomCornerRadius,
                        bottomEnd = bottomCornerRadius
                    )
                )
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
        )

        // --- CONTENT ---
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = horizontalPadding)
                .padding(top = topContentPadding) // Protect status bar
        ) {
            // 4. DRAG HANDLE ("The Stick")
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp, bottom = 4.dp), // Spacing for handle
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    modifier = Modifier
                        .width(32.dp)
                        .height(4.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                ) {}
            }

            // MAIN CONTROL ROW
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Mute Button (Persistent State)
                VoiceControlButton(
                    icon = if (isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                    label = if (isMuted) "Unmute" else "Mute",
                    isExpanded = isMuted,
                    isActive = !isMuted,
                    onClick = onToggleMic
                )

                // Chat Button (Momentary)
                VoiceControlButton(
                    icon = Icons.AutoMirrored.Filled.Chat,
                    label = "Chat",
                    onClick = onOpenChat
                )

                // Effects Button (Momentary)
                VoiceControlButton(
                    icon = Icons.Default.MusicNote,
                    label = "Effects",
                    onClick = onOpenVoiceEffects
                )

                // More Button (Momentary)
                VoiceControlButton(
                    icon = Icons.Default.MoreHoriz,
                    label = "More",
                    onClick = onMoreClick
                )

                // Leave Button (Momentary)
                VoiceControlButton(
                    icon = Icons.Default.CallEnd,
                    label = "Leave",
                    isDestructive = true,
                    onClick = onLeaveRoom
                )
            }

            // EXPANDED SETTINGS (Fade in)
            // We fade this in slightly later (starts at 10% expansion)
            val contentAlpha = (expansionFraction - 0.1f).coerceIn(0f, 1f)

            if (expansionFraction > 0.01f) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f) // Fill remaining space
                        .graphicsLayer { alpha = contentAlpha }
                        .padding(top = 8.dp)
                ) {
                    Spacer(modifier = Modifier.height(8.dp))
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 20.dp),
                        thickness = DividerDefaults.Thickness,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp),
                        verticalArrangement = Arrangement.spacedBy(24.dp)
                    ) {
                        Text(
                            text = "In-call settings",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
                        )

                        // Output Volume
                        VolumeControlRow(
                            icon = Icons.AutoMirrored.Filled.VolumeUp,
                            label = "Output Volume",
                            value = outputVolume,
                            onValueChange = onOutputVolumeChange
                        )

                        // Input Volume
                        VolumeControlRow(
                            icon = Icons.Default.Mic,
                            label = "Input Volume",
                            value = micVolume,
                            onValueChange = onMicVolumeChange
                        )

                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text(
                                    text = "Bluetooth audio mode",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = if (isA2dpModeOn)
                                        "Music profile (A2DP)"
                                    else
                                        "Call profile (SCO)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            AudioModeToggle(
                                isA2dpModeOn = isA2dpModeOn,
                                onToggle = onToggleAudioMode
                            )
                        }
                    }
                }
            }
        }
    }
}

// --- Helpers ---

@Composable
private fun RowScope.VoiceControlButton(
    icon: ImageVector,
    label: String,
    isExpanded: Boolean = false,
    isActive: Boolean = false,
    isDestructive: Boolean = false,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val targetExpanded = isExpanded || isPressed

    val weight by animateFloatAsState(
        targetValue = if (targetExpanded) 1.5f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "weight"
    )

    val cornerRadiusPercent by animateIntAsState(
        targetValue = if (targetExpanded) 20 else 50,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "cornerRadius"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.weight(weight)
    ) {
        Box(
            modifier = Modifier
                .height(56.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(cornerRadiusPercent))
                .background(
                    when {
                        isDestructive -> MaterialTheme.colorScheme.error
                        isActive -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.surfaceVariant
                    }
                )
                .clickable(
                    interactionSource = interactionSource,
                    indication = androidx.compose.material3.ripple(),
                    onClick = onClick
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = when {
                    isDestructive -> MaterialTheme.colorScheme.onError
                    isActive -> MaterialTheme.colorScheme.onPrimary
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.size(24.dp)
            )
        }
        
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun AudioModeToggle(
    isA2dpModeOn: Boolean,
    onToggle: () -> Unit
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ModeChip(
            label = "Call",
            selected = !isA2dpModeOn,
            onClick = { if (isA2dpModeOn) onToggle() }
        )
        ModeChip(
            label = "Music",
            selected = isA2dpModeOn,
            onClick = { if (!isA2dpModeOn) onToggle() }
        )
    }
}

@Composable
private fun ModeChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = if (selected)
            MaterialTheme.colorScheme.primary
        else
            MaterialTheme.colorScheme.surfaceVariant,
            tonalElevation = if (selected) 2.dp else 0.dp
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelMedium,
            color = if (selected)
                MaterialTheme.colorScheme.onPrimary
            else
                MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun VolumeControlRow(
    icon: ImageVector,
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Medium
                )
            }
            Text(
                text = "${(value * 100).toInt()}%",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
        }
        
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = 0f..1f,
            colors = androidx.compose.material3.SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
            modifier = Modifier.fillMaxWidth()
        )
    }
}