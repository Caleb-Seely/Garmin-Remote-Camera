package com.garmin.android.apps.connectiq.sample.comm.camera

import android.util.Log

/**
 * Utility class for consistent logging across camera components.
 * Centralizes log tag management and provides convenience methods for different log levels.
 */
object CameraLogger {
    private const val BASE_TAG = "CIQCamera"
    
    // Log tags for specific components
    const val CAMERA_MANAGER = "$BASE_TAG-Manager"
    const val CAMERA_INITIALIZER = "$BASE_TAG-Init"
    const val PHOTO_CAPTURE = "$BASE_TAG-Photo"
    const val VIDEO_CAPTURE = "$BASE_TAG-Video"
    const val CAMERA_CONFIG = "$BASE_TAG-Config"
    const val COUNTDOWN = "$BASE_TAG-Countdown"
    const val METADATA_MANAGER = "$BASE_TAG-Metadata"
    
    fun d(tag: String, message: String, instance: Any? = null) {
        val instanceId = instance?.let { " [${System.identityHashCode(it)}]" } ?: ""
        Log.d(tag, "$message$instanceId")
    }
    
    fun e(tag: String, message: String, throwable: Throwable? = null, instance: Any? = null) {
        val instanceId = instance?.let { " [${System.identityHashCode(it)}]" } ?: ""
        if (throwable != null) {
            Log.e(tag, "$message$instanceId", throwable)
        } else {
            Log.e(tag, "$message$instanceId")
        }
    }
    
    fun i(tag: String, message: String, instance: Any? = null) {
        val instanceId = instance?.let { " [${System.identityHashCode(it)}]" } ?: ""
        Log.i(tag, "$message$instanceId")
    }
    
    fun w(tag: String, message: String, throwable: Throwable? = null, instance: Any? = null) {
        val instanceId = instance?.let { " [${System.identityHashCode(it)}]" } ?: ""
        if (throwable != null) {
            Log.w(tag, "$message$instanceId", throwable)
        } else {
            Log.w(tag, "$message$instanceId")
        }
    }
} 