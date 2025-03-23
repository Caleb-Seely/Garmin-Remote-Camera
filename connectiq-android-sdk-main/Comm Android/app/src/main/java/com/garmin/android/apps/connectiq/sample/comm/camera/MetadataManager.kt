package com.garmin.android.apps.connectiq.sample.comm.camera

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.provider.MediaStore
import android.view.Surface
import android.view.WindowManager
import android.view.OrientationEventListener
import androidx.camera.core.ImageCapture
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.garmin.android.apps.connectiq.sample.comm.camera.CameraLogger.METADATA_MANAGER
import kotlin.math.roundToInt
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Manages metadata operations for camera captures including device orientation
 * and location data.
 */
class MetadataManager(private val context: Context) {
    companion object {
        private const val LOCATION_EXPIRATION_MS = 300000 // 5 minutes
    }

    private val locationManager: LocationManager? by lazy {
        context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
    }

    private val windowManager: WindowManager by lazy {
        context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    }

    // Track the last known device orientation
    private var lastDeviceOrientation = 0

    // Create an OrientationEventListener to track physical device orientation
    private val orientationListener = object : OrientationEventListener(context) {
        override fun onOrientationChanged(orientation: Int) {
            // orientation == -1 means the device is flat or the orientation is unknown
            if (orientation == -1) return
            
            // Convert the raw orientation value to one of the four EXIF orientation values
            val newOrientation = when {
                orientation >= 315 || orientation < 45 -> 0        // Portrait (upright)
                orientation >= 45 && orientation < 135 -> 90       // Landscape right
                orientation >= 135 && orientation < 225 -> 180     // Portrait upside down
                orientation >= 225 && orientation < 315 -> 270     // Landscape left
                else -> lastDeviceOrientation                      // Default to last known orientation
            }

            if (newOrientation != lastDeviceOrientation) {
                CameraLogger.d(METADATA_MANAGER, 
                    "Physical device orientation changed from $lastDeviceOrientation to $newOrientation (raw angle: $orientation)", 
                    this)
                lastDeviceOrientation = newOrientation
            }
        }
    }

    init {
        // Start monitoring orientation changes if the sensor is available
        if (orientationListener.canDetectOrientation()) {
            orientationListener.enable()
            CameraLogger.d(METADATA_MANAGER, "Orientation listener enabled", this)
        } else {
            CameraLogger.d(METADATA_MANAGER, "Cannot detect orientation changes", this)
        }
    }

    /**
     * Gets the current device's physical orientation in degrees (0, 90, 180, 270)
     * This returns the actual device orientation regardless of screen rotation settings
     * @return orientation in degrees
     */
    private fun getDeviceOrientation(): Int {
        return lastDeviceOrientation
    }

    /**
     * Public method to get the current device orientation
     * @return Current device orientation in degrees (0, 90, 180, 270)
     */
    fun getCurrentDeviceOrientation(): Int {
        return lastDeviceOrientation
    }

    /**
     * Checks if location permissions are granted
     * @return true if location permissions are granted
     */
    fun hasLocationPermission(): Boolean {
        val hasFineLocation = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val hasCoarseLocation = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        CameraLogger.d(METADATA_MANAGER, 
            "Location permissions - Fine: $hasFineLocation, Coarse: $hasCoarseLocation", this)
        return hasFineLocation || hasCoarseLocation
    }

    /**
     * Gets the last known location if available and permissions are granted
     * @return Location object or null if location is unavailable
     */
    fun getLastKnownLocation(): Location? {
        if (!hasLocationPermission()) {
            CameraLogger.d(METADATA_MANAGER, "Location permission not granted", this)
            return null
        }

        val manager = locationManager ?: run {
            CameraLogger.d(METADATA_MANAGER, "Location manager not available", this)
            return null
        }

        try {
            val providers = manager.getProviders(true)
            CameraLogger.d(METADATA_MANAGER, "Available location providers: $providers", this)
            
            var bestLocation: Location? = null

            // Try to get location from available providers
            for (provider in providers) {
                val location = manager.getLastKnownLocation(provider) ?: continue
                CameraLogger.d(METADATA_MANAGER, 
                    "Got location from provider $provider: Lat ${location.latitude}, Lon ${location.longitude}, Time: ${location.time}, Age: ${System.currentTimeMillis() - location.time}ms", this)

                // Use this location if it's the first one or better than previous best
                if (bestLocation == null || location.accuracy < bestLocation.accuracy) {
                    bestLocation = location
                }
            }

            if (bestLocation != null) {
                CameraLogger.d(METADATA_MANAGER, 
                    "Selected best location: Lat ${bestLocation.latitude}, Lon ${bestLocation.longitude}, Accuracy: ${bestLocation.accuracy}m, Age: ${System.currentTimeMillis() - bestLocation.time}ms", this)
            } else {
                CameraLogger.d(METADATA_MANAGER, "No valid location found", this)
            }

            return bestLocation
        } catch (e: SecurityException) {
            CameraLogger.e(METADATA_MANAGER, "Error accessing location", e, this)
            return null
        } catch (e: Exception) {
            CameraLogger.e(METADATA_MANAGER, "Unexpected error getting location", e, this)
            return null
        }
    }

    /**
     * Adds metadata to ContentValues for image capture
     * @param contentValues The ContentValues to update with metadata
     */
    fun addMetadataToContentValues(contentValues: ContentValues) {
        // Add orientation metadata
        val orientation = getDeviceOrientation()
        contentValues.put(MediaStore.Images.Media.ORIENTATION, orientation)
        CameraLogger.d(METADATA_MANAGER, "Adding orientation metadata: $orientation", this)

        // Add date taken metadata
        val dateTaken = System.currentTimeMillis()
        contentValues.put(MediaStore.Images.Media.DATE_TAKEN, dateTaken)
        CameraLogger.d(METADATA_MANAGER, "Adding date taken metadata: $dateTaken", this)

    }

    /**
     * Creates and returns an ImageCapture.Metadata object with all available metadata
     * @return ImageCapture.Metadata object with location data
     */
    fun createImageMetadata(): ImageCapture.Metadata {
        val metadata = ImageCapture.Metadata()
        
        // Set the device orientation
        val orientation = getDeviceOrientation()
        metadata.isReversedHorizontal = false
        metadata.isReversedVertical = false
        
        // Log the orientation being added to metadata
        CameraLogger.d(METADATA_MANAGER, 
            "Adding orientation to ImageCapture.Metadata: $orientation", this)

        // Set location if available
        if (hasLocationPermission()) {
            getLastKnownLocation()?.let { loc ->
                // This is the proper way to set location in EXIF data
                metadata.location = android.location.Location("").apply {
                    latitude = loc.latitude
                    longitude = loc.longitude
                    altitude = loc.altitude
                    accuracy = loc.accuracy
                    time = loc.time
                }
                CameraLogger.d(METADATA_MANAGER, 
                    "Adding location metadata to ImageCapture.Metadata: Lat ${loc.latitude}, Lon ${loc.longitude}", this)
            }
        } else {
            CameraLogger.d(METADATA_MANAGER, "Location permission not granted, skipping location metadata", this)
        }

        return metadata
    }
    
    /**
     * Cleans up resources when the manager is no longer needed
     */
    fun cleanup() {
        orientationListener.disable()
        CameraLogger.d(METADATA_MANAGER, "Orientation listener disabled", this)
    }
} 