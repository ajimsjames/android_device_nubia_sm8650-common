/*
 * SPDX-FileCopyrightText: 2025 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.trigger

import android.os.Bundle
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.SwitchPreferenceCompat
import com.android.settingslib.widget.MainSwitchPreference
import com.android.settingslib.widget.SettingsBasePreferenceFragment
import com.android.settingslib.widget.SliderPreference
import org.lineageos.settings.R
import org.lineageos.settings.utils.*

class TriggerFragment : SettingsBasePreferenceFragment(), Preference.OnPreferenceChangeListener {

    private lateinit var mSwitchBar: MainSwitchPreference
    private var mTouchCategory: PreferenceCategory? = null

    private var mL1PosX: SliderPreference? = null
    private var mL1PosY: SliderPreference? = null
    private var mR1PosX: SliderPreference? = null
    private var mR1PosY: SliderPreference? = null

    private var mRapidL1: SwitchPreferenceCompat? = null
    private var mRapidR1: SwitchPreferenceCompat? = null
    private var mRapidRate: SliderPreference? = null
    private var mHaptic: SwitchPreferenceCompat? = null

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        addPreferencesFromResource(R.xml.trigger_preferences)

        val triggerEnabled = TriggerController.isTriggerEnabled(requireContext())

        mSwitchBar =
            findPreference<MainSwitchPreference>(TriggerController.KEY_TRIGGER_ENABLE)!!.apply {
                isChecked = triggerEnabled
                onPreferenceChangeListener = this@TriggerFragment
            }

        mTouchCategory = findPreference("trigger_touch_category")
        mTouchCategory?.isEnabled = triggerEnabled

        mL1PosX = findPreference<SliderPreference>(TriggerController.KEY_TRIGGER_L1_POS_X)?.apply {
            value = TriggerController.getTriggerCoord(requireContext(), TriggerController.KEY_TRIGGER_L1_POS_X, TriggerController.DEFAULT_L1_POS_X)
            onPreferenceChangeListener = this@TriggerFragment
        }

        mL1PosY = findPreference<SliderPreference>(TriggerController.KEY_TRIGGER_L1_POS_Y)?.apply {
            value = TriggerController.getTriggerCoord(requireContext(), TriggerController.KEY_TRIGGER_L1_POS_Y, TriggerController.DEFAULT_L1_POS_Y)
            onPreferenceChangeListener = this@TriggerFragment
        }

        mR1PosX = findPreference<SliderPreference>(TriggerController.KEY_TRIGGER_R1_POS_X)?.apply {
            value = TriggerController.getTriggerCoord(requireContext(), TriggerController.KEY_TRIGGER_R1_POS_X, TriggerController.DEFAULT_R1_POS_X)
            onPreferenceChangeListener = this@TriggerFragment
        }

        mR1PosY = findPreference<SliderPreference>(TriggerController.KEY_TRIGGER_R1_POS_Y)?.apply {
            value = TriggerController.getTriggerCoord(requireContext(), TriggerController.KEY_TRIGGER_R1_POS_Y, TriggerController.DEFAULT_R1_POS_Y)
            onPreferenceChangeListener = this@TriggerFragment
        }

        mRapidL1 = findPreference<SwitchPreferenceCompat>(TriggerController.KEY_TRIGGER_RAPID_L1)?.apply {
            isChecked = TriggerController.isRapidFireL1Enabled(requireContext())
            onPreferenceChangeListener = this@TriggerFragment
        }

        mRapidR1 = findPreference<SwitchPreferenceCompat>(TriggerController.KEY_TRIGGER_RAPID_R1)?.apply {
            isChecked = TriggerController.isRapidFireR1Enabled(requireContext())
            onPreferenceChangeListener = this@TriggerFragment
        }

        val currentRate = TriggerController.getRapidFireRate(requireContext())
        mRapidRate = findPreference<SliderPreference>(TriggerController.KEY_TRIGGER_RAPID_RATE)?.apply {
            value = currentRate
            summary = getString(R.string.trigger_rapid_fire_rate_summary, currentRate)
            onPreferenceChangeListener = this@TriggerFragment
        }

        mHaptic = findPreference<SwitchPreferenceCompat>(TriggerController.KEY_TRIGGER_HAPTIC)?.apply {
            isChecked = TriggerController.isHapticEnabled(requireContext())
            onPreferenceChangeListener = this@TriggerFragment
        }

        findPreference<Preference>("trigger_launch_overlay")?.setOnPreferenceClickListener {
            val intent = android.content.Intent(requireContext(), TriggerOverlayService::class.java)
            requireContext().startService(intent)
            true
        }
    }

    override fun onPreferenceChange(preference: Preference, newValue: Any): Boolean {
        val context = requireContext()
        return when (preference.key) {
            TriggerController.KEY_TRIGGER_ENABLE -> {
                val isEnabled = newValue as Boolean
                TriggerController.setTriggerEnabled(context, isEnabled)
                mTouchCategory?.isEnabled = isEnabled
                true
            }

            TriggerController.KEY_TRIGGER_L1_POS_X,
            TriggerController.KEY_TRIGGER_L1_POS_Y,
            TriggerController.KEY_TRIGGER_R1_POS_X,
            TriggerController.KEY_TRIGGER_R1_POS_Y -> {
                val value = newValue as Int
                TriggerController.setTriggerCoord(context, preference.key, value)
                true
            }

            TriggerController.KEY_TRIGGER_RAPID_L1 -> {
                TriggerController.setRapidFireL1Enabled(context, newValue as Boolean)
                true
            }

            TriggerController.KEY_TRIGGER_RAPID_R1 -> {
                TriggerController.setRapidFireR1Enabled(context, newValue as Boolean)
                true
            }

            TriggerController.KEY_TRIGGER_RAPID_RATE -> {
                val rate = newValue as Int
                TriggerController.setRapidFireRate(context, rate)
                preference.summary = getString(R.string.trigger_rapid_fire_rate_summary, rate)
                true
            }

            TriggerController.KEY_TRIGGER_HAPTIC -> {
                TriggerController.setHapticEnabled(context, newValue as Boolean)
                true
            }

            else -> false
        }
    }
}
