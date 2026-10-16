/*
 * SPDX-FileCopyrightText: 2025 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.fan

import android.os.Bundle
import androidx.preference.Preference
import androidx.preference.SwitchPreferenceCompat
import com.android.settingslib.widget.MainSwitchPreference
import com.android.settingslib.widget.SettingsBasePreferenceFragment
import com.android.settingslib.widget.SliderPreference
import android.os.Handler
import android.os.Looper
import androidx.preference.PreferenceCategory
import org.lineageos.settings.R
import org.lineageos.settings.utils.*

class FanFragment : SettingsBasePreferenceFragment(), Preference.OnPreferenceChangeListener {

    private lateinit var mSwitchBar: MainSwitchPreference
    private lateinit var mSmartFanSwitch: SwitchPreferenceCompat
    private lateinit var mFanSpeedBar: SliderPreference

    private lateinit var mTempBar1: SliderPreference
    private lateinit var mTempBar2: SliderPreference
    private lateinit var mTempBar3: SliderPreference
    private lateinit var mTempBar4: SliderPreference
    private lateinit var mTempBar5: SliderPreference

    private var mCpuTempPref: Preference? = null
    private var mGpuTempPref: Preference? = null
    private var mBatteryTempPref: Preference? = null
    private var mFanRpmPref: Preference? = null

    private val mHandler = Handler(Looper.getMainLooper())
    private val mTelemetryRunnable = object : Runnable {
        override fun run() {
            updateTelemetry()
            mHandler.postDelayed(this, 1000L)
        }
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        addPreferencesFromResource(R.xml.fan_preferences)

        val fanEnabled = getInt(requireContext(), FanController.KEY_FAN_ENABLE, 0) == 1
        val smartFanEnabled = getInt(requireContext(), FanController.KEY_SMART_FAN_ENABLE, 1) == 1
        val savedSpeed =
            getInt(requireContext(), FanController.KEY_FAN_SPEED, FanController.FAN_DEFAULT_SPEED)

        val t1 = getInt(requireContext(), FanController.KEY_SMART_FAN_TEMP_SPEED_1, FanController.DEFAULT_TEMP_SPEED_1)
        val t2 = getInt(requireContext(), FanController.KEY_SMART_FAN_TEMP_SPEED_2, FanController.DEFAULT_TEMP_SPEED_2)
        val t3 = getInt(requireContext(), FanController.KEY_SMART_FAN_TEMP_SPEED_3, FanController.DEFAULT_TEMP_SPEED_3)
        val t4 = getInt(requireContext(), FanController.KEY_SMART_FAN_TEMP_SPEED_4, FanController.DEFAULT_TEMP_SPEED_4)
        val t5 = getInt(requireContext(), FanController.KEY_SMART_FAN_TEMP_SPEED_5, FanController.DEFAULT_TEMP_SPEED_5)

        mSwitchBar =
            findPreference<MainSwitchPreference>(FanController.KEY_FAN_ENABLE)!!.apply {
                setChecked(fanEnabled)
                onPreferenceChangeListener = this@FanFragment
            }

        mSmartFanSwitch =
            findPreference<SwitchPreferenceCompat>(FanController.KEY_SMART_FAN_ENABLE)!!.apply {
                isChecked = smartFanEnabled
                onPreferenceChangeListener = this@FanFragment
            }

        mFanSpeedBar =
            findPreference<SliderPreference>(FanController.KEY_FAN_SPEED)!!.apply {
                setMin(FanController.FAN_MIN_SPEED)
                setMax(FanController.FAN_MAX_SPEED)
                setSliderIncrement(1)
                setValue(savedSpeed)
                setShowSliderValue(true)
                isEnabled = fanEnabled && !smartFanEnabled
                onPreferenceChangeListener = this@FanFragment
            }

        mTempBar1 = findPreference<SliderPreference>(FanController.KEY_SMART_FAN_TEMP_SPEED_1)!!.apply {
            setMin(30); setMax(65); setSliderIncrement(1); setValue(t1); setShowSliderValue(true)
            title = getString(R.string.smart_fan_temp_speed_1_title) + " ($t1 °C)"
            summary = getString(R.string.smart_fan_temp_speed_1_summary) + " ($t1 °C)"
            isEnabled = smartFanEnabled; onPreferenceChangeListener = this@FanFragment
        }
        mTempBar2 = findPreference<SliderPreference>(FanController.KEY_SMART_FAN_TEMP_SPEED_2)!!.apply {
            setMin(30); setMax(70); setSliderIncrement(1); setValue(t2); setShowSliderValue(true)
            title = getString(R.string.smart_fan_temp_speed_2_title) + " ($t2 °C)"
            summary = getString(R.string.smart_fan_temp_speed_2_summary) + " ($t2 °C)"
            isEnabled = smartFanEnabled; onPreferenceChangeListener = this@FanFragment
        }
        mTempBar3 = findPreference<SliderPreference>(FanController.KEY_SMART_FAN_TEMP_SPEED_3)!!.apply {
            setMin(35); setMax(75); setSliderIncrement(1); setValue(t3); setShowSliderValue(true)
            title = getString(R.string.smart_fan_temp_speed_3_title) + " ($t3 °C)"
            summary = getString(R.string.smart_fan_temp_speed_3_summary) + " ($t3 °C)"
            isEnabled = smartFanEnabled; onPreferenceChangeListener = this@FanFragment
        }
        mTempBar4 = findPreference<SliderPreference>(FanController.KEY_SMART_FAN_TEMP_SPEED_4)!!.apply {
            setMin(40); setMax(80); setSliderIncrement(1); setValue(t4); setShowSliderValue(true)
            title = getString(R.string.smart_fan_temp_speed_4_title) + " ($t4 °C)"
            summary = getString(R.string.smart_fan_temp_speed_4_summary) + " ($t4 °C)"
            isEnabled = smartFanEnabled; onPreferenceChangeListener = this@FanFragment
        }
        mTempBar5 = findPreference<SliderPreference>(FanController.KEY_SMART_FAN_TEMP_SPEED_5)!!.apply {
            setMin(45); setMax(85); setSliderIncrement(1); setValue(t5); setShowSliderValue(true)
            title = getString(R.string.smart_fan_temp_speed_5_title) + " ($t5 °C)"
            summary = getString(R.string.smart_fan_temp_speed_5_summary) + " ($t5 °C)"
            isEnabled = smartFanEnabled; onPreferenceChangeListener = this@FanFragment
        }

        mCpuTempPref = findPreference("gauge_cpu_temp")
        mGpuTempPref = findPreference("gauge_gpu_temp")
        mBatteryTempPref = findPreference("gauge_battery_temp")
        mFanRpmPref = findPreference("gauge_fan_rpm")
    }

    override fun onResume() {
        super.onResume()
        mHandler.post(mTelemetryRunnable)
    }

    override fun onPause() {
        super.onPause()
        mHandler.removeCallbacks(mTelemetryRunnable)
    }

    private fun updateTelemetry() {
        val cpuTemp = FanController.getCpuTemp()
        val gpuTemp = FanController.getGpuTemp()
        val battTemp = FanController.getBatteryTemp()
        val fanRpm = FanController.getFanRpm()

        mCpuTempPref?.summary = "$cpuTemp °C"
        mGpuTempPref?.summary = "$gpuTemp °C"
        mBatteryTempPref?.summary = String.format("%.1f °C", battTemp)
        mFanRpmPref?.summary = if (fanRpm > 0) "$fanRpm RPM" else "0 RPM (Idle)"
    }

    override fun onPreferenceChange(preference: Preference, newValue: Any): Boolean {
        return when (preference.key) {
            FanController.KEY_FAN_ENABLE -> {
                val isEnabled = newValue as Boolean
                val speed =
                    getInt(
                        requireContext(),
                        FanController.KEY_FAN_SPEED,
                        FanController.FAN_DEFAULT_SPEED,
                    )
                val smartEnabled = getInt(requireContext(), FanController.KEY_SMART_FAN_ENABLE, 1) == 1

                mFanSpeedBar.isEnabled = isEnabled && !smartEnabled
                FanController.applySettings(requireContext(), isEnabled, speed)
                true
            }

            FanController.KEY_SMART_FAN_ENABLE -> {
                val isSmart = newValue as Boolean
                val fanEnabled = getInt(requireContext(), FanController.KEY_FAN_ENABLE, 0) == 1

                mFanSpeedBar.isEnabled = fanEnabled && !isSmart
                mTempBar1.isEnabled = isSmart
                mTempBar2.isEnabled = isSmart
                mTempBar3.isEnabled = isSmart
                mTempBar4.isEnabled = isSmart
                mTempBar5.isEnabled = isSmart

                FanController.setSmartFanEnabled(requireContext(), isSmart)
                true
            }

            FanController.KEY_FAN_SPEED -> {
                val speed = newValue as Int
                FanController.setFanSpeed(requireContext(), speed)
                true
            }

            FanController.KEY_SMART_FAN_TEMP_SPEED_1 -> {
                val temp = newValue as Int
                putInt(requireContext(), FanController.KEY_SMART_FAN_TEMP_SPEED_1, temp)
                mTempBar1.title = getString(R.string.smart_fan_temp_speed_1_title) + " ($temp °C)"
                mTempBar1.summary = getString(R.string.smart_fan_temp_speed_1_summary) + " ($temp °C)"
                true
            }

            FanController.KEY_SMART_FAN_TEMP_SPEED_2 -> {
                val temp = newValue as Int
                putInt(requireContext(), FanController.KEY_SMART_FAN_TEMP_SPEED_2, temp)
                mTempBar2.title = getString(R.string.smart_fan_temp_speed_2_title) + " ($temp °C)"
                mTempBar2.summary = getString(R.string.smart_fan_temp_speed_2_summary) + " ($temp °C)"
                true
            }

            FanController.KEY_SMART_FAN_TEMP_SPEED_3 -> {
                val temp = newValue as Int
                putInt(requireContext(), FanController.KEY_SMART_FAN_TEMP_SPEED_3, temp)
                mTempBar3.title = getString(R.string.smart_fan_temp_speed_3_title) + " ($temp °C)"
                mTempBar3.summary = getString(R.string.smart_fan_temp_speed_3_summary) + " ($temp °C)"
                true
            }

            FanController.KEY_SMART_FAN_TEMP_SPEED_4 -> {
                val temp = newValue as Int
                putInt(requireContext(), FanController.KEY_SMART_FAN_TEMP_SPEED_4, temp)
                mTempBar4.title = getString(R.string.smart_fan_temp_speed_4_title) + " ($temp °C)"
                mTempBar4.summary = getString(R.string.smart_fan_temp_speed_4_summary) + " ($temp °C)"
                true
            }

            FanController.KEY_SMART_FAN_TEMP_SPEED_5 -> {
                val temp = newValue as Int
                putInt(requireContext(), FanController.KEY_SMART_FAN_TEMP_SPEED_5, temp)
                mTempBar5.title = getString(R.string.smart_fan_temp_speed_5_title) + " ($temp °C)"
                mTempBar5.summary = getString(R.string.smart_fan_temp_speed_5_summary) + " ($temp °C)"
                true
            }

            else -> false
        }
    }
}
