package com.wifishare.data.model

import com.google.gson.annotations.SerializedName
import java.text.DecimalFormat

data class SharedFile(
    @SerializedName("name") val name: String,
    @SerializedName("size") val size: Long,
    @SerializedName("modified") val modified: String,
    @SerializedName("mime_type") val mimeType: String? = null
) {
    fun getFormattedSize(): String {
        if (size <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (Math.log10(size.toDouble()) / Math.log10(1024.0)).toInt()
        val format = DecimalFormat("#,##0.#")
        return "${format.format(size / Math.pow(1024.0, digitGroups.toDouble()))} ${units[digitGroups]}"
    }
}

data class UploadedFileItem(
    @SerializedName("name") val name: String,
    @SerializedName("size") val size: Long
)

data class UploadResponse(
    @SerializedName("ok") val ok: Boolean,
    @SerializedName("files") val files: List<UploadedFileItem>
)

data class ClipboardResponse(
    @SerializedName("text") val text: String
)

data class DiscoveredServer(
    val name: String,
    val host: String,
    val port: Int,
    val ip: String
) {
    val baseUrl: String get() = "http://$ip:$port/"
}
