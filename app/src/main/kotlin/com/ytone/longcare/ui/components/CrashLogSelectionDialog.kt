package com.ytone.longcare.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.ytone.longcare.common.utils.CrashLogManager
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * 崩溃日志选择对话框
 * 显示所有崩溃日志文件，支持用户选择要分享的文件
 */
@Composable
fun CrashLogSelectionDialog(
    crashLogs: List<File>,
    onDismiss: () -> Unit,
    onConfirm: (List<File>) -> Unit,
    onRefresh: () -> Unit = {}
) {
    var selectedLogs by remember { mutableStateOf(setOf<File>()) }
    var showDeleteConfirmDialog by remember { mutableStateOf<File?>(null) }
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US) }
    
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(
                    text = "选择要分享的崩溃日志",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                
                if (crashLogs.isEmpty()) {
                    Text(
                        text = "暂无崩溃日志",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(vertical = 16.dp)
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 300.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(crashLogs) { logFile ->
                            CrashLogItem(
                                logFile = logFile,
                                isSelected = selectedLogs.contains(logFile),
                                onSelectionChanged = { isSelected ->
                                    selectedLogs = if (isSelected) {
                                        selectedLogs + logFile
                                    } else {
                                        selectedLogs - logFile
                                    }
                                },
                                onDeleteClick = { showDeleteConfirmDialog = logFile },
                                dateFormat = dateFormat
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("取消")
                    }
                    
                    if (crashLogs.isNotEmpty()) {
                        TextButton(
                            onClick = {
                                if (selectedLogs.isEmpty()) {
                                    // 如果没有选择任何文件，默认选择所有文件
                                    onConfirm(crashLogs)
                                } else {
                                    onConfirm(selectedLogs.toList())
                                }
                            }
                        ) {
                            Text("分享 ${if (selectedLogs.isEmpty()) "全部" else "(${selectedLogs.size})"}")
                        }
                    }
                }
            }
        }
    }
    
    // 删除确认对话框
    showDeleteConfirmDialog?.let { fileToDelete ->
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = null },
            title = { Text("确认删除") },
            text = { Text("确定要删除日志文件 \"${fileToDelete.name}\" 吗？此操作不可撤销。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        val success = CrashLogManager.deleteCrashLog(fileToDelete)
                        if (success) {
                            // 从选中列表中移除已删除的文件
                            selectedLogs = selectedLogs - fileToDelete
                            onRefresh()
                        }
                        showDeleteConfirmDialog = null
                    }
                ) {
                    Text("删除")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDeleteConfirmDialog = null }
                ) {
                    Text("取消")
                }
            }
        )
    }
}

@Composable
private fun CrashLogItem(
    logFile: File,
    isSelected: Boolean,
    onSelectionChanged: (Boolean) -> Unit,
    onDeleteClick: () -> Unit,
    dateFormat: SimpleDateFormat
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(
                selected = isSelected,
                onClick = { onSelectionChanged(!isSelected) }
            ),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = isSelected,
                onCheckedChange = onSelectionChanged
            )
            
            Spacer(modifier = Modifier.width(8.dp))
            
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = logFile.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                
                Text(
                    text = "创建时间: ${dateFormat.format(Date(logFile.lastModified()))}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Text(
                    text = "大小: ${formatFileSize(logFile.length())}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            IconButton(
                onClick = onDeleteClick
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "删除日志",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

/**
 * 格式化文件大小
 */
private fun formatFileSize(bytes: Long): String {
    return when {
        bytes < 1024 -> "${bytes}B"
        bytes < 1024 * 1024 -> "${bytes / 1024}KB"
        else -> "${bytes / (1024 * 1024)}MB"
    }
}