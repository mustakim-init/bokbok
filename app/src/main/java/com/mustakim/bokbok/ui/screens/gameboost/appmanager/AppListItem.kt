package com.mustakim.bokbok.ui.screens.gameboost.appmanager

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.mustakim.bokbok.data.bloatware.RemovalSafety
import com.mustakim.bokbok.data.model.AppItem
import java.text.DecimalFormat

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun AppListItem(
    app: AppItem,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    // Get colors based on removal safety
    val (badgeColor, badgeText, badgeIcon) = getRemovalSafetyBadge(app.removalSafety)
    
    // Selection border color
    val borderColor by animateColorAsState(
        targetValue = if (app.isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
        label = "borderColor"
    )
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .then(
                if (app.isSelected) {
                    Modifier.border(2.dp, borderColor, RoundedCornerShape(12.dp))
                } else Modifier
            )
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        colors = CardDefaults.cardColors(
            containerColor = if (app.isSelected) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            } else {
                MaterialTheme.colorScheme.surfaceContainer
            }
        ),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // App Icon with selection indicator
            Box {
                val iconBitmap = app.icon?.toBitmap()?.asImageBitmap()
                if (iconBitmap != null) {
                    Image(
                        bitmap = iconBitmap,
                        contentDescription = null,
                        modifier = Modifier
                            .size(52.dp)
                            .clip(RoundedCornerShape(12.dp))
                    )
                } else {
                    Spacer(
                        modifier = Modifier
                            .size(52.dp)
                            .background(Color.Gray, RoundedCornerShape(12.dp))
                    )
                }
                
                // Selection checkmark
                if (app.isSelected) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .size(20.dp)
                            .background(MaterialTheme.colorScheme.primary, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Selected",
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                // App Label with bloatware indicator
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = app.label,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    
                    // Show bloatware safety badge if it's bloatware
                    if (app.isBloatware && badgeText != null) {
                        SafetyBadge(
                            text = badgeText,
                            color = badgeColor
                        )
                    }
                }
                
                // Package Name
                Text(
                    text = app.packageName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.size(6.dp))

                // Metadata Row
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Size
                    if (app.apkSize > 0) {
                        MetadataChip(text = formatFileSize(app.apkSize))
                    }
                    
                    // Target SDK
                    MetadataChip(text = "SDK ${app.targetSdk}")

                    // System/User/Uninstalled Badge
                    if (!app.isInstalled) {
                        TypeBadge(
                            text = "UNINSTALLED",
                            isSystem = false,
                            colorOverride = Color.Gray
                        )
                    } else if (app.isSystemApp) {
                        TypeBadge(
                            text = "SYSTEM",
                            isSystem = true
                        )
                    } else {
                        TypeBadge(
                            text = "USER",
                            isSystem = false
                        )
                    }
                    
                    // Bloatware type indicator
                    app.bloatwareType?.let { type ->
                        val typeLabel = when (type.lowercase()) {
                            "google" -> "Google"
                            "carrier" -> "Carrier"
                            "oem" -> "OEM"
                            "aosp" -> "AOSP"
                            else -> null
                        }
                        typeLabel?.let {
                            MetadataChip(
                                text = it,
                                color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.8f)
                            )
                        }
                    }
                }
            }
            
            // Right side - Warning icon for unsafe apps
            if (app.isBloatware && app.removalSafety == RemovalSafety.UNSAFE) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = "Unsafe to remove",
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .size(24.dp),
                    tint = Color(0xFFE53935)
                )
            }
        }
    }
}

@Composable
private fun SafetyBadge(
    text: String,
    color: Color
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall.copy(
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold
        ),
        color = color,
        modifier = Modifier
            .background(
                color.copy(alpha = 0.15f),
                RoundedCornerShape(4.dp)
            )
            .padding(horizontal = 5.dp, vertical = 2.dp)
    )
}

@Composable
private fun TypeBadge(
    text: String,
    isSystem: Boolean,
    colorOverride: Color? = null
) {
    val color = colorOverride ?: if (isSystem) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.primary
    }
    
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall.copy(
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold
        ),
        color = color,
        modifier = Modifier
            .background(
                color.copy(alpha = 0.12f),
                RoundedCornerShape(4.dp)
            )
            .padding(horizontal = 5.dp, vertical = 2.dp)
    )
}

@Composable
private fun MetadataChip(
    text: String,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
        color = color
    )
}

private fun getRemovalSafetyBadge(safety: RemovalSafety): Triple<Color, String?, Boolean> {
    return when (safety) {
        RemovalSafety.SAFE -> Triple(
            Color(0xFF4CAF50), // Green
            "SAFE",
            true
        )
        RemovalSafety.REPLACEABLE -> Triple(
            Color(0xFF2196F3), // Blue
            "REPLACE",
            true
        )
        RemovalSafety.CAUTION -> Triple(
            Color(0xFFFFA726), // Orange
            "CAUTION",
            false
        )
        RemovalSafety.UNSAFE -> Triple(
            Color(0xFFE53935), // Red
            "UNSAFE",
            false
        )
        RemovalSafety.UNKNOWN -> Triple(
            Color(0xFF9E9E9E), // Gray
            null,
            false
        )
    }
}

private fun formatFileSize(size: Long): String {
    if (size <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (Math.log10(size.toDouble()) / Math.log10(1024.0)).toInt()
    return DecimalFormat("#,##0.#").format(size / Math.pow(1024.0, digitGroups.toDouble())) + " " + units[digitGroups]
}

