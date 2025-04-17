package com.garmin.android.apps.clearshot.phone.ui

import android.util.Log
import android.view.View
import android.widget.LinearLayout
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.camera.view.PreviewView
import com.garmin.android.apps.clearshot.phone.R

/**
 * Manages the positioning and layout of the bottom control bar based on aspect ratio.
 */
class BottomControlBarManager(
    private val bottomControlBar: LinearLayout,
    private val viewFinder: PreviewView,
    private val deviceTitle: View
) {
    companion object {
        private const val TAG = "BottomControlBarManager"
    }

    private var currentAspectRatio: Boolean? = null

    /**
     * Updates the position of the bottom control bar and PreviewView constraints based on the aspect ratio.
     * @param is16_9 True if the aspect ratio is 16:9, false for 4:3
     */
    fun updatePosition(is16_9: Boolean) {
        // Only update if the aspect ratio has actually changed
        if (currentAspectRatio == is16_9) {
            Log.d(TAG, "Aspect ratio unchanged, skipping update")
            return
        }

        currentAspectRatio = is16_9
        
        // Get the root ConstraintLayout
        val rootLayout = viewFinder.parent as? ConstraintLayout
        if (rootLayout == null) {
            Log.e(TAG, "Root layout is not a ConstraintLayout")
            return
        }

        // Create and apply new constraints
        val constraintSet = ConstraintSet()
        constraintSet.clone(rootLayout)

        // Update PreviewView scale type
        viewFinder.scaleType = if (is16_9) {
            PreviewView.ScaleType.FIT_START
        } else {
            PreviewView.ScaleType.FIT_CENTER
        }

        // Update PreviewView constraints
        if (is16_9) {
            // 16:9 - PreviewView fills screen
            constraintSet.connect(
                viewFinder.id,
                ConstraintSet.BOTTOM,
                ConstraintSet.PARENT_ID,
                ConstraintSet.BOTTOM
            )
        } else {
            // 4:3 - PreviewView ends at bottom control bar
            constraintSet.connect(
                viewFinder.id,
                ConstraintSet.BOTTOM,
                bottomControlBar.id,
                ConstraintSet.TOP
            )
        }

        // Apply the constraints
        constraintSet.applyTo(rootLayout)
        
        // Request layout update
        viewFinder.requestLayout()
        bottomControlBar.requestLayout()
    }
} 