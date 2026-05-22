package com.mustakim.bokbok.ui.screens.gameboost.security

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mustakim.bokbok.data.repository.ScanResult
import com.mustakim.bokbok.data.repository.SecurityStatus
import com.mustakim.bokbok.viewmodel.SecurityViewModel

@Composable
fun SecurityScreen(
    viewModel: SecurityViewModel
) {
    val isScanning by viewModel.isScanning.collectAsState()
    val progress by viewModel.scanProgress.collectAsState()
    val results by viewModel.scanResults.collectAsState()
    val score by viewModel.securityScore.collectAsState()
    
    val currentDns by viewModel.currentDns.collectAsState()
    val dnsPresets = viewModel.dnsPresets
    
    val storedApiKey by viewModel.storedApiKey.collectAsState(initial = "")
    var showApiKeyDialog by remember { mutableStateOf(false) }
    var showResultsSheet by remember { mutableStateOf(false) }
    var showDnsSheet by remember { mutableStateOf(false) }
    
    val resultsSheetState = rememberModalBottomSheetState()
    val dnsSheetState = rememberModalBottomSheetState()

    val isDnsActive = currentDns != null && currentDns != "opportunistic"
    val activePreset = dnsPresets.find { it.hostname == currentDns }
    
    val maliciousCount = results.count { it.priority >= 2 }
    val totalCount = results.size

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // ═══════════════════════════════════════════════════════════════════
        // HERO SECTION: Security Score + Scan Action
        // ═══════════════════════════════════════════════════════════════════
        item {
            HeroSecurityCard(
                score = score,
                isScanning = isScanning,
                progress = progress,
                hasApiKey = storedApiKey.isNotBlank(),
                onScanClick = {
                    if (storedApiKey.isBlank()) {
                        showApiKeyDialog = true
                    } else {
                        viewModel.startScan(storedApiKey)
                    }
                },
                onSettingsClick = { showApiKeyDialog = true }
            )
        }
        
        // ═══════════════════════════════════════════════════════════════════
        // THREAT ANALYSIS CARD (Shows only when there are results)
        // ═══════════════════════════════════════════════════════════════════
        if (results.isNotEmpty() || isScanning) {
            item {
                ThreatAnalysisCard(
                    isScanning = isScanning,
                    totalCount = totalCount,
                    maliciousCount = maliciousCount,
                    onClick = { showResultsSheet = true }
                )
            }
        }
        
        // ═══════════════════════════════════════════════════════════════════
        // PRIVACY & SHIELD SECTION
        // ═══════════════════════════════════════════════════════════════════
        item {
            Text(
                text = "Privacy & Shield",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
        
        item {
            DnsShieldCard(
                isDnsActive = isDnsActive,
                activePresetName = activePreset?.name ?: currentDns,
                onClick = { showDnsSheet = true }
            )
        }
    }

    // Modal Bottom Sheet for Results
    if (showResultsSheet) {
        ModalBottomSheet(
            onDismissRequest = { showResultsSheet = false },
            sheetState = resultsSheetState,
            dragHandle = { BottomSheetDefaults.DragHandle() },
            containerColor = MaterialTheme.colorScheme.background,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 32.dp)
            ) {
                Text(
                    text = "Application Security Reports",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    text = "Detailed analysis from VirusTotal and local database",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Spacer(modifier = Modifier.height(20.dp))
                
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(results) { result ->
                        var showDetails by remember { mutableStateOf(false) }
                        ScanResultItem(result, onClick = { showDetails = true })
                        
                        if (showDetails) {
                            ScanDetailDialog(result, onDismiss = { showDetails = false })
                        }
                    }
                }
            }
        }
    }

    // Modal Bottom Sheet for DNS Selection
    if (showDnsSheet) {
        ModalBottomSheet(
            onDismissRequest = { showDnsSheet = false },
            sheetState = dnsSheetState,
            dragHandle = { BottomSheetDefaults.DragHandle() },
            containerColor = MaterialTheme.colorScheme.background,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
        ) {
            DnsSelectionContent(
                presets = dnsPresets,
                currentDns = currentDns,
                onSelect = { hostname ->
                    viewModel.setDns(hostname)
                    showDnsSheet = false
                }
            )
        }
    }

    if (showApiKeyDialog) {
        ApiKeyDialog(
            initialValue = storedApiKey,
            onDismiss = { showApiKeyDialog = false },
            onConfirm = { key ->
                viewModel.startScan(key)
                showApiKeyDialog = false
            }
        )
    }
}

