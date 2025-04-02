package com.garmin.android.apps.clearshot.phone.camera

import android.content.Context
import android.widget.Toast
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import com.garmin.android.apps.clearshot.phone.camera.CameraLogger.CAMERA_CONFIG
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import android.view.Surface
import android.view.WindowManager

/**
 * Manages camera configuration options like, location lens selection, flash,
 * and torch settings.
 */
class CameraConfigManager(
    private val context: Context,
    private val cameraState: CameraState,
    private val cameraInitializer: CameraInitializer
) {
    private val scope = CoroutineScope(Dispatchers.Main)
    
    /**
     * Toggle flash mode for image capture
     * @return The new flash state
     */
    fun toggleFlash(): Boolean {
        CameraLogger.d(CAMERA_CONFIG, "toggleFlash() called", this)
        
        // Only update flash mode if supported
        val newFlashState = cameraState.toggleFlash()
        
        // Update ImageCapture flash mode
        cameraInitializer.getImageCapture()?.flashMode = if (newFlashState) {
            CameraLogger.d(CAMERA_CONFIG, "Flash mode set to ON", this)
            androidx.camera.core.ImageCapture.FLASH_MODE_ON
        } else {
            CameraLogger.d(CAMERA_CONFIG, "Flash mode set to OFF", this)
            androidx.camera.core.ImageCapture.FLASH_MODE_OFF
        }
        
        return newFlashState
    }
    
    /**
     * Switch between front and back cameras
     * @return true if camera was switched successfully, false otherwise
     */
    fun flipCamera(): Boolean {
        CameraLogger.d(CAMERA_CONFIG, "flipCamera() called", this)
        
        // Toggle camera facing direction in state
        cameraState.updateCameraFacing(!cameraState.isFrontCamera)
        
        // Get ProcessCameraProvider and rebind with new camera selector
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            try {
                // Get provider and unbind current use cases
                val cameraProvider = cameraProviderFuture.get()
                
                // First unbind everything to ensure a clean slate
                try {
                    cameraProvider.unbindAll()
                } catch (e: Exception) {
                    CameraLogger.e(CAMERA_CONFIG, "Error unbinding previous use cases", e, this)
                }
                
                // Log the camera direction for debugging
                val direction = if (cameraState.isFrontCamera) "FRONT" else "BACK"
                CameraLogger.d(CAMERA_CONFIG, "Switching to $direction camera", this)
                
                // Force restart camera with new facing direction
                cameraInitializer.startCamera(forceRestart = true)
            } catch (exc: Exception) {
                handleCameraFlipError(exc, cameraProviderFuture)
            }
        }, ContextCompat.getMainExecutor(context))
        
        return true
    }
    
    /**
     * Handle errors when switching cameras
     */
    private fun handleCameraFlipError(exc: Exception, cameraProviderFuture: ListenableFuture<ProcessCameraProvider>) {
        CameraLogger.e(CAMERA_CONFIG, "Failed to flip camera", exc, this)
        
        // Show error to user
        val errorMessage = when {
            exc.message?.contains("No such camera", ignoreCase = true) == true -> 
                "This device doesn't support ${if (cameraState.isFrontCamera) "front" else "back"} camera"
            else -> "Failed to switch camera: ${exc.message}"
        }
        Toast.makeText(context, errorMessage, Toast.LENGTH_SHORT).show()
        
        // Try to recover by reverting to previous camera
        CameraLogger.d(CAMERA_CONFIG, "Reverting to ${if (!cameraState.isFrontCamera) "FRONT" else "BACK"} camera", this)
        cameraState.updateCameraFacing(!cameraState.isFrontCamera)
        
        try {
            // Try to get the camera provider
            val cameraProvider = cameraProviderFuture.get()
            
            // Unbind all use cases
            try {
                cameraProvider.unbindAll()
            } catch (e: Exception) {
                CameraLogger.e(CAMERA_CONFIG, "Error unbinding use cases during recovery", e, this)
            }
            
            // Force restart with previous configuration
            cameraInitializer.startCamera(forceRestart = true)
        } catch (e: Exception) {
            CameraLogger.e(CAMERA_CONFIG, "Camera recovery failed completely", e, this)
            Toast.makeText(context, "Camera recovery failed. Please restart the app.", Toast.LENGTH_LONG).show()
        }
    }
    
    /**
     * Toggle between photo and video modes
     * @return The new video mode state
     */
    fun toggleVideoMode(): Boolean {
        return cameraState.toggleVideoMode()
    }
    
    /**
     * Flash the torch for countdown feedback
     * @param pattern Flash pattern to use ("normal" or "final")
     * @param brightness Brightness level (0.0-1.0)
     */
    fun flashBriefly(pattern: String, brightness: Float) {
        if (!cameraState.shouldUseFlashForCountdown()) {
            return
        }
        
        val camera = cameraInitializer.getCamera()
        
        when (pattern) {
            "normal" -> {
                camera?.cameraControl?.enableTorch(true)
                scope.launch {
                    kotlinx.coroutines.delay(50)
                    camera?.cameraControl?.enableTorch(false)
                }
            }
            "final" -> {
                scope.launch {
                    camera?.cameraControl?.enableTorch(true)
                    kotlinx.coroutines.delay(50)
                    camera?.cameraControl?.enableTorch(false)
                }
            }
        }
    }
    
    /**
     * Check if flash is currently enabled
     */
    fun isFlashEnabled(): Boolean = cameraState.isFlashEnabled
    
    /**
     * Check if front camera is active
     */
    fun isFrontCamera(): Boolean = cameraState.isFrontCamera
    
    /**
     * Disable flash/torch
     */
    fun disableFlash() {
        cameraState.disableFlash()
        cameraInitializer.getCamera()?.cameraControl?.enableTorch(false)
    }
    
    /**
     * Clean up resources
     */
    fun shutdown() {
        CameraLogger.d(CAMERA_CONFIG, "Shutting down", this)
        try {
            // Turn off torch if it was on
            cameraInitializer.getCamera()?.cameraControl?.enableTorch(false)
        } catch (e: Exception) {
            CameraLogger.e(CAMERA_CONFIG, "Error during shutdown", e, this)
        }
    }
} 