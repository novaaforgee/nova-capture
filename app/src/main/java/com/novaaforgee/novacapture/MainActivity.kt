package com.novaaforgee.novacapture

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import com.novaaforgee.novacapture.service.FloatingCaptureService
import com.novaaforgee.novacapture.ui.DashboardScreen
import com.novaaforgee.novacapture.ui.MainViewModel
import com.novaaforgee.novacapture.ui.theme.NovaCaptureTheme

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    // 1. Overlay Permission Launcher
    private val overlayPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (Settings.canDrawOverlays(this)) {
            requestMediaProjectionPermission()
        } else {
            Toast.makeText(this, "Overlay permission is required for floating controls", Toast.LENGTH_LONG).show()
        }
    }

    // 2. MediaProjection Screen Capture Permission Launcher
    private val mediaProjectionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            startFloatingService(result.resultCode, result.data!!)
        } else {
            Toast.makeText(this, "Screen capture permission denied", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            NovaCaptureTheme {
                DashboardScreen(
                    viewModel = viewModel,
                    onStartService = { checkPermissionsAndStart() },
                    onStopService = { stopFloatingService() }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshDashboard()
    }

    private fun checkPermissionsAndStart() {
        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            overlayPermissionLauncher.launch(intent)
        } else {
            requestMediaProjectionPermission()
        }
    }

    private fun requestMediaProjectionPermission() {
        val mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjectionLauncher.launch(mediaProjectionManager.createScreenCaptureIntent())
    }

    private fun startFloatingService(resultCode: Int, data: Intent) {
        val serviceIntent = Intent(this, FloatingCaptureService::class.java).apply {
            action = FloatingCaptureService.ACTION_START
            putExtra(FloatingCaptureService.EXTRA_RESULT_CODE, resultCode)
            putExtra(FloatingCaptureService.EXTRA_RESULT_DATA, data)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }

        viewModel.updateServiceStatus(true)
        // Minimize app to show presentation/floating controls directly over other apps
        moveTaskToBack(true)
    }

    private fun stopFloatingService() {
        val serviceIntent = Intent(this, FloatingCaptureService::class.java).apply {
            action = FloatingCaptureService.ACTION_STOP
        }
        startService(serviceIntent)
        viewModel.updateServiceStatus(false)
    }
}
