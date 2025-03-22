package com.garmin.android.apps.connectiq.sample.comm.connectiq

import android.content.Context
import android.util.Log
import com.garmin.android.apps.connectiq.sample.comm.camera.MyCameraManager
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
    private val cameraManager: MyCameraManager,
    private val onPhotoRequest: (Int) -> Unit
) {
    companion object {
        private const val TAG = "ConnectIQManager"
        private const val COMM_WATCH_ID = "a3421feed289106a538cb9547ab12095"
    }

    private val connectIQ: ConnectIQ = ConnectIQ.getInstance()
    private val myApp: IQApp = IQApp(COMM_WATCH_ID)
    private var appIsOpen = false

    private val openAppListener = ConnectIQ.IQOpenApplicationListener { _, _, status ->
        appIsOpen = status == ConnectIQ.IQOpenApplicationStatus.APP_IS_ALREADY_RUNNING
        Log.d(TAG, "App open status: $appIsOpen")
    }

    /**
     * Opens the ConnectIQ app on the device
     */
    fun openApp() {
        Log.d(TAG, "Opening app on device: ${device.friendlyName}")
        try {
            connectIQ.openApplication(device, myApp, openAppListener)
        } catch (e: InvalidStateException) {
            Log.e(TAG, "Error opening app: ConnectIQ not in valid state", e)
            onStatusUpdate("Error: ConnectIQ not ready")
        } catch (e: ServiceUnavailableException) {
            Log.e(TAG, "Error opening app: Service unavailable", e)
            onStatusUpdate("Error: ConnectIQ service unavailable")
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error opening app", e)
            onStatusUpdate("Error opening app: ${e.message}")
        }
    }

    /**
     * Registers for device and app events
     */
    fun registerForAppEvents() {
        try {
            // Register for device status updates
            connectIQ.registerForDeviceEvents(device) { device, status ->
                Log.d(TAG, "Device status changed: ${status.name}")
                onConnectionUpdate(status == IQDevice.IQDeviceStatus.CONNECTED)
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
            onStatusUpdate("Error: ConnectIQ not ready")
        } catch (e: Exception) {
            Log.e(TAG, "Error registering for app events", e)
            onStatusUpdate("Error: Failed to connect to device")
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
                onStatusUpdate("MODE_SWAP")
                
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
            requestCode == -1 -> "Cancelled request"
            requestCode == -100 -> "Swapping to video mode"
            requestCode < -100 -> {
                val delay = -(requestCode + 100)
                "Swapping to video mode, recording in $delay seconds"
            }
            requestCode == 100 -> "Swapping to photo mode"
            requestCode > 100 -> {
                val delay = requestCode - 100
                "Swapping to photo mode, photo in $delay seconds"
            }
            requestCode == -2 -> "Starting recording"
            requestCode < -2 -> {
                val delay = -(requestCode + 3)
                "Starting recording in $delay seconds"
            }
            requestCode > 0 -> "Taking photo in $requestCode seconds"
            else -> "Taking photo..."
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
            }
        } catch (e: InvalidStateException) {
            Log.e(TAG, "Error sending message: ConnectIQ not in valid state", e)
            onStatusUpdate("Error: ConnectIQ not ready")
        } catch (e: ServiceUnavailableException) {
            Log.e(TAG, "Error sending message: Service unavailable", e)
            onStatusUpdate("Error: Service unavailable")
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error sending message", e)
            onStatusUpdate("Error sending message: ${e.message}")
        }
    }

    /**
     * Unregisters from device and app events
     */
    fun unregisterForEvents() {
        try {
            connectIQ.unregisterForApplicationEvents(device, myApp)
            connectIQ.unregisterForDeviceEvents(device)
        } catch (e: InvalidStateException) {
            Log.e(TAG, "Error unregistering for events", e)
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error unregistering for events", e)
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