package com.andorid.ninmods.ui.dialogs

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.andorid.ninmods.R
import com.andorid.ninmods.databinding.DialogModFlowBinding
import com.andorid.ninmods.databinding.ItemLogLineBinding
import com.andorid.ninmods.network.AuthApiClient
import com.andorid.ninmods.ui.mod.ModEngine
import com.andorid.ninmods.ui.mod.ModItem
import com.andorid.ninmods.ui.mod.ModRuntime
import com.andorid.ninmods.data.ShellExecutor
import com.andorid.ninmods.util.DeviceIdentifier
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Unified mod flow. One dialog that walks every stage of launching a mod:
 * confirm download, download with progress, root check, license key entry with
 * a live terminal, ready/start, and stop confirmation.
 */
class ModFlowDialog : BottomSheetDialogFragment() {

    private var _binding: DialogModFlowBinding? = null
    private val binding get() = _binding!!

    private lateinit var engine: ModEngine
    private var item: ModItem? = null
    private var downloadJob: Job? = null

    private val terminalLines = ArrayDeque<String>().apply { }

    init {
        isCancelable = true
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog =
        super.onCreateDialog(savedInstanceState).apply {
            setCanceledOnTouchOutside(false)
        }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogModFlowBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val args = requireArguments()
        val id = args.getString(ARG_ID).orEmpty()
        val displayName = args.getString(ARG_LABEL).orEmpty()

        engine = ModRuntime.kernel()
        if (engine.config.displayName != displayName) {
            engine = ModRuntime.external()
        }

        binding.dialogTerminal.layoutManager = LinearLayoutManager(requireContext())
        binding.dialogTerminal.adapter = TerminalAdapter()

        binding.dialogCancel.setOnClickListener { dismiss() }
        binding.dialogConfirm.setOnClickListener { primaryAction() }
        // The key field advertises imeOptions=actionDone. Without this listener
        // the Done key on the keyboard closed nothing and submitted nothing.
        binding.dialogInput.setOnEditorActionListener { _, actionId, event ->
            val isDone = actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE ||
                (event != null && event.keyCode == android.view.KeyEvent.KEYCODE_ENTER &&
                    event.action == android.view.KeyEvent.ACTION_DOWN)
            if (isDone && stage == Stage.License && !licenseVerified) {
                verifyKey()
                true
            } else {
                false
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            val cacheDir = engine.cacheDir()
            val match = engine.scan().firstOrNull { it.id == id }
            item = match ?: ModItem(
                id = id,
                name = displayName,
                fileName = if (id.endsWith(".sh") || id.endsWith(".zip")) id else "$id.zip",
                downloadPackage = "${engine.config.serverFolder}/$id",
                file = File(cacheDir, id),
                isDownloaded = false
            )
            render(Stage.Confirm)
        }
    }

    // ------------------------------------------------------------------ stages

    private enum class Stage { Confirm, Downloading, RootRequired, License, Running, StopConfirm }

    private var stage: Stage = Stage.Confirm
    private var licenseVerified = false
    private var submittedKey: String? = null
    private var pendingErrorMessage: String? = null

    private fun render(next: Stage) {
        stage = next
        val mod = item ?: return

        binding.dialogProgress.visibility = View.GONE
        binding.dialogTerminal.visibility = View.GONE
        binding.dialogInputLayout.visibility = View.GONE
        // Downloading hides the confirm button. Every later stage has to bring
        // it back, otherwise the license prompt renders with a dead Verify
        // button and the key can never be submitted.
        binding.dialogConfirm.visibility = View.VISIBLE
        binding.dialogConfirm.isEnabled = true

        when (next) {
            Stage.Confirm -> {
                binding.dialogIcon.setImageResource(R.drawable.ic_download)
                binding.dialogTitle.text = mod.name
                binding.dialogMessage.text = getString(R.string.dialog_confirm_download, mod.name)
                binding.dialogCancel.setText(R.string.action_cancel)
                binding.dialogConfirm.setText(R.string.action_download)
            }

            Stage.Downloading -> {
                binding.dialogIcon.setImageResource(R.drawable.ic_download)
                binding.dialogTitle.text = getString(R.string.dialog_download_title)
                binding.dialogMessage.text = getString(R.string.dialog_connecting_mirror)
                binding.dialogProgress.visibility = View.VISIBLE
                binding.dialogProgress.isIndeterminate = false
                binding.dialogProgress.progress = 0
                binding.dialogCancel.setText(R.string.action_cancel)
                binding.dialogConfirm.visibility = View.GONE
            }

            Stage.RootRequired -> {
                binding.dialogIcon.setImageResource(R.drawable.ic_warning)
                binding.dialogTitle.text = getString(R.string.dialog_root_required)
                binding.dialogMessage.text = getString(R.string.dialog_root_denied_message)
                binding.dialogCancel.setText(R.string.action_close)
                binding.dialogConfirm.visibility = View.VISIBLE
                binding.dialogConfirm.setText(R.string.action_grant)
            }

            Stage.License -> {
                binding.dialogIcon.setImageResource(R.drawable.ic_license_key)
                binding.dialogTitle.text = getString(R.string.dialog_auth_required)
                binding.dialogMessage.text = if (licenseVerified) {
                    getString(R.string.dialog_license_verified)
                } else {
                    getString(R.string.dialog_license_hint)
                }
                binding.dialogTerminal.visibility = View.VISIBLE
                binding.dialogInputLayout.visibility = if (licenseVerified) View.GONE else View.VISIBLE
                // Only overwrite the field when there is something authoritative
                // to write. Re-rendering after a server rejection used to erase
                // whatever the user had just typed.
                val prefill = submittedKey ?: enginePrefillKey()
                if (!licenseVerified && !prefill.isNullOrBlank() &&
                    binding.dialogInput.text?.toString().isNullOrBlank()
                ) {
                    binding.dialogInput.setText(prefill)
                }
                binding.dialogCancel.setText(R.string.action_close)
                binding.dialogConfirm.setText(
                    if (licenseVerified) R.string.action_start else R.string.action_verify
                )
                // The error line belongs to the last failed attempt only. Leaving
                // it set after a success made a verified key still look rejected.
                binding.dialogInputLayout.error = pendingErrorMessage
                binding.dialogTerminal.scrollToPosition((terminalLines.size - 1).coerceAtLeast(0))
            }

            Stage.Running -> {
                binding.dialogIcon.setImageResource(R.drawable.ic_play)
                binding.dialogTitle.text = mod.name
                binding.dialogMessage.text = getString(R.string.dashboard_external_subtitle_active)
                binding.dialogCancel.setText(R.string.action_close)
                binding.dialogConfirm.setText(R.string.action_stop)
            }

            Stage.StopConfirm -> {
                binding.dialogIcon.setImageResource(R.drawable.ic_pause)
                binding.dialogTitle.text = getString(R.string.dialog_stop_mod)
                binding.dialogMessage.text = getString(R.string.dialog_confirm_stop, mod.name)
                binding.dialogCancel.setText(R.string.action_cancel)
                binding.dialogConfirm.setText(R.string.action_stop)
            }
        }
    }

    private fun enginePrefillKey(): String? = engine.cachedKey().takeIf { it.isNotBlank() }

    private fun primaryAction() {
        val mod = item ?: return
        when (stage) {
            Stage.Confirm -> download(mod)
            Stage.Downloading -> cancelDownload()
            Stage.RootRequired -> requestRoot()
            Stage.License -> if (licenseVerified) startMod() else verifyKey()
            Stage.Running -> render(Stage.StopConfirm)
            Stage.StopConfirm -> stopMod()
        }
    }

    // ------------------------------------------------------------------ actions

    private fun download(mod: ModItem) {
        render(Stage.Downloading)
        downloadJob?.cancel()
        downloadJob = viewLifecycleOwner.lifecycleScope.launch {
            val result = engine.download(mod) { percent ->
                if (_binding != null) {
                    binding.dialogProgress.progress = percent
                    binding.dialogMessage.text =
                        getString(R.string.loader_downloading_package, "$percent%")
                }
            }
            if (_binding == null) return@launch

            result.fold(
                onSuccess = { file ->
                    item = mod.copy(file = file, isDownloaded = true)
                    launchMod()
                },
                onFailure = { error ->
                    Toast.makeText(
                        requireContext(),
                        error.message ?: getString(R.string.loader_driver_download_failed),
                        Toast.LENGTH_LONG
                    ).show()
                    dismiss()
                }
            )
        }
    }

    private fun cancelDownload() {
        downloadJob?.cancel()
        downloadJob = null
        dismiss()
    }

    private fun requestRoot() {
        viewLifecycleOwner.lifecycleScope.launch {
            val rooted = withContext(Dispatchers.IO) { ShellExecutor.isRootAvailable() }
            if (rooted) {
                launchMod()
            } else {
                Toast.makeText(
                    requireContext(),
                    R.string.error_root_required,
                    Toast.LENGTH_LONG
                ).show()
                render(Stage.RootRequired)
            }
        }
    }

    private fun launchMod() {
        val mod = item ?: return
        viewLifecycleOwner.lifecycleScope.launch {
            val rooted = withContext(Dispatchers.IO) { ShellExecutor.isRootAvailable() }
            if (!rooted) {
                render(Stage.RootRequired)
                return@launch
            }
            if (!mod.isDownloaded) {
                render(Stage.Confirm)
                return@launch
            }

            appendTerminal("[SYSTEM] Spawning root shell for ${mod.name}…")
            render(Stage.License)

            engine.launch(
                item = mod,
                onLicensePrompt = {
                    if (_binding != null) {
                        appendTerminal(getString(R.string.dialog_license_key))
                        render(Stage.License)
                    }
                },
                onError = { message ->
                    if (_binding != null) {
                        // The binary can die right after we accepted the key
                        // server-side; its own online check may still fail. Drop
                        // the verified flag or the next tap sends START into a
                        // closed pipe and looks like a silent no-op.
                        licenseVerified = false
                        appendTerminal("[-] $message")
                        pendingErrorMessage = message
                        render(Stage.License)
                    }
                }
            )

            engine.outputLines.collect { chunk ->
                if (_binding == null) return@collect
                chunk.split('\n').forEach { line ->
                    val trimmed = line.trimEnd()
                    if (trimmed.isNotBlank()) appendTerminal(trimmed)
                }
            }
        }

        // The binary is the authority on its own gate: it re-checks the key
        // online and prints "Awaiting START signal" only when it accepted it.
        // Mirror that so the Start button appears when the engine is actually
        // ready, not the moment the app's own HTTP call returned 200.
        viewLifecycleOwner.lifecycleScope.launch {
            engine.readyToStart.collect { ready ->
                if (!ready || _binding == null) return@collect
                if (!licenseVerified) {
                    licenseVerified = true
                    pendingErrorMessage = null
                    render(Stage.License)
                }
            }
        }
    }

    private fun verifyKey() {
        val mod = item ?: return
        val key = binding.dialogInput.text?.toString()?.trim().orEmpty()
        if (key.isBlank()) {
            binding.dialogInputLayout.error = getString(R.string.dialog_license_key)
            return
        }
        pendingErrorMessage = null
        binding.dialogInputLayout.error = null
        binding.dialogConfirm.isEnabled = false
        appendTerminal(getString(R.string.dialog_verifying_license))
        appendTerminal(getString(R.string.dialog_sync_handshake))

        viewLifecycleOwner.lifecycleScope.launch {
            val hwid = DeviceIdentifier.getHwid(requireContext())
            val result = AuthApiClient.activateLicense(key, hwid)
            if (_binding == null) return@launch

            result.fold(
                onSuccess = { info ->
                    submittedKey = key
                    pendingErrorMessage = null
                    binding.dialogInputLayout.error = null
                    engine.persistLicense(key, info.remainingSeconds)
                    appendTerminal(getString(R.string.dialog_license_authorized))
                    appendTerminal(getString(R.string.dialog_license_ready))

                    val sent = engine.bridge.sendInput(key)
                    if (sent && engine.isProcessAlive.value) {
                        licenseVerified = true
                        render(Stage.License)
                    } else {
                        // Piping the key failed, so the binary never saw it and
                        // will never print "Awaiting START". Restart it with the
                        // key already exported; the ready collector flips the
                        // dialog to Start once the engine confirms it accepted.
                        appendTerminal(getString(R.string.dialog_restarting_with_key))
                        licenseVerified = false
                        render(Stage.License)
                        engine.relaunchWithKey(mod, key) { message ->
                            if (_binding != null) {
                                licenseVerified = false
                                appendTerminal("[-] $message")
                                pendingErrorMessage = message
                                render(Stage.License)
                            }
                        }
                    }
                },
                onFailure = { error ->
                    val message = error.message ?: getString(R.string.dialog_server_rejected)
                    pendingErrorMessage = message
                    appendTerminal("[-] $message")
                    binding.dialogConfirm.isEnabled = true
                    render(Stage.License)
                }
            )
        }
    }

    private fun startMod() {
        viewLifecycleOwner.lifecycleScope.launch {
            engine.bridge.sendInput("START")
            Toast.makeText(
                requireContext(),
                getString(R.string.dashboard_loading_kernel),
                Toast.LENGTH_SHORT
            ).show()
            render(Stage.Running)
        }
    }

    private fun stopMod() {
        val mod = item
        engine.stop()
        if (mod != null) {
            Toast.makeText(
                requireContext(),
                getString(R.string.dashboard_mod_stopped, mod.name),
                Toast.LENGTH_SHORT
            ).show()
        }
        dismiss()
    }

    // ------------------------------------------------------------------ terminal

    private fun appendTerminal(line: String) {
        terminalLines.addLast(line)
        // The deque is trimmed from the front, so row positions shift. A
        // positional notify would desync the adapter from the list; the list is
        // capped at 60 short rows, so a full rebind is the honest move.
        while (terminalLines.size > 60) terminalLines.removeFirst()
        (binding.dialogTerminal.adapter as? TerminalAdapter)?.notifyDataSetChanged()
        binding.dialogTerminal.scrollToPosition(terminalLines.lastIndex)
    }

    private inner class TerminalAdapter : androidx.recyclerview.widget.RecyclerView.Adapter<TerminalAdapter.Holder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            val row = ItemLogLineBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
            )
            return Holder(row)
        }

        override fun getItemCount(): Int = terminalLines.size

        override fun onBindViewHolder(holder: Holder, position: Int) {
            holder.bind(terminalLines.elementAt(position))
        }

        inner class Holder(private val row: ItemLogLineBinding) :
            androidx.recyclerview.widget.RecyclerView.ViewHolder(row.root) {

            fun bind(line: String) {
                val colorRes = when {
                    line.startsWith("[+]") || line.contains("Authorized") -> R.color.nin_success
                    line.startsWith("[-]") || line.contains("Error", true) -> R.color.nin_log_error
                    line.startsWith("[*]") -> R.color.nin_primary
                    else -> R.color.nin_log_default
                }
                row.logLine.text = line
                row.logLine.setTextColor(
                    androidx.core.content.ContextCompat.getColor(row.root.context, colorRes)
                )
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding.dialogTerminal.adapter = null
        _binding = null
    }

    override fun onDestroy() {
        downloadJob?.cancel()
        downloadJob = null
        super.onDestroy()
    }

    companion object {
        private const val ARG_ID = "arg_id"
        private const val ARG_NAME = "arg_name"
        private const val ARG_LABEL = "arg_label"

        fun newInstance(id: String, name: String, label: String): ModFlowDialog =
            ModFlowDialog().apply {
                arguments = Bundle().apply {
                    putString(ARG_ID, id)
                    putString(ARG_NAME, name)
                    putString(ARG_LABEL, label)
                }
            }
    }
}
