/**
 * Copyright (C) 2015 Garmin International Ltd.
 * Subject to Garmin SDK License Agreement and Wearables Application Developer Agreement.
 */
package com.garmin.android.apps.clearshot.phone.activities

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Parcelable
import android.os.Handler
import android.os.Looper
import android.text.SpannableString
import android.text.Spannable
import android.text.style.ForegroundColorSpan
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Observer
import com.garmin.android.apps.clearshot.phone.R
import com.garmin.android.apps.clearshot.phone.ui.StatusMessages
import com.garmin.android.apps.clearshot.phone.ui.UIConstants
import com.garmin.android.apps.clearshot.phone.viewmodel.DeviceViewModel
import com.garmin.android.connectiq.IQDevice
import com.garmin.android.apps.clearshot.phone.utils.IconRotationManager

// TODO Add a valid store app id.
private const val STORE_APP_ID = ""

/**
 * Activity for interacting with a connected Garmin device to control camera functions.
 * This activity follows MVVM architecture pattern with a DeviceViewModel handling business logic.
 */
class DeviceActivity : AppCompatActivity() {
    companion object {
        private const val TAG = "DeviceActivity"
        private const val EXTRA_IQ_DEVICE = "IQDevice"
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS =
            mutableListOf(
                Manifest.permission.CAMERA,
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ).apply {
                if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                    add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    // For Android 10+ we need these instead of WRITE_EXTERNAL_STORAGE
                    add(Manifest.permission.READ_MEDIA_VIDEO)
                    add(Manifest.permission.READ_MEDIA_IMAGES)
                }
            }.toTypedArray()

