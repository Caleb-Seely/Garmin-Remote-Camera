/**
 * Copyright (C) 2015 Garmin International Ltd.
 * Subject to Garmin SDK License Agreement and Wearables Application Developer Agreement.
 */
package com.garmin.android.apps.connectiq.sample.comm.activities

import android.app.Activity
import android.app.AlertDialog
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Parcelable
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
//import com.garmin.android.apps.connectiq.sample.comm.MessageFactory
import com.garmin.android.apps.connectiq.sample.comm.R
import com.garmin.android.apps.connectiq.sample.comm.adapter.MessagesAdapter
import com.garmin.android.connectiq.ConnectIQ
import com.garmin.android.connectiq.IQApp
import com.garmin.android.connectiq.IQDevice
import com.garmin.android.connectiq.exception.InvalidStateException
import com.garmin.android.connectiq.exception.ServiceUnavailableException
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import android.content.pm.ResolveInfo
import android.net.Uri
import androidx.core.content.FileProvider
import android.hardware.camera2.CameraManager
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraAccessException
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import com.garmin.android.apps.connectiq.sample.comm.camera.MyCameraManager
import com.garmin.android.apps.connectiq.sample.comm.connectiq.ConnectIQManager
import android.widget.ImageButton
import com.garmin.android.apps.connectiq.sample.comm.ui.UIConstants
import com.garmin.android.apps.connectiq.sample.comm.ui.StatusMessages


// TODO Add a valid store app id.
private const val STORE_APP_ID = ""

class DeviceActivity : Activity(), LifecycleOwner {
    // Implement getLifecycle() method required by LifecycleOwner
    private val lifecycleRegistry = LifecycleRegistry(this)
    override val lifecycle: Lifecycle
        get() = lifecycleRegistry


    companion object {
        private const val TAG = "DeviceActivity"
        private const val EXTRA_IQ_DEVICE = "IQDevice"
        private const val COMM_WATCH_ID = "a3421feed289106a538cb9547ab12095"
        private const val CAMERA_PERMISSION_REQUEST = 100
        private const val STORAGE_PERMISSION_REQUEST = 101
        private const val CAMERA_REQUEST_CODE = 102
        private const val FILE_PROVIDER_AUTHORITY = "com.garmin.android.apps.connectiq.sample.comm.fileprovider"
        private const val FLASH_DELAY = 1000L // 1 second between flash blinks
        private const val STORE_APP_ID = ""

        private const val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = arrayOf(android.Manifest.permission.CAMERA)

        fun getIntent(context: Context, device: IQDevice?): Intent {
            val intent = Intent(context, DeviceActivity::class.java)
            intent.putExtra(EXTRA_IQ_DEVICE, device)
            return intent
        }
    }

    private var deviceStatusView: TextView? = null
    private var openAppButtonView: ImageButton? = null
    private lateinit var statusTextView: TextView
    private lateinit var countdownTextView: TextView
    private lateinit var cameraFlipButton: ImageButton
    private lateinit var flashToggleButton: ImageButton
    private lateinit var captureButton: ImageButton
    private lateinit var videoButton: ImageButton
    private lateinit var modeIndicator: View
    private var isFlashEnabled = false
    private var isVideoMode = false

    private val connectIQ: ConnectIQ = ConnectIQ.getInstance()
    private lateinit var device: IQDevice
    private lateinit var myApp: IQApp

    private var appIsOpen = false

    private val openAppListener = ConnectIQ.IQOpenApplicationListener { _, _, status ->
        runOnUiThread {
            Log.d(TAG, "App status changed: ${status.name}")
        Toast.makeText(applicationContext, "App Status: " + status.name, Toast.LENGTH_SHORT).show()

        if (status == ConnectIQ.IQOpenApplicationStatus.APP_IS_ALREADY_RUNNING) {
            appIsOpen = true
                openAppButtonView?.setImageResource(R.drawable.ic_baseline_send_24)
        } else {
            appIsOpen = false
                openAppButtonView?.setImageResource(R.drawable.ic_baseline_send_24)
            }
        }
    }

