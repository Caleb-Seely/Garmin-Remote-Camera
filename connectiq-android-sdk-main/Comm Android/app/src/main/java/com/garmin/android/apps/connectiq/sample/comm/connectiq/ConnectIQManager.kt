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
            connectIQ.registerForAppEvents(device, myApp) { _, _, message, _ ->
                Log.d(TAG, "New message received: ${message} camera-manager: ${cameraManager.isRecording()}")
                
                if (cameraManager.isRecording()) {
                    Log.d(TAG, "Recording in progress, stopping video")
                    onPhotoRequest(-1)
                    return@registerForAppEvents
                }

                var delaySeconds = 0
                var isVideoCommand = false

                if (message != null && message.isNotEmpty()) {
                    val firstItem = message[0]

                    when (firstItem) {
                        is String -> {
                            if (firstItem.equals("cancel", ignoreCase = true)) {
                                delaySeconds = -1
                                statusTextView.post {
                                    statusTextView.text = "Cancelled request"
                                }
                                onPhotoRequest(-1)
                                return@registerForAppEvents
                            } else if (firstItem.startsWith("swap", ignoreCase = true)) {
                                val delayStr = firstItem.substringAfter("SWAP", "").trim()
                                delaySeconds = if (delayStr.isEmpty()) 0 else delayStr.toIntOrNull() ?: 0
                                Log.d(TAG, "Swap command received with delay: $delaySeconds seconds")
                                
                                // Toggle camera mode
                                if (cameraManager.isVideoMode()) {
                                    // Currently in video mode, switch to photo mode
                                    cameraManager.toggleVideoMode()
                                    statusTextView.post {
                                        statusTextView.text = "Switching to photo mode"
                                    }
                                    (context as? DeviceActivity)?.updateModeIndicator(false)
                                    
                                    if (delaySeconds > 0) {
                                        statusTextView.post {
                                            statusTextView.text = "Taking photo in $delaySeconds seconds"
                                        }
                                        onPhotoRequest(delaySeconds)
                                    } else {
                                        onPhotoRequest(0)
                                    }
                                } else {
                                    // Currently in photo mode, switch to video mode
                                    cameraManager.toggleVideoMode()
                                    statusTextView.post {
                                        statusTextView.text = "Switching to video mode"
                                    }
                                    (context as? DeviceActivity)?.updateModeIndicator(true)
                                    
                                    if (delaySeconds > 0) {
                                        statusTextView.post {
                                            statusTextView.text = "Starting recording in $delaySeconds seconds"
                                        }
                                        onPhotoRequest(-3 - delaySeconds)  // Convert to video delay format
                                    } else {
                                        onPhotoRequest(-2)  // Immediate video command
                                    }
                                }
                                return@registerForAppEvents
                            } else {
                                delaySeconds = firstItem.toIntOrNull() ?: 0
                            }
                        }
                        is Number -> {
                            delaySeconds = firstItem.toInt()
                        }
                    }
                }

                // For non-swap messages, use the current mode to determine behavior
                val isCurrentlyVideoMode = cameraManager.isVideoMode()
                
                statusTextView.post {
                    statusTextView.text = when {
                        delaySeconds == -1 -> "Cancelled request"
                        isCurrentlyVideoMode && delaySeconds > 0 -> "Starting recording in $delaySeconds seconds"
                        isCurrentlyVideoMode -> "Starting recording"
                        delaySeconds > 0 -> "Taking photo in $delaySeconds seconds"
                        else -> "Taking photo..."
                    }
                }

                // Convert delay based on current mode
                onPhotoRequest(when {
                    delaySeconds == -1 -> -1  // Cancel command
                    isCurrentlyVideoMode && delaySeconds == 0 -> -2  // Immediate video
                    isCurrentlyVideoMode -> -3 - delaySeconds  // Delayed video
                    else -> delaySeconds  // Photo (immediate or delayed)
                })
            }
        } catch (e: InvalidStateException) {
            Log.e(TAG, "ConnectIQ is not in a valid state", e)
            statusTextView.post {
                statusTextView.text = "Error: ConnectIQ not ready"
            }
        }
    }

    fun sendMessage(message: Any) {
        try {
            connectIQ.sendMessage(device, myApp, message) { _, _, status ->
                statusTextView.post {
                    statusTextView.text = "Message sent: ${status.name}"
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
        } catch (_: InvalidStateException) {
        }
    }
} 