/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.optimizer

import android.content.Context
import android.util.Log
import android.widget.Toast
import org.lineageos.settings.utils.SettingsUtils
import java.io.File

object GameOptimizerController {

    private const val TAG = "GameOptimizerController"

    const val KEY_UNLOCK_120FPS = "game_opt_120fps"
    const val KEY_NATIVE_RESOLUTION = "game_opt_native_res"
    const val KEY_ULTRA_HDR = "game_opt_ultra_hdr"
    const val KEY_GAME_SPOOF = "game_opt_model_spoof"

    private val PACKAGES = listOf(
        "com.pubg.imobile",
        "com.tencent.ig",
        "com.pubg.krmobile",
        "com.vng.pubgmobile"
    )

    fun is120FpsEnabled(context: Context): Boolean = SettingsUtils.getInt(context, KEY_UNLOCK_120FPS, 1) == 1
    fun isNativeResEnabled(context: Context): Boolean = SettingsUtils.getInt(context, KEY_NATIVE_RESOLUTION, 1) == 1
    fun isUltraHdrEnabled(context: Context): Boolean = SettingsUtils.getInt(context, KEY_ULTRA_HDR, 1) == 1
    fun isModelSpoofEnabled(context: Context): Boolean = SettingsUtils.getInt(context, KEY_GAME_SPOOF, 1) == 1

    fun set120FpsEnabled(context: Context, enabled: Boolean) {
        SettingsUtils.putInt(context, KEY_UNLOCK_120FPS, if (enabled) 1 else 0)
    }

    fun setNativeResEnabled(context: Context, enabled: Boolean) {
        SettingsUtils.putInt(context, KEY_NATIVE_RESOLUTION, if (enabled) 1 else 0)
    }

    fun setUltraHdrEnabled(context: Context, enabled: Boolean) {
        SettingsUtils.putInt(context, KEY_ULTRA_HDR, if (enabled) 1 else 0)
    }

    fun setModelSpoofEnabled(context: Context, enabled: Boolean) {
        SettingsUtils.putInt(context, KEY_GAME_SPOOF, if (enabled) 1 else 0)
    }

    private fun patchBytes(data: ByteArray, key: String, targetVal: Byte) {
        val keyBytes = key.toByteArray(Charsets.US_ASCII)
        val intPropBytes = "IntProperty".toByteArray(Charsets.US_ASCII)
        val len = data.size
        val klen = keyBytes.size

        for (i in 0..(len - klen - 30)) {
            var match = true
            for (k in 0 until klen) {
                if (data[i + k] != keyBytes[k]) {
                    match = false
                    break
                }
            }
            if (match) {
                for (j in (i + klen) until (i + klen + 30)) {
                    var intMatch = true
                    for (m in intPropBytes.indices) {
                        if (j + m >= len || data[j + m] != intPropBytes[m]) {
                            intMatch = false
                            break
                        }
                    }
                    if (intMatch) {
                        val valPos = j + intPropBytes.size + 9
                        if (valPos < len) {
                            data[valPos] = targetVal
                            Log.i(TAG, "Patched " + key + " -> " + targetVal + " at index " + valPos)
                        }
                        break
                    }
                }
            }
        }
    }

    fun applyGameOptimizations(context: Context): String {
        try {
            android.provider.Settings.System.putFloat(context.contentResolver, "peak_refresh_rate", 120.0f)
            android.provider.Settings.System.putFloat(context.contentResolver, "min_refresh_rate", 120.0f)
            android.provider.Settings.System.putInt(context.contentResolver, "user_refresh_rate", 120)
            android.os.SystemProperties.set("persist.sys.game.fps", "120")
        } catch (e: Exception) {
            Log.e(TAG, "Error setting refresh rate", e)
        }

        for (pkg in PACKAGES) {
            val basePath = "/data/media/0/Android/data/" + pkg + "/files/UE4Game/ShadowTrackerExtra/ShadowTrackerExtra/Saved"
            val savePath = basePath + "/SaveGames/Active.sav"
            val tmpPath = "/data/local/tmp/Active.sav"

            try {
                val p1 = Runtime.getRuntime().exec(arrayOf("su", "-c", "cp -f " + savePath + " " + tmpPath + " && chmod 666 " + tmpPath))
                p1.waitFor()

                val tmpFile = File(tmpPath)
                if (tmpFile.exists() && tmpFile.length() > 0) {
                    val bytes = tmpFile.readBytes()
                    patchBytes(bytes, "FPSLevel", 7)
                    patchBytes(bytes, "BattleFPS", 7)
                    patchBytes(bytes, "LobbyFPS", 7)
                    patchBytes(bytes, "MainCityFPS", 7)
                    patchBytes(bytes, "BattleRenderQuality", 2)
                    patchBytes(bytes, "LobbyRenderQuality", 2)

                    tmpFile.writeBytes(bytes)

                    val iniCmd = "mkdir -p " + basePath + "/Config/Android && printf '[ScalabilityGroups]\nsg.ResolutionQuality=100.000000\nsg.ViewDistanceQuality=3\nsg.AntiAliasingQuality=3\nsg.ShadowQuality=0\nsg.PostProcessQuality=3\nsg.TextureQuality=3\nsg.EffectsQuality=3\nsg.FoliageQuality=3\nsg.ShadingQuality=3\n\n[/Script/Engine.Console]\nr.PUBGDeviceFPSPolicy=8\nr.PUBGQualityLevel=4\nr.MobileContentScaleFactor=2.0\n' > " + basePath + "/Config/Android/UserSettings.ini && cp -f " + tmpPath + " " + savePath + " && chmod 777 " + savePath + " && chmod -R 777 " + basePath + "/Config && rm -f " + tmpPath
                    val p2 = Runtime.getRuntime().exec(arrayOf("su", "-c", iniCmd))
                    p2.waitFor()
                    Log.i(TAG, "Successfully patched BGMI Active.sav and UserSettings.ini for " + pkg)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error patching save for " + pkg, e)
            }
        }

        val msg = "✅ BGMI Patched: HD Graphics + 120 FPS Unlocked in Active.sav!"
        Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
        return msg
    }
}
