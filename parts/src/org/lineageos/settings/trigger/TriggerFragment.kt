/*
 * SPDX-FileCopyrightText: 2025-2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.trigger

import android.content.Intent
import android.os.Bundle
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreferenceCompat
import com.android.settingslib.widget.MainSwitchPreference
import org.lineageos.settings.R

class TriggerFragment : PreferenceFragmentCompat(), Preference.OnPreferenceChangeListener {

    private var triggerSwitch: MainSwitchPreference? = null
    private var overlayPref: Preference? = null
    private var feedbackSwitch: SwitchPreferenceCompat? = null
    private var appFilterSwitch: SwitchPreferenceCompat? = null
    private var selectAppsPref: Preference? = null

    private var leftModePref: ListPreference? = null
    private var leftCpsPref: ListPreference? = null
    private var leftCoordsPref: Preference? = null
    private var rightModePref: ListPreference? = null
    private var rightCpsPref: ListPreference? = null
    private var rightCoordsPref: Preference? = null

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.trigger_preferences, rootKey)

        val context = requireContext()

        triggerSwitch = findPreference(TriggerController.KEY_TRIGGER_ENABLE)
        triggerSwitch?.isChecked = TriggerController.isTriggerEnabled(context)
        triggerSwitch?.onPreferenceChangeListener = this

        overlayPref = findPreference("trigger_overlay_btn")
        overlayPref?.setOnPreferenceClickListener {
            TriggerController.startOverlayService(context)
            true
        }

        feedbackSwitch = findPreference("trigger_feedback_enable")
        feedbackSwitch?.isChecked = TriggerController.isFeedbackEnabled(context)
        feedbackSwitch?.onPreferenceChangeListener = this

        val hapticSwitch: SwitchPreferenceCompat? = findPreference("trigger_haptic_enable")
        hapticSwitch?.isChecked = TriggerHapticManager.isHapticsEnabled(context)
        hapticSwitch?.onPreferenceChangeListener = this

        appFilterSwitch = findPreference("trigger_app_filter_mode")
        appFilterSwitch?.isChecked = TriggerController.isAppFilterEnabled(context)
        appFilterSwitch?.onPreferenceChangeListener = this

        selectAppsPref = findPreference("trigger_select_apps")
        selectAppsPref?.setOnPreferenceClickListener {
            val intent = Intent(context, TriggerAppSelectorActivity::class.java)
            startActivity(intent)
            true
        }

        leftModePref = findPreference(TriggerController.KEY_LEFT_MODE)
        leftModePref?.value = TriggerController.getLeftMode(context).toString()
        leftModePref?.onPreferenceChangeListener = this

        leftCpsPref = findPreference(TriggerController.KEY_LEFT_CPS)
        leftCpsPref?.value = TriggerController.getLeftCps(context).toString()
        leftCpsPref?.onPreferenceChangeListener = this

        leftCoordsPref = findPreference("trigger_left_coords")

        rightModePref = findPreference(TriggerController.KEY_RIGHT_MODE)
        rightModePref?.value = TriggerController.getRightMode(context).toString()
        rightModePref?.onPreferenceChangeListener = this

        rightCpsPref = findPreference(TriggerController.KEY_RIGHT_CPS)
        rightCpsPref?.value = TriggerController.getRightCps(context).toString()
        rightCpsPref?.onPreferenceChangeListener = this

        rightCoordsPref = findPreference("trigger_right_coords")

        updateCoordinatesDisplay()
        updateSelectedAppsCount()
    }

    override fun onResume() {
        super.onResume()
        updateCoordinatesDisplay()
        updateSelectedAppsCount()
    }

    private fun updateCoordinatesDisplay() {
        val context = context ?: return
        val (lx, ly) = TriggerController.getLeftCoordinates(context)
        leftCoordsPref?.summary = getString(R.string.trigger_coords_format, lx, ly)

        val (rx, ry) = TriggerController.getRightCoordinates(context)
        rightCoordsPref?.summary = getString(R.string.trigger_coords_format, rx, ry)
    }

    private fun updateSelectedAppsCount() {
        val context = context ?: return
        val count = TriggerController.getSelectedApps(context).size
        selectAppsPref?.summary = if (count == 0) {
            getString(R.string.trigger_select_apps_summary)
        } else {
            "$count games/apps selected"
        }
    }

    override fun onPreferenceChange(preference: Preference, newValue: Any): Boolean {
        val context = requireContext()
        when (preference.key) {
            TriggerController.KEY_TRIGGER_ENABLE -> {
                val enabled = newValue as Boolean
                TriggerController.setTriggerEnabled(context, enabled)
                return true
            }
            "trigger_feedback_enable" -> {
                val enabled = newValue as Boolean
                TriggerController.setFeedbackEnabled(context, enabled)
                return true
            }
            "trigger_haptic_enable" -> {
                val enabled = newValue as Boolean
                TriggerHapticManager.setHapticsEnabled(context, enabled)
                return true
            }
            "trigger_app_filter_mode" -> {
                val enabled = newValue as Boolean
                TriggerController.setAppFilterEnabled(context, enabled)
                return true
            }
            TriggerController.KEY_LEFT_MODE -> {
                val mode = (newValue as String).toInt()
                TriggerController.setLeftMode(context, mode)
                return true
            }
            TriggerController.KEY_LEFT_CPS -> {
                val cps = (newValue as String).toInt()
                TriggerController.setLeftCps(context, cps)
                return true
            }
            TriggerController.KEY_RIGHT_MODE -> {
                val mode = (newValue as String).toInt()
                TriggerController.setRightMode(context, mode)
                return true
            }
            TriggerController.KEY_RIGHT_CPS -> {
                val cps = (newValue as String).toInt()
                TriggerController.setRightCps(context, cps)
                return true
            }
        }
        return false
    }
}