    private var currentPhotoPath: String? = null
    private lateinit var cameraManager: MyCameraManager
    private var cameraId: String? = null
    private val handler = Handler(Looper.getMainLooper())
    private var countdownRunnable: Runnable? = null
    private var countdownSeconds = 0
    private var isCameraOpen = false
    private var pendingPhotoDelay: Int? = null

    private lateinit var viewFinder: PreviewView
    private var imageCapture: ImageCapture? = null
    private var camera: Camera? = null
    private lateinit var cameraExecutor: ExecutorService
    private var isCameraInitialized = false
    private var isCameraActive = false

    private lateinit var connectIQManager: ConnectIQManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_device)

        try {
            device = intent.getParcelableExtra<Parcelable>(EXTRA_IQ_DEVICE) as IQDevice
            myApp = IQApp(COMM_WATCH_ID)

            // Initialize views
            statusTextView = findViewById(R.id.status_text)
            countdownTextView = findViewById(R.id.countdown_text)
            openAppButtonView = findViewById(R.id.openapp)
            viewFinder = findViewById(R.id.viewFinder)
            cameraFlipButton = findViewById(R.id.camera_flip_button)
            flashToggleButton = findViewById(R.id.flash_toggle_button)
            captureButton = findViewById(R.id.capture_button)
            videoButton = findViewById(R.id.video_button)
            modeIndicator = findViewById(R.id.mode_indicator)

            // Initialize managers with error handling
            try {
                cameraManager = MyCameraManager(
                    this,
                    this,
                    viewFinder,
                    onPhotoTaken = { status ->
                        runOnUiThread {
                            statusTextView.text = status
                            connectIQManager.sendMessage(status)
                        }
                    },
                    onCountdownUpdate = { seconds ->
                        runOnUiThread {
                            countdownTextView.text = if (seconds > 0) seconds.toString() else ""
                            countdownTextView.visibility = if (seconds > 0) View.VISIBLE else View.GONE
                        }
                    },
                    onCameraSwapEnabled = { enabled ->
                        runOnUiThread {
                            cameraFlipButton.visibility = if (enabled) View.VISIBLE else View.GONE
                        }
                    },
                    onRecordingStatusUpdate = { status ->
                        runOnUiThread {
                            statusTextView.text = status
                            // Only send messages for significant state changes
                            if (status == StatusMessages.RECORDING_STARTED || 
                                status == StatusMessages.RECORDING_STOPPED || 
                                status == StatusMessages.RECORDING_CANCELLED) {
                                connectIQManager.sendMessage(status)
                            }
                        }
                    }
                )

                // Set initial icons and states after camera manager is initialized
                isFlashEnabled = false
                flashToggleButton.setImageResource(R.drawable.ic_baseline_flash_off_24)
                flashToggleButton.isEnabled = !cameraManager.isFrontCamera()
                flashToggleButton.alpha = if (cameraManager.isFrontCamera()) 0.5f else 1.0f
                videoButton.setImageResource(R.drawable.ic_baseline_videocam_24)
                captureButton.setImageResource(R.drawable.ic_baseline_camera_24)
                updateStatusWithTimeout(StatusMessages.CAMERA_READY)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize camera manager", e)
                Toast.makeText(this, "Camera initialization failed: ${e.message}", Toast.LENGTH_LONG).show()
                finish()
                return
            }

            connectIQManager = ConnectIQManager(this, device, statusTextView) { delaySeconds ->
                when {
                    delaySeconds == -1 -> {
                        // Cancel request received or stop recording
                        if (cameraManager.isRecording()) {
                            cameraManager.stopVideoRecording()
                            statusTextView.text = StatusMessages.RECORDING_STOPPED
                            countdownTextView.text = ""
                            countdownTextView.visibility = View.GONE
                        } else {
                            cameraManager.cancelPhoto()
                            statusTextView.text = StatusMessages.RECORDING_CANCELLED
                        }
                    }
                    delaySeconds == -2 -> {
                        // Immediate video command
                        if (!cameraManager.isVideoMode()) {
                            cameraManager.toggleVideoMode()
                            updateModeIndicator(true)
                            updateStatusWithTimeout(StatusMessages.VIDEO_MODE)
                        }
                        cameraManager.startVideoRecording()
                    }
                    delaySeconds < -2 -> {
                        // Delayed video command (convert back to positive delay)
                        val actualDelay = -(delaySeconds + 3)
                        if (!cameraManager.isVideoMode()) {
                            cameraManager.toggleVideoMode()
                            updateModeIndicator(true)
                        }
                        startVideoCountdown(actualDelay)
                    }
                    delaySeconds > 0 -> {
                        // Regular photo with delay
                        if (cameraManager.isVideoMode()) {
                            cameraManager.toggleVideoMode()
                            updateModeIndicator(false)
                        }
                        startCountdown(delaySeconds)
                    }
                    else -> {
                        // Immediate photo
                        if (cameraManager.isVideoMode()) {
                            cameraManager.toggleVideoMode()
                            updateModeIndicator(false)
                        }
                        cameraManager.takePhoto()
                    }
                }
            }



            setupClickListeners()

            // Initialize camera with permission check
            if (allPermissionsGranted()) {
                try {
                    cameraManager.startCamera()
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to start camera", e)
                    Toast.makeText(this, "Failed to start camera: ${e.message}", Toast.LENGTH_LONG).show()
                    finish()
                }
            } else {
                ActivityCompat.requestPermissions(
                    this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS
                )
            }

            // Keep screen on for countdown and recording
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        } catch (e: Exception) {
            Log.e(TAG, "Error in onCreate", e)
            Toast.makeText(this, "Error initializing app: ${e.message}", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        countdownRunnable?.let { handler.removeCallbacks(it) }
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        cameraManager.shutdown()
    }

    override fun onResume() {
        super.onResume()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        connectIQManager.registerForAppEvents()
        
        if (!cameraManager.isActive()) {
            cameraManager.startCamera()
        }
    }

    override fun onPause() {
        super.onPause()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        // Don't set isCameraActive to false here, let the lifecycle handle it
        // This allows the preview to continue when returning from background

        try {
            connectIQ.unregisterForDeviceEvents(device)
            connectIQ.unregisterForApplicationEvents(device, myApp)
        } catch (_: InvalidStateException) {
        }
        connectIQManager.unregisterForEvents()
    }

    private fun setupButtons() {
        findViewById<Button>(R.id.openapp).setOnClickListener {
            connectIQManager.openApp()
        }

        findViewById<Button>(R.id.send_test_msg_button).setOnClickListener {
            connectIQManager.sendMessage("Test Message")
        }
    }

    private fun setupClickListeners() {
        // Camera flip button
        cameraFlipButton.setOnClickListener {
            cameraManager.flipCamera()
            // Disable flash for front camera
            if (cameraManager.isFrontCamera()) {
                isFlashEnabled = false
                flashToggleButton.isEnabled = false
                flashToggleButton.alpha = UIConstants.BUTTON_DISABLED_ALPHA
                updateFlashButtonIcon()
            } else {
                flashToggleButton.isEnabled = true
                flashToggleButton.alpha = UIConstants.BUTTON_ENABLED_ALPHA
            }
        }

        // Flash toggle button
        flashToggleButton.setOnClickListener {
            if (!cameraManager.isFrontCamera()) {
                isFlashEnabled = !isFlashEnabled
                flashToggleButton.isSelected = isFlashEnabled
                cameraManager.toggleFlash()
                updateFlashButtonIcon()
                updateStatusWithTimeout(
                    if (isFlashEnabled) StatusMessages.FLASH_ENABLED 
                    else StatusMessages.FLASH_DISABLED
                )
            }
        }

        // Camera mode button
        captureButton.setOnClickListener {
            if (cameraManager.isVideoMode()) {
                // Switch to camera mode
                cameraManager.toggleVideoMode()
                updateModeIndicator(false)
                updateStatusWithTimeout(StatusMessages.PHOTO_MODE)
            } else {
                // Take photo
                cameraManager.takePhoto()
            }
        }

        // Video mode button
        videoButton.setOnClickListener {
            if (cameraManager.isVideoMode()) {
                // Toggle recording
                handleVideoButtonClick()
            } else {
                // Switch to video mode
                cameraManager.toggleVideoMode()
                updateModeIndicator(true)
                updateStatusWithTimeout(StatusMessages.VIDEO_MODE)
            }
        }

        // Open app button
        findViewById<View>(R.id.openapp).setOnClickListener {
            connectIQManager.openApp()
        }

        // Send test message button
        findViewById<View>(R.id.send_test_msg_button).setOnClickListener {
            connectIQManager.sendMessage("Test Message")
        }
    }

    private fun updateFlashButtonIcon() {
        flashToggleButton.setImageResource(
            if (isFlashEnabled) R.drawable.ic_baseline_flash_on_24
            else R.drawable.ic_baseline_flash_off_24
        )
    }

    private fun updateCaptureButtonIcon() {
        captureButton.setImageResource(
            if (isVideoMode) R.drawable.ic_baseline_videocam_24
            else R.drawable.ic_baseline_camera_24
        )
    }

    private fun startCountdown(seconds: Int) {
        // Clear any existing countdown
        countdownRunnable?.let { handler.removeCallbacks(it) }
        
        // Update status text
        statusTextView.text = "Starting countdown"
        
        // Start countdown
        countdownSeconds = seconds
        countdownTextView.visibility = View.VISIBLE
        countdownTextView.text = seconds.toString()
        
        countdownRunnable = object : Runnable {
            override fun run() {
                if (countdownSeconds > 0) {
                    countdownSeconds--
                    countdownTextView.text = countdownSeconds.toString()
                    handler.postDelayed(this, 1000)
                } else {
                    countdownTextView.visibility = View.GONE
                    cameraManager.takePhoto()
                }
            }
        }
        handler.post(countdownRunnable!!)
    }

    private fun updateStatusWithTimeout(message: String, timeoutMs: Long = UIConstants.STATUS_MESSAGE_TIMEOUT) {
        // Don't update status if we're showing a countdown
        if (countdownTextView.visibility == View.VISIBLE) {
            return
        }
        
        statusTextView.text = message
        handler.removeCallbacksAndMessages(null)
        handler.postDelayed({
            if (!isFinishing) {
                statusTextView.text = StatusMessages.CAMERA_READY
            }
        }, timeoutMs)
    }

    private fun updateModeIndicator(isVideo: Boolean) {
        val targetTranslationX = if (isVideo) videoButton.x - captureButton.x else 0f
        modeIndicator.animate()
            .translationX(targetTranslationX)
            .setDuration(UIConstants.MODE_SWITCH_ANIMATION_DURATION)
            .start()
    }

    private fun handleVideoButtonClick() {
        if (cameraManager.isRecording()) {
            cameraManager.stopVideoRecording()
        } else {
            cameraManager.startVideoRecording()
        }
    }

    private fun startVideoCountdown(seconds: Int) {
        // Clear any existing countdown
        countdownRunnable?.let { handler.removeCallbacks(it) }
        
        // Update status text
        statusTextView.text = "Starting video recording in $seconds seconds"
        
        // Start countdown
        countdownSeconds = seconds
        countdownTextView.visibility = View.VISIBLE
        countdownTextView.text = seconds.toString()
        
        countdownRunnable = object : Runnable {
            override fun run() {
                if (countdownSeconds > 0) {
                    countdownSeconds--
                    countdownTextView.text = countdownSeconds.toString()
                    handler.postDelayed(this, 1000)
                } else {
                    countdownTextView.visibility = View.GONE
                    // Ensure we're in video mode before starting recording
                    if (!cameraManager.isVideoMode()) {
                        cameraManager.toggleVideoMode()
                        updateModeIndicator(true)
                    }
                    cameraManager.startVideoRecording()
                }
            }
        }
        handler.post(countdownRunnable!!)
    }

    /**
     * Checks if all required permissions are granted
     * @return true if all permissions are granted, false otherwise
     */
    private fun allPermissionsGranted(): Boolean {
        return REQUIRED_PERMISSIONS.all {
            ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                try {
                    cameraManager.startCamera()
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to start camera after permission grant", e)
                    Toast.makeText(this, "Failed to start camera: ${e.message}", Toast.LENGTH_LONG).show()
                    finish()
                }
            } else {
                Toast.makeText(this, "Permissions not granted by the user.", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }
}