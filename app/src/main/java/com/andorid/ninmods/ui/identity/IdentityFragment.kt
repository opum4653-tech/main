package com.andorid.ninmods.ui.identity

import android.os.Bundle
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.andorid.ninmods.R
import com.andorid.ninmods.data.DeviceIdentityManager
import com.andorid.ninmods.databinding.FragmentIdentityBinding
import kotlinx.coroutines.launch

/**
 * Anti-blacklist / identity screen. Reads the live hardware identifiers, lets
 * the operator randomize or hand-edit each one, then applies the set through
 * the root identity engine and wipes game guest tokens.
 *
 * The eight <include> blocks all point at partial_identity_field.xml, so the
 * child views are resolved from each include root rather than a second copy.
 */
class IdentityFragment : Fragment() {

    private var _binding: FragmentIdentityBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentIdentityBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        wire(binding.fieldImei1.root, R.string.identity_imei_primary) {
            DeviceIdentityManager.generateRealisticImei()
        }
        wire(binding.fieldImei2.root, R.string.identity_imei_secondary) {
            DeviceIdentityManager.generateRealisticImei()
        }
        wire(binding.fieldAndroidId.root, R.string.identity_android_id) {
            DeviceIdentityManager.generateAndroidId()
        }
        wire(binding.fieldSerial.root, R.string.identity_serial) {
            DeviceIdentityManager.generateSerialNumber()
        }
        wire(binding.fieldMac.root, R.string.identity_mac) {
            DeviceIdentityManager.generateMacAddress()
        }
        wire(binding.fieldBluetooth.root, R.string.identity_bluetooth) {
            DeviceIdentityManager.generateMacAddress()
        }
        wire(binding.fieldBuildId.root, R.string.identity_build_id, null)
        wire(binding.fieldFingerprint.root, R.string.identity_fingerprint, null)

        binding.buttonRandomizeAll.setOnClickListener { randomizeAll() }
        binding.buttonResetFields.setOnClickListener { loadIdentity() }
        binding.buttonApplyIdentity.setOnClickListener { applyIdentity("applied") }
        binding.buttonWipeTokens.setOnClickListener { applyIdentity("wiped") }

        loadIdentity()
    }

    private fun wire(root: View, labelRes: Int, randomize: (() -> String)?) {
        val label = getString(labelRes)
        root.findViewById<TextView>(R.id.field_label)?.text = label

        val randomizeButton = root.findViewById<View>(R.id.field_randomize)
        if (randomize == null) {
            randomizeButton.visibility = View.GONE
        } else {
            randomizeButton.setOnClickListener {
                root.findViewById<EditText>(R.id.field_value)?.setText(randomize())
            }
        }

        root.findViewById<View>(R.id.field_copy)?.setOnClickListener {
            val value = valueOf(root)
            if (value.isBlank()) return@setOnClickListener
            val clipboard = requireContext()
                .getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText(label, value))
            Toast.makeText(requireContext(), R.string.action_copy, Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadIdentity() {
        viewLifecycleOwner.lifecycleScope.launch {
            val info = DeviceIdentityManager.getDeviceInfo(requireContext())
            if (_binding == null) return@launch

            binding.textModel.text = info.model
            binding.textManufacturer.text = getString(
                R.string.identity_manufacturer_format,
                info.manufacturer,
                info.device
            )

            setValue(binding.fieldImei1.root, info.imei1)
            setValue(binding.fieldImei2.root, info.imei2)
            setValue(binding.fieldAndroidId.root, info.androidId)
            setValue(binding.fieldSerial.root, info.serialNumber)
            setValue(binding.fieldMac.root, info.macAddress)
            setValue(binding.fieldBluetooth.root, info.bluetoothAddress)
            setValue(binding.fieldBuildId.root, info.buildId)
            setValue(binding.fieldFingerprint.root, info.fingerprint)
        }
    }

    private fun setValue(root: View, value: String) {
        root.findViewById<EditText>(R.id.field_value)?.setText(value)
    }

    private fun valueOf(root: View): String =
        root.findViewById<EditText>(R.id.field_value)?.text?.toString()?.trim().orEmpty()

    private fun randomizeAll() {
        setValue(binding.fieldImei1.root, DeviceIdentityManager.generateRealisticImei())
        setValue(binding.fieldImei2.root, DeviceIdentityManager.generateRealisticImei())
        setValue(binding.fieldAndroidId.root, DeviceIdentityManager.generateAndroidId())
        setValue(binding.fieldSerial.root, DeviceIdentityManager.generateSerialNumber())
        setValue(binding.fieldMac.root, DeviceIdentityManager.generateMacAddress())
        setValue(binding.fieldBluetooth.root, DeviceIdentityManager.generateMacAddress())
        Toast.makeText(requireContext(), R.string.identity_generated, Toast.LENGTH_SHORT).show()
    }

    private fun applyIdentity(mode: String) {
        binding.progressIdentity.visibility = View.VISIBLE
        binding.buttonApplyIdentity.isEnabled = false
        binding.buttonWipeTokens.isEnabled = false

        viewLifecycleOwner.lifecycleScope.launch {
            val result = DeviceIdentityManager.applyNewIdentity(
                newAndroidId = valueOf(binding.fieldAndroidId.root),
                newSerial = valueOf(binding.fieldSerial.root),
                newImei1 = valueOf(binding.fieldImei1.root),
                newImei2 = valueOf(binding.fieldImei2.root),
                newMac = valueOf(binding.fieldMac.root),
                resetGameGuest = true
            )
            if (_binding == null) return@launch

            binding.progressIdentity.visibility = View.GONE
            binding.buttonApplyIdentity.isEnabled = true
            binding.buttonWipeTokens.isEnabled = true

            result.fold(
                onSuccess = {
                    Toast.makeText(requireContext(), R.string.identity_applied, Toast.LENGTH_LONG).show()
                    loadIdentity()
                },
                onFailure = { error ->
                    Toast.makeText(
                        requireContext(),
                        getString(R.string.identity_failed, error.message ?: mode),
                        Toast.LENGTH_LONG
                    ).show()
                }
            )
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