        fun getIntent(context: Context, device: IQDevice?): Intent {
            val intent = Intent(context, DeviceActivity::class.java)
            intent.putExtra(EXTRA_IQ_DEVICE, device)
            return intent
        }
    }

    // ViewModel
    private val viewModel: DeviceViewModel by viewModels()

    // UI components
    private lateinit var statusTextView: TextView
    private lateinit var countdownTextView: TextView
    private lateinit var cameraFlipButton: ImageButton
    private lateinit var flashToggleButton: ImageButton
    private lateinit var captureButton: ImageButton
    private lateinit var videoButton: ImageButton
    private lateinit var modeIndicator: View
    private lateinit var viewFinder: PreviewView
    private lateinit var openAppButton: ImageButton

    // Device reference
    private lateinit var device: IQDevice

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_device)

        try {
            // Get device from intent
            device = intent.getParcelableExtra<Parcelable>(EXTRA_IQ_DEVICE) as IQDevice

            // Initialize UI components
            initializeViews()

            // Initialize ViewModel with required components
            viewModel.initialize(this, this, viewFinder, device)

            // Set up observers
            setupObservers()

            // Set up click listeners
            setupClickListeners()

            // Check required permissions and start camera if granted
            if (allRequiredPermissionsGranted()) {
                viewModel.startCamera()
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

    private fun initializeViews() {
        // Find views
        statusTextView = findViewById(R.id.status_text)
        countdownTextView = findViewById(R.id.countdown_text)
        openAppButton = findViewById(R.id.open_watch_app)
        viewFinder = findViewById(R.id.viewFinder)
        cameraFlipButton = findViewById(R.id.camera_flip_button)
        flashToggleButton = findViewById(R.id.flash_toggle_button)
        captureButton = findViewById(R.id.capture_button)
        videoButton = findViewById(R.id.video_button)
        modeIndicator = findViewById(R.id.mode_indicator)

        // Initial UI state
        flashToggleButton.setImageResource(R.drawable.ic_baseline_flash_off_24)
        flashToggleButton.alpha = UIConstants.BUTTON_DISABLED_ALPHA
        videoButton.setImageResource(R.drawable.ic_baseline_videocam_24)
        captureButton.setImageResource(R.drawable.ic_baseline_camera_24)
        
        // Add a global layout listener to update mode indicator position once layout is complete
        viewFinder.viewTreeObserver.addOnGlobalLayoutListener {
            // Get current video mode
            val isVideoMode = viewModel.isVideoMode.value ?: false
            
            // Force an update to mode indicator position after layout completes
            Handler(Looper.getMainLooper()).postDelayed({
                updateModeIndicator(isVideoMode)
            }, 100)
        }
    }

    private fun setupObservers() {
        // Observe status message changes
        viewModel.statusMessage.observe(this, Observer { message ->
            statusTextView.text = message
        })

        // Observe countdown changes
        viewModel.countdownSeconds.observe(this, Observer { seconds ->
            countdownTextView.text = if (seconds > 0) seconds.toString() else ""
            countdownTextView.visibility = if (seconds > 0) View.VISIBLE else View.GONE
        })

        // Observe video mode changes
        viewModel.isVideoMode.observe(this, Observer { isVideoMode ->
            Log.d(TAG, "Observer: isVideoMode changed to $isVideoMode")
            
            // Clear any pending UI updates to avoid race conditions
            Handler(Looper.getMainLooper()).removeCallbacksAndMessages(null)
            
            // Add a small delay to ensure UI is ready
            Handler(Looper.getMainLooper()).postDelayed({
                if (!isFinishing) {
                    updateModeIndicator(isVideoMode)
                }
            }, 50)
        })

        // Observe flash state changes
        viewModel.isFlashEnabled.observe(this, Observer { isEnabled ->
            updateFlashButtonIcon(isEnabled)
        })

        // Observe device connection state
        viewModel.isDeviceConnected.observe(this, Observer { isConnected ->
            Log.d(TAG, "Connection state changed to: $isConnected")
            updateActionBarTitle(isConnected)
        })

        // Initialize with status message
        viewModel.updateStatusWithTimeout(StatusMessages.CAMERA_READY)
    }

    private fun setupClickListeners() {
        // Camera flip button
        cameraFlipButton.setOnClickListener {
            // Disable button to prevent multiple clicks
            cameraFlipButton.isEnabled = false
            
            // Provide visual feedback by briefly changing button appearance
            cameraFlipButton.alpha = 0.5f
            
            // Show a temporary status message
            viewModel.updateStatusWithTimeout("Switching camera...", 2000)
            
            // Flip camera
            viewModel.flipCamera()
            
            // Update flash button state based on camera position
            val isFrontCamera = viewModel.isFrontCamera()
            flashToggleButton.isEnabled = !isFrontCamera
            flashToggleButton.alpha = if (isFrontCamera) 
                UIConstants.BUTTON_DISABLED_ALPHA else UIConstants.BUTTON_ENABLED_ALPHA
                
            // Re-enable and restore button appearance after a delay
            Handler(Looper.getMainLooper()).postDelayed({
                cameraFlipButton.alpha = 1.0f
                cameraFlipButton.isEnabled = true
            }, 1000) // Longer delay to prevent rapid consecutive clicks
        }

        // Flash toggle button
        flashToggleButton.setOnClickListener {
            if (!viewModel.isFrontCamera()) {
                viewModel.toggleFlash()
            }
        }

        // Camera mode button
        captureButton.setOnClickListener {
            if (viewModel.isVideoMode.value == true) {
                // Switch to photo mode
                viewModel.toggleVideoMode()
                viewModel.updateStatusWithTimeout(StatusMessages.PHOTO_MODE)
            } else {
                // Take photo
                viewModel.takePhoto()
            }
        }

        // Video mode button
        videoButton.setOnClickListener {
            if (viewModel.isVideoMode.value == true) {
                // Toggle recording
                viewModel.handleVideoButtonClick()
            } else {
                // Switch to video mode
                viewModel.toggleVideoMode()
                viewModel.updateStatusWithTimeout(StatusMessages.VIDEO_MODE)
            }
        }

        // Open app button
        openAppButton.setOnClickListener {
            viewModel.openApp()
        }

        // Device menu (we now use this as a back button)
        findViewById<View>(R.id.device_menu_button).setOnClickListener {
            // Simply finish this activity to return to MainActivity
            finish()
        }
    }

    private fun updateFlashButtonIcon(isEnabled: Boolean) {
        flashToggleButton.setImageResource(
            if (isEnabled) R.drawable.ic_baseline_flash_on_24
            else R.drawable.ic_baseline_flash_off_24
        )
    }

    fun updateModeIndicator(isVideo: Boolean) {
        Log.d(TAG, "updateModeIndicator: isVideo=$isVideo")
        
        // Use post with delay to ensure UI thread execution and layout is complete
        Handler(Looper.getMainLooper()).postDelayed({
            if (isFinishing) return@postDelayed

            try {
                // Ensure the view hierarchy is properly laid out
                viewFinder.post {
                    // Measure the actual positions after layout
                    val capturePos = IntArray(2)
                    val videoPos = IntArray(2)
                    captureButton.getLocationInWindow(capturePos)
                    videoButton.getLocationInWindow(videoPos)
                    
                    val captureX = captureButton.x
                    val videoX = videoButton.x
                    
                    // Calculate target translation based on actual measured positions
                    val targetTranslationX = if (isVideo) videoX - captureX else 0f
                    Log.d(TAG, "Animating indicator to x=$targetTranslationX (isVideo=$isVideo, captureX=$captureX, videoX=$videoX)")
                    
                    modeIndicator.animate()
                        .translationX(targetTranslationX)
                        .setDuration(UIConstants.MODE_SWITCH_ANIMATION_DURATION)
                        .start()
                    
                    updateButtonSizes(isVideo)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error animating mode indicator", e)
            }
        }, 50) // Small delay to ensure layout is stable
    }

    private fun updateButtonSizes(isVideoMode: Boolean) {
        val videoScale = if (isVideoMode) UIConstants.ACTIVE_BUTTON_SCALE else UIConstants.INACTIVE_BUTTON_SCALE
        val photoScale = if (isVideoMode) UIConstants.INACTIVE_BUTTON_SCALE else UIConstants.ACTIVE_BUTTON_SCALE
        
        videoButton.animate()
            .scaleX(videoScale)
            .scaleY(videoScale)
            .setDuration(UIConstants.MODE_SWITCH_ANIMATION_DURATION)
            .start()
        
        captureButton.animate()
            .scaleX(photoScale)
            .scaleY(photoScale)
            .setDuration(UIConstants.MODE_SWITCH_ANIMATION_DURATION)
            .start()
    }

    fun updateActionBarTitle(isConnected: Boolean) {
        Log.d(TAG, "updateActionBarTitle called with isConnected=$isConnected")
        val prefix = "ClearShot | "
        val deviceName = device.friendlyName
        val fullTitle = prefix + deviceName
        
        val spannableTitle = SpannableString(fullTitle)
        val deviceNameColor = if (isConnected) {
            Log.d(TAG, "Setting title color to green")
            ContextCompat.getColor(this, android.R.color.holo_green_light)
        } else {
            Log.d(TAG, "Setting title color to red")
            ContextCompat.getColor(this, android.R.color.holo_red_light)
        }
        
        // Apply color span only to the device name portion
        spannableTitle.setSpan(
            ForegroundColorSpan(deviceNameColor), 
            prefix.length,  // Start index (after "ClearShot | ")
            fullTitle.length,  // End index (end of the string)
            Spannable.SPAN_INCLUSIVE_INCLUSIVE
        )
        
        supportActionBar?.let { actionBar ->
            actionBar.title = spannableTitle
        }
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "onResume: Registering for app events")
        
        // Start rotation updates for all buttons using existing references
        val viewsToRotate = listOf<View>(
            cameraFlipButton,
            findViewById<ImageButton>(R.id.device_menu_button),
            flashToggleButton,
            captureButton,
            videoButton,
            openAppButton,
            countdownTextView
        )
        IconRotationManager.startRotationUpdates(this, viewsToRotate)
        
        // First register for events
        viewModel.registerForAppEvents()
        
        // Then check current connection state and update UI
        Handler(Looper.getMainLooper()).postDelayed({
            if (!isFinishing) {
                try {
                    // Check current connection state from LiveData
                    val isConnected = viewModel.isDeviceConnected.value ?: false
                    Log.d(TAG, "onResume: Current connection state: $isConnected")
                    updateActionBarTitle(isConnected)
                    
                    // Start camera
                    viewModel.startCamera()
                } catch (e: Exception) {
                    Log.e(TAG, "Error in onResume", e)
                }
            }
        }, 300)
    }

    override fun onPause() {
        super.onPause()
        Log.d(TAG, "onPause: Unregistering events")
        
        // Stop rotation updates
        IconRotationManager.stopRotationUpdates()
        
        // Properly clean up before pausing
        try {
            viewModel.unregisterForEvents()
        } catch (e: Exception) {
            Log.e(TAG, "Error unregistering events in onPause", e)
        }
    }

    override fun onStop() {
        super.onStop()
        Log.d(TAG, "onStop: Shutting down")
        
        // When activity is stopped (not visible), shut down camera to free resources
        try {
            viewModel.shutdown()
        } catch (e: Exception) {
            Log.e(TAG, "Error shutting down in onStop", e)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        
        // Clear flags and perform final cleanup
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        
        // Ensure camera is properly shut down
        try {
            viewModel.shutdown()
        } catch (e: Exception) {
            Log.e(TAG, "Error shutting down in onDestroy", e)
        }
    }

    /**
     * Checks if all required permissions are granted
     * @return true if all required permissions are granted, false otherwise
     */
    private fun allRequiredPermissionsGranted(): Boolean {
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
            // Check if camera and audio permissions are granted (these are required)
            val requiredPermissionsGranted = REQUIRED_PERMISSIONS.filter {
                it != Manifest.permission.ACCESS_FINE_LOCATION && 
                it != Manifest.permission.ACCESS_COARSE_LOCATION
            }.all {
                ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
            }

            if (requiredPermissionsGranted) {
                try {
                    viewModel.startCamera()
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to start camera after permission grant", e)
                    Toast.makeText(this, "Failed to start camera: ${e.message}", Toast.LENGTH_LONG).show()
                    finish()
                }
            } else {
                Toast.makeText(this, StatusMessages.ERROR_CAMERA_PERMISSION, Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }
}