/*
 * SPDX-FileCopyrightText: 2025 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.trigger

import android.os.Bundle
import androidx.preference.Preference
import androidx.preference.SwitchPreferenceCompat
import com.android.settingslib.widget.SettingsBasePreferenceFragment

import org.lineageos.settings.R
import org.lineageos.settings.utils.*

class TriggerFragment : SettingsBasePreferenceFragment(),
    Preference.OnPreferenceChangeListener {

    private lateinit var mSwitchBar: SwitchPreferenceCompat

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        addPreferencesFromResource(R.xml.trigger_preferences)

        val triggerEnabled = getInt(requireContext(), TriggerController.KEY_TRIGGER_ENABLE, 0) == 1

        mSwitchBar = findPreference<SwitchPreferenceCompat>(TriggerController.KEY_TRIGGER_ENABLE)!!.apply {
            setChecked(triggerEnabled)
            onPreferenceChangeListener = this@TriggerFragment
        }

        TriggerController.setTriggerEnabled(requireContext(), triggerEnabled)
    }

    override fun onPreferenceChange(preference: Preference, newValue: Any): Boolean {
        return when (preference.key) {
            TriggerController.KEY_TRIGGER_ENABLE -> {
                val isEnabled = newValue as Boolean
                TriggerController.setTriggerEnabled(requireContext(), isEnabled)
                true
            }

            else -> false
        }
    }

    override fun onResume() {
        super.onResume()
        val triggerEnabled = getInt(requireContext(), TriggerController.KEY_TRIGGER_ENABLE, 0) == 1
        TriggerController.setTriggerEnabled(requireContext(), triggerEnabled)
    }
}
