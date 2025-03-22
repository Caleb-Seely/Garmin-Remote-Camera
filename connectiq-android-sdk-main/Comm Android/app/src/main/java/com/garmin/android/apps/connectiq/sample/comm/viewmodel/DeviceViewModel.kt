package com.garmin.android.apps.connectiq.sample.comm.viewmodel

import android.app.Application
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.camera.view.PreviewView
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.garmin.android.apps.connectiq.sample.comm.camera.CameraState
import com.garmin.android.apps.connectiq.sample.comm.camera.CameraManager
import com.garmin.android.apps.connectiq.sample.comm.connectiq.ConnectIQManager
import com.garmin.android.apps.connectiq.sample.comm.ui.StatusMessages
import com.garmin.android.connectiq.IQApp
import com.garmin.android.connectiq.IQDevice

/**
 * ViewModel for DeviceActivity that manages camera operations, ConnectIQ communications, 
 * and UI state updates.
 */
class DeviceViewModel(application: Application) : AndroidViewModel(application) {
    companion object {
        private const val TAG = "DeviceViewModel"
        private const val COMM_WATCH_ID = "a3421feed289106a538cb9547ab12095"
    }

    // Camera state
    private val _isFlashEnabled = MutableLiveData(false)
    val isFlashEnabled: LiveData<Boolean> = _isFlashEnabled

    private val _isVideoMode = MutableLiveData(false)
    val isVideoMode: LiveData<Boolean> = _isVideoMode

    private val _isCountdownActive = MutableLiveData(false)
    val isCountdownActive: LiveData<Boolean> = _isCountdownActive
    
    private val _countdownSeconds = MutableLiveData(0)
    val countdownSeconds: LiveData<Int> = _countdownSeconds
    
    private val _statusMessage = MutableLiveData<String>()
    val statusMessage: LiveData<String> = _statusMessage
    
    private val _isDeviceConnected = MutableLiveData(false)
    val isDeviceConnected: LiveData<Boolean> = _isDeviceConnected

    // Properties
    private var cameraManager: CameraManager? = null
    private var connectIQManager: ConnectIQManager? = null
    private val handler = Handler(Looper.getMainLooper())
    private var countdownRunnable: Runnable? = null
    private var isCountdownCancelled = false
    private var recordingStartTime: Long = 0
    
    private val myApp: IQApp = IQApp(COMM_WATCH_ID)
    private var device: IQDevice? = null

    /**
     * Initialize the ViewModel with required components
     */
    fun initialize(
        context: Context,
        lifecycleOwner: androidx.lifecycle.LifecycleOwner,
        viewFinder: PreviewView,
        device: IQDevice
    ) {
        this.device = device
        
        initializeCameraManager(context, lifecycleOwner, viewFinder)
        initializeConnectIQManager(context)
    }

    private fun initializeCameraManager(
        context: Context,
        lifecycleOwner: androidx.lifecycle.LifecycleOwner,
        viewFinder: PreviewView
    ) {
        cameraManager = CameraManager(
            context,
            lifecycleOwner,
            viewFinder,
            onPhotoTaken = { status ->
                _statusMessage.postValue(StatusMessages.PHOTO_SAVED)
                connectIQManager?.sendMessage(status)
                // Update status to ready state after a short delay
                handler.postDelayed({
                    updateStatusWithTimeout(
                        if (_isVideoMode.value == true) StatusMessages.VIDEO_READY 
                        else StatusMessages.CAMERA_READY
                    )
                }, 1500)
            },
            onCountdownUpdate = { seconds ->
                _countdownSeconds.postValue(seconds)
                _isCountdownActive.postValue(seconds > 0)
            },
            onCameraSwapEnabled = { enabled ->
                // This would be handled via LiveData observers in the Activity
            },
            onRecordingStatusUpdate = { status ->
                when (status) {
                    StatusMessages.RECORDING_STARTED -> {
                        startRecordingTimer()
                        Log.d(TAG, "Recording started: $status")
                        connectIQManager?.sendMessage(StatusMessages.RECORDING_STARTED)
                    }
                    StatusMessages.RECORDING_STOPPED -> {
                        stopRecordingTimer()
                        _statusMessage.postValue(StatusMessages.RECORDING_STOPPED)
                        connectIQManager?.sendMessage(StatusMessages.RECORDING_STOPPED)
                        // Update status to ready state after recording stops
                        handler.postDelayed({
                            updateStatusWithTimeout(StatusMessages.VIDEO_READY)
                        }, 1500)
                    }
                    StatusMessages.RECORDING_CANCELLED -> {
                        stopRecordingTimer()
                        _statusMessage.postValue(StatusMessages.RECORDING_STOPPED)
                        // Update status to ready state after recording stops
                        handler.postDelayed({
                            updateStatusWithTimeout(StatusMessages.VIDEO_READY)
                        }, 1500)
                    }
                    else -> {
                        _statusMessage.postValue(status)
                    }
                }
            }
        )
    }

