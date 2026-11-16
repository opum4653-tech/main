package com.andorid.ninmods.ui.licenses

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
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
import androidx.recyclerview.widget.LinearLayoutManager
import com.andorid.ninmods.R
import com.andorid.ninmods.data.SessionManager
import com.andorid.ninmods.databinding.FragmentLicensesBinding
import com.andorid.ninmods.network.AuthApiClient
import com.andorid.ninmods.network.UserLicenseItem
import com.andorid.ninmods.ui.MainActivity
import com.andorid.ninmods.ui.clock.LicenseClock
import com.andorid.ninmods.ui.dialogs.PurchaseKeySheet
import kotlinx.coroutines.launch

/**
 * License vault. Shows the live countdown for the bound key, every key the
 * account owns, with copy and HWID-reset actions, and the buy-key sheet.
 */
class LicensesFragment : Fragment() {

    private var _binding: FragmentLicensesBinding? = null
    private val binding get() = _binding!!

    private val adapter = LicenseKeyAdapter(
        onCopy = ::copyKey,
        onReset = ::resetHwid
    )

    private val clock = LicenseClock()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLicensesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val host = activity as MainActivity
        val session = host.sessionManager

        binding.keysList.layoutManager = LinearLayoutManager(requireContext())
        binding.keysList.adapter = adapter

        binding.buttonRefreshKeys.setOnClickListener { loadKeys() }
        binding.buttonBuyKey.setOnClickListener {
            PurchaseKeySheet().show(childFragmentManager, "purchase_key")
        }
        binding.licensesRefresh.setOnRefreshListener { loadKeys() }

        observeClock(session)
        loadKeys()
    }

    private fun observeClock(session: SessionManager) {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                clock.remaining(session).collect { seconds ->
                    if (_binding == null) return@collect

                    val active = seconds > 0L
                    binding.activeLicenseCard.visibility =
                        if (session.activeLicenseKey.isNullOrBlank() && !active) View.GONE
                        else View.VISIBLE

                    binding.activeClock.text = getString(
                        R.string.clock_format,
                        seconds / 3600L,
                        (seconds % 3600L) / 60L,
                        seconds % 60L
                    )
                    binding.activeStatus.text = getString(
                        if (active) R.string.state_active else R.string.state_expired
                    )
                    binding.activeStatus.setTextColor(
                        ContextCompat.getColor(
                            requireContext(),
                            if (active) R.color.nin_success else R.color.nin_error
                        )
                    )
                    binding.activeStatus.setBackgroundResource(
                        if (active) R.drawable.bg_badge_success else R.drawable.bg_badge_error
                    )
                }
            }
        }
    }

    private fun loadKeys() {
        val host = activity as? MainActivity ?: return
        val token = host.sessionManager.authToken ?: return

        binding.licensesRefresh.isRefreshing = true
        viewLifecycleOwner.lifecycleScope.launch {
            val result = AuthApiClient.getMyLicenseKeys(token)
            if (_binding == null) return@launch

            binding.licensesRefresh.isRefreshing = false
            result.fold(
                onSuccess = { keys ->
                    adapter.submitList(keys)
                    binding.keysHeader.text =
                        getString(R.string.licenses_my_keys, keys.size)
                    binding.textEmpty.visibility =
                        if (keys.isEmpty()) View.VISIBLE else View.GONE
                },
                onFailure = { error ->
                    adapter.submitList(emptyList())
                    binding.keysHeader.text = getString(R.string.licenses_my_keys, 0)
                    binding.textEmpty.visibility = View.VISIBLE
                    Toast.makeText(
                        requireContext(),
                        error.message ?: getString(R.string.state_offline),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            )
        }
    }

    private fun copyKey(key: String) {
        val clipboard = requireContext()
            .getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(getString(R.string.licenses_key_label), key))
        Toast.makeText(requireContext(), R.string.licenses_copied, Toast.LENGTH_SHORT).show()
    }

    private fun resetHwid(item: UserLicenseItem) {
        if (item.resetsCount >= item.maxResets) {
            Toast.makeText(requireContext(), R.string.licenses_reset_failed, Toast.LENGTH_LONG).show()
            return
        }
        val host = activity as? MainActivity ?: return
        val token = host.sessionManager.authToken ?: return

        viewLifecycleOwner.lifecycleScope.launch {
            AuthApiClient.resetMyKeyHwid(token, item.id).fold(
                onSuccess = {
                    Toast.makeText(requireContext(), R.string.state_completed, Toast.LENGTH_SHORT).show()
                    loadKeys()
                },
                onFailure = { error ->
                    Toast.makeText(
                        requireContext(),
                        error.message ?: getString(R.string.licenses_reset_failed),
                        Toast.LENGTH_LONG
                    ).show()
                }
            )
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding.keysList.adapter = null
        _binding = null
    }
}
