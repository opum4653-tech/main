package com.andorid.ninmods.ui.settings

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.andorid.ninmods.R
import com.andorid.ninmods.config.AppConfig
import com.andorid.ninmods.data.SessionManager
import com.andorid.ninmods.data.ShellExecutor
import com.andorid.ninmods.databinding.FragmentSettingsBinding
import com.andorid.ninmods.network.AuthApiClient
import com.andorid.ninmods.ui.MainActivity
import com.andorid.ninmods.ui.dialogs.SelectionSheet
import com.andorid.ninmods.util.RootDetector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Settings. Live root / storage / notification state, kernel namespace flags,
 * update check, bug report shortcut, device reboot sheet, session actions and
 * the community link. Every row acts on tap.
 */
class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    private lateinit var session: SessionManager
    private var latestDownloadUrl: String = ""

    private val storageLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { refreshStatuses() }

    private val notificationLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { refreshStatuses() }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        session = (activity as MainActivity).sessionManager

        binding.textBuild.text = getString(
            R.string.brand_version_format, AppConfig.VERSION_NAME
        )

        binding.rowRootManager.setOnClickListener {
            refreshStatuses()
        }
        binding.rowRootAccess.setOnClickListener { requestRoot() }
        binding.rowStorage.setOnClickListener { requestStorage() }
        binding.rowNotifications.setOnClickListener { requestNotifications() }
        binding.rowSetupWizard.setOnClickListener { requestAll() }

        binding.switchGlobalNamespace.isChecked = session.globalNamespace
        binding.switchGlobalNamespace.setOnCheckedChangeListener { _, checked ->
            session.globalNamespace = checked
        }
        binding.switchLegacySu.isChecked = session.legacySu
        binding.switchLegacySu.setOnCheckedChangeListener { _, checked ->
            session.legacySu = checked
        }

        binding.rowCheckUpdates.setOnClickListener { checkForUpdates() }
        binding.rowReportBug.setOnClickListener {
            (activity as? MainActivity)?.openSubScreen(
                ReportBugFragment(), R.string.nav_report_bug
            )
        }
        binding.rowReboot.setOnClickListener { showRebootSheet() }
        binding.rowChangePassword.setOnClickListener {
            (activity as? MainActivity)?.openSubScreen(
                com.andorid.ninmods.ui.profile.ChangePasswordFragment(),
                R.string.nav_change_password
            )
        }
        binding.rowSignOut.setOnClickListener {
            (activity as? MainActivity)?.signOut()
        }
        binding.rowTelegram.setOnClickListener {
            runCatching {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(TELEGRAM_URL)))
            }
        }

        refreshStatuses()
    }

    override fun onResume() {
        super.onResume()
        refreshStatuses()
    }

    // ------------------------------------------------------------- permissions

    private fun refreshStatuses() {
        binding.valueRootManager.text = getString(R.string.state_detecting)
        binding.valueRootAccess.text = getString(R.string.state_denied)
        binding.valueStorage.text = getString(R.string.state_denied)
        binding.valueNotifications.text = getString(R.string.state_denied)

        viewLifecycleOwner.lifecycleScope.launch {
            val rootInfo = withContext(Dispatchers.IO) { RootDetector.detect(requireContext()) }
            val suGranted = withContext(Dispatchers.IO) { ShellExecutor.isRootAvailable() }
            if (_binding == null) return@launch

            binding.valueRootManager.text = rootInfo.managerName
                .ifBlank { getString(R.string.state_denied) }

            binding.valueRootAccess.text = getString(
                if (suGranted) R.string.settings_granted_uid else R.string.settings_denied_root
            )
            binding.valueRootAccess.setTextColor(
                ContextCompat.getColor(
                    requireContext(),
                    if (suGranted) R.color.nin_success else R.color.nin_error
                )
            )

            val storageOk = hasStorageAccess()
            binding.valueStorage.text = getString(
                if (storageOk) R.string.state_granted else R.string.settings_denied_storage
            )
            binding.valueStorage.setTextColor(
                ContextCompat.getColor(
                    requireContext(),
                    if (storageOk) R.color.nin_success else R.color.nin_error
                )
            )

            val notifOk = hasNotificationAccess()
            binding.valueNotifications.text = getString(
                if (notifOk) R.string.state_granted else R.string.settings_denied_notification
            )
            binding.valueNotifications.setTextColor(
                ContextCompat.getColor(
                    requireContext(),
                    if (notifOk) R.color.nin_success else R.color.nin_error
                )
            )
        }
    }

    private fun hasStorageAccess(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(
                requireContext(), Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        }

    private fun hasNotificationAccess(): Boolean {
        val nm = requireContext()
            .getSystemService(android.content.Context.NOTIFICATION_SERVICE)
            as? android.app.NotificationManager
        val enabled = nm?.areNotificationsEnabled() ?: false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            enabled && ContextCompat.checkSelfPermission(
                requireContext(), Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            enabled
        }
    }

    private fun requestRoot() {
        viewLifecycleOwner.lifecycleScope.launch {
            val granted = withContext(Dispatchers.IO) { ShellExecutor.isRootAvailable() }
            if (_binding == null) return@launch
            Toast.makeText(
                requireContext(),
                if (granted) R.string.settings_root_granted else R.string.settings_root_denied,
                Toast.LENGTH_SHORT
            ).show()
            refreshStatuses()
        }
    }

    private fun requestStorage() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                .setData(Uri.parse("package:${requireContext().packageName}"))
            runCatching { startActivity(intent) }.onFailure {
                startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
            }
        } else {
            storageLauncher.launch(
                arrayOf(
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                )
            )
        }
    }

    private fun requestNotifications() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            startActivity(
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, requireContext().packageName)
            )
        }
    }

    private fun requestAll() {
        requestRoot()
        requestStorage()
        requestNotifications()
    }

    // ----------------------------------------------------------------- updates

    private fun checkForUpdates() {
        if (_binding == null) return
        binding.progressUpdate.visibility = View.VISIBLE
        binding.valueUpdateStatus.text = getString(R.string.settings_update_connecting)

        viewLifecycleOwner.lifecycleScope.launch {
            val result = AuthApiClient.checkAppVersion("loader")
            if (_binding == null) return@launch
            binding.progressUpdate.visibility = View.GONE

            result.fold(
                onSuccess = { version ->
                    if (version.versionCode > AppConfig.VERSION_CODE) {
                        latestDownloadUrl = version.downloadUrl
                        binding.valueUpdateStatus.text = getString(
                            R.string.settings_update_available, version.versionName
                        )
                        binding.valueUpdateStatus.setTextColor(
                            ContextCompat.getColor(requireContext(), R.color.nin_warning)
                        )
                        binding.rowCheckUpdates.setOnClickListener { openUpdate() }
                    } else {
                        latestDownloadUrl = ""
                        binding.valueUpdateStatus.text = getString(
                            R.string.settings_update_current, version.versionName
                        )
                        binding.valueUpdateStatus.setTextColor(
                            ContextCompat.getColor(requireContext(), R.color.nin_success)
                        )
                        binding.rowCheckUpdates.setOnClickListener { checkForUpdates() }
                    }
                },
                onFailure = {
                    binding.valueUpdateStatus.text = getString(
                        R.string.settings_update_official, AppConfig.VERSION_NAME
                    )
                    binding.valueUpdateStatus.setTextColor(
                        ContextCompat.getColor(requireContext(), R.color.nin_text_secondary)
                    )
                }
            )
        }
    }

    private fun openUpdate() {
        if (latestDownloadUrl.isBlank()) {
            checkForUpdates()
            return
        }
        runCatching {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(latestDownloadUrl)))
        }
    }

    // ------------------------------------------------------------------ reboot

    private fun showRebootSheet() {
        val commands = listOf("reboot", "recovery", "bootloader", "soft")
        val sheet = SelectionSheet.newInstance(
            getString(R.string.settings_reboot_title),
            getString(R.string.settings_reboot_hint),
            listOf(
                getString(R.string.settings_reboot_normal),
                getString(R.string.settings_reboot_recovery),
                getString(R.string.settings_reboot_bootloader),
                getString(R.string.settings_reboot_soft)
            )
        )
        sheet.onItemSelected = { index ->
            sheet.dismiss()
            val command = commands.getOrNull(index)
            if (command != null) runReboot(command)
        }
        sheet.show(childFragmentManager, "reboot_sheet")
    }

    private fun runReboot(mode: String) {
        val command = when (mode) {
            "recovery" -> "reboot recovery"
            "bootloader" -> "reboot bootloader"
            "soft" -> "setprop ctl.restart zygote"
            else -> "svc power reboot || reboot"
        }
        viewLifecycleOwner.lifecycleScope.launch {
            val ok = withContext(Dispatchers.IO) { ShellExecutor.executeRoot(command) }
            if (!ok) {
                Toast.makeText(requireContext(), R.string.error_root_required, Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private companion object {
        const val TELEGRAM_URL = "https://t.me/ninmods"
    }
}
