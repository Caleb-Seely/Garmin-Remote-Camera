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

class ConnectIQManager(
    private val context: Context,
    private val device: IQDevice,
    private val statusTextView: TextView,
    private val onPhotoRequest: (Int) -> Unit
) {
    companion object {
        private const val TAG = "ConnectIQManager"
        private const val COMM_WATCH_ID = "a3421feed289106a538cb9547ab12095"
    }

    private val connectIQ: ConnectIQ = ConnectIQ.getInstance()
    private val myApp: IQApp = IQApp(COMM_WATCH_ID)
    private var appIsOpen = false
    private var cameraManager: MyCameraManager? = null

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
                Log.d(TAG, "New message received: ${message}")
                
                // If we're recording, treat any message as a stop command
                if (cameraManager?.isRecording() == true) {
                    Log.d(TAG, "Recording in progress, stopping video")
                    onPhotoRequest(-1) // Use -1 to indicate stop recording
                    return@registerForAppEvents
                }

                var delaySeconds = 0
                var isVideoCommand = false

                // Message is likely a List<Any>, so we need to extract the content
                if (message != null && message.isNotEmpty()) {
                    val firstItem = message[0]

                    when (firstItem) {
                        is String -> {
                            if (firstItem.equals("cancel", ignoreCase = true)) {
                                delaySeconds = -1  // Set to -1 to indicate cancellation
                            } else if (firstItem.startsWith("video", ignoreCase = true)) {
                                isVideoCommand = true
                                // Extract number following "video" (e.g., "video 3" -> 3)
                                val delayStr = firstItem.substringAfter("VIDEO", "").trim()
                                delaySeconds = if (delayStr.isEmpty()) 0 else delayStr.toIntOrNull() ?: 0
                                Log.d(TAG, "Video command received with delay: $delaySeconds seconds")
                            } else {
                                // Try to parse as a number for countdown
                                delaySeconds = firstItem.toIntOrNull() ?: 0
                            }
                        }
                        is Number -> {
                            delaySeconds = firstItem.toInt()
                        }
                        // Add handling for other message types if needed
                    }
                }

                // Update the status text
                statusTextView.post {
                    statusTextView.text = when {
                        isVideoCommand -> {
                            if (delaySeconds > 0) {
                                "Starting video recording in $delaySeconds seconds"
                            } else {
                                "Starting video recording"
                            }
                        }
                        delaySeconds > 0 -> "Starting countdown: $delaySeconds seconds"
                        delaySeconds == -1 -> "Cancelled photo request"
                        else -> "Taking photo..."
                    }
                }

                // Call onPhotoRequest with the calculated delay
                // For video commands: use negative numbers to indicate video (-2 for immediate, -3-delay for delayed)
                onPhotoRequest(when {
                    isVideoCommand && delaySeconds == 0 -> -2  // immediate video
                    isVideoCommand -> -3 - delaySeconds  // delayed video (e.g., 3 second delay becomes -6)
                    else -> delaySeconds  // regular photo (positive or 0 for immediate)
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