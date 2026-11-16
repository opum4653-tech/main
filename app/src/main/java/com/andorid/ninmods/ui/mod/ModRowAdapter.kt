package com.andorid.ninmods.ui.mod

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.andorid.ninmods.R
import com.andorid.ninmods.databinding.ItemModRowBinding

/**
 * Mod row list. Kernel and external variants both use this adapter; the
 * [variant] argument only picks the icon frame accent.
 */
class ModRowAdapter(
    private val variant: String,
    private val onClick: (ModItem) -> Unit
) : ListAdapter<ModItem, ModRowAdapter.Holder>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val binding = ItemModRowBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return Holder(binding)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(getItem(position))

    inner class Holder(private val binding: ItemModRowBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: ModItem) {
            val context = binding.root.context
            binding.modName.text = item.name

            val isKernel = variant == ModEngine.KERNEL.cacheDirName
            val subtitleRes = when {
                isKernel && item.isDownloaded -> R.string.dashboard_kernel_subtitle_active
                isKernel -> R.string.dashboard_kernel_subtitle_idle
                item.isDownloaded -> R.string.dashboard_external_subtitle_active
                else -> R.string.dashboard_external_subtitle_idle
            }
            binding.modSubtitle.text = context.getString(subtitleRes)

            val accent = ContextCompat.getColor(
                context,
                if (isKernel) R.color.nin_kernel_accent else R.color.nin_external_accent
            )
            binding.modIconFrame.background?.mutate()?.setTint(accent)
            binding.modIcon.setColorFilter(accent)

            binding.modBadge.text = context.getString(
                if (item.isDownloaded) R.string.state_ready else R.string.state_available
            )
            binding.modBadge.setTextColor(
                ContextCompat.getColor(
                    context,
                    if (item.isDownloaded) R.color.nin_success else R.color.nin_text_secondary
                )
            )

            binding.modUpdate.visibility = View.GONE

            binding.root.setOnClickListener { onClick(item) }
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<ModItem>() {
            override fun areItemsTheSame(oldItem: ModItem, newItem: ModItem) =
                oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: ModItem, newItem: ModItem) =
                oldItem == newItem
        }
    }
}
