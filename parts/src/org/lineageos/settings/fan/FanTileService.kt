/*
 * SPDX-FileCopyrightText: 2025 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.fan

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import org.lineageos.settings.utils.*

class FanTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        updateQsState()
    }

    override fun onClick() {
        super.onClick()
        val currentState = getInt(this, FanController.KEY_FAN_ENABLE, 0) == 1

        FanController.setFanEnabled(this, !currentState)

        updateQsState()
    }

    private fun updateQsState() {
        val isFanEnabled = getInt(this, FanController.KEY_FAN_ENABLE, 0) == 1

        qsTile
            .apply { state = if (isFanEnabled) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE }
            .updateTile()
    }
}
