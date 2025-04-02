package com.garmin.android.apps.clearshot.phone.camera

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.core.content.ContextCompat
import com.garmin.android.apps.clearshot.phone.camera.CameraLogger.PHOTO_CAPTURE
import java.text.SimpleDateFormat
import java.util.Locale
import com.garmin.android.apps.clearshot.phone.ui.StatusMessages
import com.garmin.android.apps.clearshot.phone.ui.StatusMessages.PHOTO_SUCCESS
import com.garmin.android.apps.clearshot.phone.camera.CameraConfigManager
import android.view.Surface
/**
 * Manages photo capture operations including saving to storage and handling errors.
 */
class PhotoCaptureManager(
    private val context: Context,
    private val onPhotoTaken: (String) -> Unit = {},
    private val metadataManager: MetadataManager = MetadataManager(context)
) {
    companion object {
        private const val FILENAME_FORMAT = "yyyyMMdd_HHmmSSsss"
    }
    
    /**
     * Capture a photo using the provided ImageCapture use case
     * @param imageCapture The camera's image capture use case
     * @return true if capture was initiated, false if there was an issue
     */
    fun capturePhoto(imageCapture: ImageCapture?): Boolean {
        CameraLogger.d(PHOTO_CAPTURE, "capturePhoto() called", this)
        
        val capture = imageCapture ?: run {
            CameraLogger.e(PHOTO_CAPTURE, "Photo capture failed - imageCapture is null", null, this)
            return false
        }

        // Get the current device orientation from the metadata manager
        val deviceOrientation = metadataManager.getCurrentDeviceOrientation()
        
        // Update the image capture's target rotation based on the device orientation
        // This ensures the capture respects the physical orientation regardless of screen rotation lock
        when (deviceOrientation) {
            0 -> capture.targetRotation = Surface.ROTATION_0
            90 -> capture.targetRotation = Surface.ROTATION_270
            180 -> capture.targetRotation = Surface.ROTATION_180
            270 -> capture.targetRotation = Surface.ROTATION_90
        }
        
        CameraLogger.d(PHOTO_CAPTURE, "Setting capture rotation to match device orientation: $deviceOrientation", this)

        // Create output options object which contains file + metadata
        val name = "CS_" + SimpleDateFormat(FILENAME_FORMAT, Locale.US)
            .format(System.currentTimeMillis())
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")

            // Add title for better display in gallery apps
            put(MediaStore.Images.Media.TITLE, "Photo $name")
            
            // Add description for additional context
            put(MediaStore.Images.Media.DESCRIPTION, "Captured with ClearShot app")

            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "DCIM/Camera")
            }

            // Add basic metadata
            metadataManager.addMetadataToContentValues(this)
        }

        CameraLogger.d(PHOTO_CAPTURE, "Saving photo with metadata: $contentValues", this)

        // Create output options object which contains file + metadata
        val outputOptions = ImageCapture.OutputFileOptions
            .Builder(context.contentResolver,
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                contentValues)
            .setMetadata(metadataManager.createImageMetadata())
            .build()

        // Set up image capture listener, which is triggered after photo has been taken
        capture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onError(exc: ImageCaptureException) {
                    CameraLogger.e(PHOTO_CAPTURE, "Capture failed", exc, this@PhotoCaptureManager)
                    val errorMessage = "Photo capture failed: ${exc.message}"
                    Toast.makeText(context, errorMessage, Toast.LENGTH_SHORT).show()
                    onPhotoTaken(errorMessage)
                }

                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    val successMessage = PHOTO_SUCCESS
                    CameraLogger.d(PHOTO_CAPTURE, "Photo saved successfully with metadata", this)
                    onPhotoTaken(successMessage)
                }
            }
        )
        
        return true
    }
} 