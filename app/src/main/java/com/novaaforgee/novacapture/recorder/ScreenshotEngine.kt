package com.novaaforgee.novacapture.recorder

import android.content.Context
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.view.WindowManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
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
            "NovaPresentationDisplay",
            width,
            height,
            density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader?.surface,
            null,
            Handler(Looper.getMainLooper())
        )

        kotlinx.coroutines.delay(120)

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

            if (rowPadding > 0) {
                val cropped = Bitmap.createBitmap(bitmap, 0, 0, width, height)
                bitmap.recycle()
                bitmap = cropped
            }
        }

        virtualDisplay?.release()
        virtualDisplay = null
        imageReader?.close()
        imageReader = null

        if (bitmap == null) return@withContext null

        // 100% PRIVATE AIR-GAPPED VAULT ONLY
        // DO NOT TOUCH MediaStore at all during capture!
        // This is the EXACT reason ShamanLand Private Screenshots works without notifications!
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val fileName = "SNAP_$timeStamp.bin"

        val privateFile = File(context.filesDir, fileName)
        try {
            FileOutputStream(privateFile).use { fos ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            bitmap.recycle()
        }

        return@withContext Uri.fromFile(privateFile)
    }
}
