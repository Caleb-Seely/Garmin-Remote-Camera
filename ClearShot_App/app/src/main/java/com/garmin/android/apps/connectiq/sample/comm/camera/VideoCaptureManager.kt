package com.garmin.android.apps.connectiq.sample.comm.camera

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.view.Surface
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
import android.net.Uri
import androidx.camera.video.Recorder
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector

/**
 * Manages video recording operations including starting, stopping,
 * and saving recordings to storage.
 */
class VideoCaptureManager(
    private val context: Context,
    private val cameraState: CameraState,
    private val onRecordingStatusUpdate: ((String) -> Unit)? = null,
    private val metadataManager: MetadataManager? = MetadataManager(context)
) {
    companion object {
        private const val FILENAME_FORMAT = "yyyyMMdd_HHmmSSsss"
    }
    
    private var currentRecording: Recording? = null
    private var recordingStatusRunnable: Runnable? = null
    private val handler = Handler(Looper.getMainLooper())
    
    // Store initial recording metadata to ensure consistency between start and finish
    private var initialOrientation: Int = 0
    private var initialLocation: android.location.Location? = null
    
    /**
     * Create a configured video capture use case with proper rotation and quality settings
     */
    fun createConfiguredVideoCapture(): VideoCapture<Recorder>? {
        try {
            // Capture device orientation and store it before recording
            initialOrientation = metadataManager?.getCurrentDeviceOrientation() ?: 0
            CameraLogger.d(VIDEO_CAPTURE, "Setting initial orientation for video capture: $initialOrientation", this)
            
            // Capture location data before recording
            initialLocation = if (metadataManager?.hasLocationPermission() == true) {
                metadataManager.getLastKnownLocation()?.also { location ->
                    CameraLogger.d(VIDEO_CAPTURE, 
                        "Setting initial location for video capture: Lat ${location.latitude}, Lon ${location.longitude}", 
                        this)
                }
            } else {
                CameraLogger.d(VIDEO_CAPTURE, "Location permission not granted, no location metadata", this)
                null
            }
            
            // Convert orientation to Surface rotation constant
            val surfaceRotation = getRotationFromOrientation(initialOrientation)
            CameraLogger.d(VIDEO_CAPTURE, "Using rotation value: $surfaceRotation for orientation: $initialOrientation", this)
            
            // Create quality selector for highest quality video
            val qualitySelector = QualitySelector.from(Quality.HIGHEST)
            
            // Create recorder with quality selection
            val recorder = Recorder.Builder()
                .setQualitySelector(qualitySelector)
                .build()
            
            // Create video capture with the recorder
            val videoCapture = VideoCapture.withOutput(recorder)
            
            // Set the target rotation to ensure proper video orientation
            try {
                // The CameraX setTargetRotation method can be accessed via reflection 
                val setTargetRotationMethod = videoCapture.javaClass.getDeclaredMethod("setTargetRotation", Int::class.java)
                setTargetRotationMethod.isAccessible = true
                setTargetRotationMethod.invoke(videoCapture, surfaceRotation)
                CameraLogger.d(VIDEO_CAPTURE, "Successfully set target rotation to: $surfaceRotation", this)
            } catch (e: Exception) {
                CameraLogger.e(VIDEO_CAPTURE, "Failed to set target rotation via reflection", e, this)
                
                // Try alternative methods to set rotation if reflection fails
                try {
                    // Some versions of CameraX might have a different method name or structure
                    val cameraInfo = videoCapture.javaClass.getDeclaredField("cameraInfo")
                    cameraInfo.isAccessible = true
                    val cameraInfoObj = cameraInfo.get(videoCapture)
                    
                    if (cameraInfoObj != null) {
                        val setTargetRotationMethod = cameraInfoObj.javaClass.getDeclaredMethod("setTargetRotation", Int::class.java)
                        setTargetRotationMethod.isAccessible = true
                        setTargetRotationMethod.invoke(cameraInfoObj, surfaceRotation)
                        CameraLogger.d(VIDEO_CAPTURE, "Set target rotation via camera info", this)
                    }
                } catch (innerEx: Exception) {
                    CameraLogger.e(VIDEO_CAPTURE, "All attempts to set target rotation failed", innerEx, this)
                }
            }
            
            CameraLogger.d(VIDEO_CAPTURE, "Created video capture with orientation: $initialOrientation", this)
            return videoCapture
        } catch (e: Exception) {
            CameraLogger.e(VIDEO_CAPTURE, "Error creating configured video capture", e, this)
            
            // Fallback to a simple configuration 
            return try {
                val qualitySelector = QualitySelector.from(Quality.HIGHEST)
                val recorder = Recorder.Builder()
                    .setQualitySelector(qualitySelector)
                    .build()
                VideoCapture.withOutput(recorder)
            } catch (fallbackEx: Exception) {
                CameraLogger.e(VIDEO_CAPTURE, "Fallback video capture creation failed", fallbackEx, this)
                null
            }
        }
    }
    
    /**
     * Convert orientation degrees to Surface rotation constant
     */
    private fun getRotationFromOrientation(orientation: Int): Int {
        return when (orientation) {
            0 -> Surface.ROTATION_0
            90 -> Surface.ROTATION_90
            180 -> Surface.ROTATION_180
            270 -> Surface.ROTATION_270
            else -> Surface.ROTATION_0
        }
    }
    
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
            
            // Capture device orientation at the start of recording
            initialOrientation = metadataManager?.getCurrentDeviceOrientation() ?: 0
            CameraLogger.d(VIDEO_CAPTURE, "Capturing initial orientation: $initialOrientation", this)
            
            // Capture location data at the start if available
            initialLocation = if (metadataManager?.hasLocationPermission() == true) {
                metadataManager.getLastKnownLocation()?.also { location ->
                    CameraLogger.d(VIDEO_CAPTURE, 
                        "Capturing initial location: Lat ${location.latitude}, Lon ${location.longitude}", 
                        this)
                }
            } else {
                CameraLogger.d(VIDEO_CAPTURE, "Location permission not granted, no location metadata", this)
                null
            }
            
            val name = "CS_" + SimpleDateFormat(FILENAME_FORMAT, Locale.US)
                .format(System.currentTimeMillis())
            val contentValues = ContentValues().apply {
                // Only add essential fields for initial creation
                put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
                
                // Set appropriate directory path
                if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
                    put(MediaStore.Video.Media.RELATIVE_PATH, "DCIM/Camera")
                    put(MediaStore.Video.Media.IS_PENDING, 1)
                }
                
                // Store minimal metadata initially
                put(MediaStore.Video.Media.TITLE, "Video $name")
                
                // Add orientation directly at creation time to maximize chances of it being applied
                put(MediaStore.Video.Media.ORIENTATION, initialOrientation)
                
                // Add location if available
                initialLocation?.let { location ->
                    put(MediaStore.Video.VideoColumns.LATITUDE, location.latitude)
                    put(MediaStore.Video.VideoColumns.LONGITUDE, location.longitude)
                }
            }

            CameraLogger.d(VIDEO_CAPTURE, "Creating video with metadata: $contentValues", this)

            val mediaStoreOutputOptions = MediaStoreOutputOptions
                .Builder(context.contentResolver, MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
                .setContentValues(contentValues)
                .build()

            try {
                @Suppress("UNCHECKED_CAST")
                currentRecording = (videoCaptureWithOutput as? VideoCapture<Recorder>)?.output
                    ?.prepareRecording(context, mediaStoreOutputOptions)
                    ?.withAudioEnabled()  // Enable audio recording
                    ?.start(ContextCompat.getMainExecutor(context)) { recordEvent ->
                        handleRecordEvent(recordEvent)
                    }
                
                if (currentRecording == null) {
                    CameraLogger.e(VIDEO_CAPTURE, "Failed to create recording - output preparation failed", null, this)
                    cameraState.stopRecording()
                    Toast.makeText(context, "Unable to create media store entry. Check storage permissions.", Toast.LENGTH_LONG).show()
                    return false
                }
                
                return true
            } catch (e: ClassCastException) {
                CameraLogger.e(VIDEO_CAPTURE, "Error casting VideoCapture to correct type", e, this)
                cameraState.stopRecording()
                Toast.makeText(context, "Error starting video recording: Type mismatch", Toast.LENGTH_SHORT).show()
                return false
            } catch (e: SecurityException) {
                CameraLogger.e(VIDEO_CAPTURE, "Security exception when creating media store entry", e, this)
                cameraState.stopRecording()
                Toast.makeText(context, "Permission denied: Unable to create media store entry", Toast.LENGTH_LONG).show()
                return false
            } catch (e: IllegalArgumentException) {
                CameraLogger.e(VIDEO_CAPTURE, "Invalid argument when creating media store entry", e, this)
                cameraState.stopRecording()
                Toast.makeText(context, "Invalid configuration: Unable to create media store entry", Toast.LENGTH_LONG).show()
                return false
            }
        } catch (e: Exception) {
            CameraLogger.e(VIDEO_CAPTURE, "Error starting video recording", e, this)
            Toast.makeText(context, "Error starting video recording: ${e.message}", Toast.LENGTH_SHORT).show()
            // Reset recording state if start failed
            cameraState.stopRecording()
            // Reset the stored metadata
            initialOrientation = 0
            initialLocation = null
            return false
        }
    }
    
    /**
     * Apply final metadata to the video file
     */
    private fun applyFinalMetadata(uri: Uri) {
        if (uri == Uri.EMPTY) {
            CameraLogger.e(VIDEO_CAPTURE, "Cannot apply metadata to empty URI", null, this)
            return
        }
        
        try {
            CameraLogger.d(VIDEO_CAPTURE, "Applying final metadata to video: $uri", this)
            
            val updateValues = ContentValues().apply {
                // Add orientation using the standard MediaStore constant
                put(MediaStore.Video.Media.ORIENTATION, initialOrientation)
                
                // Add location if available - use standard constants
                initialLocation?.let { location ->
                    put(MediaStore.Video.VideoColumns.LATITUDE, location.latitude)
                    put(MediaStore.Video.VideoColumns.LONGITUDE, location.longitude)
                }
                
                // Add date taken metadata
                val currentTime = System.currentTimeMillis()
                put(MediaStore.Video.Media.DATE_TAKEN, currentTime)
                
                // Mark the file as no longer pending on Android 10+
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Video.Media.IS_PENDING, 0)
                }
            }
            
            val rowsUpdated = context.contentResolver.update(uri, updateValues, null, null)
            CameraLogger.d(VIDEO_CAPTURE, "Updated MediaStore metadata: $rowsUpdated rows affected", this)
            
            // Trigger media scan to ensure the system indexes the file with our metadata
            android.media.MediaScannerConnection.scanFile(
                context,
                arrayOf(uri.toString()),
                arrayOf("video/mp4")
            ) { path, scanUri ->
                CameraLogger.d(VIDEO_CAPTURE, "Media scan completed for: $path", this)
            }
        } catch (e: Exception) {
            CameraLogger.e(VIDEO_CAPTURE, "Error applying final metadata", e, this)
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
                    try {
                        // Get the Uri of the saved video
                        val uri = recordEvent.outputResults.outputUri
                        
                        // First ensure the file exists and is valid
                        if (uri != null && isUriValid(uri)) {
                            // Apply final metadata
                            applyFinalMetadata(uri)
                            
                            // Finally, inform the user
                            onRecordingStatusUpdate?.invoke(StatusMessages.VIDEO_SAVED)
                        } else {
                            CameraLogger.e(VIDEO_CAPTURE, "Invalid or null video URI", null, this)
                        }
                    } catch (e: Exception) {
                        CameraLogger.e(VIDEO_CAPTURE, "Error processing recording finalization", e, this)
                    }
                }
                stopRecordingStatusUpdates()
                currentRecording = null
                cameraState.stopRecording()
                
                // Reset the stored metadata after recording is complete
                initialOrientation = 0
                initialLocation = null
            }
        }
    }
    
    /**
     * Validate that a URI is valid and exists
     */
    private fun isUriValid(uri: Uri): Boolean {
        return try {
            // Try to get file descriptor to check if URI is valid
            context.contentResolver.openFileDescriptor(uri, "r")?.close()
            true
        } catch (e: Exception) {
            CameraLogger.e(VIDEO_CAPTURE, "URI validation failed: $uri", e, this)
            false
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
            
            // Clear any stored metadata
            initialOrientation = 0
            initialLocation = null
        } catch (e: Exception) {
            CameraLogger.e(VIDEO_CAPTURE, "Error during shutdown", e, this)
        }
    }
} 