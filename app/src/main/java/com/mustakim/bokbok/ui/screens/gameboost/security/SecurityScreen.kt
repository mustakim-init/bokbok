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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        // Compact Security Score
        CompactSecurityScore(score, isScanning, progress)

        Spacer(modifier = Modifier.height(16.dp))

        // Actions
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                modifier = Modifier.weight(1f),
                onClick = { 
                    if (storedApiKey.isBlank()) {
                        showApiKeyDialog = true 
                    } else {
                        viewModel.startScan(storedApiKey)
                    }
                },
                enabled = !isScanning,
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            ) {
                Icon(if (isScanning) Icons.Default.Sync else Icons.Default.Shield, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (storedApiKey.isBlank()) "Setup Scan API" else if (isScanning) "Scanning..." else "Perform Scan")
            }
            
            if (storedApiKey.isNotBlank()) {
                IconButton(
                    onClick = { showApiKeyDialog = true },
                    enabled = !isScanning,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Icon(Icons.Default.Settings, contentDescription = "Change API Key")
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (results.isNotEmpty() || isScanning) {
            val maliciousCount = results.count { it.priority >= 2 }
            val totalCount = results.size
            
            Card(
                onClick = { showResultsSheet = true },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (maliciousCount > 0) 
                        MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f) 
                    else MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        if (maliciousCount > 0) Icons.Default.Warning else Icons.Default.Assignment,
                        contentDescription = null,
                        tint = if (maliciousCount > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isScanning) "Scan in Progress" else "Threat Analysis",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (maliciousCount > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = if (isScanning) "$totalCount apps checked so far" else "$totalCount Results • $maliciousCount Flagged",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Icon(Icons.Default.ChevronRight, contentDescription = null)
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // DNS Shield Section (Functional)
        Text(
            text = "Privacy & Shield",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(12.dp))
        Card(
            onClick = { showDnsSheet = true },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isDnsActive) 
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f) 
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            )
        ) {
            Row(
                modifier = Modifier.padding(20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(if (isDnsActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (isDnsActive) Icons.Default.VerifiedUser else Icons.Default.Lock, 
                        contentDescription = null, 
                        tint = if (isDnsActive) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
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
                        text = if (isDnsActive) (activePreset?.name ?: currentDns ?: "Active") else "Ad-blocking & safe browsing",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isDnsActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
            }
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

@Composable
fun CompactSecurityScore(score: Int, isScanning: Boolean, progress: Float) {
    val animatedScore by animateIntAsState(targetValue = score, label = "ScoreAnimation")
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp)
        )
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    progress = { if (isScanning) progress else 1f },
                    modifier = Modifier.size(64.dp),
                    color = getScoreColor(score),
                    strokeWidth = 6.dp,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
                Text(
                    text = if (isScanning) "${(progress * 100).toInt()}%" else animatedScore.toString(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (isScanning) MaterialTheme.colorScheme.primary else getScoreColor(score)
                )
            }
            
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 20.dp)
            ) {
                Text(
                    text = if (isScanning) "Scanning Device..." else "System Integrity",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (score >= 90) "Safe Atmosphere" else if (score >= 70) "Minor Concerns" else "System at Risk",
                    style = MaterialTheme.typography.bodySmall,
                    color = getScoreColor(score)
                )
            }
        }
    }
}

@Composable
fun SecurityScoreCard(score: Int, isScanning: Boolean, progress: Float) {
    val animatedScore by animateIntAsState(targetValue = score, label = "ScoreAnimation")
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp)
        )
    ) {
        Column(
            modifier = Modifier
                .padding(24.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(contentAlignment = Alignment.Center) {
                // Score Circle
                CircularProgressIndicator(
                    progress = { if (isScanning) progress else 1f },
                    modifier = Modifier.size(120.dp),
                    color = getScoreColor(score),
                    strokeWidth = 8.dp,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
                
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = if (isScanning) "${(progress * 100).toInt()}%" else animatedScore.toString(),
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (isScanning) MaterialTheme.colorScheme.primary else getScoreColor(score)
                    )
                    Text(
                        text = if (isScanning) "Scanning..." else "Security Score",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
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