    private fun initializeConnectIQManager(context: Context) {
        device?.let { device ->
            connectIQManager = ConnectIQManager(
                context,
                device,
                onStatusUpdate = { status ->
                    if (status == "MODE_SWAP") {
                        Log.d(TAG, "Received MODE_SWAP command")
                        
                        // No need to toggle the camera mode here as it will be toggled
                        // in handlePhotoRequest based on the request code
                        // Just update the status message
                        _statusMessage.postValue("Mode swap in progress...")
                    } else {
                        _statusMessage.postValue(status)
                    }
                },
                onConnectionUpdate = { isConnected ->
                    _isDeviceConnected.postValue(isConnected)
                },
                cameraManager = cameraManager!!,
                onPhotoRequest = { delaySeconds ->
                    handlePhotoRequest(delaySeconds)
                }
            )
        }
    }

    private fun handlePhotoRequest(delaySeconds: Int) {
        Log.d(TAG, "handlePhotoRequest with code: $delaySeconds, current video mode: ${_isVideoMode.value}")
        
        when {
            delaySeconds == -1 -> {
                // Cancel request received
                Log.d(TAG, "Cancel request received")
                isCountdownCancelled = true
                cameraManager?.cancelCapture()
                _statusMessage.postValue(StatusMessages.RECORDING_CANCELLED)
                _countdownSeconds.postValue(0)
                _isCountdownActive.postValue(false)
            }
            // Special case: Swap to video mode then record
            delaySeconds == -100 -> {
                Log.d(TAG, "Swap to video mode then immediate recording")
                
                // Toggle to video mode first if we're not already in video mode
                if (_isVideoMode.value != true) {
                    Log.d(TAG, "  Toggling to video mode first")
                    toggleVideoMode()
                } else {
                    Log.d(TAG, "  Already in video mode, no need to toggle")
                }
                
                // Then start recording with a delay to ensure UI update completes
                handler.postDelayed({
                    Log.d(TAG, "  Starting video recording after mode swap")
                    startVideoRecording()
                }, 500)
            }
            delaySeconds < -100 -> {
                // Swap to video mode then delayed recording
                val actualDelay = -(delaySeconds + 100)
                Log.d(TAG, "Swap to video mode then delayed recording: $actualDelay sec")
                
                // Toggle to video mode first if we're not already in video mode
                if (_isVideoMode.value != true) {
                    Log.d(TAG, "  Toggling to video mode first")
                    toggleVideoMode()
                } else {
                    Log.d(TAG, "  Already in video mode, no need to toggle")
                }
                
                // Then start countdown with a delay to ensure UI update completes
                handler.postDelayed({
                    Log.d(TAG, "  Starting video countdown after mode swap")
                    startVideoCountdown(actualDelay)
                }, 500)
            }
            delaySeconds == 100 -> {
                // Swap to photo mode then immediate photo
                Log.d(TAG, "Swap to photo mode then immediate photo")
                
                // Toggle to photo mode first if we're not already in photo mode
                if (_isVideoMode.value == true) {
                    Log.d(TAG, "  Toggling to photo mode first")
                    toggleVideoMode()
                } else {
                    Log.d(TAG, "  Already in photo mode, no need to toggle")
                }
                
                // Then take photo with a delay to ensure UI update completes
                handler.postDelayed({
                    Log.d(TAG, "  Taking photo after mode swap")
                    takePhoto()
                }, 500)
            }
            delaySeconds > 100 -> {
                // Swap to photo mode then delayed photo
                val actualDelay = delaySeconds - 100
                Log.d(TAG, "Swap to photo mode then delayed photo: $actualDelay sec")
                
                // Toggle to photo mode first if we're not already in photo mode
                if (_isVideoMode.value == true) {
                    Log.d(TAG, "  Toggling to photo mode first")
                    toggleVideoMode()
                } else {
                    Log.d(TAG, "  Already in photo mode, no need to toggle")
                }
                
                // Then start countdown with a delay to ensure UI update completes
                handler.postDelayed({
                    Log.d(TAG, "  Starting photo countdown after mode swap")
                    startCountdown(actualDelay)
                }, 500)
            }
            delaySeconds == -2 -> {
                // Immediate video command (no mode swap)
                if (_isVideoMode.value != true) {
                    toggleVideoMode()
                }
                startVideoRecording()
            }
            delaySeconds < -2 -> {
                // Delayed video command (convert back to positive delay, no mode swap)
                val actualDelay = -(delaySeconds + 3)
                if (_isVideoMode.value != true) {
                    toggleVideoMode()
                }
                startVideoCountdown(actualDelay)
            }
            delaySeconds > 0 -> {
                // Regular photo with delay (no mode swap)
                if (_isVideoMode.value == true) {
                    toggleVideoMode()
                }
                startCountdown(delaySeconds)
            }
            else -> {
                // Immediate photo (no mode swap)
                if (_isVideoMode.value == true) {
                    toggleVideoMode()
                }
                takePhoto()
            }
        }
    }

