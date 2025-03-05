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
import android.graphics.Bitmap
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
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
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
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*
import android.content.pm.ResolveInfo


private const val TAG = "DeviceActivity"
private const val EXTRA_IQ_DEVICE = "IQDevice"
private const val COMM_WATCH_ID = "a3421feed289106a538cb9547ab12095"
private const val CAMERA_PERMISSION_REQUEST = 100
private const val STORAGE_PERMISSION_REQUEST = 101
private const val CAMERA_REQUEST_CODE = 102

// TODO Add a valid store app id.
private const val STORE_APP_ID = ""

class DeviceActivity : Activity() {

    companion object {
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
        Toast.makeText(applicationContext, "App Status: " + status.name, Toast.LENGTH_SHORT).show()

        if (status == ConnectIQ.IQOpenApplicationStatus.APP_IS_ALREADY_RUNNING) {
            appIsOpen = true
            openAppButtonView?.setText(R.string.open_app_already_open)
        } else {
            appIsOpen = false
            openAppButtonView?.setText(R.string.open_app_open)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_device)

        device = intent.getParcelableExtra<Parcelable>(EXTRA_IQ_DEVICE) as IQDevice
        myApp = IQApp(COMM_WATCH_ID)
        appIsOpen = false

        val deviceNameView = findViewById<TextView>(R.id.devicename)
        deviceStatusView = findViewById(R.id.devicestatus)
        openAppButtonView = findViewById(R.id.openapp)
        val openAppStoreView = findViewById<View>(R.id.openstore)
        val cameraButton = findViewById<Button>(R.id.camera_button)

        deviceNameView?.text = device.friendlyName
        deviceStatusView?.text = device.status?.name
        openAppButtonView?.setOnClickListener { openMyApp() }
        openAppStoreView?.setOnClickListener { openStore() }
        cameraButton.setOnClickListener { checkCameraPermissionAndOpen() }

        printCameraAppDetails()
    }

    public override fun onResume() {
        super.onResume()
        listenByDeviceEvents()
        listenByMyAppEvents()
        getMyAppStatus()
    }

    public override fun onPause() {
        super.onPause()

        // It is a good idea to unregister everything and shut things down to
        // release resources and prevent unwanted callbacks.
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
                // We know from our Comm sample widget that it will only ever send us strings, but in case
                // we get something else, we are simply going to do a toString() on each object in the
                // message list.
                val builder = StringBuilder()
                if (message.size > 0) {
                    for (o in message) {
                        builder.append(o.toString())
                        builder.append("\r\n")
                    }
                } else {
                    builder.append("Received an empty message from the application")
                }

                AlertDialog.Builder(this@DeviceActivity)
                    .setTitle(R.string.received_message)
                    .setMessage(builder.toString())
                    .setPositiveButton(android.R.string.ok, null)
                    .create()
                    .show()
            }
        } catch (e: InvalidStateException) {
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
            // Create multiple camera intents
            val intents = listOf(
                Intent(MediaStore.ACTION_IMAGE_CAPTURE),
                Intent("android.media.action.STILL_IMAGE_CAMERA"),
                Intent("android.intent.action.CAMERA_BUTTON")
            )

            // Possible camera packages
            val cameraPackages = listOf(
                "com.google.android.GoogleCamera",
                "com.android.camera2",
                "com.google.pixel.camera.services",
                "com.android.cameraextensions",
                "com.google.android.apps.camera.services"
            )

            // Try each intent with package combinations
            for (intent in intents) {
                // First, try without specifying a package
                val standardResolve = packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
                if (standardResolve != null) {
                    Log.d(TAG, "Standard camera intent resolved: ${standardResolve.activityInfo?.packageName ?: "Unknown Package"}")
                    startActivityForResult(intent, CAMERA_REQUEST_CODE)
                    return
                }

                // Then try with specific packages
                for (pkg in cameraPackages) {
                    val packagedIntent = Intent(intent).apply {
                        setPackage(pkg)
                    }

                    val resolveInfo = packageManager.resolveActivity(packagedIntent, PackageManager.MATCH_DEFAULT_ONLY)
                    if (resolveInfo != null) {
                        Log.d(TAG, "Camera intent resolved with package: $pkg")
                        try {
                            startActivityForResult(packagedIntent, CAMERA_REQUEST_CODE)
                            return
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to start camera intent with package $pkg", e)
                        }
                    }
                }
            }

            // Last resort: direct camera capture
            val directCaptureIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
            startActivityForResult(directCaptureIntent, CAMERA_REQUEST_CODE)
        } catch (e: Exception) {
            Log.e(TAG, "Comprehensive camera intent error", e)
            Toast.makeText(this, "Error opening camera: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }

    // Diagnostic method to print camera app details
    private fun printCameraAppDetails() {
        val cameraApps = packageManager.getInstalledApplications(0)
            .filter { it.packageName.contains("camera", ignoreCase = true) }

        Log.d(TAG, "Camera-related apps:")
        cameraApps.forEach { app ->
            try {
                Log.d(TAG, "Package: ${app.packageName}")
                Log.d(TAG, "Name: ${packageManager.getApplicationLabel(app)}")
            } catch (e: Exception) {
                Log.e(TAG, "Error getting app details for ${app.packageName}", e)
            }
        }

        // Check intent resolution
        val cameraIntents = listOf(
            Intent(MediaStore.ACTION_IMAGE_CAPTURE),
            Intent("android.media.action.STILL_IMAGE_CAMERA"),
            Intent("android.intent.action.CAMERA_BUTTON")
        )

        cameraIntents.forEach { intent ->
            val resolveInfoList = packageManager.queryIntentActivities(intent, PackageManager.MATCH_ALL)
            Log.d(TAG, "Intent: ${intent.action}")
            Log.d(TAG, "Resolved Activities: ${resolveInfoList.size}")

            resolveInfoList.forEach { resolveInfo ->
                Log.d(TAG, "  Package: ${resolveInfo.activityInfo?.packageName}")
                Log.d(TAG, "  Class: ${resolveInfo.activityInfo?.name}")
            }
        }
    }

    private fun savePhoto(bitmap: Bitmap) {
        try {
            // Create file name with timestamp
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val fileName = "PHOTO_${timeStamp}.jpg"

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Save using MediaStore (Android 10 and above)
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES)
                }

                val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                uri?.let {
                    contentResolver.openOutputStream(it)?.use { outputStream ->
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream)
                    }
                    Toast.makeText(this, "Photo saved successfully!", Toast.LENGTH_SHORT).show()
                }
            } else {
                // Legacy storage for older Android versions
                val imagesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                val image = File(imagesDir, fileName)
                FileOutputStream(image).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 100, out)
                }
                Toast.makeText(this, "Photo saved successfully!", Toast.LENGTH_SHORT).show()
            }
        } catch (e: IOException) {
            e.printStackTrace()
            Toast.makeText(this, "Failed to save photo", Toast.LENGTH_SHORT).show()
        }
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
        if (requestCode == CAMERA_REQUEST_CODE && resultCode == RESULT_OK) {
            val imageBitmap = data?.extras?.get("data") as? Bitmap
            if (imageBitmap != null) {
                savePhoto(imageBitmap)
            } else {
                Toast.makeText(this, "Failed to capture photo", Toast.LENGTH_SHORT).show()
            }
        }
    }
}