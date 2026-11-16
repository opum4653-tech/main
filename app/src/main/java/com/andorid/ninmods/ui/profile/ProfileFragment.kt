package com.andorid.ninmods.ui.profile

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.andorid.ninmods.R
import com.andorid.ninmods.data.SessionManager
import com.andorid.ninmods.data.UserRepository
import com.andorid.ninmods.databinding.FragmentProfileBinding
import com.andorid.ninmods.ui.MainActivity
import com.andorid.ninmods.ui.clock.LicenseClock
import com.andorid.ninmods.ui.dialogs.PurchaseKeySheet
import com.andorid.ninmods.ui.deposit.SubmitDepositFragment
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Account screen. Identity header, wallet balance with refresh, active license
 * block with a live countdown, and the account action rows.
 */
class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!

    private lateinit var session: SessionManager
    private val clock = LicenseClock()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val host = activity as MainActivity
        session = host.sessionManager

        binding.buttonRefreshBalance.setOnClickListener { refreshProfile() }

        binding.rowBuyKey.setOnClickListener {
            PurchaseKeySheet().show(childFragmentManager, "purchase_key")
        }
        binding.rowAddFunds.setOnClickListener {
            host.openSubScreen(SubmitDepositFragment(), R.string.nav_submit_deposit)
        }
        binding.rowChangePassword.setOnClickListener {
            host.openSubScreen(ChangePasswordFragment(), R.string.nav_change_password)
        }
        binding.rowSignOut.setOnClickListener { host.signOut() }

        render()
        verifySession()
        observeClock()
    }

    private fun render() {
        if (_binding == null) return

        binding.textName.text = session.userName?.takeIf { it.isNotBlank() }
            ?: getString(R.string.brand_default_user)
        binding.textEmail.text = session.userEmail?.takeIf { it.isNotBlank() }
            ?: getString(R.string.profile_no_token)

        val role = session.userRole
        if (role.isNotBlank()) {
            binding.textRole.text = role.uppercase(Locale.US)
            binding.textRole.visibility = View.VISIBLE
        } else {
            binding.textRole.visibility = View.GONE
        }

        binding.textBalance.text = String.format(Locale.US, "$%.2f", session.userBalance)

        val key = session.activeLicenseKey
        if (key.isNullOrBlank()) {
            binding.licenseCard.visibility = View.GONE
        } else {
            binding.licenseCard.visibility = View.VISIBLE
            binding.licenseKey.text = key
        }
    }

    private fun verifySession() {
        val token = session.authToken ?: return
        viewLifecycleOwner.lifecycleScope.launch {
            val result = UserRepository.getProfile(token, forceRefresh = true)
            if (_binding == null) return@launch

            result.fold(
                onSuccess = { profile ->
                    session.userName = profile.username
                    session.userEmail = profile.email
                    session.userRole = profile.role
                    session.userBalance = profile.balance
                    profile.activeLicense?.let { license ->
                        if (license.licenseKey.isNotBlank()) {
                            session.activeLicenseKey = license.licenseKey
                            session.serverRemainingSeconds = license.remainingSeconds
                        }
                    }
                    render()
                },
                onFailure = { error ->
                    Toast.makeText(
                        requireContext(),
                        error.message ?: getString(R.string.profile_backend_failed),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            )
        }
    }

    private fun refreshProfile() {
        binding.textBalance.text = getString(R.string.state_loading)
        verifySession()
    }

    private fun observeClock() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                clock.remaining(session).collect { seconds ->
                    if (_binding == null) return@collect
                    if (binding.licenseCard.visibility != View.VISIBLE) return@collect

                    val active = seconds > 0L
                    binding.licenseStatus.text = getString(
                        if (active) R.string.state_active else R.string.state_expired
                    )
                    binding.licenseStatus.setTextColor(
                        ContextCompat.getColor(
                            requireContext(),
                            if (active) R.color.nin_success else R.color.nin_error
                        )
                    )
                    binding.licenseStatus.setBackgroundResource(
                        if (active) R.drawable.bg_badge_success else R.drawable.bg_badge_error
                    )
                    binding.licenseRemaining.text = getString(
                        R.string.clock_format,
                        seconds / 3600L,
                        (seconds % 3600L) / 60L,
                        seconds % 60L
                    )
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
