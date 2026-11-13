package com.novaaforgee.novacapture.service

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.novaaforgee.novacapture.MainActivity
import com.novaaforgee.novacapture.recorder.ScreenshotEngine
import com.novaaforgee.novacapture.recorder.VideoRecordingEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class FloatingCaptureService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var windowManager: WindowManager? = null
    private var floatingView: View? = null
    private var mediaProjection: MediaProjection? = null

    private var screenshotEngine: ScreenshotEngine? = null
    private var videoRecordingEngine: VideoRecordingEngine? = null

    private var isRecording = false

    companion object {
        const val ACTION_START = "ACTION_START"
        const val ACTION_STOP = "ACTION_STOP"
        const val EXTRA_RESULT_CODE = "EXTRA_RESULT_CODE"
        const val EXTRA_RESULT_DATA = "EXTRA_RESULT_DATA"
        private const val CHANNEL_ID = "ps_silent_channel"
        private const val NOTIFICATION_ID = 1001

        var isServiceRunning = false
            private set
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_START -> {
                val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
                val resultData: Intent? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(EXTRA_RESULT_DATA)
                }

                if (resultCode == Activity.RESULT_OK && resultData != null) {
                    startForegroundServiceWithNotification()
                    initMediaProjection(resultCode, resultData)
                    createShamanlandSingleFloatingButton()
                    isServiceRunning = true
                } else {
                    stopSelf()
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun startForegroundServiceWithNotification() {
        val openIntent = Intent(this, MainActivity::class.java)
        val pendingOpen = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, FloatingCaptureService::class.java).apply {
            action = ACTION_STOP
        }
        val pendingStop = PendingIntent.getService(
            this, 1, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Private Screenshots Active")
            .setContentText("Tap to open app • Stop to exit")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentIntent(pendingOpen)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", pendingStop)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun initMediaProjection(resultCode: Int, resultData: Intent) {
        val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjection = projectionManager.getMediaProjection(resultCode, resultData)

        mediaProjection?.let { projection ->
            screenshotEngine = ScreenshotEngine(applicationContext, projection)
            videoRecordingEngine = VideoRecordingEngine(applicationContext, projection)
        }
    }

    // EXACT 1:1 SHAMANLAND FLOATING CIRCULAR SHUTTER BUTTON
    private fun createShamanlandSingleFloatingButton() {
        if (floatingView != null) return

        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 40
            y = 400
        }

        // Circular Container (Orange Shamanland style circle)
        val container = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(130, 130)
            val circle = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#F97316")) // Vibrant Orange ShamanLand Shutter Color
                setStroke(3, Color.WHITE)
            }
            background = circle
            alpha = 0.88f
        }

        // Center Aperture Camera Icon
        val icon = ImageView(this).apply {
            setImageResource(android.R.drawable.ic_menu_camera)
            setColorFilter(Color.WHITE)
            val iconParams = FrameLayout.LayoutParams(64, 64).apply {
                gravity = Gravity.CENTER
            }
            layoutParams = iconParams
        }

        container.addView(icon)
        floatingView = container

        // ULTRA SMOOTH DRAGGING LOGIC
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var touchDownTime = 0L
        var isClick = true

        container.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    touchDownTime = System.currentTimeMillis()
                    isClick = true
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val deltaX = (event.rawX - initialTouchX).toInt()
                    val deltaY = (event.rawY - initialTouchY).toInt()
                    if (Math.abs(deltaX) > 6 || Math.abs(deltaY) > 6) {
                        isClick = false
                        params.x = initialX + deltaX
                        params.y = initialY + deltaY
                        windowManager?.updateViewLayout(container, params)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val duration = System.currentTimeMillis() - touchDownTime
                    if (isClick && duration < 380) {
                        // TAP: Instant silent screenshot (Hides button, saves silently to vault, background syncs to gallery)
                        vibrateFeedback(30)
                        hideWidgetBriefly {
                            triggerSilentScreenshot()
                        }
                    } else if (isClick && duration >= 380) {
                        // LONG PRESS: Toggle Screen Recording / Jump back to app
                        vibrateFeedback(60)
                        toggleVideoRecording(container)
                    }
                    true
                }
                else -> false
            }
        }

        windowManager?.addView(floatingView, params)
    }

    private fun hideWidgetBriefly(onHidden: () -> Unit) {
        floatingView?.visibility = View.GONE
        serviceScope.launch {
            delay(100)
            onHidden()
            delay(280)
            floatingView?.visibility = View.VISIBLE
        }
    }

    private fun triggerSilentScreenshot() {
        serviceScope.launch {
            val uri = screenshotEngine?.captureScreenshot()
            withContext(Dispatchers.Main) {
                if (uri != null) {
                    Toast.makeText(applicationContext, "📸 Saved! (Zero Alert)", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun toggleVideoRecording(container: FrameLayout) {
        if (!isRecording) {
            val started = videoRecordingEngine?.startRecording(recordAudio = false) ?: false
            if (started) {
                isRecording = true
                // Make button ultra transparent (ghost mode) during video
                container.alpha = 0.22f
                val redCircle = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.parseColor("#EF4444")) // Red when recording
                    setStroke(3, Color.WHITE)
                }
                container.background = redCircle
                Toast.makeText(applicationContext, "🔴 Recording video... Long press to stop", Toast.LENGTH_SHORT).show()
            }
        } else {
            videoRecordingEngine?.stopRecording()
            isRecording = false
            container.alpha = 0.88f
            val orangeCircle = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#F97316"))
                setStroke(3, Color.WHITE)
            }
            container.background = orangeCircle
            Toast.makeText(applicationContext, "✅ Video saved to Movies/NovaCapture", Toast.LENGTH_SHORT).show()
        }
    }

    private fun vibrateFeedback(ms: Long) {
        try {
            val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(ms)
            }
        } catch (e: Exception) {
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Private Screenshots Service",
                NotificationManager.IMPORTANCE_MIN
            ).apply {
                description = "Silent screen projection"
                setShowBadge(false)
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        isServiceRunning = false
        if (isRecording) {
            videoRecordingEngine?.stopRecording()
        }
        floatingView?.let {
            try {
                windowManager?.removeView(it)
            } catch (e: Exception) {
            }
            floatingView = null
        }
        mediaProjection?.stop()
        mediaProjection = null
        serviceScope.cancel()
    }
}
