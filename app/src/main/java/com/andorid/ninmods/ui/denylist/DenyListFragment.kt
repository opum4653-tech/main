package com.andorid.ninmods.ui.denylist

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
import com.andorid.ninmods.data.AppProcessItem
import com.andorid.ninmods.data.DenyListManager
import com.andorid.ninmods.data.InstalledAppItem
import com.andorid.ninmods.data.RootEnvironmentInfo
import com.andorid.ninmods.databinding.FragmentDenylistBinding
import kotlinx.coroutines.launch

/**
 * Root cloak screen. Reads the live environment, enumerates installed apps with
 * their processes, and toggles per-process isolation through DenyListManager.
 * A global switch mirrors the denylist state; the quick-cloak button hides
 * every game/target package in one pass.
 */
class DenyListFragment : Fragment() {

    private var _binding: FragmentDenylistBinding? = null
    private val binding get() = _binding!!

    private val adapter = AppListAdapter(
        onProcessToggle = ::toggleProcess,
        onAppToggle = ::toggleApp
    )

    private var env: RootEnvironmentInfo? = null
    private var allApps: List<InstalledAppItem> = emptyList()
    private var query: String = ""
    private var suppressSwitchCallback = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDenylistBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.appList.layoutManager = LinearLayoutManager(requireContext())
        binding.appList.adapter = adapter

        binding.inputSearch.doAfterTextChanged { editable ->
            query = editable?.toString().orEmpty().trim()
            applyFilter()
        }

        binding.switchGlobalCloak.setOnCheckedChangeListener { _, checked ->
            if (suppressSwitchCallback) return@setOnCheckedChangeListener
            setGlobalCloak(checked)
        }

        binding.buttonCloakTargets.setOnClickListener { cloakTargets() }

        refresh()
    }

    private fun refresh() {
        viewLifecycleOwner.lifecycleScope.launch {
            val info = DenyListManager.detectEnvironment(requireContext())
            env = info
            renderEnvironment(info)

            val apps = if (info.rootAvailable) {
                DenyListManager.getInstalledApps(requireContext())
            } else {
                emptyList()
            }
            allApps = apps
            applyFilter()
        }
    }

    private fun renderEnvironment(info: RootEnvironmentInfo) {
        if (_binding == null) return

        binding.textProvider.text = info.providerName

        val isolation = if (info.denylistEnabled) {
            getString(R.string.denylist_isolation_active)
        } else {
            getString(R.string.denylist_isolation_disabled)
        }
        binding.textIsolation.text = isolation
        binding.textIsolation.setTextColor(
            requireContext().getColor(
                if (info.denylistEnabled) R.color.nin_success else R.color.nin_error
            )
        )

        binding.textStatusMessage.text = info.statusMessage
        binding.textProtectedSummary.text = getString(
            R.string.denylist_protected_count,
            info.totalHiddenProcesses
        )

        suppressSwitchCallback = true
        binding.switchGlobalCloak.isChecked = info.denylistEnabled
        binding.switchGlobalCloak.isEnabled = info.rootAvailable
        suppressSwitchCallback = false

        binding.buttonCloakTargets.isEnabled = info.rootAvailable
    }

    private fun applyFilter() {
        val filtered = allApps.filter { app ->
            if (query.isBlank()) return@filter true
            app.appName.contains(query, true) || app.packageName.contains(query, true)
        }
        adapter.submitList(filtered)
        binding.textAppCount.text =
            getString(R.string.denylist_applications, filtered.size)
        binding.textEmpty.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun setGlobalCloak(enabled: Boolean) {
        viewLifecycleOwner.lifecycleScope.launch {
            val ok = DenyListManager.setDenyListEnabled(requireContext(), enabled)
            if (!ok) {
                Toast.makeText(
                    requireContext(),
                    R.string.denylist_toggle_failed,
                    Toast.LENGTH_SHORT
                ).show()
                suppressSwitchCallback = true
                binding.switchGlobalCloak.isChecked = !enabled
                suppressSwitchCallback = false
            }
            refresh()
        }
    }

    private fun cloakTargets() {
        binding.progressCloak.visibility = View.VISIBLE
        binding.buttonCloakTargets.isEnabled = false

        viewLifecycleOwner.lifecycleScope.launch {
            val result = DenyListManager.quickCloakGames(requireContext())
            if (_binding == null) return@launch

            binding.progressCloak.visibility = View.GONE
            binding.buttonCloakTargets.isEnabled = true

            result.fold(
                onSuccess = { count ->
                    Toast.makeText(
                        requireContext(),
                        getString(R.string.denylist_protected_count, count),
                        Toast.LENGTH_SHORT
                    ).show()
                    refresh()
                },
                onFailure = { error ->
                    Toast.makeText(
                        requireContext(),
                        error.message ?: getString(R.string.denylist_cloak_failed),
                        Toast.LENGTH_LONG
                    ).show()
                }
            )
        }
    }

    private fun toggleApp(app: InstalledAppItem, hide: Boolean) {
        viewLifecycleOwner.lifecycleScope.launch {
            val queued = app.processes.ifEmpty {
                listOf(AppProcessItem(app.packageName, false))
            }
            var ok = true
            for (process in queued) {
                val result = if (hide) {
                    DenyListManager.addToDenyList(
                        requireContext(), app.packageName,
                        process.processName.takeIf { it != app.packageName }
                    )
                } else {
                    DenyListManager.removeFromDenyList(
                        requireContext(), app.packageName,
                        process.processName.takeIf { it != app.packageName }
                    )
                }
                ok = ok && result
            }
            if (!ok) {
                Toast.makeText(
                    requireContext(),
                    R.string.denylist_operation_failed,
                    Toast.LENGTH_SHORT
                ).show()
            }
            refresh()
        }
    }

    private fun toggleProcess(
        app: InstalledAppItem,
        processName: String,
        hide: Boolean
    ) {
        viewLifecycleOwner.lifecycleScope.launch {
            val ok = if (hide) {
                DenyListManager.addToDenyList(
                    requireContext(), app.packageName,
                    processName.takeIf { it != app.packageName }
                )
            } else {
                DenyListManager.removeFromDenyList(
                    requireContext(), app.packageName,
                    processName.takeIf { it != app.packageName }
                )
            }
            if (!ok) {
                Toast.makeText(
                    requireContext(),
                    R.string.denylist_operation_failed,
                    Toast.LENGTH_SHORT
                ).show()
            }
            refresh()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding.appList.adapter = null
        _binding = null
    }
}
