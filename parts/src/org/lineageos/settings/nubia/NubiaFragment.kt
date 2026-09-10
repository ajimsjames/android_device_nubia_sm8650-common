/*
 * SPDX-FileCopyrightText: 2025-2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.nubia

import android.content.Intent
import android.database.ContentObserver
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreferenceCompat
import org.lineageos.settings.R
import org.lineageos.settings.power.ChargeBypassController
import org.lineageos.settings.power.PowerProfileController
import org.lineageos.settings.power.SmartChargingController
import org.lineageos.settings.rgb.RgbActivity
import org.lineageos.settings.slider.SliderController
import org.lineageos.settings.touch.TouchEnhancer

class NubiaFragment : PreferenceFragmentCompat(), Preference.OnPreferenceChangeListener {

    private var powerProfilePref: ListPreference? = null
    private var bypassSwitch: SwitchPreferenceCompat? = null
    private var sliderActionPref: ListPreference? = null
    private var touchRateSwitch: SwitchPreferenceCompat? = null
    private var touchDeadzonePref: ListPreference? = null
    private var autoBypassGameSwitch: SwitchPreferenceCompat? = null
    private var chargeLimitSwitch: SwitchPreferenceCompat? = null

    private val settingsObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            val context = context ?: return
            powerProfilePref?.value = PowerProfileController.getProfile(context).toString()
            bypassSwitch?.isChecked = ChargeBypassController.isBypassEnabled(context)
        }
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.nubia_preferences, rootKey)

        val context = requireContext()

        powerProfilePref = findPreference(PowerProfileController.KEY_POWER_PROFILE)
        powerProfilePref?.value = PowerProfileController.getProfile(context).toString()
        powerProfilePref?.onPreferenceChangeListener = this

        val rgbPref: Preference? = findPreference("rgb_settings")
        rgbPref?.setOnPreferenceClickListener {
            startActivity(Intent(context, RgbActivity::class.java))
            true
        }

        sliderActionPref = findPreference("slider_switch_action")
        sliderActionPref?.value = SliderController.getSliderAction(context).toString()
        sliderActionPref?.onPreferenceChangeListener = this

        touchRateSwitch = findPreference("touch_high_sampling_rate")
        touchRateSwitch?.isChecked = TouchEnhancer.isHighSamplingRateEnabled(context)
        touchRateSwitch?.onPreferenceChangeListener = this

        val hudSwitch: SwitchPreferenceCompat? = findPreference("gaming_hud_overlay_enable")
        hudSwitch?.isChecked = org.lineageos.settings.utils.SettingsUtils.getInt(context, "gaming_hud_overlay_enable", 0) == 1
        hudSwitch?.onPreferenceChangeListener = this

        val hudKeys = arrayOf(
            org.lineageos.settings.hud.GamingHudOverlayService.KEY_HUD_SHOW_FPS,
            org.lineageos.settings.hud.GamingHudOverlayService.KEY_HUD_SHOW_TEMPS,
            org.lineageos.settings.hud.GamingHudOverlayService.KEY_HUD_SHOW_CPU,
            org.lineageos.settings.hud.GamingHudOverlayService.KEY_HUD_SHOW_GPU,
            org.lineageos.settings.hud.GamingHudOverlayService.KEY_HUD_SHOW_FAN,
            org.lineageos.settings.hud.GamingHudOverlayService.KEY_HUD_SHOW_POWER,
            org.lineageos.settings.hud.GamingHudOverlayService.KEY_HUD_SHOW_RAM,
            org.lineageos.settings.hud.GamingHudOverlayService.KEY_HUD_SHOW_PROFILE
        )
        for (key in hudKeys) {
            val pref: SwitchPreferenceCompat? = findPreference(key)
            pref?.isChecked = org.lineageos.settings.utils.SettingsUtils.getInt(context, key, 1) == 1
            pref?.onPreferenceChangeListener = this
        }

        touchDeadzonePref = findPreference("touch_edge_deadzone")
        touchDeadzonePref?.value = TouchEnhancer.getEdgeDeadzone(context).toString()
        touchDeadzonePref?.onPreferenceChangeListener = this

        bypassSwitch = findPreference(ChargeBypassController.KEY_CHARGE_BYPASS_ENABLE)
        bypassSwitch?.isChecked = ChargeBypassController.isBypassEnabled(context)
        bypassSwitch?.onPreferenceChangeListener = this

        autoBypassGameSwitch = findPreference("smart_charge_auto_bypass_game")
        autoBypassGameSwitch?.isChecked = SmartChargingController.isAutoBypassOnGameEnabled(context)
        autoBypassGameSwitch?.onPreferenceChangeListener = this

        chargeLimitSwitch = findPreference("smart_charge_limit_enable")
        chargeLimitSwitch?.isChecked = SmartChargingController.isChargeLimitEnabled(context)
        chargeLimitSwitch?.onPreferenceChangeListener = this
    }

    override fun onStart() {
        super.onStart()
        val context = context ?: return
        context.contentResolver.registerContentObserver(
            Settings.Global.getUriFor("nubia_parts_" + PowerProfileController.KEY_POWER_PROFILE),
            false,
            settingsObserver
        )
        context.contentResolver.registerContentObserver(
            Settings.Global.getUriFor("nubia_parts_" + ChargeBypassController.KEY_CHARGE_BYPASS_ENABLE),
            false,
            settingsObserver
        )
    }

    override fun onStop() {
        super.onStop()
        context?.contentResolver?.unregisterContentObserver(settingsObserver)
    }

    override fun onResume() {
        super.onResume()
        val context = context ?: return
        powerProfilePref?.value = PowerProfileController.getProfile(context).toString()
        bypassSwitch?.isChecked = ChargeBypassController.isBypassEnabled(context)
    }

    override fun onPreferenceChange(preference: Preference, newValue: Any): Boolean {
        val context = requireContext()
        when (preference.key) {
            PowerProfileController.KEY_POWER_PROFILE -> {
                val profile = (newValue as String).toInt()
                PowerProfileController.setProfile(context, profile)
                return true
            }
            "slider_switch_action" -> {
                val action = (newValue as String).toInt()
                SliderController.setSliderAction(context, action)
                return true
            }
            "touch_high_sampling_rate" -> {
                val enabled = newValue as Boolean
                TouchEnhancer.setHighSamplingRateEnabled(context, enabled)
                return true
            }
            "gaming_hud_overlay_enable" -> {
                val enabled = newValue as Boolean
                org.lineageos.settings.utils.SettingsUtils.putInt(context, "gaming_hud_overlay_enable", if (enabled) 1 else 0)
                val intent = Intent(context, org.lineageos.settings.hud.GamingHudOverlayService::class.java)
                if (enabled) {
                    context.startService(intent)
                } else {
                    context.stopService(intent)
                }
                return true
            }
            org.lineageos.settings.hud.GamingHudOverlayService.KEY_HUD_SHOW_FPS,
            org.lineageos.settings.hud.GamingHudOverlayService.KEY_HUD_SHOW_TEMPS,
            org.lineageos.settings.hud.GamingHudOverlayService.KEY_HUD_SHOW_CPU,
            org.lineageos.settings.hud.GamingHudOverlayService.KEY_HUD_SHOW_GPU,
            org.lineageos.settings.hud.GamingHudOverlayService.KEY_HUD_SHOW_FAN,
            org.lineageos.settings.hud.GamingHudOverlayService.KEY_HUD_SHOW_POWER,
            org.lineageos.settings.hud.GamingHudOverlayService.KEY_HUD_SHOW_RAM,
            org.lineageos.settings.hud.GamingHudOverlayService.KEY_HUD_SHOW_PROFILE -> {
                val enabled = newValue as Boolean
                org.lineageos.settings.utils.SettingsUtils.putInt(context, preference.key, if (enabled) 1 else 0)
                return true
            }
            "touch_edge_deadzone" -> {
                val mode = (newValue as String).toInt()
                TouchEnhancer.setEdgeDeadzone(context, mode)
                return true
            }
            ChargeBypassController.KEY_CHARGE_BYPASS_ENABLE -> {
                val enabled = newValue as Boolean
                ChargeBypassController.setBypassEnabled(context, enabled)
                return true
            }
            "smart_charge_auto_bypass_game" -> {
                val enabled = newValue as Boolean
                SmartChargingController.setAutoBypassOnGameEnabled(context, enabled)
                return true
            }
            "smart_charge_limit_enable" -> {
                val enabled = newValue as Boolean
                SmartChargingController.setChargeLimitEnabled(context, enabled)
                return true
            }
        }
        return false
    }
}
