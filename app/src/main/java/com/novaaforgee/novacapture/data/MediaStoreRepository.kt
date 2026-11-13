package com.novaaforgee.novacapture.data

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.OutputStream

class MediaStoreRepository(private val context: Context) {

    suspend fun getRecentCaptures(): List<MediaItem> = withContext(Dispatchers.IO) {
        val items = mutableListOf<MediaItem>()

        // 1. Load from 100% Isolated App Private Vault
        try {
            context.filesDir.listFiles()?.filter { it.name.startsWith("SNAP_") || it.name.startsWith("IMG_") }?.forEach { f ->
                items.add(
                    MediaItem(
                        id = f.lastModified(),
                        uri = Uri.fromFile(f),
                        name = f.name,
                        dateAdded = f.lastModified() / 1000,
                        size = f.length(),
                        type = CaptureType.SCREENSHOT
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 2. Load Screen Recordings
        val videoProjection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.DATE_ADDED,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.DURATION
        )
        val videoSelection = "${MediaStore.Video.Media.DISPLAY_NAME} LIKE 'VID_Nova_%' OR ${MediaStore.Video.Media.DISPLAY_NAME} LIKE 'Nova_Record_%'"
        val videoSortOrder = "${MediaStore.Video.Media.DATE_ADDED} DESC"

        try {
            context.contentResolver.query(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                videoProjection,
                videoSelection,
                null,
                videoSortOrder
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
                val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)

                var count = 0
                while (cursor.moveToNext() && count < 25) {
                    val id = cursor.getLong(idCol)
                    val name = cursor.getString(nameCol) ?: "Recording"
                    val dateAdded = cursor.getLong(dateCol)
                    val size = cursor.getLong(sizeCol)
                    val duration = cursor.getLong(durationCol)
                    val contentUri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)

                    items.add(
                        MediaItem(
                            id = id,
                            uri = contentUri,
                            name = name,
                            dateAdded = dateAdded,
                            size = size,
                            type = CaptureType.RECORDING,
                            durationMs = duration
                        )
                    )
                    count++
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        items.sortedByDescending { it.dateAdded }
    }

    // Manual Export to Phone Gallery (Only when user taps Download icon inside the app)
    suspend fun exportToGallery(item: MediaItem): Boolean = withContext(Dispatchers.IO) {
        if (item.uri.scheme != "file") return@withContext true
        val srcFile = File(item.uri.path ?: return@withContext false)
        if (!srcFile.exists()) return@withContext false

        try {
            val displayName = "Photo_${System.currentTimeMillis()}.png"
            val contentValues = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/NovaCapture")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues) ?: return@withContext false

            resolver.openOutputStream(uri)?.use { os: OutputStream ->
                FileInputStream(srcFile).use { fis ->
                    fis.copyTo(os)
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentValues.clear()
                contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(uri, contentValues, null, null)
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun deleteMedia(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            if (uri.scheme == "file") {
                val f = File(uri.path ?: "")
                if (f.exists()) f.delete() else false
            } else {
                val rows = context.contentResolver.delete(uri, null, null)
                rows > 0
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
