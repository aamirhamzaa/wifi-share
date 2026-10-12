package com.wifishare.data.api

import com.wifishare.data.model.ClipboardResponse
import com.wifishare.data.model.SharedFile
import com.wifishare.data.model.UploadResponse
import okhttp3.MultipartBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.*

interface WifiShareApi {

    @GET("files")
    suspend fun getFiles(): List<SharedFile>

    @Multipart
    @POST("upload")
    suspend fun uploadFiles(
        @Part files: List<MultipartBody.Part>
    ): UploadResponse

    @Streaming
    @GET("download/{filename}")
    suspend fun downloadFile(
        @Path("filename") filename: String,
        @Header("Range") range: String? = null
    ): Response<ResponseBody>

    @DELETE("files/{filename}")
    suspend fun deleteFile(
        @Path("filename") filename: String
    ): Response<Void>

    @GET("clipboard")
    suspend fun getClipboard(): ClipboardResponse

    @POST("clipboard")
    suspend fun setClipboard(
        @Body body: Map<String, String>
    ): Response<Void>

    @GET("health")
    suspend fun checkHealth(): Response<Void>
}
