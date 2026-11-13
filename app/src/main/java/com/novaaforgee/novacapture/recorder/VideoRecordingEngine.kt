package com.novaaforgee.novacapture.recorder

import android.content.ContentValues
import android.content.Context
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.util.DisplayMetrics
import android.view.WindowManager
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class VideoRecordingEngine(
    private val context: Context,
    private val mediaProjection: MediaProjection
) {
    private var mediaRecorder: MediaRecorder? = null
    private var virtualDisplay: VirtualDisplay? = null
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var currentVideoUri: Uri? = null
    private var pfd: ParcelFileDescriptor? = null
    private var tempFile: File? = null

    var isRecording = false
        private set

    fun startRecording(recordAudio: Boolean = false): Boolean {
        if (isRecording) return false

        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay.getRealMetrics(metrics)
        val width = if (metrics.widthPixels % 2 == 0) metrics.widthPixels else metrics.widthPixels - 1
        val height = if (metrics.heightPixels % 2 == 0) metrics.heightPixels else metrics.heightPixels - 1
        val density = metrics.densityDpi

        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val fileName = "Nova_Record_$timeStamp.mp4"

        try {
            val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }

            if (recordAudio) {
                try {
                    recorder.setAudioSource(MediaRecorder.AudioSource.MIC)
                } catch (e: Exception) {
                    // Fallback to video only if mic unavailable
                }
            }
            recorder.setVideoSource(MediaRecorder.VideoSource.SURFACE)
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)

            // Setup destination Uri in MediaStore
            val contentValues = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/NovaCapture")
                    put(MediaStore.Video.Media.IS_PENDING, 1)
                }
            }

            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, contentValues)
            currentVideoUri = uri

            if (uri != null) {
                pfd = resolver.openFileDescriptor(uri, "w")
                if (pfd != null) {
                    recorder.setOutputFile(pfd!!.fileDescriptor)
                }
            }

            recorder.setVideoSize(width, height)
            recorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264)
            if (recordAudio) {
                recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                recorder.setAudioSamplingRate(44100)
                recorder.setAudioEncodingBitRate(128000)
            }
            recorder.setVideoEncodingBitRate(8 * 1024 * 1024) // 8 Mbps high quality
            recorder.setVideoFrameRate(30)

            recorder.prepare()
            recorder.start()

            virtualDisplay = mediaProjection.createVirtualDisplay(
                "NovaRecordDisplay",
                width,
                height,
                density,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                recorder.surface,
                null,
                null
            )

            mediaRecorder = recorder
            isRecording = true
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            cleanup()
            return false
        }
    }

    fun stopRecording(): Uri? {
        if (!isRecording) return null

        try {
            mediaRecorder?.stop()
            mediaRecorder?.reset()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        cleanup()
        isRecording = false

        // Mark pending = 0 in MediaStore
        currentVideoUri?.let { uri ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Video.Media.IS_PENDING, 0)
                }
                context.contentResolver.update(uri, values, null, null)
            }
        }

        val resultUri = currentVideoUri
        currentVideoUri = null
        return resultUri
    }

    private fun cleanup() {
        try {
            virtualDisplay?.release()
            virtualDisplay = null
            mediaRecorder?.release()
            mediaRecorder = null
            pfd?.close()
            pfd = null
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
