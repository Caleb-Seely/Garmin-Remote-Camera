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

class MyCameraManager(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val viewFinder: PreviewView
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

    init {
        cameraExecutor = Executors.newSingleThreadExecutor()
    }

    fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)

        cameraProviderFuture.addListener({
            try {
                val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

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
                    .setFlashMode(ImageCapture.FLASH_MODE_OFF)
                    .setTargetResolution(android.util.Size(4032, 3024))
                    .setJpegQuality(100)
                    .build()

                // Select back camera as a default
                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                try {
                    // Unbind all use cases before rebinding
                    cameraProvider.unbindAll()

                    // Bind use cases to camera
                    camera = cameraProvider.bindToLifecycle(
                        lifecycleOwner, cameraSelector, preview, imageCapture
                    )
                    isCameraInitialized = true
                    isCameraActive = true
                    Log.d(TAG, "Camera initialized and active")
                } catch (exc: Exception) {
                    Log.e(TAG, "Use case binding failed", exc)
                    isCameraActive = false
                    // Try to recover by unbinding and retrying
                    try {
                        cameraProvider.unbindAll()
                        camera = cameraProvider.bindToLifecycle(
                            lifecycleOwner, cameraSelector, preview, imageCapture
                        )
                        isCameraActive = true
                        Log.d(TAG, "Camera recovered after binding failure")
                    } catch (e: Exception) {
                        Log.e(TAG, "Camera recovery failed", e)
                    }
                }
            } catch (exc: Exception) {
                Log.e(TAG, "Camera initialization failed", exc)
                isCameraActive = false
            }
        }, ContextCompat.getMainExecutor(context))
    }

    fun takePhoto() {
        if (!isCameraInitialized || !isCameraActive) {
            Log.e(TAG, "Camera not initialized or not active")
            startCamera()
            return
        }

        val imageCapture = imageCapture ?: run {
            Log.e(TAG, "ImageCapture is null")
            return
        }

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
                        val msg = "Photo capture succeeded: ${output.savedUri}"
                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                        Log.d(TAG, msg)
                    }

                    override fun onError(exc: ImageCaptureException) {
                        Log.e(TAG, "Photo capture failed: ${exc.message}", exc)
                    }
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error preparing photo capture", e)
        }
    }

    fun toggleFlash() {
        camera?.let { camera ->
            val flashMode = if (camera.cameraInfo.torchState.value == TorchState.ON) {
                ImageCapture.FLASH_MODE_OFF
            } else {
                ImageCapture.FLASH_MODE_ON
            }
            imageCapture?.flashMode = flashMode
        }
    }

    fun shutdown() {
        cameraExecutor.shutdown()
        isCameraActive = false
    }

    fun isActive() = isCameraActive
} 