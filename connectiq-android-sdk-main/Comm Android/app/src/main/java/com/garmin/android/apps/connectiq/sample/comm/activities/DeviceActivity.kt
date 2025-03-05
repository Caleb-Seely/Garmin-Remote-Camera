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
import com.garmin.android.apps.connectiq.sample.comm.MessageFactory
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

private const val TAG = "DeviceActivity"
private const val EXTRA_IQ_DEVICE = "IQDevice"
private const val COMM_WATCH_ID = "a3421feed289106a538cb9547ab12095"
private const val CAMERA_PERMISSION_REQUEST = 100
private const val STORAGE_PERMISSION_REQUEST = 101
private const val CAMERA_REQUEST_CODE = 102
private const val FILE_PROVIDER_AUTHORITY = "com.garmin.android.apps.connectiq.sample.comm.fileprovider"
private const val FLASH_DELAY = 1000L // 1 second between flash blinks

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
    private var cameraManager: CameraManager? = null
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_device)

        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)

        try {
            device = intent.getParcelableExtra<Parcelable>(EXTRA_IQ_DEVICE) as IQDevice
            myApp = IQApp(COMM_WATCH_ID)
            appIsOpen = false

            val deviceNameView = findViewById<TextView>(R.id.devicename)
            deviceStatusView = findViewById(R.id.devicestatus)
            openAppButtonView = findViewById(R.id.openapp) // Updated ID to match layout
            val openAppStoreView = findViewById<View>(R.id.openstore)
            val cameraButton = findViewById<Button>(R.id.camera_button)

            deviceNameView?.text = device.friendlyName
            deviceStatusView?.text = device.status?.name
            openAppButtonView?.setOnClickListener { openMyApp() }
            openAppStoreView?.setOnClickListener { openStore() }
            cameraButton.setOnClickListener { checkCameraPermissionAndOpen() }

            // Initialize camera manager
            cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
            try {
                cameraId = cameraManager?.cameraIdList?.firstOrNull()
                Log.d(TAG, "Camera ID initialized: $cameraId")
            } catch (e: CameraAccessException) {
                Log.e(TAG, "Error accessing camera", e)
            }

            // Keep screen on for countdown
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

            viewFinder = findViewById(R.id.viewFinder)
            cameraExecutor = Executors.newSingleThreadExecutor()

            // Initialize camera
            if (allPermissionsGranted()) {
                startCamera()
            } else {
                Log.d(TAG, "Requesting camera permissions")
                ActivityCompat.requestPermissions(
                    this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in onCreate", e)
            Toast.makeText(this, "Error initializing app: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Clean up countdown
        countdownRunnable?.let { handler.removeCallbacks(it) }
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        cameraExecutor.shutdown()
        isCameraActive = false
    }

    override fun onResume() {
        super.onResume()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        listenByDeviceEvents()
        listenByMyAppEvents()
        getMyAppStatus()
        
        // Reinitialize camera if needed
        if (!isCameraActive) {
            startCamera()
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
    }

    private fun openMyApp() {
        Toast.makeText(this, "Opening app...", Toast.LENGTH_SHORT).show()

        // Send a message to open the app
        try {
            connectIQ.openApplication(device, myApp, openAppListener)
        } catch (_: Exception) {
        }
    }

    private fun openStore() {
        Toast.makeText(this, "Opening ConnectIQ Store...", Toast.LENGTH_SHORT).show()

        // Send a message to open the store
        try {
            if (STORE_APP_ID.isBlank()) {
                AlertDialog.Builder(this@DeviceActivity)
                    .setTitle(R.string.missing_store_id)
                    .setMessage(R.string.missing_store_id_message)
                    .setPositiveButton(android.R.string.ok, null)
                    .create()
                    .show()
            } else {
                connectIQ.openStore(STORE_APP_ID)
            }
        } catch (_: Exception) {
        }
    }

    private fun listenByDeviceEvents() {
        // Get our instance of ConnectIQ. Since we initialized it
        // in our MainActivity, there is no need to do so here, we
        // can just get a reference to the one and only instance.
        try {
            connectIQ.registerForDeviceEvents(device) { _, status ->
                // Since we will only get updates for this device, just display the status
                deviceStatusView?.text = status.name
            }
        } catch (e: InvalidStateException) {
            Log.wtf(TAG, "InvalidStateException:  We should not be here!")
        }
    }

    // Let's register to receive messages from our application on the device.
    private fun listenByMyAppEvents() {
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

                if (delaySeconds > 0) {
                    // Start countdown with flash
                    startCountdown(delaySeconds)
                    Toast.makeText(this, "Starting countdown: $delaySeconds seconds", Toast.LENGTH_SHORT).show()
                } else {
                    // Ensure camera is active before taking photo
                    if (!isCameraActive) {
                        startCamera()
                    }
                    // Take photo immediately
                    takePhoto()
                }
            }
        } catch (e: InvalidStateException) {
            Log.e(TAG, "ConnectIQ is not in a valid state", e)
            Toast.makeText(this, "ConnectIQ is not in a valid state", Toast.LENGTH_SHORT).show()
        }
    }

    // Let's check the status of our application on the device.
    private fun getMyAppStatus() {
        try {
            connectIQ.getApplicationInfo(COMM_WATCH_ID, device, object :
                ConnectIQ.IQApplicationInfoListener {
                override fun onApplicationInfoReceived(app: IQApp) {
                    // This is a good thing. Now we can show our list of message options.
                    buildMessageList()
                }

                override fun onApplicationNotInstalled(applicationId: String) {
                    // The Comm widget is not installed on the device so we have
                    // to let the user know to install it.
                    AlertDialog.Builder(this@DeviceActivity)
                        .setTitle(R.string.missing_widget)
                        .setMessage(R.string.missing_widget_message)
                        .setPositiveButton(android.R.string.ok, null)
                        .create()
                        .show()
                }
            })
        } catch (_: InvalidStateException) {
        } catch (_: ServiceUnavailableException) {
        }
    }

    private fun buildMessageList() {
        val adapter = MessagesAdapter { onItemClick(it) }
        adapter.submitList(MessageFactory.getMessages(this@DeviceActivity))
        findViewById<RecyclerView>(android.R.id.list).apply {
            layoutManager = LinearLayoutManager(this@DeviceActivity)
            this.adapter = adapter
        }
    }

    private fun onItemClick(message: Any) {
        try {
            connectIQ.sendMessage(device, myApp, message) { _, _, status ->
                Toast.makeText(this@DeviceActivity, status.name, Toast.LENGTH_SHORT).show()
            }
        } catch (e: InvalidStateException) {
            Toast.makeText(this, "ConnectIQ is not in a valid state", Toast.LENGTH_SHORT).show()
        } catch (e: ServiceUnavailableException) {
            Toast.makeText(
                this,
                "ConnectIQ service is unavailable.   Is Garmin Connect Mobile installed and running?",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun checkCameraPermissionAndOpen() {
        when {
            ContextCompat.checkSelfPermission(
                this,
                android.Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED -> {
                openCamera()
            }
            shouldShowRequestPermissionRationale(android.Manifest.permission.CAMERA) -> {
                showCameraPermissionRationale()
            }
            else -> {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(android.Manifest.permission.CAMERA),
                    CAMERA_PERMISSION_REQUEST
                )
            }
        }
    }

    private fun showCameraPermissionRationale() {
        AlertDialog.Builder(this)
            .setTitle("Camera Permission Required")
            .setMessage("Camera permission is required to take photos.")
            .setPositiveButton("Grant") { _, _ ->
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(android.Manifest.permission.CAMERA),
                    CAMERA_PERMISSION_REQUEST
                )
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun openCamera() {
        try {
            // Create the File where the photo should go
            val photoFile = createImageFile()
            currentPhotoPath = photoFile.absolutePath

            // Get the content URI for the image file
            val photoURI = FileProvider.getUriForFile(
                this,
                FILE_PROVIDER_AUTHORITY,
                photoFile
            )

            // Create and start the camera intent with auto-capture flags
            val takePictureIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
                putExtra(MediaStore.EXTRA_OUTPUT, photoURI)
                putExtra("android.intent.extras.CAMERA_FACING", 0) // Use back camera
                putExtra("android.intent.extras.LENS_FACING_FRONT", 0) // Use back camera
                putExtra("android.intent.extra.quickCapture", true) // Enable quick capture
                putExtra("android.intent.extra.USE_FRONT_CAMERA", false) // Use back camera
                addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            }

            isCameraOpen = true
            startActivityForResult(takePictureIntent, CAMERA_REQUEST_CODE)
        } catch (e: Exception) {
            Log.e(TAG, "Error opening camera", e)
            Toast.makeText(this, "Error opening camera: ${e.message}", Toast.LENGTH_LONG).show()
            isCameraOpen = false
        }
    }

    private fun createImageFile(): File {
        // Create an image file name
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val imageFileName = "JPEG_${timeStamp}_"
        val storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        return File.createTempFile(
            imageFileName,  /* prefix */
            ".jpg",        /* suffix */
            storageDir     /* directory */
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        when (requestCode) {
            CAMERA_PERMISSION_REQUEST -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    openCamera()
                } else {
                    Toast.makeText(this, "Camera permission denied", Toast.LENGTH_SHORT).show()
                }
            }
            else -> super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        isCameraOpen = false

        if (requestCode == CAMERA_REQUEST_CODE) {
            if (resultCode == RESULT_OK) {
                currentPhotoPath?.let { photoPath ->
                    // Move the photo to the public Pictures directory
                    val photoFile = File(photoPath)
                    val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                    val fileName = "PHOTO_${timeStamp}.jpg"

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        // For Android 10 and above, use MediaStore
                        val contentValues = ContentValues().apply {
                            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DCIM + "/Camera")
                        }

                        val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                        uri?.let {
                            contentResolver.openOutputStream(it)?.use { outputStream ->
                                photoFile.inputStream().use { inputStream ->
                                    inputStream.copyTo(outputStream)
                                }
                            }
                            Toast.makeText(this, "Photo saved successfully!", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        // For older Android versions
                        val cameraDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM), "Camera")
                        if (!cameraDir.exists()) {
                            cameraDir.mkdirs()
                        }

                        val destFile = File(cameraDir, fileName)
                        photoFile.copyTo(destFile, overwrite = true)
                        Toast.makeText(this, "Photo saved successfully!", Toast.LENGTH_SHORT).show()
                    }

                    // Clean up the temporary file
                    photoFile.delete()

                    // Check if there's a pending photo request
                    pendingPhotoDelay?.let { delay ->
                        pendingPhotoDelay = null
                        if (delay > 0) {
                            startCountdown(delay)
                        } else {
                            takePhoto()
                        }
                    }
                }
            } else {
                Toast.makeText(this, "Photo capture cancelled or failed", Toast.LENGTH_SHORT).show()
                // Check if there's a pending photo request
                pendingPhotoDelay?.let { delay ->
                    pendingPhotoDelay = null
                    if (delay > 0) {
                        startCountdown(delay)
                    } else {
                        takePhoto()
                    }
                }
            }
        }
    }

    private fun startCountdown(seconds: Int) {
        countdownSeconds = seconds
        countdownRunnable = object : Runnable {
            override fun run() {
                if (countdownSeconds > 0) {
                    toggleFlash()
                    countdownSeconds--
                    handler.postDelayed(this, FLASH_DELAY)
                } else {
                    takePhoto()
                }
            }
        }
        handler.post(countdownRunnable!!)
    }

    private fun toggleFlash() {
        camera?.let { camera ->
            val flashMode = if (camera.cameraInfo.torchState.value == TorchState.ON) {
                ImageCapture.FLASH_MODE_OFF
            } else {
                ImageCapture.FLASH_MODE_ON
            }
            imageCapture?.flashMode = flashMode
        }
    }

    private fun takePhoto() {
        if (!isCameraInitialized || !isCameraActive) {
            Log.e(TAG, "Camera not initialized or not active")
            // Try to reinitialize the camera
            startCamera()
            return
        }

        val imageCapture = imageCapture ?: run {
            Log.e(TAG, "ImageCapture is null")
            return
        }

        try {
            // Create time stamped name and MediaStore entry
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
                .Builder(contentResolver,
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    contentValues)
                .build()

            // Take the picture
            imageCapture.takePicture(
                outputOptions,
                ContextCompat.getMainExecutor(this),
                object : ImageCapture.OnImageSavedCallback {
                    override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                        val msg = "Photo capture succeeded: ${output.savedUri}"
                        Toast.makeText(baseContext, msg, Toast.LENGTH_SHORT).show()
                        Log.d(TAG, msg)
                    }

                    override fun onError(exc: ImageCaptureException) {
                        Log.e(TAG, "Photo capture failed: ${exc.message}", exc)
                        // Don't reinitialize camera on capture error, just log it
                        // This allows the preview to continue working
                    }
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error preparing photo capture", e)
            // Don't reinitialize camera on preparation error, just log it
            // This allows the preview to continue working
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            try {
                val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

                // Preview
                val preview = Preview.Builder()
                    .setTargetRotation(viewFinder.display.rotation)
                    .build()
                    .also {
                        it.setSurfaceProvider(viewFinder.surfaceProvider)
                    }

                // ImageCapture with high quality settings
                imageCapture = ImageCapture.Builder()
                    .setTargetRotation(viewFinder.display.rotation)
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                    .setFlashMode(ImageCapture.FLASH_MODE_OFF)
                    .setTargetResolution(android.util.Size(4032, 3024))
                    .setJpegQuality(100)
                    .build()

                // Select back camera as a default
                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                try {
                    // Unbind all use cases before rebinding
                    cameraProvider.unbindAll()

                    // Bind use cases to camera
                    camera = cameraProvider.bindToLifecycle(
                        this, cameraSelector, preview, imageCapture
                    )
                    isCameraInitialized = true
                    isCameraActive = true
                    Log.d(TAG, "Camera initialized and active")
                } catch (exc: Exception) {
                    Log.e(TAG, "Use case binding failed", exc)
                    isCameraActive = false
                    // Try to recover by unbinding and retrying
                    try {
                        cameraProvider.unbindAll()
                        camera = cameraProvider.bindToLifecycle(
                            this, cameraSelector, preview, imageCapture
                        )
                        isCameraActive = true
                        Log.d(TAG, "Camera recovered after binding failure")
                    } catch (e: Exception) {
                        Log.e(TAG, "Camera recovery failed", e)
                    }
                }
            } catch (exc: Exception) {
                Log.e(TAG, "Camera initialization failed", exc)
                isCameraActive = false
            }
        }, ContextCompat.getMainExecutor(this))
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
}