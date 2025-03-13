package com.garmin.android.apps.connectiq.sample.comm.camera

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import com.google.common.util.concurrent.ListenableFuture
import android.graphics.ImageFormat

//For the flash functionality
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.widget.TextView
import android.view.View

private val scope = CoroutineScope(Dispatchers.Main)

class MyCameraManager(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val viewFinder: PreviewView,
    private val onPhotoTaken: (String) -> Unit = {},
    private val onCountdownUpdate: ((Int) -> Unit)? = null,
    private val onCameraSwapEnabled: ((Boolean) -> Unit)? = null
) {
    companion object {
        private const val TAG = "MyCameraManager"
        private const val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"
    }

    private var imageCapture: ImageCapture? = null
    private var camera: Camera? = null
    private lateinit var cameraExecutor: ExecutorService
    private var isCameraInitialized = false
    private var isCameraActive = false
    private var currentCameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
    private var photoCount = 0
    private var isCountdownActive = false
    private var currentCountdown = 0
    private val cameraState = CameraState()

    init {
        cameraExecutor = Executors.newSingleThreadExecutor()
    }

    fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)

        cameraProviderFuture.addListener({
            try {
                val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()
                bindCamera(cameraProvider)
            } catch (exc: Exception) {
                Log.e(TAG, "Camera initialization failed", exc)
                isCameraActive = false
                Toast.makeText(context, "Failed to start camera: ${exc.message}", Toast.LENGTH_SHORT).show()
            }
        }, ContextCompat.getMainExecutor(context))
    }

    private fun bindCamera(cameraProvider: ProcessCameraProvider) {
        try {
            // Preview with high quality settings
            val preview = Preview.Builder()
                .setTargetRotation(viewFinder.display.rotation)
                .build()
                .also {
                    it.setSurfaceProvider(viewFinder.surfaceProvider)
                }

            // ImageCapture with maximum quality settings
            imageCapture = ImageCapture.Builder()
                .setTargetRotation(viewFinder.display.rotation)
                // Use maximum quality mode
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                // Set flash mode
                .setFlashMode(if (cameraState.isFlashEnabled) ImageCapture.FLASH_MODE_ON else ImageCapture.FLASH_MODE_OFF)
                // Use the highest available stable resolution
                .setTargetResolution(android.util.Size(8000, 6000))  // This will automatically fall back to highest available
                // Maximum JPEG quality
                .setJpegQuality(100)
                .build()

            try {
                // Unbind all use cases before rebinding
                cameraProvider.unbindAll()

                // Get camera with highest quality configuration
                val cameraSelector = CameraSelector.Builder()
                    .requireLensFacing(if (currentCameraSelector == CameraSelector.DEFAULT_BACK_CAMERA) 
                        CameraSelector.LENS_FACING_BACK else CameraSelector.LENS_FACING_FRONT)
                    .build()

                // Bind use cases to camera
                camera = cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    preview,
                    imageCapture
                )

                // Configure additional camera controls if available
                camera?.let { cam ->
                    // Initially disable torch
                    cam.cameraControl.enableTorch(false)
                    // Set capture mode to maximize quality
                    cam.cameraInfo.exposureState.let { exposureState ->
                        if (exposureState.isExposureCompensationSupported) {
                            // Set exposure compensation to 0 (neutral)
                            cam.cameraControl.setExposureCompensationIndex(0)
                        }
                    }
                }

                isCameraInitialized = true
                isCameraActive = true
                Log.d(TAG, "Camera initialized and active with highest quality settings")

                // Update countdown display after successful camera switch
                if (isCountdownActive) {
                    updateCountdownDisplay(currentCountdown)
                }
            } catch (exc: Exception) {
                handleCameraBindingError(exc, cameraProvider)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Use case configuration failed", e)
            Toast.makeText(context, "Camera configuration failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun handleCameraBindingError(exc: Exception, cameraProvider: ProcessCameraProvider) {
        Log.e(TAG, "Use case binding failed", exc)
        isCameraActive = false
        Toast.makeText(context, "Camera binding failed. Retrying...", Toast.LENGTH_SHORT).show()

        try {
            cameraProvider.unbindAll()
            camera = cameraProvider.bindToLifecycle(
                lifecycleOwner,
                currentCameraSelector,
                Preview.Builder().build(),
                imageCapture
            )
            isCameraActive = true
            Log.d(TAG, "Camera recovered after binding failure")
        } catch (e: Exception) {
            Log.e(TAG, "Camera recovery failed", e)
            Toast.makeText(context, "Camera recovery failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    fun cancelPhoto() {
        isCountdownActive = false
        currentCountdown = 0
        updateCountdownDisplay(0)
        // Re-enable camera swap button
        onCameraSwapEnabled?.invoke(true)
        // Turn off flash if it was on
        try {
            camera?.cameraControl?.enableTorch(false)
            cameraState.disableFlash()
        } catch (e: Exception) {
            Log.e(TAG, "Error turning off flash during cancel", e)
        }
    }

    fun takePhoto(delaySeconds: Int = 0) {
        if (!isCameraInitialized || !isCameraActive) {
            Log.e(TAG, "Camera not initialized or not active")
            Toast.makeText(context, "Camera not ready. Restarting...", Toast.LENGTH_SHORT).show()
            startCamera()
            return
        }

        val imageCapture = imageCapture ?: run {
            Log.e(TAG, "ImageCapture is null")
            Toast.makeText(context, "Camera not configured properly", Toast.LENGTH_SHORT).show()
            return
        }

        // Cancel any existing countdown before starting a new one
        if (isCountdownActive) {
            cancelPhoto()
        }

        if (delaySeconds > 0) {
            isCountdownActive = true
            currentCountdown = delaySeconds
            // Disable camera swap button at start of countdown
            onCameraSwapEnabled?.invoke(false)
            
            scope.launch {
                var remainingSeconds = delaySeconds
                while (remainingSeconds > 0 && isCountdownActive) {
                    // Update countdown display based on current camera
                    updateCountdownDisplay(remainingSeconds)
                    
                    // Only flash if enabled and using back camera
                    if (cameraState.shouldUseFlashForCountdown()) {
                        if (remainingSeconds > 1) {
                            // Regular countdown flash
                            flashBriefly("normal", 0.2f)
                        } else if (remainingSeconds == 1) {
                            // Special signal when about to capture
                            flashBriefly("final", 0.6f)
                        }
                    }
                    remainingSeconds--
                    delay(1000) // Wait 1 second between countdown steps
                }
                
                // Only take photo if countdown wasn't cancelled
                if (isCountdownActive) {
                    // Clear countdown display
                    updateCountdownDisplay(0)
                    capturePhoto(imageCapture)
                }
                isCountdownActive = false
                currentCountdown = 0
                // Re-enable camera swap button after countdown/photo
                onCameraSwapEnabled?.invoke(true)
            }
        } else {
            capturePhoto(imageCapture)
        }
    }

    private fun updateCountdownDisplay(seconds: Int) {
        if (cameraState.isFrontCamera) {
            onCountdownUpdate?.invoke(seconds)
        } else {
            // Always clear the countdown when using back camera
            onCountdownUpdate?.invoke(0)
        }
    }

    private fun capturePhoto(imageCapture: ImageCapture) {
        try {
            // Create time stamped name and MediaStore entry
            val name = SimpleDateFormat(FILENAME_FORMAT, Locale.US)
                .format(System.currentTimeMillis())
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                put(MediaStore.Images.Media.WIDTH, 8000)  // Target max resolution
                put(MediaStore.Images.Media.HEIGHT, 6000) // Will automatically adjust to available
                if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
                    put(MediaStore.Images.Media.RELATIVE_PATH, "DCIM/Camera")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
            }

            // Create output options object which contains file + metadata
            val outputOptions = ImageCapture.OutputFileOptions.Builder(
                context.contentResolver,
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                contentValues
            ).apply {
                setMetadata(
                    ImageCapture.Metadata().apply {
                        // Enable location if available
                        isReversedHorizontal = currentCameraSelector == CameraSelector.DEFAULT_FRONT_CAMERA
                    }
                )
            }.build()

            // Take the picture with maximum quality
            imageCapture.takePicture(
                outputOptions,
                ContextCompat.getMainExecutor(context),
                object : ImageCapture.OnImageSavedCallback {
                    override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                        photoCount++
                        val msg = "High quality photo captured successfully"
                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                        Log.d(TAG, "Photo capture succeeded: ${output.savedUri}")
                        
                        // Update the IS_PENDING flag after successful save
                        output.savedUri?.let { uri ->
                            contentValues.clear()
                            contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                            context.contentResolver.update(uri, contentValues, null, null)
                        }
                        
                        onPhotoTaken("Photos Captured: $photoCount")
                    }

                    override fun onError(exc: ImageCaptureException) {
                        Log.e(TAG, "Photo capture failed: ${exc.message}", exc)
                        Toast.makeText(context, "Failed to capture photo: ${exc.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error preparing photo capture", e)
            Toast.makeText(context, "Error preparing photo: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    fun toggleFlash() {
        // Only update flash mode, don't trigger the flash
        val isEnabled = cameraState.toggleFlash()
        imageCapture?.flashMode = if (isEnabled) ImageCapture.FLASH_MODE_ON else ImageCapture.FLASH_MODE_OFF
    }

    fun flipCamera() {
        currentCameraSelector = if (currentCameraSelector == CameraSelector.DEFAULT_BACK_CAMERA) {
            CameraSelector.DEFAULT_FRONT_CAMERA
        } else {
            CameraSelector.DEFAULT_BACK_CAMERA
        }

        // Update state and handle flash availability
        cameraState.updateCameraFacing(currentCameraSelector == CameraSelector.DEFAULT_FRONT_CAMERA)

        // Update countdown display based on new camera state
        if (isCountdownActive) {
            updateCountdownDisplay(currentCountdown)
        }

        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            try {
                val cameraProvider = cameraProviderFuture.get()
                bindCamera(cameraProvider)
            } catch (exc: Exception) {
                handleCameraFlipError(exc, cameraProviderFuture)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    private fun handleCameraFlipError(exc: Exception, cameraProviderFuture: ListenableFuture<ProcessCameraProvider>) {
        Log.e(TAG, "Failed to flip camera", exc)
        Toast.makeText(context, "Failed to switch camera: ${exc.message}", Toast.LENGTH_SHORT).show()
        // Try to recover by reverting to previous camera
        currentCameraSelector = if (currentCameraSelector == CameraSelector.DEFAULT_FRONT_CAMERA) {
            CameraSelector.DEFAULT_BACK_CAMERA
        } else {
            CameraSelector.DEFAULT_FRONT_CAMERA
        }
        cameraState.updateCameraFacing(currentCameraSelector == CameraSelector.DEFAULT_FRONT_CAMERA)
        try {
            bindCamera(cameraProviderFuture.get())
        } catch (e: Exception) {
            Log.e(TAG, "Camera recovery failed", e)
        }
    }

    private fun flashBriefly(pattern: String, brightness: Float) {
        when (pattern) {
            "normal" -> {
                if (camera == null || imageCapture == null) return
                camera?.cameraControl?.enableTorch(true)
                scope.launch {
                    delay(50)
                    camera?.cameraControl?.enableTorch(false)
                }
            }
            "final" -> {
                if (camera == null || imageCapture == null) return
                scope.launch {
                    // No double tap bc we handle flash differently for the photo
//                    repeat(2) {
//                        camera?.cameraControl?.enableTorch(true)
//                        delay(50)
//                        camera?.cameraControl?.enableTorch(false)
//                        delay(100)
//                    }
                }
            }
        }
    }

    fun shutdown() {
        isCountdownActive = false
        currentCountdown = 0
        updateCountdownDisplay(0)
        // Make sure camera swap button is enabled when shutting down
        onCameraSwapEnabled?.invoke(true)
        cameraExecutor.shutdown()
        try {
            if (!cameraExecutor.awaitTermination(1000, TimeUnit.MILLISECONDS)) {
                cameraExecutor.shutdownNow()
            }
        } catch (e: InterruptedException) {
            cameraExecutor.shutdownNow()
        }
        isCameraActive = false
    }

    fun isActive() = isCameraActive

    fun isFrontCamera() = cameraState.isFrontCamera
}