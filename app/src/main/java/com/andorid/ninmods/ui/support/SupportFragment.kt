package com.andorid.ninmods.ui.support

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.andorid.ninmods.R
import com.andorid.ninmods.databinding.FragmentSupportBinding
import com.andorid.ninmods.network.AuthApiClient
import com.andorid.ninmods.network.UserTicketItem
import com.andorid.ninmods.ui.MainActivity
import kotlinx.coroutines.launch

/**
 * Support inbox. Lists the account's tickets with a search filter; tapping a
 * row opens the chat, and the new-ticket button pushes the create form.
 */
class SupportFragment : Fragment() {

    private var _binding: FragmentSupportBinding? = null
    private val binding get() = _binding!!

    private val adapter = TicketAdapter { ticket -> openChat(ticket) }

    private var allTickets: List<UserTicketItem> = emptyList()
    private var query: String = ""

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSupportBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.ticketList.layoutManager = LinearLayoutManager(requireContext())
        binding.ticketList.adapter = adapter

        binding.inputSearch.doAfterTextChanged { editable ->
            query = editable?.toString().orEmpty().trim()
            applyFilter()
        }

        binding.buttonNewTicket.setOnClickListener {
            (activity as? MainActivity)?.openSubScreen(
                CreateTicketFragment(), R.string.nav_create_ticket
            )
        }
        binding.supportRefresh.setOnRefreshListener { load() }

        load()
    }

    private fun load() {
        val host = activity as? MainActivity ?: return
        val token = host.sessionManager.authToken ?: return

        binding.supportRefresh.isRefreshing = true
        viewLifecycleOwner.lifecycleScope.launch {
            val result = AuthApiClient.fetchMyTickets(token)
            if (_binding == null) return@launch

            binding.supportRefresh.isRefreshing = false
            result.fold(
                onSuccess = { tickets ->
                    allTickets = tickets
                    applyFilter()
                },
                onFailure = { error ->
                    allTickets = emptyList()
                    applyFilter()
                    Toast.makeText(
                        requireContext(),
                        error.message ?: getString(R.string.state_offline),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            )
        }
    }

    private fun applyFilter() {
        val filtered = allTickets.filter { ticket ->
            query.isBlank() ||
                ticket.subject.contains(query, true) ||
                ticket.department.contains(query, true) ||
                ticket.id.toString() == query
        }
        adapter.submitList(filtered)
        binding.textEmpty.visibility =
            if (filtered.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun openChat(ticket: UserTicketItem) {
        (activity as? MainActivity)?.openSubScreen(
            TicketChatFragment.newInstance(ticket.id, ticket.subject),
            R.string.nav_ticket_chat
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding.ticketList.adapter = null
        _binding = null
    }
}
