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

class MyCameraManager(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val viewFinder: PreviewView,
    private val onPhotoTaken: (String) -> Unit = {}
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
    private var isFlashEnabled = false
    private var currentCameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
    private var photoCount = 0

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
            // Preview
            val preview = Preview.Builder()
                .setTargetRotation(viewFinder.display.rotation)
                .build()
                .also {
                    it.setSurfaceProvider(viewFinder.surfaceProvider)
                }

            // ImageCapture with high quality settings
            imageCapture = ImageCapture.Builder()
                .setTargetRotation(viewFinder.display.rotation)
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                .setFlashMode(if (isFlashEnabled) ImageCapture.FLASH_MODE_ON else ImageCapture.FLASH_MODE_OFF)
                .setTargetResolution(android.util.Size(4032, 3024))
                .setJpegQuality(100)
                .build()

            try {
                // Unbind all use cases before rebinding
                cameraProvider.unbindAll()

                // Bind use cases to camera
                camera = cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    currentCameraSelector,
                    preview,
                    imageCapture
                )
                isCameraInitialized = true
                isCameraActive = true
                Log.d(TAG, "Camera initialized and active")
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

        if (delaySeconds > 0) {
            // Start countdown with flash
            var remainingSeconds = delaySeconds
            val flashTimer = Timer()
            flashTimer.scheduleAtFixedRate(object : TimerTask() {
                override fun run() {
                    ContextCompat.getMainExecutor(context).execute {
                        if (remainingSeconds > 0) {
                            toggleFlash()
                            remainingSeconds--
                        } else {
                            flashTimer.cancel()
                            capturePhoto(imageCapture)
                        }
                    }
                }
            }, 0, 1000)
        } else {
            capturePhoto(imageCapture)
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
                if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
                    put(MediaStore.Images.Media.RELATIVE_PATH, "DCIM/Camera")
                }
            }

            // Create output options object which contains file + metadata
            val outputOptions = ImageCapture.OutputFileOptions
                .Builder(context.contentResolver,
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    contentValues)
                .build()

            // Take the picture
            imageCapture.takePicture(
                outputOptions,
                ContextCompat.getMainExecutor(context),
                object : ImageCapture.OnImageSavedCallback {
                    override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                        photoCount++
                        val msg = "Photo captured successfully"
                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                        Log.d(TAG, "Photo capture succeeded: ${output.savedUri}")
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
        isFlashEnabled = !isFlashEnabled
        imageCapture?.flashMode = if (isFlashEnabled) {
            ImageCapture.FLASH_MODE_ON
        } else {
            ImageCapture.FLASH_MODE_OFF
        }
        camera?.cameraControl?.enableTorch(isFlashEnabled)
    }

    fun flipCamera() {
        currentCameraSelector = if (currentCameraSelector == CameraSelector.DEFAULT_BACK_CAMERA) {
            CameraSelector.DEFAULT_FRONT_CAMERA
        } else {
            CameraSelector.DEFAULT_BACK_CAMERA
        }
        
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            try {
                val cameraProvider = cameraProviderFuture.get()
                bindCamera(cameraProvider)
            } catch (exc: Exception) {
                Log.e(TAG, "Failed to flip camera", exc)
                Toast.makeText(context, "Failed to switch camera: ${exc.message}", Toast.LENGTH_SHORT).show()
            }
        }, ContextCompat.getMainExecutor(context))
    }

    fun shutdown() {
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
} 