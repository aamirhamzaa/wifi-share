package com.wifishare.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.wifishare.data.model.SharedFile

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileBrowserScreen(
    serverUrl: String,
    files: List<SharedFile>,
    isLoading: Boolean,
    clipboardText: String,
    onRefresh: () -> Unit,
    onDownloadFile: (SharedFile) -> Unit,
    onDeleteFile: (String) -> Unit,
    onSendClipboard: (String) -> Unit,
    onNavigateUpload: () -> Unit,
    onDisconnect: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var clipboardInput by remember { mutableStateOf("") }
    var fileToDelete by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(clipboardText) {
        clipboardInput = clipboardText
    }

    val filteredFiles = remember(files, searchQuery) {
        if (searchQuery.isBlank()) files
        else files.filter { it.name.contains(searchQuery, ignoreCase = true) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Shared Files", fontWeight = FontWeight.Bold)
                        Text(serverUrl, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                actions = {
                    IconButton(onClick = onRefresh) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                    IconButton(onClick = onDisconnect) {
                        Icon(Icons.Default.PowerSettingsNew, contentDescription = "Disconnect")
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onNavigateUpload,
                icon = { Icon(Icons.Default.CloudUpload, contentDescription = null) },
                text = { Text("Upload") }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                placeholder = { Text("Search shared files...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear")
                        }
                    }
                },
                singleLine = true
            )

            // Clipboard Quick Sync Box
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Shared Clipboard", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = clipboardInput,
                            onValueChange = { clipboardInput = it },
                            modifier = Modifier.weight(1f),
                            placeholder = { Text("Sync text with Ubuntu...") },
                            singleLine = true
                        )
                        Button(
                            onClick = { onSendClipboard(clipboardInput) },
                            enabled = clipboardInput.isNotBlank()
                        ) {
                            Text("Send")
                        }
                    }
                }
            }

            // Files count
            Text(
                text = "${filteredFiles.size} of ${files.size} file${if (files.size == 1) "" else "s"}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 6.dp)
            )

            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (filteredFiles.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        if (files.isEmpty()) "No shared files on Ubuntu yet.\nTap Upload below to send some!"
                        else "No files matching '$searchQuery'",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 80.dp)
                ) {
                    items(filteredFiles, key = { it.name }) { file ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                        ) {
                            ListItem(
                                leadingContent = {
                                    Icon(
                                        imageVector = when {
                                            file.mimeType?.startsWith("image/") == true -> Icons.Default.Image
                                            file.mimeType?.startsWith("video/") == true -> Icons.Default.VideoFile
                                            file.mimeType?.startsWith("audio/") == true -> Icons.Default.AudioFile
                                            file.name.endsWith(".zip") || file.name.endsWith(".tar.gz") -> Icons.Default.FolderZip
                                            else -> Icons.Default.InsertDriveFile
                                        },
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                },
                                headlineContent = {
                                    Text(file.name, fontWeight = FontWeight.Medium, maxLines = 1)
                                },
                                supportingContent = {
                                    Text(file.getFormattedSize(), style = MaterialTheme.typography.bodySmall)
                                },
                                trailingContent = {
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        IconButton(onClick = { onDownloadFile(file) }) {
                                            Icon(Icons.Default.Download, contentDescription = "Download")
                                        }
                                        IconButton(onClick = { fileToDelete = file.name }) {
                                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }

        fileToDelete?.let { filename ->
            AlertDialog(
                onDismissRequest = { fileToDelete = null },
                title = { Text("Delete File") },
                text = { Text("Are you sure you want to delete '$filename' from Ubuntu?") },
                confirmButton = {
                    Button(
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        onClick = {
                            onDeleteFile(filename)
                            fileToDelete = null
                        }
                    ) {
                        Text("Delete")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { fileToDelete = null }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}
