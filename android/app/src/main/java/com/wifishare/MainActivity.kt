package com.wifishare

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.wifishare.data.model.SharedFile
import com.wifishare.data.repository.FileRepository
import com.wifishare.service.NsdDiscoveryService
import com.wifishare.service.TransferWorker
import com.wifishare.ui.screens.DiscoveryScreen
import com.wifishare.ui.screens.FileBrowserScreen
import com.wifishare.ui.screens.UploadScreen
import com.wifishare.ui.theme.WifiShareTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class Screen {
    DISCOVERY,
    FILE_BROWSER,
    UPLOAD
}

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var nsdDiscoveryService: NsdDiscoveryService

    @Inject
    lateinit var fileRepository: FileRepository

    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { _ -> }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        setContent {
            WifiShareTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    var currentScreen by remember { mutableStateOf(Screen.DISCOVERY) }
                    var connectedServerUrl by remember { mutableStateOf<String?>(null) }
                    var files by remember { mutableStateOf<List<SharedFile>>(emptyList()) }
                    var isLoading by remember { mutableStateOf(false) }
                    var clipboardText by remember { mutableStateOf("") }
                    var isUploading by remember { mutableStateOf(false) }
                    var uploadProgress by remember { mutableStateOf(0f) }

                    val discoveredServers by nsdDiscoveryService.discoveredServers.collectAsState()
                    val isDiscovering by nsdDiscoveryService.isDiscovering.collectAsState()

                    fun refreshFiles() {
                        if (connectedServerUrl == null) return
                        isLoading = true
                        lifecycleScope.launch {
                            val result = fileRepository.getFiles()
                            isLoading = false
                            result.onSuccess { files = it }
                                .onFailure {
                                    Toast.makeText(this@MainActivity, "Error: ${it.message}", Toast.LENGTH_SHORT).show()
                                }

                            fileRepository.getClipboard().onSuccess { clipboardText = it }
                        }
                    }

                    fun connectToServer(url: String, pin: String?) {
                        connectedServerUrl = url
                        fileRepository.setServerUrl(url, pin)
                        currentScreen = Screen.FILE_BROWSER
                        refreshFiles()
                    }

                    when (currentScreen) {
                        Screen.DISCOVERY -> {
                            LaunchedEffect(Unit) {
                                nsdDiscoveryService.startDiscovery()
                            }

                            DiscoveryScreen(
                                discoveredServers = discoveredServers,
                                isDiscovering = isDiscovering,
                                onRefresh = { nsdDiscoveryService.startDiscovery() },
                                onSelectServer = { url, pin -> connectToServer(url, pin) }
                            )
                        }

                        Screen.FILE_BROWSER -> {
                            FileBrowserScreen(
                                serverUrl = connectedServerUrl ?: "",
                                files = files,
                                isLoading = isLoading,
                                clipboardText = clipboardText,
                                onRefresh = { refreshFiles() },
                                onDownloadFile = { file ->
                                    val workRequest = OneTimeWorkRequestBuilder<TransferWorker>()
                                        .setInputData(
                                            workDataOf(
                                                TransferWorker.KEY_ACTION to TransferWorker.ACTION_DOWNLOAD,
                                                TransferWorker.KEY_FILENAME to file.name,
                                                TransferWorker.KEY_TOTAL_SIZE to file.size
                                            )
                                        )
                                        .build()
                                    WorkManager.getInstance(this@MainActivity).enqueue(workRequest)
                                    Toast.makeText(this@MainActivity, "Download queued: ${file.name}", Toast.LENGTH_SHORT).show()
                                },
                                onDeleteFile = { filename ->
                                    lifecycleScope.launch {
                                        fileRepository.deleteFile(filename)
                                            .onSuccess {
                                                Toast.makeText(this@MainActivity, "Deleted $filename", Toast.LENGTH_SHORT).show()
                                                refreshFiles()
                                            }
                                            .onFailure {
                                                Toast.makeText(this@MainActivity, "Delete failed: ${it.message}", Toast.LENGTH_SHORT).show()
                                            }
                                    }
                                },
                                onSendClipboard = { text ->
                                    lifecycleScope.launch {
                                        fileRepository.setClipboard(text)
                                            .onSuccess { Toast.makeText(this@MainActivity, "Synced to Ubuntu clipboard", Toast.LENGTH_SHORT).show() }
                                    }
                                },
                                onNavigateUpload = { currentScreen = Screen.UPLOAD },
                                onDisconnect = {
                                    connectedServerUrl = null
                                    currentScreen = Screen.DISCOVERY
                                    nsdDiscoveryService.startDiscovery()
                                }
                            )
                        }

                        Screen.UPLOAD -> {
                            UploadScreen(
                                isUploading = isUploading,
                                uploadProgress = uploadProgress,
                                onUploadFiles = { uris ->
                                    isUploading = true
                                    lifecycleScope.launch {
                                        var count = 0
                                        for (uri in uris) {
                                            uploadProgress = 0f
                                            val res = fileRepository.uploadFileUri(uri) { progress ->
                                                uploadProgress = progress
                                            }
                                            if (res.isSuccess) count++
                                        }
                                        isUploading = false
                                        Toast.makeText(this@MainActivity, "Uploaded $count file(s)", Toast.LENGTH_SHORT).show()
                                        currentScreen = Screen.FILE_BROWSER
                                        refreshFiles()
                                    }
                                },
                                onBack = { currentScreen = Screen.FILE_BROWSER }
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        nsdDiscoveryService.stopDiscovery()
    }
}
