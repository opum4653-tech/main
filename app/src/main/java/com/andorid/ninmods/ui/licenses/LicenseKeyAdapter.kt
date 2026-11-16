package com.andorid.ninmods.ui.licenses

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.andorid.ninmods.R
import com.andorid.ninmods.databinding.ItemLicenseKeyBinding
import com.andorid.ninmods.network.UserLicenseItem
import java.util.Locale

/**
 * License key rows. Shows the key, its game type / plan badge, and gives copy
 * plus HWID-reset actions. Reset disables itself once the resets are used up.
 */
class LicenseKeyAdapter(
    private val onCopy: (String) -> Unit,
    private val onReset: (UserLicenseItem) -> Unit
) : ListAdapter<UserLicenseItem, LicenseKeyAdapter.Holder>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val binding = ItemLicenseKeyBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return Holder(binding)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(getItem(position))

    inner class Holder(private val binding: ItemLicenseKeyBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: UserLicenseItem) {
            val context = binding.root.context

            val gameType = item.gameType.takeIf { it.isNotBlank() }?.uppercase(Locale.US) ?: "ALL"
            val duration = item.durationType.takeIf { it.isNotBlank() }?.uppercase(Locale.US)
                ?: "${item.durationHours}H"

            binding.keyLabel.text = gameType
            binding.keyValue.text = item.licenseKey

            val active = item.status.equals("active", ignoreCase = true) &&
                item.remainingSeconds > 0L

            binding.keyWallet.text = "$duration · " + item.status.uppercase(Locale.US)
            binding.keyWallet.setTextColor(
                ContextCompat.getColor(
                    context,
                    if (active) R.color.nin_success else R.color.nin_text_secondary
                )
            )

            binding.keyCopy.setOnClickListener { onCopy(item.licenseKey) }

            val canReset = item.resetsCount < item.maxResets
            binding.keyReset.isEnabled = canReset
            binding.keyReset.alpha = if (canReset) 1f else 0.4f
            binding.keyReset.setOnClickListener {
                if (canReset) onReset(item)
            }

            val bound = item.deviceModel?.takeIf { it.isNotBlank() }
                ?: item.hwid?.take(16)?.plus("…")
            binding.keyReset.contentDescription = if (bound == null) {
                context.getString(R.string.licenses_unbound)
            } else {
                context.getString(R.string.licenses_bound_device, bound)
            }
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<UserLicenseItem>() {
            override fun areItemsTheSame(oldItem: UserLicenseItem, newItem: UserLicenseItem) =
                oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: UserLicenseItem, newItem: UserLicenseItem) =
                oldItem == newItem
        }
    }
}
