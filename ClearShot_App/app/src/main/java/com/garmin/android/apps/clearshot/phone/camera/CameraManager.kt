package com.garmin.android.apps.clearshot.phone.camera

import android.content.Context
import android.widget.Toast
import androidx.camera.view.PreviewView
import androidx.lifecycle.LifecycleOwner
import com.garmin.android.apps.clearshot.phone.camera.CameraLogger.CAMERA_MANAGER
import java.util.concurrent.TimeUnit

/**
 * Coordinates camera components to provide a unified camera interface.
 * This class delegates to specialized managers for different camera operations.
 */
class CameraManager(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val viewFinder: PreviewView,
    private val onPhotoTaken: (String) -> Unit = {},
    private val onCountdownUpdate: ((Int) -> Unit)? = null,
    private val onCameraSwapEnabled: ((Boolean) -> Unit)? = null,
    private val onRecordingStatusUpdate: ((String) -> Unit)? = null,
    private val initialAspectRatio: Boolean = false
) {
    companion object {
        private var instanceCount = 0
    }

    // The central state repository
    private val cameraState = CameraState()
    
    // Metadata manager for orientation and location data
    private val metadataManager = MetadataManager(context)
    
    // Component managers
    private val videoManager = VideoCaptureManager(context, cameraState, onRecordingStatusUpdate, metadataManager)
    private val cameraInitializer = CameraInitializer(context, lifecycleOwner, viewFinder, cameraState, videoManager)
    private val photoManager = PhotoCaptureManager(context, onPhotoTaken, metadataManager)
    private val configManager: CameraConfigManager by lazy { CameraConfigManager(context, cameraState, cameraInitializer) }
    
    // Countdown manager with callback to appropriate capture method
    private val countdownManager = CountdownManager(
        cameraState,
        onCountdownUpdate,
        onCameraSwapEnabled
    ) {
        if (cameraState.isVideoMode) {
            executeVideoCapture()
        } else {
            executePhotoCapture()
        }
    }

    init {
        instanceCount++
        CameraLogger.d(CAMERA_MANAGER, "CameraManager instance created. Total instances: $instanceCount", this)
        // Set initial aspect ratio
        cameraState.is16_9AspectRatio = initialAspectRatio
    }

    /**
     * Initialize and start the camera
     */
    fun startCamera() {
        CameraLogger.d(CAMERA_MANAGER, "startCamera() called", this)
        if (!cameraInitializer.startCamera()) {
            CameraLogger.e(CAMERA_MANAGER, "Failed to start camera", null, this)
            Toast.makeText(context, "Failed to start camera", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Take a photo with optional countdown delay
     * @param delaySeconds Seconds to wait before capturing
     */
    fun takePhoto(delaySeconds: Int = 0) {
        CameraLogger.d(CAMERA_MANAGER, "takePhoto() with delay: $delaySeconds", this)
        
        // Switch to photo mode if needed
        if (cameraState.isVideoMode) {
            cameraState.toggleVideoMode()
        }
        
        // Start countdown (or immediate capture if delay is 0)
        countdownManager.startCountdown(delaySeconds, true)
    }
    
    /**
     * Execute the actual photo capture once countdown completes
     */
    private fun executePhotoCapture() {
        CameraLogger.d(CAMERA_MANAGER, "executePhotoCapture() called", this)
        if (!photoManager.capturePhoto(cameraInitializer.getImageCapture())) {
            CameraLogger.e(CAMERA_MANAGER, "Photo capture failed", null, this)
            Toast.makeText(context, "Failed to take photo", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Start video recording with optional countdown delay
     * @param delaySeconds Seconds to wait before starting recording
     */
    fun startVideoRecording(delaySeconds: Int = 0) {
        CameraLogger.d(CAMERA_MANAGER, "startVideoRecording() with delay: $delaySeconds", this)
        
        // Switch to video mode if needed
        if (!cameraState.isVideoMode) {
            cameraState.toggleVideoMode()
        }
        
        // Ensure camera is initialized
        if (!cameraInitializer.isCameraActive()) {
            CameraLogger.d(CAMERA_MANAGER, "Camera not active, restarting", this)
            startCamera()
            // Give camera time to initialize before proceeding
            Toast.makeText(context, "Preparing camera...", Toast.LENGTH_SHORT).show()
            return
        }
        
        // Start countdown (or immediate recording if delay is 0)
        countdownManager.startCountdown(delaySeconds, false)
    }
    
    /**
     * Execute the actual video recording once countdown completes
     */
    private fun executeVideoCapture() {
        CameraLogger.d(CAMERA_MANAGER, "executeVideoCapture() called", this)
        if (!videoManager.startRecording(cameraInitializer.getVideoCapture())) {
            CameraLogger.e(CAMERA_MANAGER, "Video recording failed to start", null, this)
            Toast.makeText(context, "Failed to start recording", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Stop video recording if active
     */
    fun stopVideoRecording() {
        CameraLogger.d(CAMERA_MANAGER, "stopVideoRecording() called", this)
        videoManager.stopRecording()
    }

    /**
     * Cancel any active capture operation (photo countdown or video recording)
     */
    fun cancelCapture() {
        CameraLogger.d(CAMERA_MANAGER, "cancelCapture() called", this)
        
        // Cancel any countdown first
        countdownManager.cancelCountdown()
        
        // Stop recording if active
        if (isRecording()) {
            stopVideoRecording()
        }
        
        // Disable flash if it was on
        configManager.disableFlash()
    }

    /**
     * Toggle flash on/off
     * @return New flash state
     */
    fun toggleFlash(): Boolean {
        return configManager.toggleFlash()
    }

    /**
     * Switch between front and back cameras
     */
    fun flipCamera() {
        configManager.flipCamera()
    }

    /**
     * Toggle between photo and video modes
     * @return New video mode state
     */
    fun toggleVideoMode(): Boolean {
        return configManager.toggleVideoMode()
    }

    /**
     * Check if recording is currently active
     */
    fun isRecording(): Boolean {
        return videoManager.isRecording()
    }

    /**
     * Check if in video mode
     */
    fun isVideoMode(): Boolean {
        return cameraState.isVideoMode
    }

    /**
     * Check if camera is active
     */
    fun isActive(): Boolean {
        return cameraInitializer.isCameraActive()
    }

    /**
     * Check if front camera is active
     */
    fun isFrontCamera(): Boolean {
        return cameraState.isFrontCamera
    }
    
    /**
     * Check if flash is enabled
     */
    fun isFlashEnabled(): Boolean {
        return cameraState.isFlashEnabled
    }

    /**
     * Clean up all resources and shutdown camera
     */
    fun shutdown() {
        CameraLogger.d(CAMERA_MANAGER, "Shutting down CameraManager", this)
        
        // First cancel any active operations
        cancelCapture()
        
        // Shutdown all components
        try {
            countdownManager.shutdown()
            videoManager.shutdown()
            configManager.shutdown()
            cameraInitializer.shutdown()
        } catch (e: Exception) {
            CameraLogger.e(CAMERA_MANAGER, "Error during shutdown", e, this)
        }
        
        // Decrement instance count for tracking purposes
        instanceCount--
        CameraLogger.d(CAMERA_MANAGER, "CameraManager instance shut down. Remaining instances: $instanceCount", this)
    }

    fun setAspectRatio(is16_9: Boolean) {
        cameraState.is16_9AspectRatio = is16_9
        restartCamera()
    }

    private fun restartCamera() {
        // First stop the current camera
        cameraInitializer.stopCamera()
        
        // Then start it again with new settings
        cameraInitializer.startCamera()
    }
} 