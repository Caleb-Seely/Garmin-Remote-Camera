package com.garmin.android.apps.connectiq.sample.comm.connectiq

import android.content.Context
import android.util.Log
import com.garmin.android.apps.connectiq.sample.comm.camera.CameraManager
import com.garmin.android.apps.connectiq.sample.comm.ui.StatusMessages
import com.garmin.android.apps.connectiq.sample.comm.utils.Constants
import com.garmin.android.connectiq.ConnectIQ
import com.garmin.android.connectiq.IQApp
import com.garmin.android.connectiq.IQDevice
import com.garmin.android.connectiq.exception.InvalidStateException
import com.garmin.android.connectiq.exception.ServiceUnavailableException

/**
 * Manager for ConnectIQ device communications.
 * Handles message exchange with Garmin devices and provides callbacks for events.
 */
class ConnectIQManager(
    private val context: Context,
    private val device: IQDevice,
    private val onStatusUpdate: (String) -> Unit,
    private val onConnectionUpdate: (Boolean) -> Unit,
    private val cameraManager: CameraManager,
    private val onPhotoRequest: (Int) -> Unit
) {
    companion object {
        private const val TAG = "ConnectIQManager"
    }

    private val connectIQ: ConnectIQ = ConnectIQ.getInstance(context, ConnectIQ.IQConnectType.WIRELESS)
    private val myApp: IQApp = IQApp(Constants.COMM_WATCH_ID)
    private var appIsOpen = false

    private val openAppListener = ConnectIQ.IQOpenApplicationListener { _, _, status ->
        appIsOpen = status == ConnectIQ.IQOpenApplicationStatus.APP_IS_ALREADY_RUNNING
        Log.d(TAG, "App open status: $appIsOpen")
        
        // Log detailed app status
        when (status) {
            ConnectIQ.IQOpenApplicationStatus.APP_IS_ALREADY_RUNNING -> {
                Log.d(TAG, "App is already running on device")
                onStatusUpdate(StatusMessages.APP_RUNNING)
            }
            ConnectIQ.IQOpenApplicationStatus.PROMPT_SHOWN_ON_DEVICE -> {
                Log.d(TAG, "Prompt shown on device to open app")
                onStatusUpdate(StatusMessages.APP_PROMPT_SHOWN)
            }
            else -> {
                Log.w(TAG, "App not opened: $status")
                onStatusUpdate(StatusMessages.APP_OPEN_FAILED)
            }
        }
    }

    /**
     * Opens the ConnectIQ app on the device
     */
    fun openApp() {
        Log.d(TAG, "Opening app on device: ${device.friendlyName}")

        // First check if the device is connected
        if (!isDeviceConnected()) {
            Log.w(TAG, "Cannot open app: Device not connected")
            onStatusUpdate(StatusMessages.DEVICE_NOT_CONNECTED)
            onConnectionUpdate(false)
            return
        }

        try {
            // Check if the device supports the app
            // Note: We don't use getApplicationInfo as it may not be available in all SDK versions
            val status = connectIQ.getDeviceStatus(device)
            Log.d(TAG, "Device status: $status")

            // Only proceed if the device is connected
            if (status == IQDevice.IQDeviceStatus.CONNECTED) {
                connectIQ.openApplication(device, myApp, openAppListener)
            } else {
                Log.w(TAG, "Device not connected, cannot open app. Status: $status")
                onStatusUpdate(StatusMessages.DEVICE_NOT_CONNECTED)
                onConnectionUpdate(false)
            }
        } catch (e: InvalidStateException) {
            Log.e(TAG, "Error opening app: ConnectIQ not in valid state", e)
            onStatusUpdate(StatusMessages.CONNECTIQ_NOT_READY)
        } catch (e: ServiceUnavailableException) {
            Log.e(TAG, "Error opening app: Service unavailable", e)
            onStatusUpdate(StatusMessages.SERVICE_UNAVAILABLE)
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error opening app", e)
            onStatusUpdate(StatusMessages.APP_OPEN_FAILED)
        }
    }

    /**
     * Registers for device and app events
     */
    fun registerForAppEvents() {
        try {
            Log.d(TAG, "Registering for app events for device: ${device.friendlyName}")
            
            // First check current connection state
            val currentStatus = connectIQ.getDeviceStatus(device)
            Log.d(TAG, "Initial device status: ${currentStatus?.name}")
            onConnectionUpdate(currentStatus == IQDevice.IQDeviceStatus.CONNECTED)
            
            // Register for device status updates
            connectIQ.registerForDeviceEvents(device) { device, status ->
                Log.d(TAG, "Device status changed: ${status.name} for device: ${device.friendlyName}")
                
                // Log detailed device connection information
                when (status) {
                    IQDevice.IQDeviceStatus.CONNECTED -> {
                        Log.d(TAG, "Device connected: ${device.friendlyName} [${device.deviceIdentifier}]")
                        onStatusUpdate(StatusMessages.CONNECTION_RESTORED)
                        onConnectionUpdate(true)
                    }
                    IQDevice.IQDeviceStatus.NOT_CONNECTED -> {
                        Log.d(TAG, "Device disconnected: ${device.friendlyName}")
                        onStatusUpdate(StatusMessages.CONNECTION_LOST)
                        onConnectionUpdate(false)
                    }
                    else -> {
                        Log.d(TAG, "Device status: ${status.name} for ${device.friendlyName}")
                        // For other states, we should check if the device is actually connected
                        val currentStatus = connectIQ.getDeviceStatus(device)
                        Log.d(TAG, "Current device status from getDeviceStatus: ${currentStatus?.name}")
                        onConnectionUpdate(currentStatus == IQDevice.IQDeviceStatus.CONNECTED)
                    }
                }
            }

            connectIQ.registerForAppEvents(device, myApp) { _, _, message, _ ->
                Log.d(TAG, "New message received: $message, recording: ${cameraManager.isRecording()}")

                // If recording is in progress, stop it regardless of the message
                if (cameraManager.isRecording()) {
                    Log.d(TAG, "Recording in progress, stopping video")
                    onPhotoRequest(-1) // Send cancellation command to stop recording
                    return@registerForAppEvents
                }

                // Parse message to determine command type and delay
                val requestCode = parseRequest(message)

                // Update status text based on request code
                updateStatusText(requestCode)

                // Execute the photo/video request with proper code
                onPhotoRequest(requestCode)
            }
        } catch (e: InvalidStateException) {
            Log.e(TAG, "ConnectIQ is not in a valid state", e)
            onStatusUpdate(StatusMessages.CONNECTIQ_NOT_READY)
        } catch (e: Exception) {
            Log.e(TAG, "Error registering for app events", e)
            onStatusUpdate(StatusMessages.CONNECTION_LOST)
        }
    }

    /**
     * Parse the incoming message and return the appropriate request code for onPhotoRequest
     *
     * Request codes:
     * -1: Cancel operation
     * -2: Immediate video
     * < -2: Delayed video (delay = -[code + 3])
     * 0: Immediate photo
     * > 0: Delayed photo (delay = code)
     */
    private fun parseRequest(message: List<Any>?): Int {
        if (message == null || message.isEmpty()) {
            // Default: take immediate photo/video in current mode
            return if (cameraManager.isVideoMode()) -2 else 0
        }

        val firstItem = message[0]

        // Handle string commands
        if (firstItem is String) {
            // Handle cancellation command
            if (firstItem.equals("cancel", ignoreCase = true)) {
                return -1
            }

            // Handle mode swap command
            if (firstItem.startsWith("SWAP", ignoreCase = true)) {
                val delayStr = firstItem.substringAfter("SWAP", "").trim()
                val delay = if (delayStr.isEmpty()) 0 else delayStr.toIntOrNull() ?: 0

                // Notify that a mode swap occurred (this will be observed in the ViewModel)
                Log.d(TAG, "Mode swap command received, sending MODE_SWAP notification")
                onStatusUpdate(StatusMessages.MODE_SWAP_IN_PROGRESS)
                
                // Get current mode - but DON'T toggle here, let the ViewModel handle it
                val isCurrentlyVideoMode = cameraManager.isVideoMode()

                // If currently in video mode, we'll switch to photo mode
                // If currently in photo mode, we'll switch to video mode
                // So we need to return codes for the OPPOSITE of the current mode
                return if (isCurrentlyVideoMode) {
                    // Currently in video mode, will switch to photo mode -> return photo codes
                    if (delay > 0) 100 + delay else 100  // 100 = swap to photo then take photo
                } else {
                    // Currently in photo mode, will switch to video mode -> return video codes
                    if (delay > 0) -100 - delay else -100  // -100 = swap to video then record
                }
            }
            
            // Handle numeric delay as string
            val delay = firstItem.toIntOrNull() ?: 0
            return if (cameraManager.isVideoMode()) {
                if (delay > 0) -3 - delay else -2
            } else {
                delay // For photo mode, delay is just the number
            }
        }

        // Handle numeric delay
        if (firstItem is Number) {
            val delay = firstItem.toInt()
            return if (cameraManager.isVideoMode()) {
                if (delay > 0) -3 - delay else -2
            } else {
                delay // For photo mode, delay is just the number
            }
        }

        // Default fallback - immediate capture in current mode
        return if (cameraManager.isVideoMode()) -2 else 0
    }

    /**
     * Update status based on the request code
     */
    private fun updateStatusText(requestCode: Int) {
        val statusMessage = when {
            requestCode == -1 -> StatusMessages.RECORDING_CANCELLED
            requestCode == -100 -> StatusMessages.VIDEO_MODE
            requestCode < -100 -> {
                val delay = -(requestCode + 100)
                "Starting video recording in $delay seconds"
            }
            requestCode == 100 -> StatusMessages.PHOTO_MODE
            requestCode > 100 -> {
                val delay = requestCode - 100
                "Taking photo in $delay seconds"
            }
            requestCode == -2 -> StatusMessages.RECORDING_STARTED
            requestCode < -2 -> {
                val delay = -(requestCode + 3)
                "Starting recording in $delay seconds"
            }
            requestCode > 0 -> "Taking photo in $requestCode seconds"
            else -> StatusMessages.TAKING_PHOTO
        }
        onStatusUpdate(statusMessage)
    }

    /**
     * Sends a message to the connected device
     */
    fun sendMessage(message: Any) {
        try {
            connectIQ.sendMessage(device, myApp, message) { _, _, status ->
                Log.d(TAG, "Message sent: ${status.name}")
                onStatusUpdate(StatusMessages.MESSAGE_SENT)
            }
        } catch (e: InvalidStateException) {
            Log.e(TAG, "Error sending message: ConnectIQ not in valid state", e)
            onStatusUpdate(StatusMessages.CONNECTIQ_NOT_READY)
        } catch (e: ServiceUnavailableException) {
            Log.e(TAG, "Error sending message: Service unavailable", e)
            onStatusUpdate(StatusMessages.SERVICE_UNAVAILABLE)
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error sending message", e)
            onStatusUpdate(StatusMessages.ERROR_SENDING_MESSAGE)
        }
    }

    /**
     * Unregisters from device and app events
     */
    fun unregisterForEvents() {
        try {
            Log.d(TAG, "Unregistering events for device: ${device.friendlyName}")
            connectIQ.unregisterForDeviceEvents(device)
        } catch (e: Exception) {
            Log.e(TAG, "Error unregistering events", e)
        }
    }

    /**
     * Checks if the device is currently connected
     */
    fun isDeviceConnected(): Boolean {
        return try {
            connectIQ.getDeviceStatus(device) == IQDevice.IQDeviceStatus.CONNECTED
        } catch (e: Exception) {
            Log.e(TAG, "Error checking device connection status", e)
            false
        }
    }
} 