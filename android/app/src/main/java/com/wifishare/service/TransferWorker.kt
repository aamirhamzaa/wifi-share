package com.wifishare.service

import android.content.Context
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.wifishare.WifiShareApp
import com.wifishare.data.repository.FileRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class TransferWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted private val workerParams: WorkerParameters,
    private val fileRepository: FileRepository
) : CoroutineWorker(context, workerParams) {

    companion object {
        const val KEY_ACTION = "action"
        const val KEY_FILE_URI = "file_uri"
        const val KEY_FILENAME = "filename"
        const val KEY_TOTAL_SIZE = "total_size"

        const val ACTION_UPLOAD = "upload"
        const val ACTION_DOWNLOAD = "download"

        private const val NOTIFICATION_ID = 1001
    }

    override suspend fun doWork(): Result {
        val action = inputData.getString(KEY_ACTION) ?: return Result.failure()

        val notification = createNotification("Starting file transfer...", 0)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            setForeground(ForegroundInfo(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC))
        } else {
            setForeground(ForegroundInfo(NOTIFICATION_ID, notification))
        }

        return when (action) {
            ACTION_UPLOAD -> {
                val uriString = inputData.getString(KEY_FILE_URI) ?: return Result.failure()
                val uri = Uri.parse(uriString)
                val uploadResult = fileRepository.uploadFileUri(uri) { progress ->
                    val percent = (progress * 100).toInt()
                    val updatedNotification = createNotification("Uploading file: $percent%", percent)
                    val foregroundInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        ForegroundInfo(NOTIFICATION_ID, updatedNotification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
                    } else {
                        ForegroundInfo(NOTIFICATION_ID, updatedNotification)
                    }
                    setProgressAsync(workDataOf("progress" to percent))
                }
                if (uploadResult.isSuccess) Result.success() else Result.failure()
            }

            ACTION_DOWNLOAD -> {
                val filename = inputData.getString(KEY_FILENAME) ?: return Result.failure()
                val totalSize = inputData.getLong(KEY_TOTAL_SIZE, 0L)
                try {
                    fileRepository.downloadFile(filename, totalSize).collect { progress ->
                        val percent = (progress * 100).toInt()
                        val updatedNotification = createNotification("Downloading $filename: $percent%", percent)
                        val foregroundInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            ForegroundInfo(NOTIFICATION_ID, updatedNotification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
                        } else {
                            ForegroundInfo(NOTIFICATION_ID, updatedNotification)
                        }
                        setProgressAsync(workDataOf("progress" to percent))
                    }
                    Result.success()
                } catch (e: Exception) {
                    Result.failure()
                }
            }

            else -> Result.failure()
        }
    }

    private fun createNotification(message: String, progress: Int) =
        NotificationCompat.Builder(context, WifiShareApp.CHANNEL_ID)
            .setContentTitle("WiFi Share")
            .setContentText(message)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setProgress(100, progress, progress == 0)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
}
