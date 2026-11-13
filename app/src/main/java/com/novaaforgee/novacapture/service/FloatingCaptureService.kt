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
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.novaaforgee.novacapture.MainActivity
import com.novaaforgee.novacapture.recorder.ScreenshotEngine
import com.novaaforgee.novacapture.recorder.VideoRecordingEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
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
        private const val CHANNEL_ID = "nova_presentation_channel"
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
                    createFloatingWidget()
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
            .setContentTitle("Nova Capture Active")
            .setContentText("Tap to open Dashboard | Floating shutter active")
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

    private fun createFloatingWidget() {
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
            y = 380
        }

        // ULTRA-SLEEK COMPACT PILL WIDGET (Private Screenshots Style)
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(12, 8, 12, 8)
            gravity = Gravity.CENTER_VERTICAL
            val shape = GradientDrawable().apply {
                setColor(Color.parseColor("#CC1E293B")) // 80% Translucent Dark Slate
                cornerRadius = 50f
                setStroke(2, Color.parseColor("#38BDF8")) // Neon subtle border
            }
            background = shape
            alpha = 0.88f // Sleek translucent look
        }

        // 1. Shutter Button (Screenshot)
        val btnScreenshot = ImageView(this).apply {
            setImageResource(android.R.drawable.ic_menu_camera)
            setColorFilter(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(78, 78).apply {
                setMargins(4, 0, 10, 0)
            }
            val bg = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#0284C7"))
            }
            background = bg
            setPadding(16, 16, 16, 16)
        }

        // 2. Video Record Button
        val btnRecord = ImageView(this).apply {
            setImageResource(android.R.drawable.presence_video_online)
            setColorFilter(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(78, 78).apply {
                setMargins(4, 0, 10, 0)
            }
            val bg = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#E11D48"))
            }
            background = bg
            setPadding(16, 16, 16, 16)
        }

        // 3. Close Button
        val btnClose = ImageView(this).apply {
            setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
            setColorFilter(Color.parseColor("#94A3B8"))
            layoutParams = LinearLayout.LayoutParams(54, 54).apply {
                setMargins(4, 0, 4, 0)
            }
        }

        container.addView(btnScreenshot)
        container.addView(btnRecord)
        container.addView(btnClose)

        floatingView = container

        // SMOOTH ACCURATE DRAGGING LOGIC
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var isDragging = false

        container.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isDragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val deltaX = (event.rawX - initialTouchX).toInt()
                    val deltaY = (event.rawY - initialTouchY).toInt()
                    if (Math.abs(deltaX) > 6 || Math.abs(deltaY) > 6) {
                        isDragging = true
                        params.x = initialX + deltaX
                        params.y = initialY + deltaY
                        windowManager?.updateViewLayout(container, params)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    // Touch released
                    true
                }
                else -> false
            }
        }

        // Single Tap = Snap Clean (Widget hides during capture so NEVER visible in screenshot)
        btnScreenshot.setOnClickListener {
            vibrateFeedback()
            hideWidgetBriefly {
                triggerScreenshot()
            }
        }

        // Video Record Toggle
        btnRecord.setOnClickListener {
            vibrateFeedback()
            toggleVideoRecording(btnRecord)
        }

        btnClose.setOnClickListener {
            stopSelf()
        }

        windowManager?.addView(floatingView, params)
    }

    // Auto-hide floating widget during screenshot so it NEVER shows up on image
    private fun hideWidgetBriefly(onHidden: () -> Unit) {
        floatingView?.visibility = View.GONE
        serviceScope.launch {
            kotlinx.coroutines.delay(100)
            onHidden()
            kotlinx.coroutines.delay(280)
            floatingView?.visibility = View.VISIBLE
        }
    }

    private fun triggerScreenshot() {
        serviceScope.launch {
            val uri = screenshotEngine?.captureScreenshot()
            withContext(Dispatchers.Main) {
                if (uri != null) {
                    Toast.makeText(
                        applicationContext,
                        "📸 Saved directly to Gallery!",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private fun toggleVideoRecording(recordButton: ImageView) {
        if (!isRecording) {
            val started = videoRecordingEngine?.startRecording(recordAudio = false) ?: false
            if (started) {
                isRecording = true
                // DURING RECORDING: Make floating widget 92% transparent and minimal!
                // It stays barely visible to you so you can stop it, but avoids dominating the screen
                floatingView?.alpha = 0.18f

                val recBg = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.parseColor("#10B981")) // Green when recording
                }
                recordButton.background = recBg
                Toast.makeText(applicationContext, "🔴 Recording started! (Ghost widget active)", Toast.LENGTH_SHORT).show()
            }
        } else {
            val uri = videoRecordingEngine?.stopRecording()
            isRecording = false
            // Restore normal opacity when stopped
            floatingView?.alpha = 0.88f

            val defaultBg = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#E11D48"))
            }
            recordButton.background = defaultBg
            Toast.makeText(applicationContext, "✅ Video saved to Movies/NovaCapture", Toast.LENGTH_SHORT).show()
        }
    }

    private fun vibrateFeedback() {
        try {
            val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(28, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(28)
            }
        } catch (e: Exception) {
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Presentation Engine",
                NotificationManager.IMPORTANCE_MIN
            ).apply {
                description = "Silent display projection"
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
