package com.mustakim.bokbok.ui.screens.gameboost

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mustakim.bokbok.ui.theme.GoogleSansFlex
import com.mustakim.bokbok.viewmodel.GameBoostTab

@Composable
fun OptimizerDashboard(
    onTabSelected: (GameBoostTab) -> Unit
) {
    val items = listOf(
        DashboardItem(
            tab = GameBoostTab.GAME_BOOST,
            icon = Icons.Default.RocketLaunch,
            color = Color(0xFF6366F1), // Indigo
            subtitle = "Boost your games"
        ),
        DashboardItem(
            tab = GameBoostTab.APP_MANAGER,
            icon = Icons.Default.Category,
            color = Color(0xFF10B981), // Emerald
            subtitle = "Control bloatware"
        ),
        DashboardItem(
            tab = GameBoostTab.DEVICE_MONITOR,
            icon = Icons.Default.Speed,
            color = Color(0xFFF59E0B), // Amber
            subtitle = "Real-time system stats"
        ),
        DashboardItem(
            tab = GameBoostTab.USAGE_STATS,
            icon = Icons.Default.PieChart,
            color = Color(0xFFEC4899), // Pink
            subtitle = "Screen time analysis"
        ),
        DashboardItem(
            tab = GameBoostTab.SCREEN_RECORD,
            icon = Icons.Default.Videocam,
            color = Color(0xFF8B5CF6), // Violet
            subtitle = "Capture gameplay"
        ),
        DashboardItem(
            tab = GameBoostTab.SECURITY,
            icon = Icons.Default.Security,
            color = Color(0xFFF43F5E), // Rose
            subtitle = "Privacy & Safety"
        )
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(
            text = "Optimization Suite",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 20.dp, start = 4.dp)
        )

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(items) { item ->
                DashboardCard(item) {
                    onTabSelected(item.tab)
                }
            }
        }
    }
}

@Composable
private fun DashboardCard(
    item: DashboardItem,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.65f),
        tonalElevation = 2.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            item.color.copy(alpha = 0.08f),
                            Color.Transparent,
                        )
                    )
                )
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.Center
            ) {
                // Gradient icon container (frosted glass style)
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    item.color.copy(alpha = 0.20f),
                                    item.color.copy(alpha = 0.10f),
                                )
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = null,
                        tint = item.color,
                        modifier = Modifier.size(26.dp)
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))

                Text(
                    text = item.tab.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = item.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 14.sp
                )
            }
        }
    }
}

private data class DashboardItem(
    val tab: GameBoostTab,
    val icon: ImageVector,
    val color: Color,
    val subtitle: String
)
