package com.garmin.android.apps.clearshot.phone.utils

import android.animation.ObjectAnimator
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.view.View
import android.widget.ImageButton

/**
 * Utility class to handle icon rotation based on device orientation.
 * Follows the singleton pattern to ensure consistent rotation behavior across the app.
 */
object IconRotationManager : SensorEventListener {
    private const val TAG = "IconRotationManager"
    private var targetViews = mutableListOf<View>()
    private var sensorManager: SensorManager? = null
    private var lastUpdate = 0L
    private val ROTATION_THRESHOLD = 1000 // Minimum time between rotations in milliseconds
    private var currentOrientation = Orientation.PORTRAIT
    private var currentAnimators = mutableListOf<ObjectAnimator>()

    private enum class Orientation {
        PORTRAIT,
        LANDSCAPE_RIGHT,
        LANDSCAPE_LEFT
    }

    fun startRotationUpdates(context: Context, views: List<View>) {
        targetViews.clear()
        targetViews.addAll(views)
        sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        
        val accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        if (accelerometer != null) {
            sensorManager?.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL)
        }
    }

    fun stopRotationUpdates() {
        currentAnimators.forEach { it.cancel() }
        currentAnimators.clear()
        sensorManager?.unregisterListener(this)
        targetViews.clear()
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_ACCELEROMETER) return

        val currentTime = System.currentTimeMillis()
        if (currentTime - lastUpdate < ROTATION_THRESHOLD) return
        lastUpdate = currentTime

        val x = event.values[0]
        val y = event.values[1]
        
        // Calculate the angle between the device and the ground
        val angle = Math.toDegrees(Math.atan2(x.toDouble(), y.toDouble())).toFloat()
        
        // Determine the new orientation
        val newOrientation = when {
            angle < -60 && angle > -120 -> Orientation.LANDSCAPE_RIGHT
            angle > 60 && angle < 120 -> Orientation.LANDSCAPE_LEFT
            else -> Orientation.PORTRAIT
        }
        
        // Only update if the orientation has changed
        if (newOrientation != currentOrientation) {
            currentOrientation = newOrientation
            
            // Cancel any ongoing animations
            currentAnimators.forEach { it.cancel() }
            currentAnimators.clear()
            
            // Calculate target rotation
            val targetRotation = when (newOrientation) {
                Orientation.LANDSCAPE_RIGHT -> -90f
                Orientation.LANDSCAPE_LEFT -> 90f
                Orientation.PORTRAIT -> 0f
            }
            
            // Animate all views
            targetViews.forEach { view ->
                val animator = ObjectAnimator.ofFloat(view, "rotation", view.rotation, targetRotation).apply {
                    duration = 300
                    start()
                }
                currentAnimators.add(animator)
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Not needed for this implementation
    }
} 