package com.filebridge.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.filebridge.ui.components.TrashItem
import com.filebridge.viewmodel.FileViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrashScreen(
    onBack: () -> Unit,
    viewModel: FileViewModel = hiltViewModel()
) {
    val deletedFiles by viewModel.deletedFiles.collectAsStateWithLifecycle()
    val deletedDuplicateHashes by viewModel.deletedDuplicateHashes.collectAsStateWithLifecycle()
    val trashDuplicateHashes by viewModel.trashDuplicateHashes.collectAsStateWithLifecycle()
    var showEmptyTrashDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.refreshDuplicateHashes()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("最近删除") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    if (deletedFiles.isNotEmpty()) {
                        IconButton(onClick = { showEmptyTrashDialog = true }) {
                            Icon(Icons.Default.DeleteSweep, contentDescription = "清空回收站")
                        }
                    }
                }
            )
        }
    ) { padding ->
        if (deletedFiles.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "回收站为空",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "删除的文件会出现在这里",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(deletedFiles, key = { it.originalId }) { file ->
                    TrashItem(
                        file = file,
                        isDuplicateWithActive = file.fileHash in deletedDuplicateHashes,
                        isDuplicateInTrash = file.fileHash in trashDuplicateHashes,
                        onRestore = { viewModel.restoreFile(file.originalId) },
                        onPermanentDelete = { viewModel.permanentlyDeleteFile(file.originalId) }
                    )
                }
            }
        }
    }

    if (showEmptyTrashDialog) {
        AlertDialog(
            onDismissRequest = { showEmptyTrashDialog = false },
            title = { Text("清空回收站") },
            text = { Text("确定要永久删除所有 ${deletedFiles.size} 个文件吗？此操作不可恢复。") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.emptyTrash()
                    showEmptyTrashDialog = false
                }) {
                    Text("清空", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showEmptyTrashDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
}
