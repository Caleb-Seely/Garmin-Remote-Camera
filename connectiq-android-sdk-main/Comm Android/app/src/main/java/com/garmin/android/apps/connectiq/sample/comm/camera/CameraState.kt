package com.garmin.android.apps.connectiq.sample.comm.camera

/**
 * Manages the state of the camera and its features
 */
class CameraState {
    var isFlashEnabled = false
        private set
    
    var isVideoMode = false
        private set
    
    var isFrontCamera = false
        private set

    var isRecording = false
        private set

    var recordingStartTime: Long = 0
        private set

    /**
     * Updates the camera facing state and returns whether flash should be available
     */
    fun updateCameraFacing(isFront: Boolean): Boolean {
        isFrontCamera = isFront
        // If switching to front camera, ensure flash is disabled
        if (isFront && isFlashEnabled) {
            isFlashEnabled = false
        }
        return !isFront // Flash is only available for back camera
    }

    /**
     * Toggles the flash state if allowed
     * @return true if flash is now enabled, false otherwise
     */
    fun toggleFlash(): Boolean {
        if (!isFrontCamera) {
            isFlashEnabled = !isFlashEnabled
        }
        return isFlashEnabled
    }

    /**
     * Toggles the video mode
     * @return true if now in video mode, false if in photo mode
     */
    fun toggleVideoMode(): Boolean {
        isVideoMode = !isVideoMode
        return isVideoMode
    }

    /**
     * Forces flash to be disabled
     */
    fun disableFlash() {
        isFlashEnabled = false
    }

    /**
     * Checks if flash should be used for countdown
     */
    fun shouldUseFlashForCountdown(): Boolean {
        return !isFrontCamera && isFlashEnabled
    }

    /**
     * Starts video recording
     */
    fun startRecording() {
        isRecording = true
        recordingStartTime = System.currentTimeMillis()
    }

    /**
     * Stops video recording
     */
    fun stopRecording() {
        isRecording = false
        recordingStartTime = 0
    }

    /**
     * Gets the current recording duration in seconds
     */
    fun getRecordingDuration(): Long {
        return if (isRecording) {
            (System.currentTimeMillis() - recordingStartTime) / 1000
        } else {
            0
        }
    }
} 