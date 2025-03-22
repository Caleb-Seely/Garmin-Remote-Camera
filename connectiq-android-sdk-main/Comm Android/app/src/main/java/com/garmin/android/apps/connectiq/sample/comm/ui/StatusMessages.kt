package com.garmin.android.apps.connectiq.sample.comm.ui

/**
 * Centralized container for all status messages used in the application.
 * This improves consistency and makes it easier to update or localize messages.
 */
object StatusMessages {
    // Camera modes
    const val CAMERA_READY = "Camera ready"
    const val VIDEO_READY = "Video ready"
    const val PHOTO_MODE = "Photo mode"
    const val VIDEO_MODE = "Video mode"
    
    // Flash states
    const val FLASH_ENABLED = "Flash enabled"
    const val FLASH_DISABLED = "Flash disabled"
    
    // Photo states
    const val TAKING_PHOTO = "Taking photo..."
    const val PHOTO_SAVED = "Photo saved!"
    const val PHOTO_SUCCESS = "Captured!"   //Watch Message

    // Video states
    const val RECORDING_STARTED = "Recording started"   //Watch Message
    const val RECORDING_STOPPED = "Recording stopped"   //Watch Message
    const val RECORDING_CANCELLED = "Recording cancelled"
    const val VIDEO_SAVED = "Video saved!"
    
    // Error states
    const val ERROR_CAMERA_INIT = "Camera initialization failed"
    const val ERROR_TAKING_PHOTO = "Failed to take photo"
    const val ERROR_STARTING_VIDEO = "Failed to start video"
    const val ERROR_CAMERA_PERMISSION = "Camera permission denied"
    
    // ConnectIQ states
    const val CONNECTION_LOST = "Device connection lost"
    const val CONNECTION_RESTORED = "Device connection restored"
    const val MESSAGE_SENT = "Message sent to device"
    const val ERROR_SENDING_MESSAGE = "Failed to send message"


} 