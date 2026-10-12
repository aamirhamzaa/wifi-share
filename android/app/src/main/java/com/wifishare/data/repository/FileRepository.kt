package com.wifishare.data.repository

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import com.wifishare.data.api.ApiClient
import com.wifishare.data.api.WifiShareApi
import com.wifishare.data.model.SharedFile
import com.wifishare.data.model.UploadResponse
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okio.BufferedSink
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FileRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val apiClient: ApiClient
) {
    private val _currentBaseUrl = MutableStateFlow<String?>(null)
    val currentBaseUrl = _currentBaseUrl.asStateFlow()

    private var activeApi: WifiShareApi? = null

    fun setServerUrl(url: String, pin: String? = null) {
        _currentBaseUrl.value = url
        apiClient.setPin(pin)
        activeApi = apiClient.createApi(url)
    }

    private fun getApi(): WifiShareApi {
        return activeApi ?: throw IllegalStateException("Server not connected. Select a server first.")
    }

    suspend fun getFiles(): Result<List<SharedFile>> = withContext(Dispatchers.IO) {
        try {
            Result.success(getApi().getFiles())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteFile(filename: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val response = getApi().deleteFile(filename)
            if (response.isSuccessful) Result.success(Unit)
            else Result.failure(Exception("Failed to delete: ${response.code()}"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getClipboard(): Result<String> = withContext(Dispatchers.IO) {
        try {
            Result.success(getApi().getClipboard().text)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun setClipboard(text: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            getApi().setClipboard(mapOf("text" to text))
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun downloadFile(filename: String, totalSize: Long): Flow<Float> = flow {
        val response = getApi().downloadFile(filename)
        if (!response.isSuccessful || response.body() == null) {
            throw Exception("Failed to download $filename: ${response.code()}")
        }

        val body = response.body()!!
        val contentLength = if (body.contentLength() > 0) body.contentLength() else totalSize

        // Save to Downloads folder via MediaStore
        val resolver = context.contentResolver
        val contentValues = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, filename)
            put(MediaStore.Downloads.MIME_TYPE, "application/octet-stream")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/WiFiShare")
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
        }

        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
            ?: throw Exception("Could not create MediaStore entry for $filename")

        try {
            resolver.openOutputStream(uri)?.use { outputStream ->
                body.byteStream().use { inputStream ->
                    val buffer = ByteArray(64 * 1024)
                    var read: Int
                    var totalRead = 0L

                    while (inputStream.read(buffer).also { read = it } != -1) {
                        outputStream.write(buffer, 0, read)
                        totalRead += read
                        if (contentLength > 0) {
                            emit((totalRead.toFloat() / contentLength.toFloat()).coerceIn(0f, 1f))
                        }
                    }
                    outputStream.flush()
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentValues.clear()
                contentValues.put(MediaStore.Downloads.IS_PENDING, 0)
                resolver.update(uri, contentValues, null, null)
            }
            emit(1f)
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            throw e
        }
    }.flowOn(Dispatchers.IO)

    suspend fun uploadFileUri(uri: Uri, onProgress: (Float) -> Unit): Result<UploadResponse> = withContext(Dispatchers.IO) {
        try {
            val fileName = getFileNameFromUri(uri) ?: "upload_${System.currentTimeMillis()}"
            val fileSize = getFileSizeFromUri(uri)

            val requestBody = object : RequestBody() {
                override fun contentType() = "application/octet-stream".toMediaTypeOrNull()

                override fun contentLength() = fileSize

                override fun writeTo(sink: BufferedSink) {
                    context.contentResolver.openInputStream(uri)?.use { inputStream ->
                        val buffer = ByteArray(64 * 1024)
                        var uploaded = 0L
                        var read: Int
                        while (inputStream.read(buffer).also { read = it } != -1) {
                            sink.write(buffer, 0, read)
                            uploaded += read
                            if (fileSize > 0) {
                                onProgress((uploaded.toFloat() / fileSize.toFloat()).coerceIn(0f, 1f))
                            }
                        }
                    }
                }
            }

            val part = MultipartBody.Part.createFormData("files", fileName, requestBody)
            val response = getApi().uploadFiles(listOf(part))
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun getFileNameFromUri(uri: Uri): String? {
        var name: String? = null
        if (uri.scheme == "content") {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (cursor.moveToFirst() && nameIndex >= 0) {
                    name = cursor.getString(nameIndex)
                }
            }
        }
        return name ?: uri.lastPathSegment
    }

    private fun getFileSizeFromUri(uri: Uri): Long {
        var size = -1L
        if (uri.scheme == "content") {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (cursor.moveToFirst() && sizeIndex >= 0) {
                    size = cursor.getLong(sizeIndex)
                }
            }
        }
        return size
    }
}
