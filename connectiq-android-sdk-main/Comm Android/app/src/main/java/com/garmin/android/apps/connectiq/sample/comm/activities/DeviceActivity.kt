/**
 * Copyright (C) 2015 Garmin International Ltd.
 * Subject to Garmin SDK License Agreement and Wearables Application Developer Agreement.
 */
package com.garmin.android.apps.connectiq.sample.comm.activities

import android.app.Activity
import android.app.AlertDialog
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Parcelable
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
//import com.garmin.android.apps.connectiq.sample.comm.MessageFactory
import com.garmin.android.apps.connectiq.sample.comm.R
import com.garmin.android.apps.connectiq.sample.comm.adapter.MessagesAdapter
import com.garmin.android.connectiq.ConnectIQ
import com.garmin.android.connectiq.IQApp
import com.garmin.android.connectiq.IQDevice
import com.garmin.android.connectiq.exception.InvalidStateException
import com.garmin.android.connectiq.exception.ServiceUnavailableException
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import android.content.pm.ResolveInfo
import android.net.Uri
import androidx.core.content.FileProvider
import android.hardware.camera2.CameraManager
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraAccessException
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import com.garmin.android.apps.connectiq.sample.comm.camera.MyCameraManager
import com.garmin.android.apps.connectiq.sample.comm.connectiq.ConnectIQManager


// TODO Add a valid store app id.
private const val STORE_APP_ID = ""

class DeviceActivity : Activity(), LifecycleOwner {
    // Implement getLifecycle() method required by LifecycleOwner
    private val lifecycleRegistry = LifecycleRegistry(this)
    override fun getLifecycle(): Lifecycle = lifecycleRegistry

    companion object {
        private const val TAG = "DeviceActivity"
        private const val EXTRA_IQ_DEVICE = "IQDevice"
        private const val COMM_WATCH_ID = "a3421feed289106a538cb9547ab12095"
        private const val CAMERA_PERMISSION_REQUEST = 100
        private const val STORAGE_PERMISSION_REQUEST = 101
        private const val CAMERA_REQUEST_CODE = 102
        private const val FILE_PROVIDER_AUTHORITY = "com.garmin.android.apps.connectiq.sample.comm.fileprovider"
        private const val FLASH_DELAY = 1000L // 1 second between flash blinks
        private const val STORE_APP_ID = ""

        private const val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = arrayOf(android.Manifest.permission.CAMERA)

        fun getIntent(context: Context, device: IQDevice?): Intent {
            val intent = Intent(context, DeviceActivity::class.java)
            intent.putExtra(EXTRA_IQ_DEVICE, device)
            return intent
        }
    }

    private var deviceStatusView: TextView? = null
    private var openAppButtonView: TextView? = null
    private lateinit var statusTextView: TextView
    private lateinit var countdownTextView: TextView
    private lateinit var cameraFlipButton: View

    private val connectIQ: ConnectIQ = ConnectIQ.getInstance()
    private lateinit var device: IQDevice
    private lateinit var myApp: IQApp

    private var appIsOpen = false

    private val openAppListener = ConnectIQ.IQOpenApplicationListener { _, _, status ->
        runOnUiThread {
            Log.d(TAG, "App status changed: ${status.name}")
        Toast.makeText(applicationContext, "App Status: " + status.name, Toast.LENGTH_SHORT).show()

        if (status == ConnectIQ.IQOpenApplicationStatus.APP_IS_ALREADY_RUNNING) {
            appIsOpen = true
            openAppButtonView?.setText(R.string.open_app_already_open)
        } else {
            appIsOpen = false
            openAppButtonView?.setText(R.string.open_app_open)
        }
    }
    }

    private var currentPhotoPath: String? = null
    private lateinit var cameraManager: MyCameraManager
    private var cameraId: String? = null
    private val handler = Handler(Looper.getMainLooper())
    private var countdownRunnable: Runnable? = null
    private var countdownSeconds = 0
    private var isCameraOpen = false
    private var pendingPhotoDelay: Int? = null

    private lateinit var viewFinder: PreviewView
    private var imageCapture: ImageCapture? = null
    private var camera: Camera? = null
    private lateinit var cameraExecutor: ExecutorService
    private var isCameraInitialized = false
    private var isCameraActive = false

