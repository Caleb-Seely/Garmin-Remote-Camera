package com.garmin.android.apps.connectiq.sample.comm.camera

/**
 * Encapsulates the state of the camera, including mode, recording status, and flash settings.
 * Using a dedicated state class makes the state management more maintainable and testable.
 */
class CameraState {
    // Camera modes
    var isVideoMode = false
        private set
    
    var isRecording = false
        private set
    
    var isFrontCamera = false
        private set
    
    var isFlashEnabled = false
        private set
    
    private var recordingStartTime: Long = 0
    
    /**
     * Toggle between photo and video mode
     * @return The new video mode state
     */
    fun toggleVideoMode(): Boolean {
        isVideoMode = !isVideoMode
        return isVideoMode
    }
    
    /**
     * Mark recording as started and record the start time
     */
    fun startRecording() {
        isRecording = true
        recordingStartTime = System.currentTimeMillis()
    }
    
    /**
     * Mark recording as stopped
     */
    fun stopRecording() {
        isRecording = false
    }
    
    /**
     * Update the camera facing mode
     * @param isFront True if the front camera is active
     */
    fun updateCameraFacing(isFront: Boolean) {
        val wasFront = isFrontCamera
        isFrontCamera = isFront
        
        // Log the change to help with debugging
        if (wasFront != isFront) {
            println("Camera facing changed: " + (if (isFront) "FRONT" else "BACK"))
        }
        
        // Front camera can't use flash, so disable it if front camera is active
        if (isFront && isFlashEnabled) {
            isFlashEnabled = false
        }
    }
    
    /**
     * Toggle flash on/off
     * @return The new flash state
     */
    fun toggleFlash(): Boolean {
        // Only allow flash for back camera
        if (!isFrontCamera) {
            isFlashEnabled = !isFlashEnabled
        }
        return isFlashEnabled
    }
    
    /**
     * Disable flash
     */
    fun disableFlash() {
        isFlashEnabled = false
    }
    
    /**
     * Calculate recording duration in seconds
     * @return Duration in seconds if recording, 0 otherwise
     */
    fun getRecordingDuration(): Long {
        return if (isRecording) {
            (System.currentTimeMillis() - recordingStartTime) / 1000
        } else {
            0
        }
    }
    
    /**
     * Check if flash should be used for countdown based on camera state
     */
    fun shouldUseFlashForCountdown(): Boolean {
        return isFlashEnabled && !isFrontCamera
    }
} 