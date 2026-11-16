package com.andorid.ninmods.ui.denylist

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.andorid.ninmods.R
import com.andorid.ninmods.data.AppProcessItem
import com.andorid.ninmods.data.InstalledAppItem
import com.andorid.ninmods.databinding.ItemAppRowBinding
import com.andorid.ninmods.databinding.ItemProcessRowBinding

/**
 * Installed apps with expandable process lists. Each process carries a switch
 * that adds or removes that exact package|process pair from the denylist.
 */
class AppListAdapter(
    private val onProcessToggle: (InstalledAppItem, String, Boolean) -> Unit,
    private val onAppToggle: (InstalledAppItem, Boolean) -> Unit
) : ListAdapter<InstalledAppItem, AppListAdapter.Holder>(DIFF) {

    private val expanded = mutableSetOf<String>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val binding = ItemAppRowBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return Holder(binding)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(getItem(position))

    inner class Holder(private val binding: ItemAppRowBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(app: InstalledAppItem) {
            val context = binding.root.context
            binding.appLabel.text = app.appName
            binding.appPackage.text = app.packageName

            binding.appBadge.text = when {
                app.isFullyHidden -> context.getString(R.string.state_protected)
                app.isPartiallyHidden -> context.getString(R.string.state_partial)
                app.isGameOrTarget -> context.getString(R.string.denylist_target_badge)
                else -> context.getString(R.string.state_exposed)
            }
            val badgeColor = when {
                app.isFullyHidden -> R.color.nin_success
                app.isPartiallyHidden -> R.color.nin_warning
                app.isGameOrTarget -> R.color.nin_primary
                else -> R.color.nin_text_secondary
            }
            binding.appBadge.setTextColor(ContextCompat.getColor(context, badgeColor))

            val isOpen = expanded.contains(app.packageName)
            binding.processContainer.visibility = if (isOpen) View.VISIBLE else View.GONE
            binding.appExpand.rotation = if (isOpen) 180f else 0f

            binding.appExpand.setOnClickListener {
                if (isOpen) expanded.remove(app.packageName) else expanded.add(app.packageName)
                notifyItemChanged(bindingAdapterPosition)
            }

            binding.appBadge.setOnClickListener {
                onAppToggle(app, !app.isFullyHidden)
            }

            buildProcessRows(app)
        }

        private fun buildProcessRows(app: InstalledAppItem) {
            binding.processList.removeAllViews()

            val rows = app.processes.ifEmpty {
                listOf(AppProcessItem(app.packageName, false))
            }

            rows.forEach { process ->
                val row = ItemProcessRowBinding.inflate(
                    LayoutInflater.from(binding.root.context),
                    binding.processList,
                    false
                )
                row.processName.text = process.processName
                row.processSwitch.isChecked = process.isHidden
                row.processSwitch.setOnCheckedChangeListener { _, checked ->
                    onProcessToggle(app, process.processName, checked)
                }
                binding.processList.addView(row.root)
            }
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<InstalledAppItem>() {
            override fun areItemsTheSame(oldItem: InstalledAppItem, newItem: InstalledAppItem) =
                oldItem.packageName == newItem.packageName

            override fun areContentsTheSame(oldItem: InstalledAppItem, newItem: InstalledAppItem) =
                oldItem == newItem
        }
    }
}