    private lateinit var connectIQManager: ConnectIQManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_device)
        setupButtons()
        

        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)

        try {
            device = intent.getParcelableExtra<Parcelable>(EXTRA_IQ_DEVICE) as IQDevice
            myApp = IQApp(COMM_WATCH_ID)

            // Initialize views
            statusTextView = findViewById(R.id.status_text)
            countdownTextView = findViewById(R.id.countdown_text)  //Front cam timer
            openAppButtonView = findViewById(R.id.openapp)
            viewFinder = findViewById(R.id.viewFinder)
            cameraFlipButton = findViewById(R.id.camera_flip_button)

            // Initialize managers
            cameraManager = MyCameraManager(
                this,
                this,
                viewFinder,
                onPhotoTaken = { status ->
                    statusTextView.text = status
                    connectIQManager.sendMessage(status)
                },
                onCountdownUpdate = { seconds ->
                    runOnUiThread {
                        countdownTextView.text = if (seconds > 0) seconds.toString() else ""
                        countdownTextView.visibility = if (seconds > 0) View.VISIBLE else View.GONE
                    }
                },
                onCameraSwapEnabled = { enabled ->
                    cameraFlipButton.visibility = if (enabled) View.VISIBLE else View.GONE
                }
            )

            connectIQManager = ConnectIQManager(this, device, statusTextView) { delaySeconds ->
                if (delaySeconds == -1) {
                    // Cancel request received
                    cameraManager.cancelPhoto()
                    statusTextView.text = "Photo cancelled"
                } else if (delaySeconds > 0) {
                    startCountdown(delaySeconds)
                } else {
                    cameraManager.takePhoto()
                }
            }

            // Set up click listeners
            openAppButtonView?.setOnClickListener { connectIQManager.openApp() }
            findViewById<View>(R.id.camera_flip_button).setOnClickListener {
                cameraManager.flipCamera()
            }

            // Initialize camera
            if (allPermissionsGranted()) {
                cameraManager.startCamera()
            } else {
                ActivityCompat.requestPermissions(
                    this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS
                )
            }

            // Keep screen on for countdown
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        } catch (e: Exception) {
            Log.e(TAG, "Error in onCreate", e)
            Toast.makeText(this, "Error initializing camera: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        countdownRunnable?.let { handler.removeCallbacks(it) }
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        cameraManager.shutdown()
    }

    override fun onResume() {
        super.onResume()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        connectIQManager.registerForAppEvents()
        
        if (!cameraManager.isActive()) {
            cameraManager.startCamera()
        }
    }

    override fun onPause() {
        super.onPause()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        // Don't set isCameraActive to false here, let the lifecycle handle it
        // This allows the preview to continue when returning from background

        try {
            connectIQ.unregisterForDeviceEvents(device)
            connectIQ.unregisterForApplicationEvents(device, myApp)
        } catch (_: InvalidStateException) {
        }
        connectIQManager.unregisterForEvents()
    }

    private fun setupButtons() {
        findViewById<Button>(R.id.openapp).setOnClickListener {
            connectIQManager.openApp()
        }

        findViewById<Button>(R.id.send_test_msg_button).setOnClickListener {
            connectIQManager.sendMessage("Test Message")
        }
    }

//    private fun openStore() {
//        Toast.makeText(this, "Opening ConnectIQ Store...", Toast.LENGTH_SHORT).show()
//
//        // Send a message to open the store
//        try {
//            if (STORE_APP_ID.isBlank()) {
//                AlertDialog.Builder(this@DeviceActivity)
//                    .setTitle(R.string.missing_store_id)
//                    .setMessage(R.string.missing_store_id_message)
//                    .setPositiveButton(android.R.string.ok, null)
//                    .create()
//                    .show()
//            } else {
//                connectIQ.openStore(STORE_APP_ID)
//            }
//        } catch (_: Exception) {
//        }
//    }

    private fun startCountdown(seconds: Int) {
        cameraManager.takePhoto(seconds)
    }

    /**
     * Checks if all required permissions are granted
     * @return true if all permissions are granted, false otherwise
     */
    private fun allPermissionsGranted(): Boolean {
        return REQUIRED_PERMISSIONS.all {
            ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        when (requestCode) {
            REQUEST_CODE_PERMISSIONS -> {
                if (allPermissionsGranted()) {
                    cameraManager.startCamera()
                } else {
                    Toast.makeText(this, "Camera permission denied", Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
            else -> super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        }
    }
}