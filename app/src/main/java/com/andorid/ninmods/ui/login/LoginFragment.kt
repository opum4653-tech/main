package com.andorid.ninmods.ui.login

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.andorid.ninmods.R
import com.andorid.ninmods.databinding.FragmentLoginBinding
import com.andorid.ninmods.network.AuthApiClient
import com.andorid.ninmods.data.SessionManager
import com.andorid.ninmods.util.DeviceIdentifier
import kotlinx.coroutines.launch

class LoginFragment : Fragment() {

    private var _binding: FragmentLoginBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLoginBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val session = SessionManager(requireContext())
        binding.inputEmail.setText(session.userEmail.orEmpty())
        binding.checkRemember.isChecked = session.rememberLogin

        binding.buttonLogin.setOnClickListener { submit() }
        binding.inputPassword.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                submit()
                true
            } else {
                false
            }
        }
        binding.buttonForgotPassword.setOnClickListener {
            (activity as? LoginActivity)?.showAuthScreen(AuthScreenState.FORGOT_PASSWORD)
        }
        binding.buttonGoSignup.setOnClickListener {
            (activity as? LoginActivity)?.showAuthScreen(AuthScreenState.SIGNUP)
        }
    }

    private fun submit() {
        binding.inputEmailLayout.error = null
        binding.inputPasswordLayout.error = null

        val email = binding.inputEmail.text?.toString()?.trim().orEmpty()
        val password = binding.inputPassword.text?.toString()?.trim().orEmpty()

        if (email.isBlank()) {
            binding.inputEmailLayout.error = getString(R.string.login_error_empty)
            return
        }
        if (password.isBlank()) {
            binding.inputPasswordLayout.error = getString(R.string.login_error_empty)
            return
        }

        setBusy(true)
        viewLifecycleOwner.lifecycleScope.launch {
            val hwid = DeviceIdentifier.getHwid(requireContext())
            val result = AuthApiClient.login(usernameOrEmail = email, password = password, hwid = hwid)
            setBusy(false)

            result.fold(
                onSuccess = { response ->
                    val license = response.activeLicense
                    (activity as? LoginActivity)?.onLoginSucceeded(
                        email = response.email.ifBlank { email },
                        name = response.username.ifBlank { email },
                        apiKey = response.apiKey,
                        role = response.role,
                        balance = response.balance,
                        activeKey = license?.licenseKey?.takeIf { it.isNotBlank() },
                        remainingSeconds = license?.remainingSeconds ?: 0L,
                        remember = binding.checkRemember.isChecked
                    )
                },
                onFailure = { error ->
                    val message = error.message ?: getString(R.string.error_generic)
                    binding.inputPasswordLayout.error = message
                }
            )
        }
    }

    private fun setBusy(busy: Boolean) {
        binding.progressLogin.visibility = if (busy) View.VISIBLE else View.GONE
        binding.buttonLogin.isEnabled = !busy
        binding.buttonLogin.text = getString(if (busy) R.string.login_logging_in else R.string.action_login)
        binding.buttonGoSignup.isEnabled = !busy
        binding.buttonForgotPassword.isEnabled = !busy
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
