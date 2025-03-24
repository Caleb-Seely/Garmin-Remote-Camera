package com.garmin.android.apps.connectiq.sample.comm.camera

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.garmin.android.apps.connectiq.sample.comm.camera.CameraLogger.COUNTDOWN
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Manages countdown functionality for both photo and video capture.
 * Handles timing, cancellation, and display updates.
 */
class CountdownManager(
    private val cameraState: CameraState,
    private val onCountdownUpdate: ((Int) -> Unit)? = null,
    private val onCameraSwapEnabled: ((Boolean) -> Unit)? = null,
    private val onCountdownComplete: () -> Unit
) {
    private var countdownScope = CoroutineScope(Dispatchers.Main)
    private var currentCountdownJob: Job? = null
    private var isCountdownActive = false
    private var currentCountdown = 0
    private var isCaptureQueued = false
    private var lastCancelTime = 0L
    private val handler = Handler(Looper.getMainLooper())
    
    // Prevent new captures for 1 second after cancel
    private val CAPTURE_LOCKOUT_MS = 1000 
    
    /**
     * Start a countdown with the specified duration
     * @param delaySeconds Number of seconds to count down
     * @param isPhotoMode Whether this is for photo mode (vs video mode)
     * @return true if countdown started, false if already running or locked out
     */
    fun startCountdown(delaySeconds: Int, isPhotoMode: Boolean): Boolean {
        CameraLogger.d(COUNTDOWN, "startCountdown() called with delay: $delaySeconds, isCountdownActive: $isCountdownActive", this)

        // Check if we're in a lockout period after cancellation
        val timeSinceLastCancel = System.currentTimeMillis() - lastCancelTime
        if (timeSinceLastCancel < CAPTURE_LOCKOUT_MS) {
            CameraLogger.d(COUNTDOWN, "Ignoring start request during lockout period", this)
            return false
        }

        // Reset any stale state
        if (isCountdownActive && !isCaptureQueued) {
            CameraLogger.d(COUNTDOWN, "Resetting stale countdown state", this)
            resetState()
        }

        // If there's already a valid countdown active, don't start another one
        if (isCountdownActive && isCaptureQueued) {
            CameraLogger.d(COUNTDOWN, "Countdown already active, ignoring request", this)
            return false
        }

        // If no delay, complete immediately
        if (delaySeconds <= 0) {
            onCountdownComplete()
            return true
        }

        // Set capture state
        isCaptureQueued = true
        isCountdownActive = true
        currentCountdown = delaySeconds
        
        // Disable camera swap during countdown
        onCameraSwapEnabled?.invoke(false)

        // Start the actual countdown
        beginCountdownSequence(delaySeconds)
        return true
    }
    
    /**
     * Execute the countdown sequence
     */
    private fun beginCountdownSequence(seconds: Int) {
        CameraLogger.d(COUNTDOWN, "Beginning countdown with $seconds seconds", this)
        
        // Cancel any existing countdown job
        currentCountdownJob?.cancel()
        
        // Create new countdown job
        currentCountdownJob = countdownScope.launch {
            try {
                for (i in seconds downTo 1) {
                    if (!isCountdownActive || !isCaptureQueued) {
                        CameraLogger.d(COUNTDOWN, "Countdown cancelled during loop", this@CountdownManager)
                        resetState()
                        break
                    }
                    
                    currentCountdown = i
                    updateCountdownDisplay(i)
                    
                    // Flash notification if needed and available
                    if (cameraState.shouldUseFlashForCountdown()) {
                        if (i > 1) {
                            flashNotification("normal", 0.2f)
                        } else if (i == 1) {
                            flashNotification("final", 0.6f)
                        }
                    }
                    
                    delay(1000)
                }
                
                // Final check before completing
                if (isCountdownActive && isCaptureQueued) {
                    updateCountdownDisplay(0)
                    CameraLogger.d(COUNTDOWN, "Countdown completed, triggering completion", this@CountdownManager)
                    onCountdownComplete()
                } else {
                    resetState()
                }
            } catch (e: CancellationException) {
                CameraLogger.d(COUNTDOWN, "Countdown job cancelled", this@CountdownManager)
                resetState()
            } catch (e: Exception) {
                CameraLogger.e(COUNTDOWN, "Error during countdown", e, this@CountdownManager)
                resetState()
            }
        }
    }
    
    /**
     * Cancel an active countdown
     */
    fun cancelCountdown() {
        CameraLogger.d(COUNTDOWN, "cancelCountdown() called", this)
        
        // Set cancel timestamp to prevent immediate recapture
        lastCancelTime = System.currentTimeMillis()

        // Cancel the countdown job
        currentCountdownJob?.cancel()
        currentCountdownJob = null

        // Remove all pending callbacks
        handler.removeCallbacksAndMessages(null)

        // Reset state
        resetState()
        
        // Cancel all pending operations in the coroutine scope
        countdownScope.cancel()
        countdownScope = CoroutineScope(Dispatchers.Main)
        
        // Re-enable camera swap
        onCameraSwapEnabled?.invoke(true)
    }
    
    /**
     * Reset countdown state
     */
    private fun resetState() {
        CameraLogger.d(COUNTDOWN, "Resetting state - was active: $isCountdownActive, was queued: $isCaptureQueued", this)
        isCountdownActive = false
        isCaptureQueued = false
        currentCountdown = 0
        updateCountdownDisplay(0)
    }
    
    /**
     * Update the UI with current countdown value
     */
    private fun updateCountdownDisplay(seconds: Int) {
        if (cameraState.isFrontCamera) {
            onCountdownUpdate?.invoke(seconds)
        } else {
            // Always clear the countdown when using back camera
            onCountdownUpdate?.invoke(0)
        }
    }
    
    /**
     * Flash notification for countdown
     */
    private fun flashNotification(pattern: String, brightness: Float) {
        // This is a placeholder - the actual flash control will be in the CameraManager
        // This just notifies when a flash should occur
        CameraLogger.d(COUNTDOWN, "Flash notification: $pattern (brightness: $brightness)", this)
    }
    
    /**
     * Check if countdown is currently active
     */
    fun isActive(): Boolean = isCountdownActive
    
    /**
     * Get current countdown value
     */
    fun getCurrentValue(): Int = currentCountdown
    
    /**
     * Clean up resources
     */
    fun shutdown() {
        CameraLogger.d(COUNTDOWN, "Shutting down", this)
        cancelCountdown()
        countdownScope.cancel()
    }
} 