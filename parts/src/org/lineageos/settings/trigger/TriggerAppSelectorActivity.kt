/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.trigger

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.android.settingslib.collapsingtoolbar.CollapsingToolbarBaseActivity
import com.android.settingslib.collapsingtoolbar.R as ToolbarR
import org.lineageos.settings.R
import kotlin.concurrent.thread

class TriggerAppSelectorActivity : CollapsingToolbarBaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setTitle(R.string.trigger_select_apps_title)

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(ToolbarR.id.content_frame, TriggerAppSelectorFragment())
                .commit()
        }
    }
}

class TriggerAppSelectorFragment : Fragment() {

    private data class AppItem(
        val label: String,
        val packageName: String,
        val icon: Drawable,
        var isSelected: Boolean
    )

    private val appList = mutableListOf<AppItem>()
    private var adapter: AppAdapter? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val recyclerView = RecyclerView(requireContext()).apply {
            layoutManager = LinearLayoutManager(context)
        }

        adapter = AppAdapter(appList) { item ->
            item.isSelected = !item.isSelected
            saveSelections()
        }
        recyclerView.adapter = adapter

        loadApps()
        return recyclerView
    }

    private fun loadApps() {
        val context = context ?: return
        thread {
            val pm = context.packageManager
            val selected = TriggerController.getSelectedApps(context)
            val packages = pm.getInstalledApplications(PackageManager.GET_META_DATA)

            val items = packages
                .filter { (it.flags and ApplicationInfo.FLAG_SYSTEM) == 0 || it.packageName.contains("game", ignoreCase = true) }
                .map { appInfo ->
                    AppItem(
                        label = appInfo.loadLabel(pm).toString(),
                        packageName = appInfo.packageName,
                        icon = appInfo.loadIcon(pm),
                        isSelected = selected.contains(appInfo.packageName)
                    )
                }
                .sortedBy { it.label.lowercase() }

            activity?.runOnUiThread {
                appList.clear()
                appList.addAll(items)
                adapter?.notifyDataSetChanged()
            }
        }
    }

    private fun saveSelections() {
        val ctx = context ?: return
        val selected = appList.filter { it.isSelected }.map { it.packageName }.toSet()
        TriggerController.setSelectedApps(ctx, selected)
    }

    private class AppAdapter(
        private val list: List<AppItem>,
        private val onClick: (AppItem) -> Unit
    ) : RecyclerView.Adapter<AppAdapter.ViewHolder>() {

        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val icon: ImageView = view.findViewById(android.R.id.icon)
            val title: TextView = view.findViewById(android.R.id.title)
            val summary: TextView = view.findViewById(android.R.id.summary)
            val checkbox: CheckBox = view.findViewById(android.R.id.checkbox)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val row = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_app_select, parent, false)
            return ViewHolder(row)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = list[position]
            holder.title.text = item.label
            holder.summary.text = item.packageName
            holder.icon.setImageDrawable(item.icon)
            holder.checkbox.isChecked = item.isSelected

            holder.itemView.setOnClickListener {
                onClick(item)
                holder.checkbox.isChecked = item.isSelected
            }
        }

        override fun getItemCount(): Int = list.size
    }
}
