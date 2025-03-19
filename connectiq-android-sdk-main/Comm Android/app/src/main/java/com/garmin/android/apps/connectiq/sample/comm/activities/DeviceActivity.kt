/**
 * Copyright (C) 2015 Garmin International Ltd.
 * Subject to Garmin SDK License Agreement and Wearables Application Developer Agreement.
 */
package com.garmin.android.apps.connectiq.sample.comm.activities

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Parcelable
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import com.garmin.android.apps.connectiq.sample.comm.R
import com.garmin.android.connectiq.ConnectIQ
import com.garmin.android.connectiq.IQApp
import com.garmin.android.connectiq.IQDevice
import com.garmin.android.connectiq.exception.InvalidStateException

import android.os.Handler
import android.os.Looper
import android.view.WindowManager

import androidx.camera.view.PreviewView

import com.garmin.android.apps.connectiq.sample.comm.camera.MyCameraManager
import com.garmin.android.apps.connectiq.sample.comm.connectiq.ConnectIQManager
import android.widget.ImageButton
import com.garmin.android.apps.connectiq.sample.comm.ui.UIConstants
import com.garmin.android.apps.connectiq.sample.comm.ui.StatusMessages

import android.text.SpannableString
import android.text.Spannable
import android.text.style.ForegroundColorSpan

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


        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = arrayOf(android.Manifest.permission.CAMERA)

        fun getIntent(context: Context, device: IQDevice?): Intent {
            val intent = Intent(context, DeviceActivity::class.java)
            intent.putExtra(EXTRA_IQ_DEVICE, device)
            return intent
        }
    }

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

    private lateinit var cameraManager: MyCameraManager
    private val handler = Handler(Looper.getMainLooper())
    private var countdownRunnable: Runnable? = null
    private var countdownSeconds = 0
    private var isCountdownCancelled = false  // Add flag to track cancellation

    private lateinit var viewFinder: PreviewView


    private lateinit var connectIQManager: ConnectIQManager

    private val onPhotoTaken = { status: String ->
        runOnUiThread {
            statusTextView.text = StatusMessages.PHOTO_SAVED
            connectIQManager.sendMessage(status)
            // Update status to ready state after a short delay
            handler.postDelayed({
                updateStatusWithTimeout(
                    if (cameraManager.isVideoMode()) StatusMessages.VIDEO_READY 
                    else StatusMessages.CAMERA_READY
                )
            }, UIConstants.STATUS_MESSAGE_TIMEOUT)
        }
    }

    private val onRecordingStatusUpdate = { status: String ->
        runOnUiThread {
            when (status) {
                StatusMessages.RECORDING_STARTED -> {
                    startRecordingTimer()
                    Log.d(TAG, "Recording started: $status")
                    connectIQManager.sendMessage("Recording started")
                }
                StatusMessages.RECORDING_STOPPED -> {
                    stopRecordingTimer()
                    statusTextView.text = StatusMessages.RECORDING_STOPPED
                    connectIQManager.sendMessage(StatusMessages.RECORDING_STOPPED)
                    // Update status to ready state after recording stops
                    handler.postDelayed({
                        updateStatusWithTimeout(StatusMessages.VIDEO_READY)
                    }, UIConstants.STATUS_MESSAGE_TIMEOUT)
                }
                StatusMessages.RECORDING_CANCELLED -> {

                    //I am not sure what the difference between stopped and cancelled is, should investigate, likely todo with the watch 
                    stopRecordingTimer()
                    statusTextView.text = StatusMessages.RECORDING_STOPPED
                    // Don't send any message for cancellation
                    // Update status to ready state after recording stops
                    handler.postDelayed({
                        updateStatusWithTimeout(StatusMessages.VIDEO_READY)
                    }, UIConstants.STATUS_MESSAGE_TIMEOUT)
                }
                else -> {
                    statusTextView.text = status
                }
            }
        }
    }

    private var recordingStartTime: Long = 0
    private val recordingUpdateRunnable = object : Runnable {
        override fun run() {
            if (cameraManager.isRecording()) {
                val elapsedSeconds = (System.currentTimeMillis() - recordingStartTime) / 1000
                val minutes = elapsedSeconds / 60
                val seconds = elapsedSeconds % 60
                statusTextView.text = String.format("Recording: %02d:%02d", minutes, seconds)
                handler.postDelayed(this, 1000)
            }
        }
    }

    private fun startRecordingTimer() {
        recordingStartTime = System.currentTimeMillis()
        handler.post(recordingUpdateRunnable)
    }

    private fun stopRecordingTimer() {
        handler.removeCallbacks(recordingUpdateRunnable)
    }

    private fun updateStatusWithTimeout(message: String, timeoutMs: Long = UIConstants.STATUS_MESSAGE_TIMEOUT) {
        // Don't update status if we're showing a countdown or recording
        if (countdownTextView.visibility == View.VISIBLE || cameraManager.isRecording()) {
            return
        }
        
        statusTextView.text = message
        handler.removeCallbacksAndMessages(null)
        
        // Only set up auto-reset for temporary messages
        if (message != StatusMessages.CAMERA_READY && message != StatusMessages.VIDEO_READY) {
            handler.postDelayed({
                if (!isFinishing) {
                    statusTextView.text = if (cameraManager.isVideoMode()) 
                        StatusMessages.VIDEO_READY 
                    else 
                        StatusMessages.CAMERA_READY
                }
            }, timeoutMs)
        }
    }

