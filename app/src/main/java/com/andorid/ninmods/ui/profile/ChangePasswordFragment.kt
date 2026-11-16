package com.andorid.ninmods.ui.profile

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.andorid.ninmods.R
import com.andorid.ninmods.databinding.FragmentChangePasswordBinding
import com.andorid.ninmods.network.AuthApiClient
import com.andorid.ninmods.ui.MainActivity
import kotlinx.coroutines.launch

/**
 * Password update. Validates length and confirmation locally, then pushes the
 * new password to the server under the current bearer token.
 */
class ChangePasswordFragment : Fragment() {

    private var _binding: FragmentChangePasswordBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentChangePasswordBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.buttonUpdatePassword.setOnClickListener { submit() }
    }

    private fun submit() {
        val password = binding.inputNew.text?.toString().orEmpty()
        val confirm = binding.inputConfirm.text?.toString().orEmpty()

        if (password.length < MIN_LENGTH) {
            binding.inputNewLayout.error = getString(R.string.signup_error_password_short)
            return
        }
        binding.inputNewLayout.error = null

        if (password != confirm) {
            binding.inputConfirmLayout.error = getString(R.string.signup_error_password_mismatch)
            return
        }
        binding.inputConfirmLayout.error = null

        val host = activity as? MainActivity ?: return
        val token = host.sessionManager.authToken ?: run {
            Toast.makeText(requireContext(), R.string.profile_no_token, Toast.LENGTH_LONG).show()
            return
        }

        binding.buttonUpdatePassword.isEnabled = false
        binding.progressUpdate.visibility = View.VISIBLE

        viewLifecycleOwner.lifecycleScope.launch {
            val result = AuthApiClient.updatePassword(token, password)
            if (_binding == null) return@launch

            binding.progressUpdate.visibility = View.GONE
            binding.buttonUpdatePassword.isEnabled = true

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
                        error.message ?: getString(R.string.error_generic),
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

    private companion object {
        const val MIN_LENGTH = 6
    }
}
