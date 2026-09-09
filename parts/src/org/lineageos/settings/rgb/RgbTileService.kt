/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.rgb

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

class RgbTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        updateTile()
    }

    override fun onClick() {
        super.onClick()
        val enabled = !RgbController.isRgbEnabled(this)
        RgbController.setRgbEnabled(this, enabled)
        updateTile()
    }

    private fun updateTile() {
        val tile = qsTile ?: return
        val enabled = RgbController.isRgbEnabled(this)
        tile.state = if (enabled) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.subtitle = if (enabled) "RGB On" else "RGB Off"
        tile.updateTile()
    }
}
