/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.power

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import org.lineageos.settings.R

class PowerProfileTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        updateTileState()
    }

    override fun onClick() {
        super.onClick()
        val currentProfile = PowerProfileController.getProfile(this)
        val nextProfile = (currentProfile + 1) % 4
        PowerProfileController.setProfile(this, nextProfile)
        updateTileState()
    }

    private fun updateTileState() {
        val tile = qsTile ?: return
        val profile = PowerProfileController.getProfile(this)

        tile.state = Tile.STATE_ACTIVE
        tile.label = getString(R.string.power_profile_title)
        tile.subtitle = when (profile) {
            PowerProfileController.PROFILE_BATTERY_SAVER -> getString(R.string.power_profile_battery_saver)
            PowerProfileController.PROFILE_BALANCED -> getString(R.string.power_profile_balanced)
            PowerProfileController.PROFILE_PERFORMANCE -> getString(R.string.power_profile_performance)
            PowerProfileController.PROFILE_DIABLO -> getString(R.string.power_profile_diablo)
            else -> getString(R.string.power_profile_balanced)
        }
        tile.updateTile()
    }
}
