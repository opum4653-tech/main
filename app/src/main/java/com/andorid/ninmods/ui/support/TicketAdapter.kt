package com.andorid.ninmods.ui.support

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.andorid.ninmods.R
import com.andorid.ninmods.databinding.ItemTicketRowBinding
import com.andorid.ninmods.network.UserTicketItem
import java.util.Locale

/**
 * Ticket rows. Subject, status pill colored by state, department + priority
 * meta, and the last activity line.
 */
class TicketAdapter(
    private val onClick: (UserTicketItem) -> Unit
) : ListAdapter<UserTicketItem, TicketAdapter.Holder>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val binding = ItemTicketRowBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return Holder(binding)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(getItem(position))

    inner class Holder(private val binding: ItemTicketRowBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(ticket: UserTicketItem) {
            val context = binding.root.context

            binding.ticketSubject.text = ticket.subject.ifBlank {
                context.getString(R.string.support_ticket_title, ticket.id)
            }

            binding.ticketStatus.text = ticket.status.uppercase(Locale.US)
            val statusColor = when (ticket.status.lowercase(Locale.US)) {
                "open", "pending" -> R.color.nin_warning
                "answered", "resolved", "closed" -> R.color.nin_success
                else -> R.color.nin_text_secondary
            }
            binding.ticketStatus.setTextColor(ContextCompat.getColor(context, statusColor))

            binding.ticketMeta.text = context.getString(
                R.string.support_ticket_row, ticket.id, ticket.department
            ) + " · " + ticket.priority.uppercase(Locale.US)

            val activity = ticket.lastReplyAt ?: ticket.createdAt
            binding.ticketActivity.visibility =
                if (activity.isNullOrBlank()) View.GONE else View.VISIBLE
            if (!activity.isNullOrBlank()) {
                binding.ticketActivity.text =
                    context.getString(R.string.support_last_activity, activity)
            }

            binding.root.setOnClickListener { onClick(ticket) }
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<UserTicketItem>() {
            override fun areItemsTheSame(oldItem: UserTicketItem, newItem: UserTicketItem) =
                oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: UserTicketItem, newItem: UserTicketItem) =
                oldItem == newItem
        }
    }
}
