/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.rgb

import android.os.Bundle
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SeekBarPreference
import com.android.settingslib.widget.MainSwitchPreference
import org.lineageos.settings.R

class RgbFragment : PreferenceFragmentCompat(), Preference.OnPreferenceChangeListener {

    private var rgbSwitch: MainSwitchPreference? = null
    private var modePref: ListPreference? = null
    private var colorPref: ListPreference? = null
    private var brightnessPref: SeekBarPreference? = null

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.rgb_preferences, rootKey)

        val context = requireContext()

        rgbSwitch = findPreference(RgbController.KEY_RGB_ENABLE)
        rgbSwitch?.isChecked = RgbController.isRgbEnabled(context)
        rgbSwitch?.onPreferenceChangeListener = this

        modePref = findPreference(RgbController.KEY_RGB_MODE)
        modePref?.value = RgbController.getMode(context).toString()
        modePref?.onPreferenceChangeListener = this

        colorPref = findPreference(RgbController.KEY_RGB_COLOR)
        colorPref?.value = RgbController.getColor(context).toString()
        colorPref?.onPreferenceChangeListener = this

        brightnessPref = findPreference(RgbController.KEY_RGB_BRIGHTNESS)
        brightnessPref?.value = RgbController.getBrightness(context)
        brightnessPref?.onPreferenceChangeListener = this
    }

    override fun onPreferenceChange(preference: Preference, newValue: Any): Boolean {
        val context = requireContext()
        when (preference.key) {
            RgbController.KEY_RGB_ENABLE -> {
                val enabled = newValue as Boolean
                RgbController.setRgbEnabled(context, enabled)
                return true
            }
            RgbController.KEY_RGB_MODE -> {
                val mode = (newValue as String).toInt()
                RgbController.setMode(context, mode)
                return true
            }
            RgbController.KEY_RGB_COLOR -> {
                val color = (newValue as String).toInt()
                RgbController.setColor(context, color)
                return true
            }
            RgbController.KEY_RGB_BRIGHTNESS -> {
                val brightness = newValue as Int
                RgbController.setBrightness(context, brightness)
                return true
            }
        }
        return false
    }
}
