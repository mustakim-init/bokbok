package com.mustakim.bokbok.ui.screens.gameboost.appmanager.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mustakim.bokbok.viewmodel.AppSortOrder

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppSortBottomSheet(
    selectedOrder: AppSortOrder,
    onOrderSelected: (AppSortOrder) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp)
                .selectableGroup(),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "Sort by",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            AppSortOrder.values().forEach { order ->
                val isSelected = order == selectedOrder
                val containerColor = if (isSelected) 
                    MaterialTheme.colorScheme.secondaryContainer 
                else 
                    MaterialTheme.colorScheme.surfaceContainerLow

                Surface(
                    shape = MaterialTheme.shapes.extraLarge,
                    color = containerColor,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clip(CircleShape)
                        .selectable(
                            selected = isSelected,
                            onClick = { onOrderSelected(order) },
                            role = Role.RadioButton
                        )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = order.getDisplayName(),
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (isSelected) 
                                MaterialTheme.colorScheme.onSecondaryContainer 
                            else 
                                MaterialTheme.colorScheme.onSurface
                        )
                        RadioButton(
                            selected = isSelected,
                            onClick = null
                        )
                    }
                }
            }
        }
    }
}

private fun AppSortOrder.getDisplayName(): String = when (this) {
    AppSortOrder.NAME_ASC -> "Name (A-Z)"
    AppSortOrder.NAME_DESC -> "Name (Z-A)"
    AppSortOrder.SIZE -> "Size"
    AppSortOrder.INSTALL_DATE -> "Install Date"
    AppSortOrder.UPDATE_DATE -> "Last Update"
    AppSortOrder.BLOATWARE_FIRST -> "Bloatware First"
    AppSortOrder.PACKAGE_NAME -> "Package Name"
    AppSortOrder.TARGET_SDK -> "Target SDK"
}
