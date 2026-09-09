/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import org.lineageos.settings.R
import org.lineageos.settings.fan.FanController
import org.lineageos.settings.nubia.NubiaActivity
import org.lineageos.settings.power.PowerProfileController
import org.lineageos.settings.trigger.TriggerController
import org.lineageos.settings.utils.FileUtils

class GamingHudWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            updateWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            ACTION_TOGGLE_DIABLO -> {
                val cur = PowerProfileController.getProfile(context)
                val next = if (cur == PowerProfileController.PROFILE_DIABLO) {
                    PowerProfileController.PROFILE_BALANCED
                } else {
                    PowerProfileController.PROFILE_DIABLO
                }
                PowerProfileController.setProfile(context, next)
                refreshAllWidgets(context)
            }
            ACTION_TOGGLE_FAN -> {
                val enabled = FanController.isFanEnabled(context)
                FanController.setFanEnabled(context, !enabled)
                refreshAllWidgets(context)
            }
            ACTION_TOGGLE_TRIGGERS -> {
                val enabled = TriggerController.isTriggerEnabled(context)
                TriggerController.setTriggerEnabled(context, !enabled)
                refreshAllWidgets(context)
            }
            ACTION_REFRESH_WIDGET -> {
                refreshAllWidgets(context)
            }
        }
    }

    companion object {
        const val ACTION_REFRESH_WIDGET = "org.lineageos.settings.widget.REFRESH_HUD"
        const val ACTION_TOGGLE_DIABLO = "org.lineageos.settings.widget.TOGGLE_DIABLO"
        const val ACTION_TOGGLE_FAN = "org.lineageos.settings.widget.TOGGLE_FAN"
        const val ACTION_TOGGLE_TRIGGERS = "org.lineageos.settings.widget.TOGGLE_TRIGGERS"

        fun refreshAllWidgets(context: Context) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val componentName = ComponentName(context, GamingHudWidget::class.java)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)
            for (id in appWidgetIds) {
                updateWidget(context, appWidgetManager, id)
            }
        }

        fun updateWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
            val views = RemoteViews(context.packageName, R.layout.widget_gaming_hud)

            // 1. CPU Freq & Power Profile
            val cpuFreqRaw = FileUtils.readOneLine("/sys/devices/system/cpu/cpufreq/policy7/scaling_cur_freq")
            val cpuMhz = cpuFreqRaw?.trim()?.toIntOrNull()?.let { it / 1000 } ?: 3398
            val profile = PowerProfileController.getProfile(context)
            val profileName = when (profile) {
                PowerProfileController.PROFILE_BATTERY_SAVER -> "Eco"
                PowerProfileController.PROFILE_PERFORMANCE -> "Perf"
                PowerProfileController.PROFILE_DIABLO -> "Diablo 🔥"
                else -> "Balanced"
            }
            views.setTextViewText(R.id.widget_cpu_text, "${cpuMhz} MHz ($profileName)")

            // 2. GPU State
            val gpuPwr = FileUtils.readOneLine("/sys/class/kgsl/kgsl-3d0/max_pwrlevel")?.trim() ?: "0"
            val gpuStatus = if (gpuPwr == "0") "1.0 GHz Turbo" else "Lvl $gpuPwr"
            views.setTextViewText(R.id.widget_gpu_text, gpuStatus)

            // 3. Cooling Fan
            val fanOn = FanController.isFanEnabled(context)
            val fanLevel = FileUtils.readOneLine("/sys/kernel/fan/fan_speed_level")?.trim() ?: "1"
            val fanRpm = FanController.getFanRpm(context)
            val fanText = if (fanOn) "Lvl $fanLevel ($fanRpm RPM)" else "OFF"
            views.setTextViewText(R.id.widget_fan_text, fanText)

            // 4. Triggers
            val triggersOn = TriggerController.isTriggerEnabled(context)
            val leftMode = TriggerController.getLeftMode(context)
            val rightMode = TriggerController.getRightMode(context)
            val lStr = if (leftMode == TriggerController.MODE_RAPID_FIRE) "L1: Rapid" else "L1: Tap"
            val rStr = if (rightMode == TriggerController.MODE_RAPID_FIRE) "R1: Rapid" else "R1: Tap"
            val triggerText = if (triggersOn) "$lStr | $rStr" else "Disabled"
            views.setTextViewText(R.id.widget_triggers_text, triggerText)

            // Clicks
            // Tap Header -> Open Nubia Hub
            val openIntent = Intent(context, NubiaActivity::class.java)
            val pendingOpen = PendingIntent.getActivity(
                context, 0, openIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            views.setOnClickPendingIntent(R.id.widget_title, pendingOpen)

            // Tap Refresh
            val refreshIntent = Intent(context, GamingHudWidget::class.java).apply { action = ACTION_REFRESH_WIDGET }
            views.setOnClickPendingIntent(
                R.id.widget_btn_refresh,
                PendingIntent.getBroadcast(context, 1, refreshIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            )

            // Tap CPU Card -> Toggle Diablo
            val diabloIntent = Intent(context, GamingHudWidget::class.java).apply { action = ACTION_TOGGLE_DIABLO }
            views.setOnClickPendingIntent(
                R.id.widget_card_cpu,
                PendingIntent.getBroadcast(context, 2, diabloIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            )

            // Tap Fan Card -> Toggle Fan
            val fanIntent = Intent(context, GamingHudWidget::class.java).apply { action = ACTION_TOGGLE_FAN }
            views.setOnClickPendingIntent(
                R.id.widget_card_fan,
                PendingIntent.getBroadcast(context, 3, fanIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            )

            // Tap Triggers Card -> Toggle Triggers
            val trigIntent = Intent(context, GamingHudWidget::class.java).apply { action = ACTION_TOGGLE_TRIGGERS }
            views.setOnClickPendingIntent(
                R.id.widget_card_triggers,
                PendingIntent.getBroadcast(context, 4, trigIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            )

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}
