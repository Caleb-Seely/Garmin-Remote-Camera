package com.garmin.android.apps.clearshot.phone.dialogs

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.Switch
import com.garmin.android.apps.clearshot.phone.R
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class SettingsDialog : BottomSheetDialogFragment() {
    private var onAspectRatioChanged: ((Boolean) -> Unit)? = null
    private var onFlashChanged: ((Boolean) -> Unit)? = null
    private var is16_9: Boolean = false
    private var isFlashEnabled: Boolean = false

    fun setOnAspectRatioChangedListener(listener: (Boolean) -> Unit) {
        onAspectRatioChanged = listener
    }

    fun setOnFlashChangedListener(listener: (Boolean) -> Unit) {
        onFlashChanged = listener
    }

    fun setInitialAspectRatio(is16_9: Boolean) {
        this.is16_9 = is16_9
    }

    fun setInitialFlashState(isEnabled: Boolean) {
        this.isFlashEnabled = isEnabled
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.bottom_sheet_settings, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val aspectRatioGroup = view.findViewById<RadioGroup>(R.id.aspect_ratio_group)
        // Set the initial checked state based on saved preference
        if (is16_9) {
            aspectRatioGroup.check(R.id.aspect_ratio_16_9)
        } else {
            aspectRatioGroup.check(R.id.aspect_ratio_4_3)
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
    }

    companion object {
        const val TAG = "SettingsDialog"
    }
} 