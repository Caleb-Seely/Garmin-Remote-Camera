package com.garmin.android.apps.connectiq.sample.comm.connectiq

import android.content.Context
import android.util.Log
import android.widget.TextView
import android.widget.Toast
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
                // Parse the message to get delay (if any)
                var delaySeconds = 0
                if (message.isNotEmpty()) {
                    try {
                        delaySeconds = message[0].toString().toIntOrNull() ?: 0
                    } catch (e: Exception) {
                        Log.e(TAG, "Error parsing delay from message", e)
                    }
                }

                statusTextView.post {
                    statusTextView.text = if (delaySeconds > 0) {
                        "Starting countdown: $delaySeconds seconds"
                    } else {
                        "Taking photo..."
                    }
                }

                onPhotoRequest(delaySeconds)
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