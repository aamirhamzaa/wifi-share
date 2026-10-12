package com.wifishare.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.wifishare.data.model.DiscoveredServer

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiscoveryScreen(
    discoveredServers: List<DiscoveredServer>,
    isDiscovering: Boolean,
    onRefresh: () -> Unit,
    onSelectServer: (String, String?) -> Unit
) {
    var manualIp by remember { mutableStateOf("") }
    var pin by remember { mutableStateOf("") }
    var showManualDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("WiFi Share", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = onRefresh) {
                        Icon(Icons.Default.Refresh, contentDescription = "Scan")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header card
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        Icons.Default.Wifi,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp)
                    )
                    Column {
                        Text(
                            text = if (isDiscovering) "Scanning local Wi-Fi..." else "Local Discovery Ready",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Ensure your phone & Ubuntu are on the same Wi-Fi",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        )
                    }
                }
            }

            // PIN field (optional)
            OutlinedTextField(
                value = pin,
                onValueChange = { pin = it },
                label = { Text("Optional Server PIN") },
                placeholder = { Text("Enter 4-digit PIN if enabled") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Text(
                text = "Nearby Servers (${discoveredServers.size})",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )

            if (discoveredServers.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        CircularProgressIndicator(modifier = Modifier.size(36.dp))
                        Text(
                            "Searching for Ubuntu machine...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Button(
                            onClick = { showManualDialog = true },
                            modifier = Modifier.padding(top = 8.dp)
                        ) {
                            Text("Connect via IP Manually")
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(discoveredServers) { server ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelectServer(server.baseUrl, pin.ifBlank { null }) },
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            ListItem(
                                leadingContent = {
                                    Icon(Icons.Default.Computer, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                },
                                headlineContent = { Text(server.name, fontWeight = FontWeight.SemiBold) },
                                supportingContent = { Text("http://${server.ip}:${server.port}") },
                                trailingContent = {
                                    FilledTonalButton(onClick = { onSelectServer(server.baseUrl, pin.ifBlank { null }) }) {
                                        Text("Connect")
                                    }
                                }
                            )
                        }
                    }
                }

                OutlinedButton(
                    onClick = { showManualDialog = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Manual IP Connection")
                }
            }
        }

        if (showManualDialog) {
            AlertDialog(
                onDismissRequest = { showManualDialog = false },
                title = { Text("Manual Connection") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Enter the Ubuntu IP displayed in terminal:", style = MaterialTheme.typography.bodySmall)
                        OutlinedTextField(
                            value = manualIp,
                            onValueChange = { manualIp = it },
                            label = { Text("IP Address or URL") },
                            placeholder = { Text("192.168.1.50:8080") },
                            singleLine = true
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            var clean = manualIp.trim()
                            if (!clean.startsWith("http://") && !clean.startsWith("https://")) {
                                clean = "http://$clean"
                            }
                            if (!clean.contains(":") || clean.substringAfterLast(":").contains("/")) {
                                clean = "$clean:8080"
                            }
                            showManualDialog = false
                            onSelectServer(clean, pin.ifBlank { null })
                        }
                    ) {
                        Text("Connect")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showManualDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}
