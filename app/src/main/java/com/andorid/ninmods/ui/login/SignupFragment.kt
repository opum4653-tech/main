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
import com.andorid.ninmods.databinding.FragmentSignupBinding
import com.andorid.ninmods.network.AuthApiClient
import kotlinx.coroutines.launch

class SignupFragment : Fragment() {

    private var _binding: FragmentSignupBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSignupBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.buttonSignup.setOnClickListener { submit() }
        binding.inputConfirm.setOnEditorActionListener { _, actionId, _ ->
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
        clearErrors()

        val username = binding.inputUsername.text?.toString()?.trim().orEmpty()
        val email = binding.inputEmail.text?.toString()?.trim().orEmpty()
        val password = binding.inputPassword.text?.toString()?.trim().orEmpty()
        val confirm = binding.inputConfirm.text?.toString()?.trim().orEmpty()

        when {
            username.isBlank() -> {
                binding.inputUsernameLayout.error = getString(R.string.signup_error_username_required)
                return
            }
            username.length < 3 -> {
                binding.inputUsernameLayout.error = getString(R.string.signup_error_username_format)
                return
            }
            email.isBlank() -> {
                binding.inputEmailLayout.error = getString(R.string.signup_error_email_required)
                return
            }
            !LoginActivity.looksLikeEmail(email) -> {
                binding.inputEmailLayout.error = getString(R.string.signup_error_email_required)
                return
            }
            password.length < 6 -> {
                binding.inputPasswordLayout.error = getString(R.string.signup_error_password_short)
                return
            }
            password != confirm -> {
                binding.inputConfirmLayout.error = getString(R.string.signup_error_password_mismatch)
                return
            }
        }

        setBusy(true)
        viewLifecycleOwner.lifecycleScope.launch {
            val result = AuthApiClient.signup(
                username = username,
                email = email,
                password = password,
                passwordAgain = confirm
            )
            setBusy(false)

            result.fold(
                onSuccess = {
                    Toast.makeText(requireContext(), R.string.signup_success, Toast.LENGTH_SHORT).show()
                    (activity as? LoginActivity)?.let { host ->
                        host.showAuthScreen(AuthScreenState.LOGIN)
                        host.supportFragmentManager.executePendingTransactions()
                        host.supportFragmentManager.findFragmentById(R.id.auth_container)
                            ?.view?.findViewById<android.widget.EditText>(R.id.input_email)
                            ?.setText(email)
                    }
                },
                onFailure = { error ->
                    binding.inputEmailLayout.error = error.message ?: getString(R.string.error_generic)
                }
            )
        }
    }

    private fun clearErrors() {
        binding.inputUsernameLayout.error = null
        binding.inputEmailLayout.error = null
        binding.inputPasswordLayout.error = null
        binding.inputConfirmLayout.error = null
    }

    private fun setBusy(busy: Boolean) {
        binding.progressSignup.visibility = if (busy) View.VISIBLE else View.GONE
        binding.buttonSignup.isEnabled = !busy
        binding.buttonSignup.text = getString(if (busy) R.string.signup_signing_up else R.string.action_sign_up)
        binding.buttonGoLogin.isEnabled = !busy
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