fun updateActionBarTitle(isConnected: Boolean) {
    val prefix = "ClearShot | "
    val deviceName = device.friendlyName
    val fullTitle = prefix + deviceName
    
    val spannableTitle = SpannableString(fullTitle)
    val deviceNameColor = if (isConnected) 
                        ContextCompat.getColor(this, android.R.color.holo_green_light)
                     else 
                        ContextCompat.getColor(this, android.R.color.holo_red_light)
    
    // Apply color span only to the device name portion
    spannableTitle.setSpan(
        ForegroundColorSpan(deviceNameColor), 
        prefix.length,  // Start index (after "ClearShot | ")
        fullTitle.length,  // End index (end of the string)
        Spannable.SPAN_INCLUSIVE_INCLUSIVE
    )
    
    actionBar?.title = spannableTitle
}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_device)

        try {
            device = intent.getParcelableExtra<Parcelable>(EXTRA_IQ_DEVICE) as IQDevice
            myApp = IQApp(COMM_WATCH_ID)

            // Set initial action bar title
            updateActionBarTitle(true)

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
                    onPhotoTaken = onPhotoTaken,
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
                    onRecordingStatusUpdate = onRecordingStatusUpdate
                )

                // Set initial icons and states after camera manager is initialized
                isFlashEnabled = false
                flashToggleButton.setImageResource(R.drawable.ic_baseline_flash_off_24)
                flashToggleButton.isEnabled = !cameraManager.isFrontCamera()
                flashToggleButton.alpha = if (cameraManager.isFrontCamera()) 0.5f else 1.0f
                videoButton.setImageResource(R.drawable.ic_baseline_videocam_24)
                captureButton.setImageResource(R.drawable.ic_baseline_camera_24)
                updateStatusWithTimeout(StatusMessages.CAMERA_READY)

                // Initialize camera with permission check
                if (allPermissionsGranted()) {
                    try {
                        // Add a small delay before starting camera to ensure view is ready
                        handler.postDelayed({
                            try {
                                cameraManager.startCamera()
                            } catch (e: Exception) {
                                Log.e(TAG, "Failed to start camera after delay", e)
                                // Try flipping camera as a fallback
                                try {
                                    cameraManager.flipCamera()
                                } catch (e2: Exception) {
                                    Log.e(TAG, "Failed to flip camera as fallback", e2)
                                    Toast.makeText(this, "Camera initialization failed", Toast.LENGTH_LONG).show()
                                    finish()
                                }
                            }
                        }, 100)
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
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize camera manager", e)
                Toast.makeText(this, "Camera initialization failed: ${e.message}", Toast.LENGTH_LONG).show()
                finish()
                return
            }

            connectIQManager = ConnectIQManager(
                this, 
                device, 
                statusTextView,
                cameraManager,  // Pass the same instance
                onPhotoRequest = { delaySeconds ->
                    when {
                        delaySeconds == -1 -> {
                            // Cancel request received
                            Log.d(TAG, "Cancel request received")
                            isCountdownCancelled = true
                            cameraManager.cancelCapture()
                            statusTextView.text = StatusMessages.RECORDING_CANCELLED
                            countdownTextView.text = ""
                            countdownTextView.visibility = View.GONE
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
            )



            setupClickListeners()

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
        if (cameraManager.isRecording()) {
            cameraManager.cancelCapture()
        }
        cameraManager.shutdown()
    }

    override fun onResume() {
        super.onResume()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        connectIQManager.registerForAppEvents()
        
        // Update action bar title color based on connection status
        updateActionBarTitle(connectIQManager.isDeviceConnected())
        
        // Add a small delay before restarting camera to ensure proper cleanup
        handler.postDelayed({
            if (!cameraManager.isActive()) {
                try {
                    cameraManager.startCamera()
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to restart camera in onResume", e)
                    // Try flipping camera as a fallback
                    try {
                        cameraManager.flipCamera()
                    } catch (e2: Exception) {
                        Log.e(TAG, "Failed to flip camera as fallback", e2)
                    }
                }
            }
        }, 100) // Small delay to ensure proper cleanup
    }

    override fun onPause() {
        super.onPause()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        
        // Update action bar title color based on connection status
        updateActionBarTitle(connectIQManager.isDeviceConnected())
        
        // Ensure camera is properly stopped before pausing
        try {
            if (cameraManager.isActive()) {
                // If we're recording, stop the recording first
                if (cameraManager.isRecording()) {
                    cameraManager.stopVideoRecording()
                }
                // Then shutdown the camera
                cameraManager.shutdown()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping camera in onPause", e)
        }

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
            finish()
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
        // Reset cancel flag
        isCountdownCancelled = false
        
        // Clear any existing countdown
        countdownRunnable?.let { handler.removeCallbacks(it) }

        // Start countdown
        countdownSeconds = seconds
        countdownTextView.visibility = View.VISIBLE
        countdownTextView.text = seconds.toString()
        
        countdownRunnable = object : Runnable {
            override fun run() {
                if (countdownSeconds > 0 && !isCountdownCancelled) {
                    countdownSeconds--
                    countdownTextView.text = countdownSeconds.toString()
                    handler.postDelayed(this, 1000)
                } else {
                    countdownTextView.visibility = View.GONE
                    if (!isCountdownCancelled) {
                        cameraManager.takePhoto()
                    }
                }
            }
        }
        handler.post(countdownRunnable!!)
    }

    fun updateModeIndicator(isVideo: Boolean) {
        val targetTranslationX = if (isVideo) videoButton.x - captureButton.x else 0f
        modeIndicator.animate()
            .translationX(targetTranslationX)
            .setDuration(UIConstants.MODE_SWITCH_ANIMATION_DURATION)
            .start()

        updateButtonSizes(isVideo) 
    }

   private fun updateButtonSizes(isVideoMode: Boolean) {
        val scale = if (isVideoMode) 1.4f else 1.0f
        videoButton.animate()
            .scaleX(scale)
            .scaleY(scale)
            .setDuration(200)
            .start()
        
        captureButton.animate()
            .scaleX(1.0f / scale)
            .scaleY(1.0f / scale)
            .setDuration(200)
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
        // Reset cancel flag
        isCountdownCancelled = false
        
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
                if (countdownSeconds > 0 && !isCountdownCancelled) {
                    countdownSeconds--
                    countdownTextView.text = countdownSeconds.toString()
                    handler.postDelayed(this, 1000)
                } else {
                    countdownTextView.visibility = View.GONE
                    if (!isCountdownCancelled) {
                        // Ensure we're in video mode before starting recording
                        if (!cameraManager.isVideoMode()) {
                            cameraManager.toggleVideoMode()
                            updateModeIndicator(true)
                        }
                        cameraManager.startVideoRecording()
                    }
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