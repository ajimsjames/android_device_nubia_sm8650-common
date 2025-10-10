/*
 * SPDX-FileCopyrightText: 2025 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.fan

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

import org.lineageos.settings.R
import org.lineageos.settings.utils.*

class FanTileService : TileService() {

    override fun onStartListening() {
        updateQsState()
        super.onStartListening()
    }

    override fun onClick() {
        val tile = qsTile
        val currentState = getInt(this, FanController.KEY_FAN_ENABLE, 0) == 1

        FanController.setFanEnabled(this, !currentState)

        updateQsState()
        super.onClick()
    }

    private fun updateQsState() {
        val isFanEnabled = getInt(this, FanController.KEY_FAN_ENABLE, 0) == 1

        qsTile.apply {
            state = if (isFanEnabled) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            subtitle = if (isFanEnabled) getString(R.string.qs_tile_on) else getString(R.string.qs_tile_off)
        }.updateTile()
    }
}
