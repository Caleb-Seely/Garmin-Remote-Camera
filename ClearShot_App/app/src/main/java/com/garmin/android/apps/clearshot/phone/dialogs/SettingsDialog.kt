package com.garmin.android.apps.clearshot.phone.dialogs

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.Switch
import android.widget.TextView
import com.garmin.android.apps.clearshot.phone.R
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class SettingsDialog : BottomSheetDialogFragment() {
    private var onAspectRatioChanged: ((Boolean) -> Unit)? = null
    private var onFlashChanged: ((Boolean) -> Unit)? = null
    private var is16_9 = false
    private var isFlashEnabled = false
    private var isVideoMode = false

    fun setInitialAspectRatio(is16_9: Boolean) {
        this.is16_9 = is16_9
    }

    fun setInitialFlashState(isEnabled: Boolean) {
        this.isFlashEnabled = isEnabled
    }

    fun setVideoMode(isVideoMode: Boolean) {
        this.isVideoMode = isVideoMode
    }

    fun setOnAspectRatioChangedListener(listener: (Boolean) -> Unit) {
        onAspectRatioChanged = listener
    }

    fun setOnFlashChangedListener(listener: (Boolean) -> Unit) {
        onFlashChanged = listener
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.bottom_sheet_settings, container, false)

        val aspectRatioGroup = view.findViewById<RadioGroup>(R.id.aspect_ratio_group)
        // Set the initial checked state based on saved preference
        if (is16_9) {
            aspectRatioGroup.check(R.id.aspect_ratio_16_9)
        } else {
            aspectRatioGroup.check(R.id.aspect_ratio_4_3)
        }
        
        // Disable aspect ratio controls in video mode
        val aspectRatioLabel = view.findViewById<TextView>(R.id.aspect_ratio_label)
        val radio4_3 = view.findViewById<RadioButton>(R.id.aspect_ratio_4_3)
        val radio16_9 = view.findViewById<RadioButton>(R.id.aspect_ratio_16_9)
        
        if (isVideoMode) {
            aspectRatioGroup.isEnabled = false
            aspectRatioLabel.alpha = 0.5f
            radio4_3.isEnabled = false
            radio16_9.isEnabled = false
            radio4_3.alpha = 0.5f
            radio16_9.alpha = 0.5f
        }
        
        aspectRatioGroup.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                R.id.aspect_ratio_4_3 -> onAspectRatioChanged?.invoke(false)
                R.id.aspect_ratio_16_9 -> onAspectRatioChanged?.invoke(true)
            }
        }

        val flashToggle = view.findViewById<Switch>(R.id.flash_toggle_button)
        flashToggle.isChecked = isFlashEnabled
        flashToggle.setOnCheckedChangeListener { _, isChecked ->
            onFlashChanged?.invoke(isChecked)
        }

        return view
    }

    companion object {
        const val TAG = "SettingsDialog"
    }
} 