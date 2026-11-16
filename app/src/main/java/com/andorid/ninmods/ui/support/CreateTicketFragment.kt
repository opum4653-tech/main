package com.andorid.ninmods.ui.support

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.andorid.ninmods.R
import com.andorid.ninmods.databinding.FragmentCreateTicketBinding
import com.andorid.ninmods.network.AuthApiClient
import com.andorid.ninmods.ui.MainActivity
import kotlinx.coroutines.launch

/**
 * New ticket form. Subject + body with inline validation and a priority
 * toggle group defaulting to medium.
 */
class CreateTicketFragment : Fragment() {

    private var _binding: FragmentCreateTicketBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCreateTicketBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.groupPriority.check(R.id.priority_medium)
        binding.buttonSubmitTicket.setOnClickListener { submit() }
    }

    private fun submit() {
        val subject = binding.inputSubject.text?.toString()?.trim().orEmpty()
        if (subject.isBlank()) {
            binding.inputSubjectLayout.error = getString(R.string.bug_error_title)
            return
        }
        binding.inputSubjectLayout.error = null

        val body = binding.inputBody.text?.toString()?.trim().orEmpty()
        if (body.isBlank()) {
            binding.inputBodyLayout.error = getString(R.string.bug_error_description)
            return
        }
        binding.inputBodyLayout.error = null

        val priority = when (binding.groupPriority.checkedButtonId) {
            R.id.priority_low -> "low"
            R.id.priority_high -> "high"
            else -> "medium"
        }

        val host = activity as? MainActivity ?: return
        val token = host.sessionManager.authToken ?: return

        binding.buttonSubmitTicket.isEnabled = false
        binding.progressSubmit.visibility = View.VISIBLE

        viewLifecycleOwner.lifecycleScope.launch {
            val result = AuthApiClient.createMyTicket(token, subject, body, priority)
            if (_binding == null) return@launch

            binding.progressSubmit.visibility = View.GONE
            binding.buttonSubmitTicket.isEnabled = true

            result.fold(
                onSuccess = {
                    Toast.makeText(
                        requireContext(),
                        R.string.state_completed,
                        Toast.LENGTH_LONG
                    ).show()
                    parentFragmentManager.popBackStack()
                },
                onFailure = { error ->
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
        _binding = null
    }
}
