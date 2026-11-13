package com.novaaforgee.novacapture.data

import android.net.Uri

enum class CaptureType {
    SCREENSHOT,
    RECORDING
}

data class MediaItem(
    val id: Long,
    val uri: Uri,
    val name: String,
    val dateAdded: Long,
    val size: Long,
    val type: CaptureType,
    val durationMs: Long = 0L
)
