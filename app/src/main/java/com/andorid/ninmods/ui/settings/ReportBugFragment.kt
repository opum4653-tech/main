package com.andorid.ninmods.ui.settings

import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.andorid.ninmods.R
import com.andorid.ninmods.config.AppConfig
import com.andorid.ninmods.data.ShellExecutor
import com.andorid.ninmods.databinding.FragmentReportBugBinding
import com.andorid.ninmods.network.AuthApiClient
import com.andorid.ninmods.ui.MainActivity
import com.andorid.ninmods.util.DeviceIdentifier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Bug report form. Device and kernel metrics are filled from the live build,
 * the logcat buffer is captured on demand, and the payload carries the HWID and
 * the active license key when one is present.
 */
class ReportBugFragment : Fragment() {

    private var _binding: FragmentReportBugBinding? = null
    private val binding get() = _binding!!

    private val deviceModel by lazy {
        listOf(Build.MANUFACTURER, Build.MODEL)
            .filter { it.isNotBlank() }
            .joinToString(" ")
    }
    private val androidVersion by lazy {
        "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"
    }
    private val kernelVersion by lazy {
        System.getProperty("os.version") ?: "Linux"
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentReportBugBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.textDevice.text = getString(
            R.string.bug_device_format, deviceModel, androidVersion
        )
        binding.textKernel.text = getString(
            R.string.bug_kernel_format, kernelVersion, AppConfig.VERSION_NAME
        )

        binding.buttonSubmitBug.setOnClickListener { submit() }
    }

    private fun submit() {
        val title = binding.inputTitle.text?.toString()?.trim().orEmpty()
        if (title.isBlank()) {
            binding.inputTitleLayout.error = getString(R.string.bug_error_title)
            return
        }
        binding.inputTitleLayout.error = null

        val description = binding.inputDescription.text?.toString()?.trim().orEmpty()
        if (description.isBlank()) {
            binding.inputDescriptionLayout.error = getString(R.string.bug_error_description)
            return
        }
        binding.inputDescriptionLayout.error = null

        val attachLog = binding.switchAttachLog.isChecked
        val host = activity as? MainActivity ?: return
        val session = host.sessionManager

        binding.buttonSubmitBug.isEnabled = false
        binding.progressSubmit.visibility = View.VISIBLE

        viewLifecycleOwner.lifecycleScope.launch {
            val hwid = withContext(Dispatchers.IO) {
                DeviceIdentifier.getHwid(requireContext())
            }
            val isRoot = withContext(Dispatchers.IO) { ShellExecutor.isRootAvailable() }

            val logs = if (attachLog) {
                withContext(Dispatchers.IO) { captureLogcat() }
            } else {
                null
            }

            val result = AuthApiClient.submitBugReport(
                title = title,
                description = description,
                deviceModel = deviceModel,
                androidVersion = androidVersion,
                appVersion = AppConfig.VERSION_NAME,
                kernelVersion = kernelVersion,
                isRoot = isRoot,
                licenseKey = session.activeLicenseKey,
                hwid = hwid,
                logcatTrace = logs
            )

            if (_binding == null) return@launch
            binding.progressSubmit.visibility = View.GONE
            binding.buttonSubmitBug.isEnabled = true

            result.fold(
                onSuccess = {
                    Toast.makeText(
                        requireContext(),
                        R.string.bug_success,
                        Toast.LENGTH_LONG
                    ).show()
                    parentFragmentManager.popBackStack()
                },
                onFailure = { error ->
                    Toast.makeText(
                        requireContext(),
                        error.message ?: getString(R.string.bug_failed),
                        Toast.LENGTH_LONG
                    ).show()
                }
            )
        }
    }

    /** Pulls the tail of the logcat ring buffer; empty when unavailable. */
    private fun captureLogcat(): String? = runCatching {
        val process = Runtime.getRuntime().exec("logcat -d -t 200")
        val text = process.inputStream.bufferedReader().use { it.readText() }
        process.waitFor()
        text.takeIf { it.isNotBlank() }
    }.getOrNull()

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
