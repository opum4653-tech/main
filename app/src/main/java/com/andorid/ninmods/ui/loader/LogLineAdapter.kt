package com.andorid.ninmods.ui.loader

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.andorid.ninmods.R
import com.andorid.ninmods.databinding.ItemLogLineBinding

/**
 * Terminal log list. Colors each line by its leading tag so the driver
 * loader output stays readable at a glance.
 */
class LogLineAdapter : ListAdapter<String, LogLineAdapter.Holder>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val binding = ItemLogLineBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return Holder(binding)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(getItem(position))

    fun submit(lines: List<String>) {
        submitList(lines)
    }

    inner class Holder(private val binding: ItemLogLineBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(line: String) {
            val view = binding.logLine
            view.text = line
            val colorRes = when {
                line.startsWith("[ERROR]") || line.startsWith("[-]") -> R.color.nin_log_error
                line.startsWith("[SUCCESS]") || line.startsWith("[+]") -> R.color.nin_success
                line.startsWith("[SYSTEM]") -> R.color.nin_log_system
                line.startsWith("[ROOT]") -> R.color.nin_log_root
                line.startsWith("[CANCEL]") -> R.color.nin_log_cancel
                line.startsWith("[SELECT]") || line.startsWith("[LOADER]") -> R.color.nin_primary
                else -> R.color.nin_log_default
            }
            view.setTextColor(ContextCompat.getColor(view.context, colorRes))
            view.invalidate()
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<String>() {
            override fun areItemsTheSame(oldItem: String, newItem: String) =
                oldItem == newItem

            override fun areContentsTheSame(oldItem: String, newItem: String) =
                oldItem == newItem
        }
    }
}