    // Camera control methods
    fun takePhoto() {
        cameraManager?.takePhoto()
    }

    fun startVideoRecording() {
        cameraManager?.startVideoRecording()
    }

    fun stopVideoRecording() {
        cameraManager?.stopVideoRecording()
    }

    fun toggleFlash() {
        cameraManager?.toggleFlash()
        updateFlashState()
    }

    fun flipCamera() {
        Log.d(TAG, "flipCamera() called, current front camera: ${cameraManager?.isFrontCamera()}")
        
        // First cancel any active operations
        if (cameraManager?.isRecording() == true || _isCountdownActive.value == true) {
            Log.d(TAG, "Cancelling active operations before flipping camera")
            cameraManager?.cancelCapture()
            _countdownSeconds.postValue(0)
            _isCountdownActive.postValue(false)
        }
        
        // Flip the camera
        cameraManager?.flipCamera()
        
        // Update flash state after camera flip
        updateFlashState()
        
        // Log the updated state to help with debugging
        Log.d(TAG, "After flipCamera, front camera: ${cameraManager?.isFrontCamera()}, flash enabled: ${_isFlashEnabled.value}")
    }

    fun toggleVideoMode() {
        Log.d(TAG, "toggleVideoMode() called, current mode: ${_isVideoMode.value}")
        cameraManager?.toggleVideoMode()
        val newMode = cameraManager?.isVideoMode() ?: false
        Log.d(TAG, "New video mode after toggle: $newMode")
        
        // Update the UI with new mode
        _isVideoMode.postValue(newMode)
        
        // Update status message
        _statusMessage.postValue(
            if (newMode) 
                StatusMessages.VIDEO_MODE 
            else 
                StatusMessages.PHOTO_MODE
        )
    }

    fun openApp() {
        connectIQManager?.openApp()
    }

    fun handleVideoButtonClick() {
        if (cameraManager?.isRecording() == true) {
            stopVideoRecording()
        } else {
            startVideoRecording()
        }
    }

    fun startCamera() {
        cameraManager?.startCamera()
    }

    fun shutdown() {
        cameraManager?.shutdown()
    }

    private val recordingUpdateRunnable = object : Runnable {
        override fun run() {
            if (cameraManager?.isRecording() == true) {
                val elapsedSeconds = (System.currentTimeMillis() - recordingStartTime) / 1000
                val minutes = elapsedSeconds / 60
                val seconds = elapsedSeconds % 60
                _statusMessage.postValue(String.format("Recording: %02d:%02d", minutes, seconds))
                handler.postDelayed(this, 1000)
            }
        }
    }

