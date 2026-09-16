package com.privacy.whatsappdecryptor.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.DataObject
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.privacy.whatsappdecryptor.ui.viewmodel.ExportFormat

@Composable
fun ExportDialog(
    exportTargetDescription: String,
    onDismiss: () -> Unit,
    onConfirmExport: (ExportFormat) -> Unit
) {
    var selectedFormat by remember { mutableStateOf(ExportFormat.TXT) }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.FileDownload,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = { Text("Export Chat History", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    text = exportTargetDescription,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Text(
                    text = "Select Export Format:",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp
                )

                ExportFormatOption(
                    format = ExportFormat.TXT,
                    icon = Icons.Default.Description,
                    description = "Human-readable text file with timestamps and sender labels",
                    isSelected = selectedFormat == ExportFormat.TXT,
                    onSelect = { selectedFormat = ExportFormat.TXT }
                )

                ExportFormatOption(
                    format = ExportFormat.JSON,
                    icon = Icons.Default.DataObject,
                    description = "Structured JSON with full metadata, timestamps, and message types",
                    isSelected = selectedFormat == ExportFormat.JSON,
                    onSelect = { selectedFormat = ExportFormat.JSON }
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirmExport(selectedFormat) },
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Save to Device...")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun ExportFormatOption(
    format: ExportFormat,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    isSelected: Boolean,
    onSelect: () -> Unit
) {
    Surface(
        onClick = onSelect,
        shape = RoundedCornerShape(10.dp),
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
        else MaterialTheme.colorScheme.surfaceVariant,
        border = if (isSelected) androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
        else null,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            RadioButton(
                selected = isSelected,
                onClick = onSelect
            )
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = format.label,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