@Composable
fun ScanDetailDialog(result: ScanResult, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(onClick = onDismiss, shape = RoundedCornerShape(12.dp)) {
                Text("Close")
            }
        },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    getStatusIcon(result.status), 
                    contentDescription = null, 
                    tint = getStatusBgColor(result.status)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(result.appName)
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = result.packageName,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                if (result.priority >= 3) {
                    Text(
                        text = "VirusTotal Analysis",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    VTStatRow("Malicious", result.maliciousCount, MaterialTheme.colorScheme.error)
                    VTStatRow("Suspicious", result.suspiciousCount, Color(0xFFFF9800))
                    VTStatRow("Undetected", result.undetectedCount, MaterialTheme.colorScheme.onSurfaceVariant)
                    VTStatRow("Harmless", result.harmlessCount, Color(0xFF4CAF50))
                    
                    if (result.engineResults.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Engine Reports (${result.engineResults.size})",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        // Sort engines: malicious -> suspicious -> others
                        val sortedEngines = result.engineResults.sortedWith(
                            compareByDescending<com.mustakim.bokbok.data.remote.EngineResult> { it.category == "malicious" }
                            .thenByDescending { it.category == "suspicious" }
                            .thenBy { it.engineName }
                        )
                        
                        Box(modifier = Modifier.heightIn(max = 300.dp)) {
                            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                                items(sortedEngines) { engine ->
                                    EngineResultRow(engine)
                                }
                            }
                        }
                    }
                    
                    if (result.message == "Unknown to database") {
                        Spacer(modifier = Modifier.height(12.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = "This signature was not found in the VT database. It might be a very new file or a private app.",
                                modifier = Modifier.padding(8.dp),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                } else if (result.priority == 2) {
                    Text(
                        text = "Bloatware Info",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = result.message ?: "Found in local debloat database.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                } else {
                    Text(
                        text = result.message ?: "This app is verified and safe.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    )
}

@Composable
fun VTStatRow(label: String, count: Int, color: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = if (count > 0) color else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun EngineResultRow(engine: com.mustakim.bokbok.data.remote.EngineResult) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = engine.engineName ?: "Unknown Engine",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold
            )
            if (engine.result != null) {
                Text(
                    text = engine.result,
                    style = MaterialTheme.typography.labelSmall,
                    color = when(engine.category) {
                        "malicious" -> MaterialTheme.colorScheme.error
                        "suspicious" -> Color(0xFFFF9800)
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
        }
        
        Badge(
            containerColor = when(engine.category) {
                "malicious" -> MaterialTheme.colorScheme.error.copy(alpha = 0.1f)
                "suspicious" -> Color(0xFFFF9800).copy(alpha = 0.1f)
                else -> MaterialTheme.colorScheme.surfaceVariant
            },
            contentColor = when(engine.category) {
                "malicious" -> MaterialTheme.colorScheme.error
                "suspicious" -> Color(0xFFFF9800)
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            }
        ) {
            Text(engine.category?.uppercase() ?: "UNKNOWN", fontSize = 8.sp, modifier = Modifier.padding(2.dp))
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// HERO SECURITY CARD - M3 Expressive with pulsing animation
// ═══════════════════════════════════════════════════════════════════════════
@Composable
fun HeroSecurityCard(
    score: Int,
    isScanning: Boolean,
    progress: Float,
    hasApiKey: Boolean,
    onScanClick: () -> Unit,
    onSettingsClick: () -> Unit
) {
    val animatedScore by animateIntAsState(targetValue = score, label = "ScoreAnimation")
    val scoreColor = getScoreColor(score)
    
    // Pulsing animation for scanning state
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Large Score Circle
            Box(
                modifier = Modifier.size(160.dp),
                contentAlignment = Alignment.Center
            ) {
                // Background glow for scanning
                if (isScanning) {
                    val themePrimary = MaterialTheme.colorScheme.primary
                    Box(
                        modifier = Modifier
                            .size(170.dp)
                            .clip(CircleShape)
                            .graphicsLayer { alpha = pulseAlpha }
                            .drawWithCache {
                                val brush = Brush.radialGradient(
                                    colors = listOf(
                                        themePrimary,
                                        Color.Transparent
                                    )
                                )
                                onDrawBehind {
                                    drawRect(brush)
                                }
                            }
                    )
                }
                
                // Progress/Score Ring
                CircularProgressIndicator(
                    progress = { if (isScanning) progress else 1f },
                    modifier = Modifier.size(140.dp),
                    color = if (isScanning) MaterialTheme.colorScheme.primary else scoreColor,
                    strokeWidth = 10.dp,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                )
                
                // Center Content
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (isScanning) {
                        Text(
                            text = "${(progress * 100).toInt()}%",
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Scanning...",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Text(
                            text = animatedScore.toString(),
                            style = MaterialTheme.typography.displayMedium,
                            fontWeight = FontWeight.Black,
                            color = scoreColor
                        )
                        Text(
                            text = getScoreLabel(score),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = scoreColor.copy(alpha = 0.8f)
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Status Text
            Text(
                text = if (isScanning) "Deep Scanning Your Device" else "System Integrity",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = if (isScanning) "Checking apps against VirusTotal database..." else "Perform a full scan to verify security",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Action Buttons Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Primary Scan Button (M3 Expressive: Large, rounded)
                FilledTonalButton(
                    onClick = onScanClick,
                    enabled = !isScanning,
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                ) {
                    Icon(
                        if (isScanning) Icons.Default.Sync else Icons.Default.Shield,
                        contentDescription = null,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = if (!hasApiKey) "Setup API" else if (isScanning) "Scanning..." else "Full Scan",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                
                // Settings Button (if API configured)
                if (hasApiKey) {
                    FilledTonalIconButton(
                        onClick = onSettingsClick,
                        enabled = !isScanning,
                        modifier = Modifier.size(56.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
                        )
                    ) {
                        Icon(Icons.Default.Settings, contentDescription = "API Settings")
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// THREAT ANALYSIS CARD
// ═══════════════════════════════════════════════════════════════════════════
@Composable
fun ThreatAnalysisCard(
    isScanning: Boolean,
    totalCount: Int,
    maliciousCount: Int,
    onClick: () -> Unit
) {
    val hasThreats = maliciousCount > 0
    
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (hasThreats)
                MaterialTheme.colorScheme.errorContainer
            else MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon with background
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(
                        if (hasThreats) MaterialTheme.colorScheme.error.copy(alpha = 0.2f)
                        else MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (hasThreats) Icons.Default.Warning else Icons.Default.Assignment,
                    contentDescription = null,
                    tint = if (hasThreats) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (isScanning) "Scan in Progress" else if (hasThreats) "Threats Detected!" else "Scan Complete",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (hasThreats) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = if (isScanning) "$totalCount apps checked" else "$totalCount apps • $maliciousCount flagged",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (hasThreats) MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = "View details",
                tint = if (hasThreats) MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// DNS SHIELD CARD
// ═══════════════════════════════════════════════════════════════════════════
@Composable
fun DnsShieldCard(
    isDnsActive: Boolean,
    activePresetName: String?,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isDnsActive)
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
            else MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        border = if (isDnsActive) BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)) else null
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(
                        if (isDnsActive) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceContainerHighest
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (isDnsActive) Icons.Default.VerifiedUser else Icons.Default.Lock,
                    contentDescription = null,
                    tint = if (isDnsActive) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(28.dp)
                )
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Cloud DNS Shield",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (isDnsActive) (activePresetName ?: "Active") else "Ad-blocking & safe browsing",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isDnsActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            // Status indicator
            if (isDnsActive) {
                Surface(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "ACTIVE",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            
            Spacer(modifier = Modifier.width(8.dp))
            
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = "Configure",
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
        }
    }
}

// Helper function for score label
private fun getScoreLabel(score: Int): String = when {
    score >= 90 -> "Excellent"
    score >= 70 -> "Good"
    score >= 50 -> "Fair"
    else -> "At Risk"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanResultItem(result: ScanResult, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(getStatusBgColor(result.status).copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = getStatusIcon(result.status),
                    contentDescription = null,
                    tint = getStatusBgColor(result.status),
                    modifier = Modifier.size(20.dp)
                )
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = result.appName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = result.packageName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (result.message != null) {
                    Text(
                        text = result.message,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
            
            if (result.maliciousCount > 0) {
                 Badge(containerColor = MaterialTheme.colorScheme.error) {
                     Text("${result.maliciousCount} threats", color = Color.White)
                 }
            }
        }
    }
}

@Composable
fun EmptyResultsState() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Security,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "No threats detected yet",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = "Perform a full scan to check for malware",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )
    }
}

@Composable
fun ApiKeyDialog(initialValue: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var text by remember { mutableStateOf(initialValue) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initialValue.isBlank()) "Setup VirusTotal API" else "Update API Key") },
        text = {
            Column {
                Text("A free VirusTotal API key is required to check apps against the cloud database. You can find yours in your VirusTotal profile settings.", style = MaterialTheme.typography.bodySmall)
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("API Key") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(text) },
                enabled = text.isNotBlank(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Save & Scan")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun DnsSelectionContent(
    presets: List<com.mustakim.bokbok.data.repository.DnsPreset>,
    currentDns: String?,
    onSelect: (String?) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(bottom = 40.dp)
    ) {
        Text(
            text = "DNS Shield Settings",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.ExtraBold
        )
        Text(
            text = "Encrypt DNS queries and block ads system-wide",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                DnsPresetItem(
                    name = "Disable Shield",
                    description = "Use system default DNS (No blocking)",
                    isSelected = currentDns == null || currentDns == "opportunistic",
                    onClick = { onSelect(null) }
                )
            }
            
            items(presets) { preset ->
                DnsPresetItem(
                    name = preset.name,
                    description = preset.description,
                    isSelected = currentDns == preset.hostname,
                    onClick = { onSelect(preset.hostname) },
                    isFamilyFriendly = preset.isFamilyFriendly
                )
            }
            
            item {
                var showCustomDialog by remember { mutableStateOf(false) }
                
                OutlinedCard(
                    onClick = { showCustomDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(16.dp))
                        Text("Custom DNS Hostname", fontWeight = FontWeight.Bold)
                    }
                }
                
                if (showCustomDialog) {
                    var customUrl by remember { mutableStateOf("") }
                    AlertDialog(
                        onDismissRequest = { showCustomDialog = false },
                        title = { Text("Custom DNS") },
                        text = {
                            OutlinedTextField(
                                value = customUrl,
                                onValueChange = { customUrl = it },
                                label = { Text("Hostname (e.g. dns.nextdns.io)") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                        },
                        confirmButton = {
                            Button(onClick = { 
                                if (customUrl.isNotBlank()) onSelect(customUrl)
                                showCustomDialog = false
                            }) { Text("Apply") }
                        },
                        dismissButton = {
                            TextButton(onClick = { showCustomDialog = false }) { Text("Cancel") }
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun DnsPresetItem(
    name: String,
    description: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    isFamilyFriendly: Boolean = false
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) 
                MaterialTheme.colorScheme.primaryContainer 
            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        ),
        border = if (isSelected) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                    )
                    if (isFamilyFriendly) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Badge(containerColor = Color(0xFF4CAF50).copy(alpha = 0.2f)) {
                            Text("Family Safe", color = Color(0xFF2E7D32), fontSize = 10.sp, modifier = Modifier.padding(2.dp))
                        }
                    }
                }
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isSelected) 
                        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f) 
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            RadioButton(selected = isSelected, onClick = onClick)
        }
    }
}

fun getScoreColor(score: Int): Color = when {

    score >= 90 -> Color(0xFF4CAF50)
    score >= 70 -> Color(0xFFFFC107)
    else -> Color(0xFFF44336)
}

fun getStatusIcon(status: SecurityStatus): ImageVector = when (status) {
    SecurityStatus.SECURE -> Icons.Default.CheckCircle
    SecurityStatus.WARNING -> Icons.Default.Warning
    SecurityStatus.MALICIOUS -> Icons.Default.Error
    else -> Icons.Default.Help
}

fun getStatusBgColor(status: SecurityStatus): Color = when (status) {
    SecurityStatus.SECURE -> Color(0xFF4CAF50)
    SecurityStatus.WARNING -> Color(0xFFFF9800)
    SecurityStatus.MALICIOUS -> Color(0xFFF44336)
    else -> Color(0xFF9E9E9E)
}