    private fun startRecordingTimer() {
        recordingStartTime = System.currentTimeMillis()
        handler.post(recordingUpdateRunnable)
    }

    private fun stopRecordingTimer() {
        handler.removeCallbacks(recordingUpdateRunnable)
    }

    private fun startCountdown(seconds: Int) {
        // Reset cancel flag
        isCountdownCancelled = false
        
        // Clear any existing countdown
        countdownRunnable?.let { handler.removeCallbacks(it) }

        // Start countdown
        _countdownSeconds.postValue(seconds)
        _isCountdownActive.postValue(true)
        
        var remainingSeconds = seconds
        countdownRunnable = object : Runnable {
            override fun run() {
                if (remainingSeconds > 0 && !isCountdownCancelled) {
                    remainingSeconds--
                    _countdownSeconds.postValue(remainingSeconds)
                    handler.postDelayed(this, 1000)
                } else {
                    _isCountdownActive.postValue(false)
                    if (!isCountdownCancelled) {
                        takePhoto()
                    }
                }
            }
        }
        handler.post(countdownRunnable!!)
    }

    private fun startVideoCountdown(seconds: Int) {
        // Reset cancel flag
        isCountdownCancelled = false
        
        // Clear any existing countdown
        countdownRunnable?.let { handler.removeCallbacks(it) }
        
        // Update status text
        _statusMessage.postValue("Starting video recording in $seconds seconds")
        
        // Start countdown
        var remainingSeconds = seconds
        _countdownSeconds.postValue(seconds)
        _isCountdownActive.postValue(true)
        
        countdownRunnable = object : Runnable {
            override fun run() {
                if (remainingSeconds > 0 && !isCountdownCancelled) {
                    remainingSeconds--
                    _countdownSeconds.postValue(remainingSeconds)
                    handler.postDelayed(this, 1000)
                } else {
                    _isCountdownActive.postValue(false)
                    if (!isCountdownCancelled) {
                        // Ensure we're in video mode before starting recording
                        if (_isVideoMode.value != true) {
                            toggleVideoMode()
                        }
                        startVideoRecording()
                    }
                }
            }
        }
        handler.post(countdownRunnable!!)
    }

    fun updateStatusWithTimeout(message: String, timeoutMs: Long = 1500) {
        // Don't update status if we're showing a countdown or recording
        if (_isCountdownActive.value == true || cameraManager?.isRecording() == true) {
            return
        }
        
        _statusMessage.postValue(message)
        handler.removeCallbacksAndMessages(null)
        
        // Only set up auto-reset for temporary messages
        if (message != StatusMessages.CAMERA_READY && message != StatusMessages.VIDEO_READY) {
            handler.postDelayed({
                _statusMessage.postValue(
                    if (_isVideoMode.value == true) 
                        StatusMessages.VIDEO_READY 
                    else 
                        StatusMessages.CAMERA_READY
                )
            }, timeoutMs)
        }
    }

    fun registerForAppEvents() {
        connectIQManager?.registerForAppEvents()
    }

    fun unregisterForEvents() {
        connectIQManager?.unregisterForEvents()
    }

    fun isFrontCamera(): Boolean {
        return cameraManager?.isFrontCamera() ?: false
    }

    fun isRecording(): Boolean {
        return cameraManager?.isRecording() ?: false
    }

    override fun onCleared() {
        super.onCleared()
        countdownRunnable?.let { handler.removeCallbacks(it) }
        stopRecordingTimer()
        if (cameraManager?.isRecording() == true) {
            cameraManager?.cancelCapture()
        }
        cameraManager?.shutdown()
    }

    /**
     * Updates the flash state LiveData based on camera manager state
     */
    private fun updateFlashState() {
        val manager = cameraManager ?: return
        
        // Get current state
        val isFront = manager.isFrontCamera()
        val isFlashOn = manager.isFlashEnabled()
        
        Log.d(TAG, "updateFlashState: isFrontCamera=${isFront}, isFlashEnabled=${isFlashOn}")
        
        // Front camera can't have flash, so ensure it's off
        if (_isFlashEnabled.value != (!isFront && isFlashOn)) {
            _isFlashEnabled.postValue(!isFront && isFlashOn)
        }
    }
} 