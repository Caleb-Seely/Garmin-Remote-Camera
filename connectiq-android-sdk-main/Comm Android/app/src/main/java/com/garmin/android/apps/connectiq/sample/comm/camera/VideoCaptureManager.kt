package com.garmin.android.apps.connectiq.sample.comm.camera

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.widget.Toast
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.core.content.ContextCompat
import com.garmin.android.apps.connectiq.sample.comm.camera.CameraLogger.VIDEO_CAPTURE
import com.garmin.android.apps.connectiq.sample.comm.ui.StatusMessages
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Manages video recording operations including starting, stopping,
 * and saving recordings to storage.
 */
class VideoCaptureManager(
    private val context: Context,
    private val cameraState: CameraState,
    private val onRecordingStatusUpdate: ((String) -> Unit)? = null
) {
    companion object {
        private const val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"
    }
    
    private var currentRecording: Recording? = null
    private var recordingStatusRunnable: Runnable? = null
    private val handler = Handler(Looper.getMainLooper())
    
    /**
     * Start video recording with the provided video capture use case
     * @param videoCapture The camera's video capture use case
     * @return true if recording started successfully, false otherwise
     */
    fun startRecording(videoCapture: VideoCapture<*>?): Boolean {
        CameraLogger.d(VIDEO_CAPTURE, "startRecording() called", this)
        
        if (videoCapture == null) {
            CameraLogger.e(VIDEO_CAPTURE, "Video recording failed - videoCapture is null", null, this)
            return false
        }
        
        val videoCaptureWithOutput = videoCapture as? VideoCapture<*> ?: run {
            CameraLogger.e(VIDEO_CAPTURE, "VideoCapture cannot be used for recording", null, this)
            return false
        }

        try {
            CameraLogger.d(VIDEO_CAPTURE, "Starting recording", this)
            cameraState.startRecording()
            
            val name = SimpleDateFormat(FILENAME_FORMAT, Locale.US)
                .format(System.currentTimeMillis())
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, "VID_$name")
                put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
                if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
                    put(MediaStore.Video.Media.RELATIVE_PATH, "DCIM/Camera")
                    put(MediaStore.Video.Media.IS_PENDING, 1)
                }
            }

            val mediaStoreOutputOptions = MediaStoreOutputOptions
                .Builder(context.contentResolver, MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
                .setContentValues(contentValues)
                .build()

            @Suppress("UNCHECKED_CAST")
            currentRecording = (videoCaptureWithOutput as VideoCapture<androidx.camera.video.Recorder>).output
                .prepareRecording(context, mediaStoreOutputOptions)
                .withAudioEnabled()  // Enable audio recording
                .start(ContextCompat.getMainExecutor(context)) { recordEvent ->
                    handleRecordEvent(recordEvent)
                }
                
            return true
        } catch (e: Exception) {
            CameraLogger.e(VIDEO_CAPTURE, "Error starting video recording", e, this)
            Toast.makeText(context, "Error starting video recording: ${e.message}", Toast.LENGTH_SHORT).show()
            // Reset recording state if start failed
            cameraState.stopRecording()
            return false
        }
    }
    
    /**
     * Handle video recording events
     */
    private fun handleRecordEvent(recordEvent: VideoRecordEvent) {
        when(recordEvent) {
            is VideoRecordEvent.Start -> {
                CameraLogger.d(VIDEO_CAPTURE, "Received VideoRecordEvent.Start", this)
                startRecordingStatusUpdates()
                onRecordingStatusUpdate?.invoke(StatusMessages.RECORDING_STARTED)
            }
            is VideoRecordEvent.Finalize -> {
                CameraLogger.d(VIDEO_CAPTURE, "Received VideoRecordEvent.Finalize", this)
                if (recordEvent.hasError()) {
                    CameraLogger.e(VIDEO_CAPTURE, "Video capture failed: ${recordEvent.cause}", recordEvent.cause, this)
                    Toast.makeText(context, "Failed to record video: ${recordEvent.cause?.message}", Toast.LENGTH_SHORT).show()
                } else {
                    onRecordingStatusUpdate?.invoke(StatusMessages.VIDEO_SAVED)
                }
                stopRecordingStatusUpdates()
                currentRecording = null
                cameraState.stopRecording()
            }
        }
    }
    
    /**
     * Stop current video recording
     * @return true if recording was stopped, false if no active recording
     */
    fun stopRecording(): Boolean {
        CameraLogger.d(VIDEO_CAPTURE, "stopRecording() called", this)
        
        try {
            if (currentRecording == null) {
                CameraLogger.d(VIDEO_CAPTURE, "No active recording to stop", this)
                return false
            }
            
            currentRecording?.stop()
            cameraState.stopRecording()
            stopRecordingStatusUpdates()
            onRecordingStatusUpdate?.invoke(StatusMessages.RECORDING_STOPPED)
            return true
        } catch (e: Exception) {
            CameraLogger.e(VIDEO_CAPTURE, "Error stopping video recording", e, this)
            Toast.makeText(context, "Error stopping video recording: ${e.message}", Toast.LENGTH_SHORT).show()
            return false
        }
    }
    
    /**
     * Start UI updates for recording duration
     */
    private fun startRecordingStatusUpdates() {
        recordingStatusRunnable = object : Runnable {
            override fun run() {
                if (cameraState.isRecording) {
                    val duration = cameraState.getRecordingDuration()
                    onRecordingStatusUpdate?.invoke("Recording... ${duration} sec")
                    handler.postDelayed(this, 1000)
                }
            }
        }
        handler.post(recordingStatusRunnable!!)
    }
    
    /**
     * Stop UI updates for recording duration
     */
    private fun stopRecordingStatusUpdates() {
        recordingStatusRunnable?.let { handler.removeCallbacks(it) }
        recordingStatusRunnable = null
    }
    
    /**
     * Check if a recording is currently active
     */
    fun isRecording(): Boolean = currentRecording != null && cameraState.isRecording
    
    /**
     * Clean up resources
     */
    fun shutdown() {
        CameraLogger.d(VIDEO_CAPTURE, "Shutting down", this)
        try {
            if (isRecording()) {
                stopRecording()
            }
            stopRecordingStatusUpdates()
            handler.removeCallbacksAndMessages(null)
            currentRecording = null
        } catch (e: Exception) {
            CameraLogger.e(VIDEO_CAPTURE, "Error during shutdown", e, this)
        }
    }
} 