package com.andorid.ninmods.ui.dashboard

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.andorid.ninmods.R
import com.andorid.ninmods.data.SessionManager
import com.andorid.ninmods.databinding.FragmentDashboardBinding
import com.andorid.ninmods.network.ActiveNotificationItem
import com.andorid.ninmods.ui.Destination
import com.andorid.ninmods.ui.MainActivity
import com.andorid.ninmods.ui.clock.LicenseClock
import com.andorid.ninmods.ui.dialogs.ModFlowDialog
import com.andorid.ninmods.ui.mod.ModEngine
import com.andorid.ninmods.ui.mod.ModItem
import com.andorid.ninmods.ui.mod.ModRowAdapter
import com.andorid.ninmods.ui.mod.ModRuntime
import kotlinx.coroutines.launch

/**
 * Dashboard. Broadcast banner, live license clock, and the two mod lists
 * (kernel + external). Row taps open the unified mod flow dialog; the refresh
 * icons rescan the local cache and the public Downloads folder.
 */
class DashboardFragment : Fragment() {

    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!

    private val viewModel: DashboardViewModel by viewModels()

    private lateinit var kernelAdapter: ModRowAdapter
    private lateinit var externalAdapter: ModRowAdapter

    private val clock = LicenseClock()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val host = activity as MainActivity
        val session = host.sessionManager
        ModRuntime.install(requireContext(), session)

        kernelAdapter = ModRowAdapter(ModEngine.KERNEL.cacheDirName) { item ->
            openModDialog(item, ModEngine.KERNEL.displayName)
        }
        externalAdapter = ModRowAdapter(ModEngine.EXTERNAL.cacheDirName) { item ->
            openModDialog(item, ModEngine.EXTERNAL.displayName)
        }

        binding.kernelList.layoutManager = LinearLayoutManager(requireContext())
        binding.kernelList.adapter = kernelAdapter
        binding.externalList.layoutManager = LinearLayoutManager(requireContext())
        binding.externalList.adapter = externalAdapter

        binding.kernelRefresh.setOnClickListener { refreshMods() }
        binding.externalRefresh.setOnClickListener { refreshMods() }
        binding.dashboardRefresh.setOnRefreshListener { refreshMods() }

        binding.broadcastDismiss.setOnClickListener {
            binding.broadcastCard.visibility = View.GONE
        }
        binding.clockCard.setOnClickListener { host.selectDestination(Destination.LICENSES) }

        observeBroadcast()
        observeClock(session)
        refreshMods()
    }

    private fun refreshMods() {
        binding.dashboardRefresh.isRefreshing = true
        viewLifecycleOwner.lifecycleScope.launch {
            val kernel = ModRuntime.kernel().scan()
            val external = ModRuntime.external().scan()
            if (_binding == null) return@launch

            kernelAdapter.submitList(kernel)
            binding.kernelEmpty.visibility = if (kernel.isEmpty()) View.VISIBLE else View.GONE

            externalAdapter.submitList(external)
            binding.externalEmpty.visibility = if (external.isEmpty()) View.VISIBLE else View.GONE

            binding.dashboardRefresh.isRefreshing = false
        }
    }

    private fun observeBroadcast() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.notifications.collect { list ->
                    if (_binding == null) return@collect
                    val first: ActiveNotificationItem? = list.firstOrNull()
                    if (first == null) {
                        binding.broadcastCard.visibility = View.GONE
                    } else {
                        binding.broadcastCard.visibility = View.VISIBLE
                        binding.broadcastTitle.text =
                            first.title.ifBlank { getString(R.string.dashboard_broadcast_default_title) }
                        binding.broadcastMessage.text = first.message
                    }
                }
            }
        }
    }

    private fun observeClock(session: SessionManager) {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                clock.remaining(session).collect { seconds ->
                    if (_binding == null) return@collect
                    binding.clockDisplay.text = formatClock(seconds)
                    binding.clockStatus.text = if (seconds > 0L) {
                        getString(R.string.clock_title)
                    } else {
                        getString(R.string.state_expired)
                    }
                }
            }
        }
    }

    private fun formatClock(totalSeconds: Long): String {
        val hours = totalSeconds / 3600L
        val minutes = (totalSeconds % 3600L) / 60L
        val seconds = totalSeconds % 60L
        return getString(R.string.clock_format, hours, minutes, seconds)
    }

    private fun openModDialog(item: ModItem, displayName: String) {
        val dialog = ModFlowDialog.newInstance(item.id, item.name, displayName)
        dialog.show(childFragmentManager, "mod_flow_${item.id}")
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding.kernelList.adapter = null
        binding.externalList.adapter = null
        _binding = null
    }
}
