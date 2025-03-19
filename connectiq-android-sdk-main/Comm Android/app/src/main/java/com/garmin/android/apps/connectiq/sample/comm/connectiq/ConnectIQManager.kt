package com.garmin.android.apps.connectiq.sample.comm.connectiq

import android.content.Context
import android.util.Log
import android.widget.TextView
import android.widget.Toast
import com.garmin.android.apps.connectiq.sample.comm.camera.MyCameraManager
import com.garmin.android.apps.connectiq.sample.comm.camera.MyCameraManager.Companion
import com.garmin.android.connectiq.ConnectIQ
import com.garmin.android.connectiq.IQApp
import com.garmin.android.connectiq.IQDevice
import com.garmin.android.connectiq.exception.InvalidStateException
import com.garmin.android.connectiq.exception.ServiceUnavailableException
import com.garmin.android.apps.connectiq.sample.comm.activities.DeviceActivity

class ConnectIQManager(
    private val context: Context,
    private val device: IQDevice,
    private val statusTextView: TextView,
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
        if (status == ConnectIQ.IQOpenApplicationStatus.APP_IS_ALREADY_RUNNING) {
            appIsOpen = true
        } else {
            appIsOpen = false
        }
    }

    fun openApp() {
        Toast.makeText(context, "Opening app...", Toast.LENGTH_SHORT).show()
        try {
            connectIQ.openApplication(device, myApp, openAppListener)
        } catch (e: Exception) {
            Log.e(TAG, "Error opening app", e)
        }
    }

    fun registerForAppEvents() {
        try {
            // Register for device status updates
            connectIQ.registerForDeviceEvents(device) { device, status ->
                Log.d(TAG, "Device status changed: ${status.name}")
                // Update action bar title color based on connection status
                (context as? DeviceActivity)?.updateActionBarTitle(status == IQDevice.IQDeviceStatus.CONNECTED)
            }

            connectIQ.registerForAppEvents(device, myApp) { _, _, message, _ ->
                Log.d(TAG, "New message received: ${message} camera-manager: ${cameraManager.isRecording()}")

                // If recording is in progress, stop it regardless of the message
                if (cameraManager.isRecording()) {
                    Log.d(TAG, "Recording in progress, stopping video")
                    //I don't think we need to update status text here
//                    statusTextView.post {
//                        statusTextView.text = "Stopping recording"
//                    }
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
            statusTextView.post {
                statusTextView.text = "Error: ConnectIQ not ready"
            }
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

                // Toggle the camera mode
                val isCurrentlyVideoMode = cameraManager.isVideoMode()
                cameraManager.toggleVideoMode()
                (context as? DeviceActivity)?.updateModeIndicator(!isCurrentlyVideoMode)

                // After swap, use proper request code based on new mode and delay
                return if (!isCurrentlyVideoMode) {
                    // Switched to video mode
                    if (delay > 0) -3 - delay else -2
                } else {
                    // Switched to photo mode
                    if (delay > 0) delay else 0
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
     * Update the status text based on the request code
     */
    private fun updateStatusText(requestCode: Int) {
        statusTextView.post {
            statusTextView.text = when {
                requestCode == -1 -> "Cancelled request"
                requestCode == -2 -> "Starting recording"
                requestCode < -2 -> {
                    val delay = -(requestCode + 3)
                    "Starting recording in $delay seconds"
                }
                requestCode > 0 -> "Taking photo in $requestCode seconds"
                else -> "Taking photo..."
            }
        }
    }

    fun sendMessage(message: Any) {
        try {
            connectIQ.sendMessage(device, myApp, message) { _, _, status ->
                statusTextView.post {
                    Log.d(TAG, "Message sent: ${status.name}")
                }
            }
        } catch (e: InvalidStateException) {
            statusTextView.post {
                statusTextView.text = "Error: ConnectIQ not ready"
            }
        } catch (e: ServiceUnavailableException) {
            statusTextView.post {
                statusTextView.text = "Error: Service unavailable"
            }
        }
    }

    fun unregisterForEvents() {
        try {
            connectIQ.unregisterForApplicationEvents(device, myApp)
            connectIQ.unregisterForDeviceEvents(device)
        } catch (_: InvalidStateException) {
        }
    }

    fun isDeviceConnected(): Boolean {
        return try {
            connectIQ.getDeviceStatus(device) == IQDevice.IQDeviceStatus.CONNECTED
        } catch (e: Exception) {
            false
        }
    }
} 