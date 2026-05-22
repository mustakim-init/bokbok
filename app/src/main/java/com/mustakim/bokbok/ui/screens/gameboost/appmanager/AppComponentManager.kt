package com.mustakim.bokbok.ui.screens.gameboost.appmanager

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mustakim.bokbok.data.model.AppComponent
import com.mustakim.bokbok.data.repository.AppManagerRepository
import com.mustakim.bokbok.viewmodel.AppDetailsViewModel
import com.mustakim.bokbok.ui.shared.BokBokIconButton

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppComponentManagerScreen(
    onBack: () -> Unit,
    viewModel: AppDetailsViewModel
) {
    val components by viewModel.components.collectAsState()
    val selectedType by viewModel.selectedComponentType.collectAsState()
    val isProcessing by viewModel.isProcessing.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Component Manager") },
                navigationIcon = {
                    BokBokIconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            // Type Selector Tabs
            PrimaryTabRow(
                selectedTabIndex = selectedType.ordinal,
                containerColor = MaterialTheme.colorScheme.surface,
                divider = {}
            ) {
                AppManagerRepository.ComponentType.values().forEach { type ->
                    Tab(
                        selected = selectedType == type,
                        onClick = { viewModel.setComponentType(type) },
                        text = { Text(type.name + "s", fontWeight = FontWeight.Bold) }
                    )
                }
            }

            if (isProcessing && components.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        InfoCard()
                    }
                    
                    items(components) { component ->
                        ComponentItem(
                            component = component,
                            isProcessing = isProcessing,
                            onToggle = { viewModel.toggleComponent(component.name, component.isEnabled) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoCard() {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f)),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.padding(bottom = 8.dp)
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                "Disabling unnecessary components can improve battery and performance. Use with caution.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer
            )
        }
    }
}

@Composable
private fun ComponentItem(
    component: AppComponent,
    isProcessing: Boolean,
    onToggle: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (component.isEnabled) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f) 
                             else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f)
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = component.label,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = component.name.substringAfterLast("."),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Row(modifier = Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (component.isExported) {
                        Badge(containerColor = MaterialTheme.colorScheme.secondaryContainer) {
                            Text("Exported", fontSize = 10.sp, modifier = Modifier.padding(2.dp))
                        }
                    }
                    if (component.processName != null && component.processName != component.packageName) {
                        Badge(containerColor = MaterialTheme.colorScheme.primaryContainer) {
                            Text("Custom Process", fontSize = 10.sp, modifier = Modifier.padding(2.dp))
                        }
                    }
                }
            }
            
            Switch(
                checked = component.isEnabled,
                onCheckedChange = { onToggle() },
                enabled = !isProcessing
            )
        }
    }
}