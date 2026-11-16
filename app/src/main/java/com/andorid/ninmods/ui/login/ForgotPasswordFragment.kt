package com.andorid.ninmods.ui.login

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.andorid.ninmods.R
import com.andorid.ninmods.databinding.FragmentForgotPasswordBinding
import com.andorid.ninmods.network.AuthApiClient
import kotlinx.coroutines.launch

class ForgotPasswordFragment : Fragment() {

    private var _binding: FragmentForgotPasswordBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentForgotPasswordBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.buttonSendReset.setOnClickListener { submit() }
        binding.inputEmail.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                submit()
                true
            } else {
                false
            }
        }
        binding.buttonGoLogin.setOnClickListener {
            (activity as? LoginActivity)?.showAuthScreen(AuthScreenState.LOGIN)
        }
    }

    private fun submit() {
        binding.inputEmailLayout.error = null
        val email = binding.inputEmail.text?.toString()?.trim().orEmpty()

        if (email.isBlank() || !LoginActivity.looksLikeEmail(email)) {
            binding.inputEmailLayout.error = getString(R.string.forgot_password_error_identifier)
            return
        }

        setBusy(true)
        viewLifecycleOwner.lifecycleScope.launch {
            val result = AuthApiClient.resetPassword(usernameOrEmail = email)
            setBusy(false)

            result.fold(
                onSuccess = { response ->
                    Toast.makeText(
                        requireContext(),
                        response.message.ifBlank {
                            getString(R.string.forgot_password_sent, email)
                        },
                        Toast.LENGTH_LONG
                    ).show()
                    (activity as? LoginActivity)?.showAuthScreen(AuthScreenState.LOGIN)
                },
                onFailure = { error ->
                    binding.inputEmailLayout.error = error.message ?: getString(R.string.error_generic)
                }
            )
        }
    }

    private fun setBusy(busy: Boolean) {
        binding.progressReset.visibility = if (busy) View.VISIBLE else View.GONE
        binding.buttonSendReset.isEnabled = !busy
        binding.buttonSendReset.text =
            getString(if (busy) R.string.forgot_password_sending else R.string.forgot_password_send)
        binding.buttonGoLogin.isEnabled = !busy
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
