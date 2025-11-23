/*
 * SPDX-FileCopyrightText: 2025 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.fan

import android.os.Bundle
import androidx.preference.Preference
import androidx.preference.SeekBarPreference
import com.android.settingslib.widget.MainSwitchPreference
import com.android.settingslib.widget.SettingsBasePreferenceFragment

import org.lineageos.settings.R
import org.lineageos.settings.utils.*

class FanFragment : SettingsBasePreferenceFragment(),
    Preference.OnPreferenceChangeListener {

    private lateinit var mSwitchBar: MainSwitchPreference
    private lateinit var mFanSpeedBar: SeekBarPreference

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        addPreferencesFromResource(R.xml.fan_preferences)

        val fanEnabled = getInt(requireContext(), FanController.KEY_FAN_ENABLE, 0) == 1
        val savedSpeed = getInt(requireContext(), FanController.KEY_FAN_SPEED, FanController.FAN_DEFAULT_SPEED)

        mSwitchBar = findPreference<MainSwitchPreference>(FanController.KEY_FAN_ENABLE)!!.apply {
            setChecked(fanEnabled)
            onPreferenceChangeListener = this@FanFragment
        }

        mFanSpeedBar = findPreference<SeekBarPreference>(FanController.KEY_FAN_SPEED)!!.apply {
            value = savedSpeed
            min = FanController.FAN_MIN_SPEED
            max = FanController.FAN_MAX_SPEED
            seekBarIncrement = 1
            showSeekBarValue = true
            isEnabled = fanEnabled
            onPreferenceChangeListener = this@FanFragment
        }

        FanController.applySettings(requireContext(), fanEnabled, savedSpeed)
    }

    override fun onPreferenceChange(preference: Preference, newValue: Any): Boolean {
        return when (preference.key) {
            FanController.KEY_FAN_ENABLE -> {
                val isEnabled = newValue as Boolean
                val speed = getInt(requireContext(), FanController.KEY_FAN_SPEED, FanController.FAN_DEFAULT_SPEED)

                mFanSpeedBar.isEnabled = isEnabled
                FanController.applySettings(requireContext(), isEnabled, speed)
                true
            }

            FanController.KEY_FAN_SPEED -> {
                val speed = newValue as Int

                FanController.setFanSpeed(requireContext(), speed)
                true
            }

            else -> false
        }
    }

    override fun onResume() {
        super.onResume()

        FanController.restoreSettings(requireContext())
    }
}
