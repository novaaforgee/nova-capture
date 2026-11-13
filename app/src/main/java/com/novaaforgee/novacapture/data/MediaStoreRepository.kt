package com.novaaforgee.novacapture.data

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MediaStoreRepository(private val context: Context) {

    suspend fun getRecentCaptures(): List<MediaItem> = withContext(Dispatchers.IO) {
        val items = mutableListOf<MediaItem>()

        // 1. Fetch Screenshots from MediaStore.Images
        val imageProjection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.DATE_ADDED,
            MediaStore.Images.Media.SIZE
        )
        val imageSelection = "${MediaStore.Images.Media.DISPLAY_NAME} LIKE 'Nova_Screenshot_%' OR ${MediaStore.Images.Media.DISPLAY_NAME} LIKE 'Screenshot_%'"
        val imageSortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"

        try {
            context.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                imageProjection,
                imageSelection,
                null,
                imageSortOrder
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)

                var count = 0
                while (cursor.moveToNext() && count < 25) {
                    val id = cursor.getLong(idCol)
                    val name = cursor.getString(nameCol) ?: "Screenshot"
                    val dateAdded = cursor.getLong(dateCol)
                    val size = cursor.getLong(sizeCol)
                    val contentUri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)

                    items.add(
                        MediaItem(
                            id = id,
                            uri = contentUri,
                            name = name,
                            dateAdded = dateAdded,
                            size = size,
                            type = CaptureType.SCREENSHOT
                        )
                    )
                    count++
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 2. Fetch Screen Recordings from MediaStore.Video
        val videoProjection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.DATE_ADDED,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.DURATION
        )
        val videoSelection = "${MediaStore.Video.Media.DISPLAY_NAME} LIKE 'Nova_Record_%'"
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

        // Sort both by dateAdded descending
        items.sortedByDescending { it.dateAdded }
    }

    suspend fun deleteMedia(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            val rows = context.contentResolver.delete(uri, null, null)
            rows > 0
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
