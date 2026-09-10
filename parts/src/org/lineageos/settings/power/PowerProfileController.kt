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
    private const val GPU_FORCE_BUS_ON_NODE = "/sys/class/kgsl/kgsl-3d0/force_bus_on"
    private const val GPU_FORCE_CLK_ON_NODE = "/sys/class/kgsl/kgsl-3d0/force_clk_on"
    private const val GPU_FORCE_RAIL_ON_NODE = "/sys/class/kgsl/kgsl-3d0/force_rail_on"

    // CPU Sched Nodes
    private const val SCHED_BOOST_NODE = "/proc/sys/walt/sched_boost"
    private const val UCLAMP_TOP_APP_MIN_NODE = "/dev/cpuctl/top-app/cpu.uclamp.min"

    // CPU Frequency Policies
    private const val POLICY0_MAX_FREQ = "/sys/devices/system/cpu/cpufreq/policy0/scaling_max_freq"
    private const val POLICY2_MAX_FREQ = "/sys/devices/system/cpu/cpufreq/policy2/scaling_max_freq"
    private const val POLICY5_MAX_FREQ = "/sys/devices/system/cpu/cpufreq/policy5/scaling_max_freq"
    private const val POLICY7_MAX_FREQ = "/sys/devices/system/cpu/cpufreq/policy7/scaling_max_freq"

    private const val POLICY0_HISPEED = "/sys/devices/system/cpu/cpufreq/policy0/walt/hispeed_freq"
    private const val POLICY2_HISPEED = "/sys/devices/system/cpu/cpufreq/policy2/walt/hispeed_freq"
    private const val POLICY5_HISPEED = "/sys/devices/system/cpu/cpufreq/policy5/walt/hispeed_freq"
    private const val POLICY7_HISPEED = "/sys/devices/system/cpu/cpufreq/policy7/walt/hispeed_freq"

    // Sched conservative & energy saving nodes
    private const val SCHED_CONSERVATIVE_PL_NODE = "/proc/sys/walt/sched_conservative_pl"
    private const val SCHED_EARLY_UPMIGRATE_NODE = "/proc/sys/walt/sched_early_upmigrate"
    private const val CPU7_CORE_CTL_MAX_CPUS = "/sys/devices/system/cpu/cpu7/core_ctl/max_cpus"
    private const val TOUCH_RATE_BOOST_NODE = "/sys/devices/platform/goodix_ts.0/rate_boost"

    fun getProfile(context: Context): Int {
        return SettingsUtils.getInt(context, KEY_POWER_PROFILE, PROFILE_BALANCED)
    }

    fun setProfile(context: Context, profile: Int) {
        SettingsUtils.putInt(context, KEY_POWER_PROFILE, profile)
        applyProfile(context, profile)
    }

    fun applyProfile(context: Context, profile: Int) {
        when (profile) {
            PROFILE_BATTERY_SAVER -> {
                // Extreme Battery Preservation for SM8650 & Android 17:
                // 1. Cap CPU max frequencies to efficient sweet spots
                FileUtils.writeLine(POLICY0_MAX_FREQ, "1689600")
                FileUtils.writeLine(POLICY2_MAX_FREQ, "1824000")
                FileUtils.writeLine(POLICY5_MAX_FREQ, "1824000")
                FileUtils.writeLine(POLICY7_MAX_FREQ, "1824000")

                FileUtils.writeLine(POLICY0_HISPEED, "787200")
                FileUtils.writeLine(POLICY2_HISPEED, "960000")
                FileUtils.writeLine(POLICY5_HISPEED, "960000")
                FileUtils.writeLine(POLICY7_HISPEED, "902400")

                // 2. Cap GPU to lowest SVS level & disable boost
                FileUtils.writeLine(GPU_MAX_PWRLEVEL_NODE, "8")
                FileUtils.writeLine(GPU_FORCE_BUS_ON_NODE, "0")
                FileUtils.writeLine(GPU_FORCE_CLK_ON_NODE, "0")
                FileUtils.writeLine(GPU_FORCE_RAIL_ON_NODE, "0")
                FileUtils.writeLine(SCHED_BOOST_NODE, "0")
                FileUtils.writeLine(UCLAMP_TOP_APP_MIN_NODE, "0")

                // 3. WALT Energy-Efficiency Scheduling
                FileUtils.writeLine(SCHED_CONSERVATIVE_PL_NODE, "1")
                FileUtils.writeLine(SCHED_EARLY_UPMIGRATE_NODE, "0")

                // 4. Downclock touch digitizer to standard 240Hz to save bus power
                FileUtils.writeLine(TOUCH_RATE_BOOST_NODE, "0")

                // 5. Complete fan shutdown
                FanController.setFanEnabled(context, false)
            }

            PROFILE_BALANCED -> {
                // Stock uncapped max frequencies + balanced hispeeds
                FileUtils.writeLine(POLICY0_MAX_FREQ, "2265600")
                FileUtils.writeLine(POLICY2_MAX_FREQ, "3148800")
                FileUtils.writeLine(POLICY5_MAX_FREQ, "2956800")
                FileUtils.writeLine(POLICY7_MAX_FREQ, "3398400")

                FileUtils.writeLine(POLICY0_HISPEED, "902400")
                FileUtils.writeLine(POLICY2_HISPEED, "1075200")
                FileUtils.writeLine(POLICY5_HISPEED, "1075200")
                FileUtils.writeLine(POLICY7_HISPEED, "1132800")

                FileUtils.writeLine(GPU_MAX_PWRLEVEL_NODE, "0") // Full GPU access
                FileUtils.writeLine(GPU_FORCE_BUS_ON_NODE, "0")
                FileUtils.writeLine(GPU_FORCE_CLK_ON_NODE, "0")
                FileUtils.writeLine(GPU_FORCE_RAIL_ON_NODE, "0")
                FileUtils.writeLine(SCHED_BOOST_NODE, "0")
                FileUtils.writeLine(UCLAMP_TOP_APP_MIN_NODE, "0")
                FileUtils.writeLine(SCHED_CONSERVATIVE_PL_NODE, "0")
                FileUtils.writeLine(SCHED_EARLY_UPMIGRATE_NODE, "1")
            }

            PROFILE_PERFORMANCE -> {
                // Full max frequencies + aggressive ramp-up
                FileUtils.writeLine(POLICY0_MAX_FREQ, "2265600")
                FileUtils.writeLine(POLICY2_MAX_FREQ, "3148800")
                FileUtils.writeLine(POLICY5_MAX_FREQ, "2956800")
                FileUtils.writeLine(POLICY7_MAX_FREQ, "3398400")

                FileUtils.writeLine(POLICY0_HISPEED, "1459200")
                FileUtils.writeLine(POLICY2_HISPEED, "1824000")
                FileUtils.writeLine(POLICY5_HISPEED, "1824000")
                FileUtils.writeLine(POLICY7_HISPEED, "2169600")

                FileUtils.writeLine(GPU_MAX_PWRLEVEL_NODE, "0") // Full 1.0 GHz GPU
                FileUtils.writeLine(SCHED_BOOST_NODE, "1")
                FileUtils.writeLine(UCLAMP_TOP_APP_MIN_NODE, "20")
            }

            PROFILE_DIABLO -> {
                // RedMagic Diablo Mode: Max CPU, Max GPU Overclock, Max DDR, Fan Turbo
                FileUtils.writeLine(POLICY0_MAX_FREQ, "2265600")
                FileUtils.writeLine(POLICY2_MAX_FREQ, "3148800")
                FileUtils.writeLine(POLICY5_MAX_FREQ, "2956800")
                FileUtils.writeLine(POLICY7_MAX_FREQ, "3398400")

                FileUtils.writeLine(POLICY0_HISPEED, "1804800")
                FileUtils.writeLine(POLICY2_HISPEED, "2438400")
                FileUtils.writeLine(POLICY5_HISPEED, "2438400")
                FileUtils.writeLine(POLICY7_HISPEED, "2803200")

                FileUtils.writeLine(GPU_MAX_PWRLEVEL_NODE, "0")
                FileUtils.writeLine(GPU_FORCE_BUS_ON_NODE, "1") // Force 4224 MHz DDR bus
                FileUtils.writeLine(SCHED_BOOST_NODE, "2")
                FileUtils.writeLine(UCLAMP_TOP_APP_MIN_NODE, "40")

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
