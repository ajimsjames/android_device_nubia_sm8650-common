/*
 * SPDX-FileCopyrightText: 2025 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.nubia

import android.os.Bundle
import com.android.settingslib.widget.SettingsBasePreferenceFragment
import org.lineageos.settings.R

class NubiaFragment : SettingsBasePreferenceFragment() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.nubia_preferences, rootKey)
    }
}
