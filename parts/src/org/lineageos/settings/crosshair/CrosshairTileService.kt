/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.crosshair

import android.content.Intent
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import org.lineageos.settings.utils.SettingsUtils

class CrosshairTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        updateTileState()
    }

    override fun onClick() {
        super.onClick()
        val isEnabled = SettingsUtils.getInt(this, CrosshairOverlayService.KEY_CROSSHAIR_ENABLE, 0) == 1
        val newState = !isEnabled
        SettingsUtils.putInt(this, CrosshairOverlayService.KEY_CROSSHAIR_ENABLE, if (newState) 1 else 0)

        val intent = Intent(this, CrosshairOverlayService::class.java)
        if (newState) {
            startService(intent)
        } else {
            stopService(intent)
        }

        updateTileState()
    }

    private fun updateTileState() {
        val isEnabled = SettingsUtils.getInt(this, CrosshairOverlayService.KEY_CROSSHAIR_ENABLE, 0) == 1
        qsTile?.let { tile ->
            tile.state = if (isEnabled) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            tile.label = "Aim Assist"
            tile.subtitle = if (isEnabled) "Crosshair On" else "Off"
            tile.updateTile()
        }
    }
}
