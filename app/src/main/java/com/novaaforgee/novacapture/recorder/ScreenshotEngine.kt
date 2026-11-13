package com.novaaforgee.novacapture.recorder

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.DisplayMetrics
import android.view.WindowManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ScreenshotEngine(
    private val context: Context,
    private val mediaProjection: MediaProjection
) {
    private var imageReader: ImageReader? = null
    private var virtualDisplay: VirtualDisplay? = null
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    suspend fun captureScreenshot(): Uri? = withContext(Dispatchers.IO) {
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay.getRealMetrics(metrics)
        val width = metrics.widthPixels
        val height = metrics.heightPixels
        val density = metrics.densityDpi

        imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        virtualDisplay = mediaProjection.createVirtualDisplay(
            "NovaScreenshotDisplay",
            width,
            height,
            density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader?.surface,
            null,
            Handler(Looper.getMainLooper())
        )

        // Wait brief delay for mirror surface to render screen contents
        kotlinx.coroutines.delay(180)

        var bitmap: Bitmap? = null
        val image: Image? = imageReader?.acquireLatestImage()
        if (image != null) {
            val planes = image.planes
            val buffer: ByteBuffer = planes[0].buffer
            val pixelStride = planes[0].pixelStride
            val rowStride = planes[0].rowStride
            val rowPadding = rowStride - pixelStride * width

            bitmap = Bitmap.createBitmap(
                width + rowPadding / pixelStride,
                height,
                Bitmap.Config.ARGB_8888
            )
            bitmap.copyPixelsFromBuffer(buffer)
            image.close()

            // If there is row padding, crop to exact display dimensions
            if (rowPadding > 0) {
                val cropped = Bitmap.createBitmap(bitmap, 0, 0, width, height)
                bitmap.recycle()
                bitmap = cropped
            }
        }

        // Clean up virtual display
        virtualDisplay?.release()
        virtualDisplay = null
        imageReader?.close()
        imageReader = null

        if (bitmap == null) return@withContext null

        // SAVE STEALTHILY:
        // We write to App Private Cache FIRST to completely avoid triggering ContentObserver in active apps.
        // Then we silently copy into DCIM/Pictures without the forbidden keyword 'screenshot' in the filename.
        saveStealthCapture(bitmap)
    }

    private fun saveStealthCapture(bitmap: Bitmap): Uri? {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        // STEALTH FIX: Do NOT use "Screenshot" or "screenshot" in name.
        // Snapchat/Instagram listen to MediaStore query containing 'screenshot'.
        // We use "IMG_Nova_" format.
        val fileName = "IMG_Nova_$timeStamp.png"

        // Step 1: Save to private internal storage first
        val privateFile = File(context.filesDir, fileName)
        try {
            FileOutputStream(privateFile).use { fos ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Step 2: Save to public gallery Pictures/NovaCapture (Bypasses DCIM/Screenshots ContentObserver)
        val contentValues = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/NovaCapture")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }

        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)

        if (uri != null) {
            try {
                resolver.openOutputStream(uri)?.use { outputStream: OutputStream ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    contentValues.clear()
                    contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                    resolver.update(uri, contentValues, null, null)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                resolver.delete(uri, null, null)
                return Uri.fromFile(privateFile)
            } finally {
                bitmap.recycle()
            }
            return uri
        }

        bitmap.recycle()
        return Uri.fromFile(privateFile)
    }
}
