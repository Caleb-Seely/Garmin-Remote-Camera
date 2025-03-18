package com.garmin.android.apps.connectiq.sample.comm.camera

//video
import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.math.min


private var scope = CoroutineScope(Dispatchers.Main)

class MyCameraManager(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val viewFinder: PreviewView,
    private val onPhotoTaken: (String) -> Unit = {},
    private val onCountdownUpdate: ((Int) -> Unit)? = null,
    private val onCameraSwapEnabled: ((Boolean) -> Unit)? = null,
    private val onRecordingStatusUpdate: ((String) -> Unit)? = null
) {
    companion object {
        private const val TAG = "MyCameraManager"
        private const val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"
        private var instanceCount = 0
    }

    private var imageCapture: ImageCapture? = null
    private var currentCountdownJob: Job? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var camera: Camera? = null
    private lateinit var cameraExecutor: ExecutorService
    private var isCameraInitialized = false
    private var isCameraActive = false
    private var currentCameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
    private var photoCount = 0
    private var isCountdownActive = false
    private var currentCountdown = 0
    private val cameraState = CameraState()
    private val handler = Handler(Looper.getMainLooper())
    private var recordingStatusRunnable: Runnable? = null
    private var currentRecording: Recording? = null
    private var isCaptureQueued = false
    private var lastCancelTime = 0L
    private val CAPTURE_LOCKOUT_MS = 1000 // Prevent new captures for 1 second after cancel

    init {
        instanceCount++
        Log.d(TAG, "MyCameraManager instance created. Total instances: $instanceCount")
        cameraExecutor = Executors.newSingleThreadExecutor()
    }

    fun startCamera() {
        Log.d(TAG, "startCamera() called - Instance: ${System.identityHashCode(this)}")
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)

        cameraProviderFuture.addListener({
            try {
                val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()
                Log.d(TAG, "Camera provider obtained, binding camera - Instance: ${System.identityHashCode(this)}")
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
            Log.d(TAG, "bindCamera() started - Instance: ${System.identityHashCode(this)}")
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
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                .setFlashMode(if (cameraState.isFlashEnabled) ImageCapture.FLASH_MODE_ON else ImageCapture.FLASH_MODE_OFF)
                .setTargetResolution(android.util.Size(8000, 6000))
                .setJpegQuality(100)
                .build()

            // VideoCapture with high quality settings
            val recorder = Recorder.Builder()
                .setQualitySelector(QualitySelector.from(Quality.HIGHEST))
                .build()
            videoCapture = VideoCapture.withOutput(recorder)

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
                imageCapture,
                videoCapture
            )

            // Configure additional camera controls if available
            camera?.let { cam ->
                // Initially disable torch
                cam.cameraControl.enableTorch(false)
                // Set capture mode to maximize quality
                cam.cameraInfo.exposureState.let { exposureState ->
                    if (exposureState.isExposureCompensationSupported) {
                        cam.cameraControl.setExposureCompensationIndex(0)
                    }
                }
            }

            isCameraInitialized = true
            isCameraActive = true
            Log.d(TAG, "Camera initialized and active - Instance: ${System.identityHashCode(this)}")

            // Update countdown display after successful camera switch
            if (isCountdownActive) {
                updateCountdownDisplay(currentCountdown)
            }
        } catch (exc: Exception) {
            handleCameraBindingError(exc, cameraProvider)
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

    fun cancelCapture() {
        Log.d(TAG, "cancelCapture() called - Instance: ${System.identityHashCode(this)}, isVideoMode: ${cameraState.isVideoMode}, isCountdownActive: $isCountdownActive")
        
        // Set cancel timestamp to prevent immediate recapture
        lastCancelTime = System.currentTimeMillis()

        // Cancel the specific countdown job immediately
        currentCountdownJob?.cancel()
        currentCountdownJob = null

        // Remove all pending callbacks from the main handler
        handler.removeCallbacksAndMessages(null)

        // Reset all capture-related state
        isCountdownActive = false
        isCaptureQueued = false
        currentCountdown = 0
        
        if (cameraState.isVideoMode) {
            // If we're in video mode and recording, stop recording
            if (isRecording()) {
                Log.d(TAG, "Stopping video recording in cancelCapture")
                stopVideoRecording()
            }
            onRecordingStatusUpdate?.invoke("Recording cancelled")
        }
        
        // Cancel all pending operations in the coroutine scope
        scope.cancel()
        scope = CoroutineScope(Dispatchers.Main)
        
        // Reset UI state
        updateCountdownDisplay(0)
        onCameraSwapEnabled?.invoke(true)
        
        // Turn off flash if it was on
        try {
            camera?.cameraControl?.enableTorch(false)
            cameraState.disableFlash()
        } catch (e: Exception) {
            Log.e(TAG, "Error turning off flash during cancel", e)
        }
        
        Log.d(TAG, "cancelCapture() completed - Instance: ${System.identityHashCode(this)}")
    }

    fun takePhoto(delaySeconds: Int = 0) {
        Log.d(TAG, "takePhoto() called with delay: $delaySeconds, isCountdownActive: $isCountdownActive, isCaptureQueued: $isCaptureQueued")

        // First, check if we need to reset any stale state
        if (isCountdownActive && !isCaptureQueued) {
            Log.d(TAG, "Resetting stale countdown state")
            resetCaptureState()
        }

        // Check if we're in a lockout period after cancellation
        val timeSinceLastCancel = System.currentTimeMillis() - lastCancelTime
        if (timeSinceLastCancel < CAPTURE_LOCKOUT_MS) {
            Log.d(TAG, "Ignoring takePhoto request during lockout period")
            return
        }

        // If there's already a valid countdown active, don't start another one
        if (isCountdownActive && isCaptureQueued) {
            Log.d(TAG, "Countdown already active, ignoring takePhoto request")
            return
        }

        // Set capture state
        isCaptureQueued = true
        isCountdownActive = delaySeconds > 0  // Only set countdown active if there's a delay
        currentCountdown = delaySeconds

        // Start countdown if delay is specified
        if (delaySeconds > 0) {
            startCountdown(delaySeconds)
        } else {
            // Take photo immediately if no delay
            capturePhoto()
        }
    }

    private fun startCountdown(seconds: Int) {
        Log.d(TAG, "Starting countdown with $seconds seconds")
        
        // Cancel any existing countdown job
        currentCountdownJob?.cancel()
        
        // Create new countdown job
        currentCountdownJob = scope.launch {
            try {
                for (i in seconds downTo 1) {  // Changed to stop at 1
                    if (!isCountdownActive || !isCaptureQueued) {
                        Log.d(TAG, "Countdown cancelled")
                        resetCaptureState()
                        break
                    }
                    
                    currentCountdown = i
                    updateCountdownDisplay(i)
                    delay(1000)
                }
                
                // Final second handling
                if (isCountdownActive && isCaptureQueued) {
                    updateCountdownDisplay(0)
                    Log.d(TAG, "Countdown completed, taking photo")
                    capturePhoto()
                } else {
                    resetCaptureState()
                }
            } catch (e: CancellationException) {
                Log.d(TAG, "Countdown job cancelled")
                resetCaptureState()
            }
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

    private fun capturePhoto() {
        Log.d(TAG, "capturePhoto() called - Instance: ${System.identityHashCode(this)}, isCountdownActive: $isCountdownActive, isCaptureQueued: $isCaptureQueued")
        
        if (!isCaptureQueued) {
            Log.d(TAG, "Photo capture cancelled - not queued - Instance: ${System.identityHashCode(this)}")
            resetCaptureState()
            return
        }

        val capture = imageCapture ?: run {
            Log.e(TAG, "Photo capture failed - imageCapture is null")
            resetCaptureState()
            return
        }

        // Create output options object which contains file + metadata
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

        // Set up image capture listener, which is triggered after photo has been taken
        capture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onError(exc: ImageCaptureException) {
                    Log.e(TAG, "Photo capture failed: ${exc.message}", exc)
                    onPhotoTaken("Photo capture failed: ${exc.message}")
                    resetCaptureState()
                }

                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    val msg = "Photo capture succeeded"
                    Log.d(TAG, msg)
                    onPhotoTaken(msg)
                    resetCaptureState()
                    onCameraSwapEnabled?.invoke(true)
                }
            }
        )
    }

    private fun resetCaptureState() {
        Log.d(TAG, "Resetting capture state - was active: $isCountdownActive, was queued: $isCaptureQueued")
        isCountdownActive = false
        isCaptureQueued = false
        currentCountdown = 0
        currentCountdownJob?.cancel()
        currentCountdownJob = null
        updateCountdownDisplay(0)
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

    fun startVideoRecording(delaySeconds: Int = 0) {
        Log.d(TAG, "startVideoRecording() called with delay: $delaySeconds, isCountdownActive: $isCountdownActive")
        
        if (!isCameraInitialized || !isCameraActive) {
            Log.e(TAG, "Camera not initialized or not active")
            Toast.makeText(context, "Camera not ready. Restarting...", Toast.LENGTH_SHORT).show()
            startCamera()
            return
        }

        val videoCapture = videoCapture ?: run {
            Log.e(TAG, "VideoCapture is null")
            Toast.makeText(context, "Camera not configured properly", Toast.LENGTH_SHORT).show()
            return
        }

        // Cancel any existing countdown before starting a new one
        if (isCountdownActive) {
            Log.d(TAG, "Cancelling existing countdown before starting new one")
            cancelCapture()
        }

        // Set countdown active at the start of any video recording attempt
        isCountdownActive = true
        Log.d(TAG, "Setting isCountdownActive to true at start of video recording")

        if (delaySeconds > 0) {
            Log.d(TAG, "Starting delayed video recording")
            currentCountdown = delaySeconds
            onCameraSwapEnabled?.invoke(false)
            
            scope.launch {
                try {
                    var remainingSeconds = delaySeconds
                    while (remainingSeconds > 0) {
                        if (!isCountdownActive) {
                            Log.d(TAG, "Video countdown cancelled, breaking loop")
                            break
                        }
                        
                        Log.d(TAG, "Video countdown: $remainingSeconds seconds remaining")
                        updateCountdownDisplay(remainingSeconds)
                        
                        if (cameraState.shouldUseFlashForCountdown()) {
                            if (remainingSeconds > 1) {
                                flashBriefly("normal", 0.2f)
                            } else if (remainingSeconds == 1) {
                                flashBriefly("final", 0.6f)
                            }
                        }
                        remainingSeconds--
                        delay(1000)
                    }
                    
                    // Double check it wasn't cancelled during the last delay
                    if (isCountdownActive) {
                        Log.d(TAG, "Video countdown completed, starting recording")
                        updateCountdownDisplay(0)
                        startRecording(videoCapture)
                    } else {
                        Log.d(TAG, "Video countdown was cancelled, not starting recording")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error during video countdown", e)
                } finally {
                    Log.d(TAG, "Video countdown finally block, cleaning up")
                    isCountdownActive = false
                    currentCountdown = 0
                    onCameraSwapEnabled?.invoke(true)
                }
            }
        } else {
            Log.d(TAG, "Starting immediate video recording")
            startRecording(videoCapture)
        }
    }

    private fun startRecording(videoCapture: VideoCapture<Recorder>) {
        Log.d(TAG, "startRecording() called, isCountdownActive: $isCountdownActive")
        if (!isCountdownActive) {
            Log.d(TAG, "Video recording cancelled - countdown not active")
            onRecordingStatusUpdate?.invoke("Recording cancelled")
            return
        }

        try {
            Log.d(TAG, "Starting recording on cameraManager instance: ${System.identityHashCode(this)}")
            cameraState.startRecording()
            Log.d(TAG, "Set recording state to true, current state: ${cameraState.isRecording}")
            
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

            currentRecording = videoCapture.output
                .prepareRecording(context, mediaStoreOutputOptions)
                .apply { 
                    if (cameraState.isFlashEnabled && !cameraState.isFrontCamera) {
                        camera?.cameraControl?.enableTorch(true)
                    }
                }
                .start(ContextCompat.getMainExecutor(context)) { recordEvent ->
                    when(recordEvent) {
                        is VideoRecordEvent.Start -> {
                            Log.d(TAG, "Received VideoRecordEvent.Start, recording state: ${cameraState.isRecording}")
                            startRecordingStatusUpdates()
                            onRecordingStatusUpdate?.invoke("Recording started")
                        }
                        is VideoRecordEvent.Finalize -> {
                            Log.d(TAG, "Received VideoRecordEvent.Finalize, recording state: ${cameraState.isRecording}")
                            if (recordEvent.hasError()) {
                                Log.e(TAG, "Video capture failed: ${recordEvent.cause}")
                                Toast.makeText(context, "Failed to record video: ${recordEvent.cause}", Toast.LENGTH_SHORT).show()
                            } else {
                                onRecordingStatusUpdate?.invoke("Video saved successfully")
                            }
                            stopRecordingStatusUpdates()
                            currentRecording = null
                            cameraState.stopRecording()
                            Log.d(TAG, "After stopping recording, state: ${cameraState.isRecording}")
                        }
                    }
                }
        } catch (e: Exception) {
            Log.e(TAG, "Error starting video recording", e)
            Toast.makeText(context, "Error starting video recording: ${e.message}", Toast.LENGTH_SHORT).show()
            // Reset recording state if start failed
            cameraState.stopRecording()
            Log.d(TAG, "Recording failed to start, reset state: ${cameraState.isRecording}")
        }
    }

    fun stopVideoRecording() {
        try {
            currentRecording?.stop()
            cameraState.stopRecording()
            stopRecordingStatusUpdates()
            onRecordingStatusUpdate?.invoke("Recording stopped")
            
            // Turn off torch if it was on
            if (cameraState.isFlashEnabled && !cameraState.isFrontCamera) {
                camera?.cameraControl?.enableTorch(false)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping video recording", e)
            Toast.makeText(context, "Error stopping video recording: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

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

    private fun stopRecordingStatusUpdates() {
        recordingStatusRunnable?.let { handler.removeCallbacks(it) }
        recordingStatusRunnable = null
    }

    fun isRecording(): Boolean {
        val recordingState = cameraState.isRecording
        Log.d(TAG, "isRecording() called, returning: $recordingState")
        return recordingState
    }

    fun isVideoMode(): Boolean {
        return cameraState.isVideoMode
    }

    fun toggleVideoMode(): Boolean {
        return cameraState.toggleVideoMode()
    }

    fun shutdown() {
        Log.d(TAG, "Shutting down MyCameraManager instance")
        stopVideoRecording()
        cameraExecutor.shutdown()
        try {
            if (!cameraExecutor.awaitTermination(800, TimeUnit.MILLISECONDS)) {
                cameraExecutor.shutdownNow()
            }
        } catch (e: InterruptedException) {
            cameraExecutor.shutdownNow()
        }
        instanceCount--
        Log.d(TAG, "MyCameraManager instance shut down. Remaining instances: $instanceCount")
    }

    fun isActive() = isCameraActive

    fun isFrontCamera() = cameraState.isFrontCamera
}