package com.garmin.android.apps.clearshot.phone.camera

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.Surface
import android.view.WindowManager
import android.widget.Toast
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.VideoCapture
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.garmin.android.apps.clearshot.phone.camera.CameraLogger.CAMERA_INITIALIZER
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import android.util.Size
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.core.AspectRatio
import androidx.camera.core.resolutionselector.AspectRatioStrategy

/**
 * Responsible for initializing and binding the camera for use.
 * Handles camera provider setup, use case binding, and error recovery.
 */
class CameraInitializer(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val viewFinder: PreviewView,
    private val cameraState: CameraState,
    private val videoCaptureManager: VideoCaptureManager? = null
) {
    private var camera: Camera? = null
    private var imageCapture: ImageCapture? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private val handler = Handler(Looper.getMainLooper())
    
    private var isCameraActive = false
    private var isCameraInitialized = false
    
    /**
     * Initialize and start the camera with current configuration.
     * @param forceRestart If true, will restart the camera even if it's already active (needed for camera flip)
     * @return true if camera started successfully, false otherwise
     */
    fun startCamera(forceRestart: Boolean = false): Boolean {
        CameraLogger.d(CAMERA_INITIALIZER, "startCamera() called, forceRestart=$forceRestart", this)
        
        // If camera is already active and not forcing restart, don't reinitialize
        if (isCameraActive && !forceRestart) {
            CameraLogger.d(CAMERA_INITIALIZER, "Camera is already active, skipping initialization", this)
            return true
        }
        
        // If forcing restart and camera is active, mark it inactive first
        if (forceRestart && isCameraActive) {
            CameraLogger.d(CAMERA_INITIALIZER, "Force restarting camera, marking current as inactive", this)
            isCameraActive = false
        }
        
        try {
            val cameraProviderFuture = ProcessCameraProvider.getInstance(context)

            cameraProviderFuture.addListener({
                try {
                    val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()
                    CameraLogger.d(CAMERA_INITIALIZER, "Camera provider obtained, binding camera", this)
                    bindCamera(cameraProvider)
                } catch (exc: Exception) {
                    CameraLogger.e(CAMERA_INITIALIZER, "Camera initialization failed", exc, this)
                    isCameraActive = false
                    // Try with a delay as a recovery mechanism
                    handler.postDelayed({
                        try {
                            val provider = ProcessCameraProvider.getInstance(context).get()
                            bindCamera(provider)
                        } catch (e: Exception) {
                            CameraLogger.e(CAMERA_INITIALIZER, "Camera recovery failed after delay", e, this)
                            Toast.makeText(context, "Failed to start camera: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }, 500)
                }
            }, ContextCompat.getMainExecutor(context))
            
            return true
        } catch (e: Exception) {
            CameraLogger.e(CAMERA_INITIALIZER, "Failed to get camera provider", e, this)
            isCameraActive = false
            return false
        }
    }
    
    /**
     * Bind camera use cases to the lifecycle.
     * @param cameraProvider The camera provider to use for binding
     */
    private fun bindCamera(cameraProvider: ProcessCameraProvider) {
        try {
            CameraLogger.d(CAMERA_INITIALIZER, "bindCamera() started", this)
            
            // First unbind everything to ensure a clean slate
            try {
                cameraProvider.unbindAll()
            } catch (e: Exception) {
                CameraLogger.e(CAMERA_INITIALIZER, "Error unbinding previous use cases", e, this)
            }
            
            // Get lens direction based on current state
            val lensFacing = if (cameraState.isFrontCamera) {
                CameraLogger.d(CAMERA_INITIALIZER, "Using FRONT camera", this)
                CameraSelector.LENS_FACING_FRONT
            } else {
                CameraLogger.d(CAMERA_INITIALIZER, "Using BACK camera", this)
                CameraSelector.LENS_FACING_BACK
            }
            
            // Create camera selector
            val cameraSelector = CameraSelector.Builder()
                .requireLensFacing(lensFacing)
                .build()
                
            // Verify camera is available
            if (!checkCameraAvailable(cameraProvider, lensFacing)) {
                CameraLogger.e(CAMERA_INITIALIZER, "Camera with lensFacing=$lensFacing not available", null, this)
                Toast.makeText(context, "Camera not available on this device", Toast.LENGTH_SHORT).show()
                return
            }
            
            // Preview with high quality settings and matching aspect ratio
            val preview = Preview.Builder()
                .setTargetRotation(viewFinder.display.rotation)
                .setResolutionSelector(
                    ResolutionSelector.Builder()
                        .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
                        .build()
                )
                .build()
                .also {
                    it.setSurfaceProvider(viewFinder.surfaceProvider)
                }

            // Calculate and set preview dimensions to eliminate side bars
            viewFinder.post {
                val displayMetrics = context.resources.displayMetrics
                val screenWidth = displayMetrics.widthPixels
                
                // Calculate a height that's more than 4:3 to eliminate side bars
                // Using a ratio closer to 3:4 for height (inverting the aspect ratio to prioritize height)
                val height = (screenWidth * 4) / 3  // This makes it much taller
                
                val params = viewFinder.layoutParams
                params.height = height
                viewFinder.layoutParams = params
                
                // Set scale type to adjust for the larger calculated height
                viewFinder.scaleType = PreviewView.ScaleType.FILL_CENTER
            }

            // ImageCapture with maximum quality settings and matching aspect ratio
            val imageCaptureBuilder = ImageCapture.Builder()
                .setTargetRotation(viewFinder.display.rotation)
                .setResolutionSelector(
                    ResolutionSelector.Builder()
                        .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
                        .build()
                )
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                .setFlashMode(if (cameraState.isFlashEnabled) ImageCapture.FLASH_MODE_ON else ImageCapture.FLASH_MODE_OFF)
                .setJpegQuality(100)

            // Only configure highest resolution for back camera
            if (!cameraState.isFrontCamera) {
                try {
                    val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as android.hardware.camera2.CameraManager
                    val cameraId = cameraManager.cameraIdList.firstOrNull { id ->
                        val characteristics = cameraManager.getCameraCharacteristics(id)
                        characteristics.get(android.hardware.camera2.CameraCharacteristics.LENS_FACING) == 
                            android.hardware.camera2.CameraCharacteristics.LENS_FACING_BACK
                    }

                    if (cameraId != null) {
                        val characteristics = cameraManager.getCameraCharacteristics(cameraId)
                        val map = characteristics.get(android.hardware.camera2.CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                        val jpegSizes = map?.getOutputSizes(android.graphics.ImageFormat.JPEG)
                        val highestResolution = jpegSizes?.maxByOrNull { it.width * it.height }

                        if (highestResolution != null) {
                            CameraLogger.d(CAMERA_INITIALIZER, "Using highest back camera resolution: $highestResolution", this)
                            imageCaptureBuilder.setResolutionSelector(
                                ResolutionSelector.Builder()
                                    .setResolutionStrategy(
                                        ResolutionStrategy(highestResolution, ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER)
                                    )
                                    .build()
                            )
                        }
                    }
                } catch (e: Exception) {
                    CameraLogger.e(CAMERA_INITIALIZER, "Error configuring back camera resolution: ${e.message}", e, this)
                }
            }

            imageCapture = imageCaptureBuilder.build()

            // VideoCapture with high quality settings and audio enabled
            // Try to use the configured video capture with metadata if available
            videoCapture = if (videoCaptureManager != null) {
                // Use VideoCaptureManager to create a configured video capture with metadata
                val configuredVideoCapture = videoCaptureManager.createConfiguredVideoCapture()
                if (configuredVideoCapture != null) {
                    CameraLogger.d(CAMERA_INITIALIZER, "Using configured video capture with metadata support", this)
                    configuredVideoCapture
                } else {
                    CameraLogger.d(CAMERA_INITIALIZER, "Falling back to default video capture", this)
                    createDefaultVideoCapture()
                }
            } else {
                CameraLogger.d(CAMERA_INITIALIZER, "Using default video capture (no VideoCaptureManager)", this)
                createDefaultVideoCapture()
            }

            // Bind use cases to camera
            try {
                // If we have a video capture use case, manually set its target rotation
                videoCapture?.let { videoCap ->
                    // Get current device orientation
                    val currentOrientation = try {
                        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
                        val rotation = windowManager.defaultDisplay.rotation
                        CameraLogger.d(CAMERA_INITIALIZER, "Setting video target rotation to: $rotation", this)
                        rotation
                    } catch (e: Exception) {
                        CameraLogger.e(CAMERA_INITIALIZER, "Error getting rotation, defaulting to 0", e, this)
                        Surface.ROTATION_0
                    }
                    // We need to use reflection to set target rotation since the API might differ between versions
                    try {
                        val setCameraMethod = videoCap.javaClass.getDeclaredMethod("setTargetRotation", Int::class.java)
                        setCameraMethod.invoke(videoCap, currentOrientation)
                        CameraLogger.d(CAMERA_INITIALIZER, "Successfully set rotation via reflection", this)
                    } catch (e: Exception) {
                        CameraLogger.e(CAMERA_INITIALIZER, "Failed to set rotation via reflection", e, this)
                    }
                }

                camera = cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    preview,
                    imageCapture,
                    videoCapture
                )

                // Configure additional camera controls if available
                configureInitialCameraSettings()

                isCameraInitialized = true
                isCameraActive = true
                CameraLogger.d(CAMERA_INITIALIZER, "Camera initialized and active with lensFacing=$lensFacing", this)
            } catch (exc: Exception) {
                CameraLogger.e(CAMERA_INITIALIZER, "Use case binding failed", exc, this)
                Toast.makeText(context, "Failed to bind camera use cases: ${exc.message}", Toast.LENGTH_SHORT).show()
                isCameraActive = false
                // Try recovery with fewer use cases
                handleCameraBindingError(exc, cameraProvider)
            }
        } catch (e: Exception) {
            CameraLogger.e(CAMERA_INITIALIZER, "Error binding camera", e, this)
            isCameraActive = false
        }
    }
    
    /**
     * Create default video capture use case without metadata configuration
     */
    private fun createDefaultVideoCapture(): VideoCapture<Recorder> {
        val recorder = Recorder.Builder()
            .setQualitySelector(QualitySelector.from(Quality.HIGHEST, FallbackStrategy.higherQualityOrLowerThan(Quality.SD)))
            .build()
        return VideoCapture.withOutput(recorder)
    }
    
    /**
     * Check if the requested camera is available on the device
     */
    private fun checkCameraAvailable(cameraProvider: ProcessCameraProvider, lensFacing: Int): Boolean {
        return try {
            cameraProvider.hasCamera(
                CameraSelector.Builder().requireLensFacing(lensFacing).build()
            )
        } catch (e: Exception) {
            CameraLogger.e(CAMERA_INITIALIZER, "Error checking camera availability", e, this)
            false
        }
    }
    
    /**
     * Configure initial camera settings after binding
     */
    private fun configureInitialCameraSettings() {
        camera?.let { cam ->
            // Initially disable torch
            try {
                cam.cameraControl.enableTorch(false)
            } catch (e: Exception) {
                CameraLogger.e(CAMERA_INITIALIZER, "Could not control torch", e, this)
            }
            
            // Set capture mode to maximize quality
            cam.cameraInfo.exposureState.let { exposureState ->
                if (exposureState.isExposureCompensationSupported) {
                    try {
                        cam.cameraControl.setExposureCompensationIndex(0)
                    } catch (e: Exception) {
                        CameraLogger.e(CAMERA_INITIALIZER, "Could not set exposure compensation", e, this)
                    }
                }
            }
        }
    }
    
    /**
     * Handle camera binding errors and attempt recovery
     */
    private fun handleCameraBindingError(exc: Exception, cameraProvider: ProcessCameraProvider) {
        CameraLogger.e(CAMERA_INITIALIZER, "Use case binding failed", exc, this)
        isCameraActive = false
        Toast.makeText(context, "Camera binding failed. Retrying...", Toast.LENGTH_SHORT).show()

        try {
            cameraProvider.unbindAll()
            camera = cameraProvider.bindToLifecycle(
                lifecycleOwner,
                getCameraSelector(),
                Preview.Builder().build(),
                imageCapture
            )
            isCameraActive = true
            CameraLogger.d(CAMERA_INITIALIZER, "Camera recovered after binding failure", this)
        } catch (e: Exception) {
            CameraLogger.e(CAMERA_INITIALIZER, "Camera recovery failed", e, this)
            Toast.makeText(context, "Camera recovery failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
    
    /**
     * Get the current camera selector based on camera state
     */
    private fun getCameraSelector(): CameraSelector {
        return if (cameraState.isFrontCamera) {
            CameraSelector.DEFAULT_FRONT_CAMERA
        } else {
            CameraSelector.DEFAULT_BACK_CAMERA
        }
    }
    
    /**
     * Check if camera is initialized and active
     */
    fun isCameraActive(): Boolean = isCameraActive
    
    /**
     * Check if camera is fully initialized with all use cases
     */
    fun isCameraInitialized(): Boolean = isCameraInitialized
    
    /**
     * Get current camera instance
     */
    fun getCamera(): Camera? = camera
    
    /**
     * Get current ImageCapture use case
     */
    fun getImageCapture(): ImageCapture? = imageCapture
    
    /**
     * Get current VideoCapture use case
     */
    fun getVideoCapture(): VideoCapture<Recorder>? = videoCapture
    
    /**
     * Shutdown and cleanup resources
     */
    fun shutdown() {
        CameraLogger.d(CAMERA_INITIALIZER, "Shutting down", this)
        isCameraActive = false
        isCameraInitialized = false
        
        // Release camera executor
        try {
            cameraExecutor.shutdown()
        } catch (e: Exception) {
            CameraLogger.e(CAMERA_INITIALIZER, "Error shutting down camera executor", e, this)
        }
        
        // Clear handler callbacks
        handler.removeCallbacksAndMessages(null)
        
        // Clear references
        camera = null
        imageCapture = null
        videoCapture = null
    }
} 