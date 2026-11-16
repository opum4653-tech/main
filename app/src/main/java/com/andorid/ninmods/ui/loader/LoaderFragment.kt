package com.andorid.ninmods.ui.loader

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
import com.andorid.ninmods.databinding.FragmentLoaderBinding
import com.andorid.ninmods.ui.MainActivity
import com.andorid.ninmods.ui.MainViewModel
import com.andorid.ninmods.ui.dialogs.SelectionSheet
import kotlinx.coroutines.launch
import java.io.File

/**
 * Driver Loader screen. Binds fragment_loader.xml to MainViewModel: kernel
 * release readout, driver/root state cards, QX + RT mode buttons that open the
 * driver selection sheet, stop button, and the live terminal log list.
 */
class LoaderFragment : Fragment() {

    private var _binding: FragmentLoaderBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: MainViewModel
    private val logAdapter = LogLineAdapter()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLoaderBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        viewModel = (activity as MainActivity).loaderViewModel()

        binding.logList.layoutManager = LinearLayoutManager(requireContext())
        binding.logList.adapter = logAdapter

        binding.cardDriverState.setOnClickListener { viewModel.refreshStatus() }
        binding.cardRootState.setOnClickListener { viewModel.requestRootAndCheck() }

        binding.buttonModeQx.setOnClickListener { startMode("QX") }
        binding.buttonModeRt.setOnClickListener { startMode("RT") }
        binding.buttonStopDriver.setOnClickListener { viewModel.unloadDrivers() }
        binding.buttonClearLog.setOnClickListener {
            viewModel.clearLog()
            logAdapter.submit(emptyList())
        }

        observeState()
    }

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    binding.textKernelRelease.text = state.kernelRelease.ifBlank {
                        getString(R.string.state_detecting)
                    }

                    val driverLoaded = state.isDriverLoaded
                    binding.textDriverState.text = getString(
                        if (driverLoaded) R.string.state_working else R.string.state_not_loaded
                    )
                    val driverColor = if (driverLoaded) R.color.nin_success else R.color.nin_error
                    binding.textDriverState.setTextColor(
                        ContextCompat.getColor(requireContext(), driverColor)
                    )
                    binding.iconDriverState.setImageResource(
                        if (driverLoaded) R.drawable.ic_check_circle else R.drawable.ic_pause
                    )
                    binding.iconDriverState.setColorFilter(
                        ContextCompat.getColor(requireContext(), driverColor)
                    )

                    val rootOk = state.isRootAvailable
                    binding.textRootState.text = getString(
                        if (rootOk) R.string.state_granted else R.string.state_denied
                    )
                    val rootColor = if (rootOk) R.color.nin_success else R.color.nin_error
                    binding.textRootState.setTextColor(
                        ContextCompat.getColor(requireContext(), rootColor)
                    )
                    binding.iconRootState.setImageResource(
                        if (rootOk) R.drawable.ic_check_circle else R.drawable.ic_lock
                    )
                    binding.iconRootState.setColorFilter(
                        ContextCompat.getColor(requireContext(), rootColor)
                    )

                    binding.buttonStopDriver.isEnabled = driverLoaded
                    binding.buttonModeQx.isEnabled = !state.isWorking
                    binding.buttonModeRt.isEnabled = !state.isWorking

                    logAdapter.submit(state.logMessages)
                    if (state.logMessages.isNotEmpty()) {
                        binding.logList.scrollToPosition(state.logMessages.lastIndex)
                    }
                }
            }
        }
    }

    /**
     * If the driver package for this mode is already on disk, go straight to
     * selection. Otherwise download the package first and show progress inside
     * the terminal, then present selection.
     */
    private fun startMode(mode: String) {
        if (viewModel.hasDriversForMode(mode)) {
            viewModel.prepareDrivers(mode) { files -> presentSelection(mode, files) }
            return
        }

        viewModel.downloadAndPrepareDrivers(
            mode = mode,
            onProgress = { percent ->
                if (_binding != null) {
                    binding.textKernelRelease.text = getString(R.string.loader_downloading_package, "$percent%")
                }
            },
            onComplete = { files -> presentSelection(mode, files) },
            onError = { message ->
                if (_binding != null) {
                    Toast.makeText(
                        requireContext(),
                        getString(R.string.loader_download_failed, message),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        )
    }

    private fun presentSelection(mode: String, files: List<File>) {
        if (_binding == null) return

        if (files.isEmpty()) {
            Toast.makeText(
                requireContext(),
                R.string.loader_driver_download_failed,
                Toast.LENGTH_LONG
            ).show()
            return
        }

        val sheet = SelectionSheet.newInstance(
            title = getString(R.string.loader_select_driver, mode),
            subtitle = getString(R.string.loader_matched_kernel, viewModel.uiState.value.kernelRelease),
            items = files.map { it.name }
        )
        sheet.onItemSelected = { index ->
            files.getOrNull(index)?.let { file ->
                sheet.dismiss()
                viewModel.loadDriverScript(file)
            }
        }
        sheet.show(childFragmentManager, "driver_selection")
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding.logList.adapter = null
        _binding = null
    }
}
