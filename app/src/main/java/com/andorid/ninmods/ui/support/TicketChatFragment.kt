package com.andorid.ninmods.ui.support

import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.andorid.ninmods.R
import com.andorid.ninmods.databinding.FragmentTicketChatBinding
import com.andorid.ninmods.databinding.ItemMessageRowBinding
import com.andorid.ninmods.network.AuthApiClient
import com.andorid.ninmods.network.UserTicketMessageItem
import com.andorid.ninmods.ui.MainActivity
import kotlinx.coroutines.launch

/**
 * Ticket conversation. Loads the message thread, sends replies, and aligns the
 * bubble to the right for messages the account wrote.
 */
class TicketChatFragment : Fragment() {

    private var _binding: FragmentTicketChatBinding? = null
    private val binding get() = _binding!!

    private var ticketId: Int = 0
    private var subject: String = ""
    private val adapter = MessageAdapter()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTicketChatBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val args = requireArguments()
        ticketId = args.getInt(ARG_ID, 0)
        subject = args.getString(ARG_SUBJECT).orEmpty()

        binding.messageList.layoutManager = LinearLayoutManager(requireContext())
        binding.messageList.adapter = adapter

        binding.chatRefresh.setOnRefreshListener { load() }
        binding.buttonSend.setOnClickListener { send() }

        load()
    }

    private fun load() {
        val host = activity as? MainActivity ?: return
        val token = host.sessionManager.authToken ?: return

        binding.chatRefresh.isRefreshing = true
        viewLifecycleOwner.lifecycleScope.launch {
            val result = AuthApiClient.fetchTicketMessages(token, ticketId)
            if (_binding == null) return@launch

            binding.chatRefresh.isRefreshing = false
            result.fold(
                onSuccess = { messages ->
                    adapter.submitList(messages)
                    if (messages.isNotEmpty()) {
                        binding.messageList.scrollToPosition(messages.lastIndex)
                    }
                },
                onFailure = { error ->
                    Toast.makeText(
                        requireContext(),
                        error.message ?: getString(R.string.support_no_messages),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            )
        }
    }

    private fun send() {
        val text = binding.inputMessage.text?.toString()?.trim().orEmpty()
        if (text.isBlank()) return

        val host = activity as? MainActivity ?: return
        val token = host.sessionManager.authToken ?: return

        binding.buttonSend.isEnabled = false
        binding.inputMessage.setText("")

        viewLifecycleOwner.lifecycleScope.launch {
            val result = AuthApiClient.replyTicketMessage(token, ticketId, text)
            if (_binding == null) return@launch
            binding.buttonSend.isEnabled = true

            result.fold(
                onSuccess = { load() },
                onFailure = { error ->
                    binding.inputMessage.setText(text)
                    Toast.makeText(
                        requireContext(),
                        error.message ?: getString(R.string.create_ticket_failed),
                        Toast.LENGTH_LONG
                    ).show()
                }
            )
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding.messageList.adapter = null
        _binding = null
    }

    /** Bubbles align by author; the sender name only shows on the first line. */
    private class MessageAdapter :
        ListAdapter<UserTicketMessageItem, MessageAdapter.Holder>(DIFF) {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            val binding = ItemMessageRowBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
            )
            return Holder(binding)
        }

        override fun onBindViewHolder(holder: Holder, position: Int) =
            holder.bind(getItem(position))

        inner class Holder(private val binding: ItemMessageRowBinding) :
            RecyclerView.ViewHolder(binding.root) {

            fun bind(item: UserTicketMessageItem) {
                val context = binding.root.context
                val fromUser = item.senderType.equals("user", ignoreCase = true)

                binding.messageText.text = item.message
                binding.messageSender.text = item.senderName

                binding.messageSender.gravity =
                    if (fromUser) Gravity.END else Gravity.START

                val params = binding.messageBubble.layoutParams as LinearLayout.LayoutParams
                params.gravity = if (fromUser) Gravity.END else Gravity.START
                binding.messageBubble.layoutParams = params

                binding.messageBubble.setCardBackgroundColor(
                    ContextCompat.getColor(
                        context,
                        if (fromUser) R.color.nin_primary_container else R.color.nin_surface_variant
                    )
                )

                binding.messageTime.visibility =
                    if (item.createdAt.isNullOrBlank()) View.GONE else View.VISIBLE
                binding.messageTime.text = item.createdAt.orEmpty()
            }
        }

        companion object {
            private val DIFF = object : DiffUtil.ItemCallback<UserTicketMessageItem>() {
                override fun areItemsTheSame(
                    oldItem: UserTicketMessageItem,
                    newItem: UserTicketMessageItem
                ) = oldItem.id == newItem.id

                override fun areContentsTheSame(
                    oldItem: UserTicketMessageItem,
                    newItem: UserTicketMessageItem
                ) = oldItem == newItem
            }
        }
    }

    companion object {
        private const val ARG_ID = "arg_ticket_id"
        private const val ARG_SUBJECT = "arg_ticket_subject"

        fun newInstance(ticketId: Int, subject: String): TicketChatFragment =
            TicketChatFragment().apply {
                arguments = Bundle().apply {
                    putInt(ARG_ID, ticketId)
                    putString(ARG_SUBJECT, subject)
                }
            }
    }
}
