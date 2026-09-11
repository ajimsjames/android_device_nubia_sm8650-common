/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.power

import android.content.Context
import org.lineageos.settings.fan.FanController
import org.lineageos.settings.utils.FileUtils
import org.lineageos.settings.utils.SettingsUtils

object PowerProfileController {

    const val KEY_POWER_PROFILE = "nubia_power_profile"

    const val PROFILE_BATTERY_SAVER = 0
    const val PROFILE_BALANCED = 1
    const val PROFILE_PERFORMANCE = 2
    const val PROFILE_DIABLO = 3

    // GPU Control Nodes
    private const val GPU_MAX_PWRLEVEL_NODE = "/sys/class/kgsl/kgsl-3d0/max_pwrlevel"
    private const val GPU_MIN_PWRLEVEL_NODE = "/sys/class/kgsl/kgsl-3d0/min_pwrlevel"

    // CPU Sched Nodes
    private const val SCHED_BOOST_NODE = "/proc/sys/walt/sched_boost"
    private const val UCLAMP_TOP_APP_MIN_NODE = "/dev/cpuctl/top-app/cpu.uclamp.min"

    // CPU Frequency Policies - Min / Max / Scaling
    private const val POLICY0_MIN_FREQ = "/sys/devices/system/cpu/cpufreq/policy0/scaling_min_freq"
    private const val POLICY2_MIN_FREQ = "/sys/devices/system/cpu/cpufreq/policy2/scaling_min_freq"
    private const val POLICY5_MIN_FREQ = "/sys/devices/system/cpu/cpufreq/policy5/scaling_min_freq"
    private const val POLICY7_MIN_FREQ = "/sys/devices/system/cpu/cpufreq/policy7/scaling_min_freq"

    private const val POLICY0_MAX_FREQ = "/sys/devices/system/cpu/cpufreq/policy0/scaling_max_freq"
    private const val POLICY2_MAX_FREQ = "/sys/devices/system/cpu/cpufreq/policy2/scaling_max_freq"
    private const val POLICY5_MAX_FREQ = "/sys/devices/system/cpu/cpufreq/policy5/scaling_max_freq"
    private const val POLICY7_MAX_FREQ = "/sys/devices/system/cpu/cpufreq/policy7/scaling_max_freq"

    private const val POLICY0_GOVERNOR = "/sys/devices/system/cpu/cpufreq/policy0/scaling_governor"
    private const val POLICY2_GOVERNOR = "/sys/devices/system/cpu/cpufreq/policy2/scaling_governor"
    private const val POLICY5_GOVERNOR = "/sys/devices/system/cpu/cpufreq/policy5/scaling_governor"
    private const val POLICY7_GOVERNOR = "/sys/devices/system/cpu/cpufreq/policy7/scaling_governor"

    private const val POLICY0_HISPEED = "/sys/devices/system/cpu/cpufreq/policy0/walt/hispeed_freq"
    private const val POLICY2_HISPEED = "/sys/devices/system/cpu/cpufreq/policy2/walt/hispeed_freq"
    private const val POLICY5_HISPEED = "/sys/devices/system/cpu/cpufreq/policy5/walt/hispeed_freq"
    private const val POLICY7_HISPEED = "/sys/devices/system/cpu/cpufreq/policy7/walt/hispeed_freq"

    // Sched conservative & energy saving nodes
    private const val SCHED_CONSERVATIVE_PL_NODE = "/proc/sys/walt/sched_conservative_pl"
    private const val SCHED_EARLY_UPMIGRATE_NODE = "/proc/sys/walt/sched_early_upmigrate"
    private const val TOUCH_RATE_BOOST_NODE = "/sys/devices/platform/goodix_ts.0/rate_boost"

    fun getProfile(context: Context): Int {
        return SettingsUtils.getInt(context, KEY_POWER_PROFILE, PROFILE_BALANCED)
    }

    fun setProfile(context: Context, profile: Int) {
        SettingsUtils.putInt(context, KEY_POWER_PROFILE, profile)
        applyProfile(context, profile)
    }

    private fun applyPolicyFreqs(policy: Int, governor: String, minFreq: String, maxFreq: String, hispeedFreq: String? = null) {
        val basePath = "/sys/devices/system/cpu/cpufreq/policy$policy"
        // 1. Temporarily drop min_freq to 0 to prevent kernel constraint check failure (min > max)
        FileUtils.writeLine("$basePath/scaling_min_freq", "0")
        // 2. Set max freq
        FileUtils.writeLine("$basePath/scaling_max_freq", maxFreq)
        // 3. Set desired min freq
        FileUtils.writeLine("$basePath/scaling_min_freq", minFreq)
        // 4. Set governor
        FileUtils.writeLine("$basePath/scaling_governor", governor)
        // 5. Set hispeed if provided
        if (hispeedFreq != null) {
            FileUtils.writeLine("$basePath/walt/hispeed_freq", hispeedFreq)
        }
    }

