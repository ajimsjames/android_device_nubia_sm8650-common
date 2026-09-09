/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.optimizer

import android.os.Bundle
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import org.lineageos.settings.R

class GameOptimizerFragment : PreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.game_optimizer_settings, rootKey)

        findPreference<Preference>("game_opt_apply_now")?.apply {
            onPreferenceClickListener = Preference.OnPreferenceClickListener {
                GameOptimizerController.applyGameOptimizations(requireContext())
                true
            }
        }
    }
}
