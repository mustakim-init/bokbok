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

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

private enum class VoiceButtonType { NONE, MIC, CHAT, EFFECTS, MORE, LEAVE }

@Composable
fun VoiceControlsSheet(
    isMuted: Boolean,
    isSpeakerOn: Boolean,
    isA2dpModeOn: Boolean,
    bitrate: Int,
    isStereo: Boolean,
    micVolume: Float,
    outputVolume: Float,
    expansionFraction: Float,
    screenHeight: Dp,
    onToggleMic: () -> Unit,
    onToggleSpeaker: () -> Unit,
    onOpenChat: () -> Unit,
    onOpenVoiceEffects: () -> Unit,
    onToggleAudioMode: () -> Unit,
    onBitrateChange: (Int) -> Unit,
    onToggleStereo: () -> Unit,
    onMoreClick: () -> Unit,
    onLeaveRoom: () -> Unit,
    onMicVolumeChange: (Float) -> Unit,
    onOutputVolumeChange: (Float) -> Unit
) {
    val haptic = LocalHapticFeedback.current
    var lastPressed by remember { mutableStateOf<VoiceButtonType?>(null) }
    
    // Auto-release the "expansion" after a delay (similar to PixelPlayer)
    LaunchedEffect(lastPressed) {
        if (lastPressed != null && lastPressed != VoiceButtonType.MIC) {
            kotlinx.coroutines.delay(300)
            lastPressed = null
        }
    }

    val statusBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    // 1. Interpolate Padding & Dimensions
    // 🎤 NEW: "Snappy Morph" logic.
    // The width and corners "snap" to the sheet state at 5%, but height stays linear.
    val isPastThreshold = expansionFraction >= 0.05f
    val visualMorphProgress by animateFloatAsState(
        targetValue = if (isPastThreshold) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "morph"
    )
    
    // Collapsed: 16dp (Wider Pill) -> Expanded: 0dp (Full Width)
    val horizontalPadding = lerp(16.dp, 0.dp, visualMorphProgress)
    val bottomGap = lerp(24.dp, 0.dp, visualMorphProgress)

    // Corner Radius: 40dp (Fully Rounded Pill) -> 24dp (Sheet at top)
    val topCornerRadius = lerp(40.dp, 24.dp, visualMorphProgress)
    val bottomCornerRadius = lerp(40.dp, 0.dp, visualMorphProgress)

    // 2. Height Calculation
    val collapsedHeight = 104.dp 
    val peekHeight = 130.dp 
    
    val currentHeight = if (isPastThreshold) {
        // Once past 5%, the sheet must be tall enough to reach from the bottom of the screen
        // to the top of the expanding scaffold.
        lerp(peekHeight, screenHeight, expansionFraction)
    } else {
        // In pill state, we use the fixed pill height
        collapsedHeight
    }

    // 3. Content Padding (Top)
    // Only apply status bar padding when threshold is passed
    val topContentPadding = lerp(0.dp, statusBarHeight, visualMorphProgress)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = bottomGap)
            .height(currentHeight)
    ) {
        // --- BACKGROUND CARD ---
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = horizontalPadding),
            shape = RoundedCornerShape(
                topStart = topCornerRadius,
                topEnd = topCornerRadius,
                bottomStart = bottomCornerRadius,
                bottomEnd = bottomCornerRadius
            ),
            color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.98f),
            tonalElevation = 8.dp,
            shadowElevation = if (expansionFraction < 0.95f) 8.dp else 0.dp
        ) {
            // --- CONTENT ---
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp)
                    .padding(top = topContentPadding)
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
                    .height(collapsedHeight)
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Fixed weights to prevent squeezing
                val baseWeight = 1.0f

                VoiceControlButton(
                    icon = if (isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                    label = if (isMuted) "Unmute" else "Mute",
                    isExpanded = isMuted,
                    isActive = !isMuted,
                    weight = baseWeight,
                    onClick = {
                        lastPressed = VoiceButtonType.MIC
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onToggleMic()
                    }
                )

                VoiceControlButton(
                    icon = Icons.AutoMirrored.Filled.Chat,
                    label = "Chat",
                    weight = baseWeight,
                    onClick = {
                        lastPressed = VoiceButtonType.CHAT
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onOpenChat()
                    }
                )

                VoiceControlButton(
                    icon = Icons.Default.MusicNote,
                    label = "Effects",
                    weight = baseWeight,
                    onClick = {
                        lastPressed = VoiceButtonType.EFFECTS
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onOpenVoiceEffects()
                    }
                )

                VoiceControlButton(
                    icon = Icons.Default.MoreHoriz,
                    label = "More",
                    weight = baseWeight,
                    onClick = {
                        lastPressed = VoiceButtonType.MORE
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onMoreClick()
                    }
                )

                VoiceControlButton(
                    icon = Icons.Default.CallEnd,
                    label = "Leave",
                    isDestructive = true,
                    weight = baseWeight,
                    onClick = {
                        lastPressed = VoiceButtonType.LEAVE
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onLeaveRoom()
                    }
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
                        QualityControls(
                            bitrate = bitrate,
                            isStereo = isStereo,
                            onBitrateChange = onBitrateChange,
                            onToggleStereo = onToggleStereo
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
    weight: Float = 1f,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val isHovered by interactionSource.collectIsHoveredAsState()
    
    // Visual state combines logical expansion (like muted) and interaction
    val isVisuallyExpanded = isExpanded || isPressed || isHovered

    val animatedWeight by animateFloatAsState(
        targetValue = weight,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "weight"
    )

    val cornerRadiusPercent by animateIntAsState(
        targetValue = if (isVisuallyExpanded) 24 else 50,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "cornerRadius"
    )
    
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.92f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessHigh
        ),
        label = "scale"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.weight(weight)
    ) {
        Box(
            modifier = Modifier
                .height(52.dp)
                .fillMaxWidth()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
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
            Crossfade(
                targetState = icon,
                animationSpec = tween(200, easing = FastOutSlowInEasing),
                label = "iconMorph"
            ) { targetIcon ->
                Icon(
                    imageVector = targetIcon,
                    contentDescription = label,
                    tint = when {
                        isDestructive -> MaterialTheme.colorScheme.onError
                        isActive -> MaterialTheme.colorScheme.onPrimary
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.size(24.dp)
                )
            }
        }
        
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (isVisuallyExpanded) 1f else 0.7f),
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun QualityModeToggle(
    isHighQuality: Boolean,
    onToggle: () -> Unit
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ModeChip(
            label = "Low",
            selected = !isHighQuality,
            onClick = { if (isHighQuality) onToggle() }
        )
        ModeChip(
            label = "High",
            selected = isHighQuality,
            onClick = { if (!isHighQuality) onToggle() }
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
@Composable
private fun QualityControls(
    bitrate: Int,
    isStereo: Boolean,
    onBitrateChange: (Int) -> Unit,
    onToggleStereo: () -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        // Bitrate Slider
        var sliderValue by remember(bitrate) { mutableStateOf(bitrate.toFloat()) }

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Bitrate",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "${sliderValue.toInt()}kbps",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }
            androidx.compose.material3.Slider(
                value = sliderValue,
                onValueChange = { sliderValue = it },
                onValueChangeFinished = { 
                    onBitrateChange(sliderValue.toInt()) 
                },
                valueRange = 8f..32f,
                steps = 23, // 1kbps increments
                modifier = Modifier.height(24.dp)
            )
            Text(
                text = "Lower bitrate saves bandwidth on weak networks.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Stereo Toggle
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Stereo Audio",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = if (isStereo) "Dual channel" else "Single channel (Efficient)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            androidx.compose.material3.Switch(
                checked = isStereo,
                onCheckedChange = { onToggleStereo() }
            )
        }
    }
}