    fun applyProfile(context: Context, profile: Int) {
        when (profile) {
            PROFILE_BATTERY_SAVER -> {
                // Extreme Battery Preservation for SM8650:
                applyPolicyFreqs(0, "walt", "364800", "1574400", "787200")
                applyPolicyFreqs(2, "walt", "499200", "1824000", "960000")
                applyPolicyFreqs(5, "walt", "499200", "1824000", "960000")
                applyPolicyFreqs(7, "walt", "480000", "1824000", "902400")

                // 2. Cap GPU to lowest SVS level & disable boost
                FileUtils.writeLine(GPU_MIN_PWRLEVEL_NODE, "8")
                FileUtils.writeLine(GPU_MAX_PWRLEVEL_NODE, "6")
                FileUtils.writeLine(SCHED_BOOST_NODE, "0")
                FileUtils.writeLine(UCLAMP_TOP_APP_MIN_NODE, "0")

                // 3. WALT Energy-Efficiency Scheduling
                FileUtils.writeLine(SCHED_CONSERVATIVE_PL_NODE, "1")
                FileUtils.writeLine(SCHED_EARLY_UPMIGRATE_NODE, "0")

                // 4. Standard touch polling
                FileUtils.writeLine(TOUCH_RATE_BOOST_NODE, "0")

                // 5. Complete fan shutdown
                FanController.setFanEnabled(context, false)
            }

            PROFILE_BALANCED -> {
                // Stock uncapped max frequencies + balanced hispeeds
                applyPolicyFreqs(0, "walt", "556800", "2265600", "902400")
                applyPolicyFreqs(2, "walt", "614400", "3148800", "1075200")
                applyPolicyFreqs(5, "walt", "499200", "2956800", "1075200")
                applyPolicyFreqs(7, "walt", "672000", "3398400", "1132800")

                FileUtils.writeLine(GPU_MIN_PWRLEVEL_NODE, "8")
                FileUtils.writeLine(GPU_MAX_PWRLEVEL_NODE, "0") // Full dynamic GPU access
                FileUtils.writeLine(SCHED_BOOST_NODE, "0")
                FileUtils.writeLine(UCLAMP_TOP_APP_MIN_NODE, "0")
                FileUtils.writeLine(SCHED_CONSERVATIVE_PL_NODE, "0")
                FileUtils.writeLine(SCHED_EARLY_UPMIGRATE_NODE, "1")
                FileUtils.writeLine(TOUCH_RATE_BOOST_NODE, "0")
            }

            PROFILE_PERFORMANCE -> {
                // High min frequencies + aggressive ramp-up
                applyPolicyFreqs(0, "walt", "1459200", "2265600", "1459200")
                applyPolicyFreqs(2, "walt", "1824000", "3148800", "1824000")
                applyPolicyFreqs(5, "walt", "1824000", "2956800", "1824000")
                applyPolicyFreqs(7, "walt", "2169600", "3398400", "2169600")

                FileUtils.writeLine(GPU_MIN_PWRLEVEL_NODE, "4")
                FileUtils.writeLine(GPU_MAX_PWRLEVEL_NODE, "0")
                FileUtils.writeLine(SCHED_BOOST_NODE, "1")
                FileUtils.writeLine(UCLAMP_TOP_APP_MIN_NODE, "20")
                FileUtils.writeLine(SCHED_CONSERVATIVE_PL_NODE, "0")
                FileUtils.writeLine(SCHED_EARLY_UPMIGRATE_NODE, "1")
                FileUtils.writeLine(TOUCH_RATE_BOOST_NODE, "1")
            }

            PROFILE_DIABLO -> {
                // RedMagic Diablo Mode: 100% Locked Max CPU Clocks, Max GPU 1.0GHz Overclock, High Sched Boost, Fan Turbo
                applyPolicyFreqs(0, "performance", "2265600", "2265600", "2265600")
                applyPolicyFreqs(2, "performance", "3148800", "3148800", "3148800")
                applyPolicyFreqs(5, "performance", "2956800", "2956800", "2956800")
                applyPolicyFreqs(7, "performance", "3398400", "3398400", "3398400")

                FileUtils.writeLine(GPU_MIN_PWRLEVEL_NODE, "0") // Lock GPU to 1.0 GHz Max Clock
                FileUtils.writeLine(GPU_MAX_PWRLEVEL_NODE, "0")
                FileUtils.writeLine(SCHED_BOOST_NODE, "2")
                FileUtils.writeLine(UCLAMP_TOP_APP_MIN_NODE, "50")
                FileUtils.writeLine(SCHED_CONSERVATIVE_PL_NODE, "0")
                FileUtils.writeLine(SCHED_EARLY_UPMIGRATE_NODE, "1")
                FileUtils.writeLine(TOUCH_RATE_BOOST_NODE, "1")

                // Engage Level 5 Turbo Fan to maintain cooling
                FanController.setFanEnabled(context, true)
                FanController.setFanSpeed(context, 5)
            }
        }
    }

    fun restoreSettings(context: Context) {
        val profile = getProfile(context)
        applyProfile(context, profile)
    }
}
