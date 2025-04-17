package com.garmin.android.apps.clearshot.phone.ui

import android.util.Log
import android.view.View
import android.widget.LinearLayout
import androidx.constraintlayout.widget.ConstraintLayout
import com.garmin.android.apps.clearshot.phone.R

/**
 * Manages the positioning and layout of the bottom control bar based on aspect ratio.
 */
class BottomControlBarManager(
    private val bottomControlBar: LinearLayout,
    private val viewFinder: View,
    private val deviceTitle: View
) {
    companion object {
        private const val TAG = "BottomControlBarManager"
    }

    private var currentAspectRatio: Boolean? = null

    /**
     * Updates the position of the bottom control bar based on the aspect ratio.
     * @param is16_9 True if the aspect ratio is 16:9, false for 4:3
     */
    fun updatePosition(is16_9: Boolean) {
        // Only update if the aspect ratio has actually changed
        if (currentAspectRatio == is16_9) {
            Log.d(TAG, "Aspect ratio unchanged, skipping update")
            return
        }

        

        bottomControlBar.requestLayout()
    }
} 