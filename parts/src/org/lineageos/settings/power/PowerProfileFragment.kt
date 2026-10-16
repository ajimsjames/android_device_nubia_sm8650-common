/*
 * SPDX-FileCopyrightText: 2025-2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.power

import android.os.Bundle
import androidx.preference.Preference
import com.android.settingslib.widget.SelectorWithWidgetPreference
import com.android.settingslib.widget.SettingsBasePreferenceFragment
import org.lineageos.settings.R

class PowerProfileFragment : SettingsBasePreferenceFragment(),
    Preference.OnPreferenceClickListener {

    private lateinit var mPowerSavePref: SelectorWithWidgetPreference
    private lateinit var mBalancedPref: SelectorWithWidgetPreference
    private lateinit var mPerformancePref: SelectorWithWidgetPreference
    private lateinit var mDiabloPref: SelectorWithWidgetPreference

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        addPreferencesFromResource(R.xml.power_preferences)

        mPowerSavePref = findPreference<SelectorWithWidgetPreference>("power_profile_power_save")!!.apply {
            onPreferenceClickListener = this@PowerProfileFragment
        }
        mBalancedPref = findPreference<SelectorWithWidgetPreference>("power_profile_balanced")!!.apply {
            onPreferenceClickListener = this@PowerProfileFragment
        }
        mPerformancePref = findPreference<SelectorWithWidgetPreference>("power_profile_performance")!!.apply {
            onPreferenceClickListener = this@PowerProfileFragment
        }
        mDiabloPref = findPreference<SelectorWithWidgetPreference>("power_profile_diablo")!!.apply {
            onPreferenceClickListener = this@PowerProfileFragment
        }

        updateRadioState(PowerProfileController.getPowerProfile(requireContext()))
    }

    private fun updateRadioState(selectedProfile: Int) {
        mPowerSavePref.isChecked = (selectedProfile == PowerProfileController.PROFILE_POWER_SAVE)
        mBalancedPref.isChecked = (selectedProfile == PowerProfileController.PROFILE_BALANCED)
        mPerformancePref.isChecked = (selectedProfile == PowerProfileController.PROFILE_PERFORMANCE)
        mDiabloPref.isChecked = (selectedProfile == PowerProfileController.PROFILE_DIABLO)
    }

    override fun onPreferenceClick(preference: Preference): Boolean {
        val selectedProfile = when (preference.key) {
            "power_profile_power_save" -> PowerProfileController.PROFILE_POWER_SAVE
            "power_profile_performance" -> PowerProfileController.PROFILE_PERFORMANCE
            "power_profile_diablo" -> PowerProfileController.PROFILE_DIABLO
            else -> PowerProfileController.PROFILE_BALANCED
        }

        PowerProfileController.setPowerProfile(requireContext(), selectedProfile)
        updateRadioState(selectedProfile)
        return true
    }
}
