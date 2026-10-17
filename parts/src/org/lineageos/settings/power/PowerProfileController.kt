/*
 * SPDX-FileCopyrightText: 2025-2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.power

import android.content.Context
import org.lineageos.settings.utils.*

object PowerProfileController {

    const val KEY_POWER_PROFILE = "power_profile"

    const val PROFILE_POWER_SAVE = 0
    const val PROFILE_BALANCED = 1
    const val PROFILE_PERFORMANCE = 2
    const val PROFILE_DIABLO = 3
    const val DEFAULT_PROFILE = PROFILE_BALANCED

    // Policy CPU max frequency nodes
    private const val POLICY0_MAX_FREQ = "/sys/devices/system/cpu/cpufreq/policy0/scaling_max_freq"
    private const val POLICY2_MAX_FREQ = "/sys/devices/system/cpu/cpufreq/policy2/scaling_max_freq"
    private const val POLICY5_MAX_FREQ = "/sys/devices/system/cpu/cpufreq/policy5/scaling_max_freq"
    private const val POLICY7_MAX_FREQ = "/sys/devices/system/cpu/cpufreq/policy7/scaling_max_freq"

    private const val POLICY0_MIN_FREQ = "/sys/devices/system/cpu/cpufreq/policy0/scaling_min_freq"
    private const val POLICY2_MIN_FREQ = "/sys/devices/system/cpu/cpufreq/policy2/scaling_min_freq"
    private const val POLICY5_MIN_FREQ = "/sys/devices/system/cpu/cpufreq/policy5/scaling_min_freq"
    private const val POLICY7_MIN_FREQ = "/sys/devices/system/cpu/cpufreq/policy7/scaling_min_freq"

    // WALT nodes
    private const val POLICY0_PL = "/sys/devices/system/cpu/cpufreq/policy0/walt/pl"
    private const val POLICY2_PL = "/sys/devices/system/cpu/cpufreq/policy2/walt/pl"
    private const val POLICY5_PL = "/sys/devices/system/cpu/cpufreq/policy5/walt/pl"
    private const val POLICY7_PL = "/sys/devices/system/cpu/cpufreq/policy7/walt/pl"

    private const val POLICY0_UP_RATE = "/sys/devices/system/cpu/cpufreq/policy0/walt/up_rate_limit_us"
    private const val POLICY2_UP_RATE = "/sys/devices/system/cpu/cpufreq/policy2/walt/up_rate_limit_us"
    private const val POLICY5_UP_RATE = "/sys/devices/system/cpu/cpufreq/policy5/walt/up_rate_limit_us"
    private const val POLICY7_UP_RATE = "/sys/devices/system/cpu/cpufreq/policy7/walt/up_rate_limit_us"

    private const val POLICY0_GOV = "/sys/devices/system/cpu/cpufreq/policy0/scaling_governor"
    private const val POLICY2_GOV = "/sys/devices/system/cpu/cpufreq/policy2/scaling_governor"
    private const val POLICY5_GOV = "/sys/devices/system/cpu/cpufreq/policy5/scaling_governor"
    private const val POLICY7_GOV = "/sys/devices/system/cpu/cpufreq/policy7/scaling_governor"

    private const val POLICY0_DOWN_RATE = "/sys/devices/system/cpu/cpufreq/policy0/walt/down_rate_limit_us"
    private const val POLICY2_DOWN_RATE = "/sys/devices/system/cpu/cpufreq/policy2/walt/down_rate_limit_us"
    private const val POLICY5_DOWN_RATE = "/sys/devices/system/cpu/cpufreq/policy5/walt/down_rate_limit_us"
    private const val POLICY7_DOWN_RATE = "/sys/devices/system/cpu/cpufreq/policy7/walt/down_rate_limit_us"

    // GPU and WALT boost nodes
    private const val GPU_MIN_CLOCK_NODE = "/sys/kernel/gpu/gpu_min_clock"
    private const val GPU_MIN_PWRLEVEL_NODE = "/sys/class/kgsl/kgsl-3d0/min_pwrlevel"
    private const val SCHED_BOOST_NODE = "/proc/sys/walt/sched_boost"

    fun setPowerProfile(context: Context, profile: Int) {
        putInt(context, KEY_POWER_PROFILE, profile)
        applyPowerProfile(context, profile)
    }

    fun getPowerProfile(context: Context): Int {
        return getInt(context, KEY_POWER_PROFILE, DEFAULT_PROFILE)
    }

    fun applyPowerProfile(context: Context, profile: Int) {
        when (profile) {
            PROFILE_POWER_SAVE -> {
                // Restore GPU default scaling
                writeLine(GPU_MIN_CLOCK_NODE, "231")
                writeLine(GPU_MIN_PWRLEVEL_NODE, "13")
                writeLine(SCHED_BOOST_NODE, "0")

                // Reset min frequencies to hardware minimums
                writeLine(POLICY0_MIN_FREQ, "364800")
                writeLine(POLICY2_MIN_FREQ, "499200")
                writeLine(POLICY5_MIN_FREQ, "499200")
                writeLine(POLICY7_MIN_FREQ, "480000")

                // Set energy-conservative governor (powersave locks to lowest frequency floor)
                writeLine(POLICY0_GOV, "powersave")
                writeLine(POLICY2_GOV, "powersave")
                writeLine(POLICY5_GOV, "powersave")
                writeLine(POLICY7_GOV, "powersave")

                // Cap peak frequencies to energy-efficient thresholds
                writeLine(POLICY0_MAX_FREQ, "1459200") // 1.45 GHz
                writeLine(POLICY2_MAX_FREQ, "1824000") // 1.82 GHz
                writeLine(POLICY5_MAX_FREQ, "1824000") // 1.82 GHz
                writeLine(POLICY7_MAX_FREQ, "1939200") // 1.93 GHz Prime

                // Smooth ramp up, slow drop down
                writeLine(POLICY0_UP_RATE, "4000")
                writeLine(POLICY2_UP_RATE, "4000")
                writeLine(POLICY5_UP_RATE, "4000")
                writeLine(POLICY7_UP_RATE, "8000")

                writeLine(POLICY0_DOWN_RATE, "5000")
                writeLine(POLICY2_DOWN_RATE, "5000")
                writeLine(POLICY5_DOWN_RATE, "5000")
                writeLine(POLICY7_DOWN_RATE, "10000")

                // Disable predicted load
                writeLine(POLICY0_PL, "0")
                writeLine(POLICY2_PL, "0")
                writeLine(POLICY5_PL, "0")
                writeLine(POLICY7_PL, "0")
            }

            PROFILE_BALANCED -> {
                // Restore GPU default scaling
                writeLine(GPU_MIN_CLOCK_NODE, "231")
                writeLine(GPU_MIN_PWRLEVEL_NODE, "13")
                writeLine(SCHED_BOOST_NODE, "0")

                // Reset min frequencies to hardware minimums
                writeLine(POLICY0_MIN_FREQ, "364800")
                writeLine(POLICY2_MIN_FREQ, "499200")
                writeLine(POLICY5_MIN_FREQ, "499200")
                writeLine(POLICY7_MIN_FREQ, "480000")

                // Restore default Snapdragon WALT governor
                writeLine(POLICY0_GOV, "walt")
                writeLine(POLICY2_GOV, "walt")
                writeLine(POLICY5_GOV, "walt")
                writeLine(POLICY7_GOV, "walt")

                // Restore hardware max frequencies
                writeLine(POLICY0_MAX_FREQ, "2265600")
                writeLine(POLICY2_MAX_FREQ, "3148800")
                writeLine(POLICY5_MAX_FREQ, "2956800")
                writeLine(POLICY7_MAX_FREQ, "3398400")

                // Balanced rate limits
                writeLine(POLICY0_UP_RATE, "2000")
                writeLine(POLICY2_UP_RATE, "2000")
                writeLine(POLICY5_UP_RATE, "2000")
                writeLine(POLICY7_UP_RATE, "4000")

                writeLine(POLICY0_DOWN_RATE, "5000")
                writeLine(POLICY2_DOWN_RATE, "5000")
                writeLine(POLICY5_DOWN_RATE, "5000")
                writeLine(POLICY7_DOWN_RATE, "10000")

                // Disable predicted load spikes
                writeLine(POLICY0_PL, "0")
                writeLine(POLICY2_PL, "0")
                writeLine(POLICY5_PL, "0")
                writeLine(POLICY7_PL, "0")
            }

            PROFILE_PERFORMANCE -> {
                // Restore GPU default scaling
                writeLine(GPU_MIN_CLOCK_NODE, "231")
                writeLine(GPU_MIN_PWRLEVEL_NODE, "13")
                writeLine(SCHED_BOOST_NODE, "0")

                // Reset min frequencies to hardware minimums
                writeLine(POLICY0_MIN_FREQ, "364800")
                writeLine(POLICY2_MIN_FREQ, "499200")
                writeLine(POLICY5_MIN_FREQ, "499200")
                writeLine(POLICY7_MIN_FREQ, "480000")

                // High-performance governor (or walt with max boost)
                writeLine(POLICY0_GOV, "performance")
                writeLine(POLICY2_GOV, "performance")
                writeLine(POLICY5_GOV, "performance")
                writeLine(POLICY7_GOV, "performance")

                // Maximum frequencies
                writeLine(POLICY0_MAX_FREQ, "2265600")
                writeLine(POLICY2_MAX_FREQ, "3148800")
                writeLine(POLICY5_MAX_FREQ, "2956800")
                writeLine(POLICY7_MAX_FREQ, "3398400")

                // Instant response rate limits
                writeLine(POLICY0_UP_RATE, "0")
                writeLine(POLICY2_UP_RATE, "0")
                writeLine(POLICY5_UP_RATE, "0")
                writeLine(POLICY7_UP_RATE, "0")

                writeLine(POLICY0_DOWN_RATE, "20000")
                writeLine(POLICY2_DOWN_RATE, "20000")
                writeLine(POLICY5_DOWN_RATE, "20000")
                writeLine(POLICY7_DOWN_RATE, "30000")

                // Enable predicted load for gaming responsiveness
                writeLine(POLICY0_PL, "1")
                writeLine(POLICY2_PL, "1")
                writeLine(POLICY5_PL, "1")
                writeLine(POLICY7_PL, "1")
            }

            PROFILE_DIABLO -> {
                // Pin GPU to peak 1000 MHz clock and Power Level 0
                writeLine(GPU_MIN_CLOCK_NODE, "1000")
                writeLine(GPU_MIN_PWRLEVEL_NODE, "0")

                // Enable kernel scheduler boost for instant task migration
                writeLine(SCHED_BOOST_NODE, "1")

                // Maximum frequencies
                writeLine(POLICY0_MAX_FREQ, "2265600")
                writeLine(POLICY2_MAX_FREQ, "3148800")
                writeLine(POLICY5_MAX_FREQ, "2956800")
                writeLine(POLICY7_MAX_FREQ, "3398400")

                // Pin high minimum clock frequencies across all clusters
                writeLine(POLICY0_MIN_FREQ, "1804800") // 1.80 GHz Little
                writeLine(POLICY2_MIN_FREQ, "2438400") // 2.43 GHz Mid-1
                writeLine(POLICY5_MIN_FREQ, "2438400") // 2.43 GHz Mid-2
                writeLine(POLICY7_MIN_FREQ, "2630400") // 2.63 GHz Prime

                // Performance governor
                writeLine(POLICY0_GOV, "performance")
                writeLine(POLICY2_GOV, "performance")
                writeLine(POLICY5_GOV, "performance")
                writeLine(POLICY7_GOV, "performance")

                // Zero up rate limit, ultra long down rate limit
                writeLine(POLICY0_UP_RATE, "0")
                writeLine(POLICY2_UP_RATE, "0")
                writeLine(POLICY5_UP_RATE, "0")
                writeLine(POLICY7_UP_RATE, "0")

                writeLine(POLICY0_DOWN_RATE, "50000")
                writeLine(POLICY2_DOWN_RATE, "50000")
                writeLine(POLICY5_DOWN_RATE, "50000")
                writeLine(POLICY7_DOWN_RATE, "50000")

                writeLine(POLICY0_PL, "1")
                writeLine(POLICY2_PL, "1")
                writeLine(POLICY5_PL, "1")
                writeLine(POLICY7_PL, "1")

                // Engage active cooling fan to Level 4 to sustain peak thermal dissipation
                org.lineageos.settings.fan.FanController.applySettings(context, true, 4)
            }
        }
    }

    fun restoreSettings(context: Context) {
        val profile = getPowerProfile(context)
        applyPowerProfile(context, profile)
    }
}
