package com.andorid.ninmods.ui.deposit

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.andorid.ninmods.R
import com.andorid.ninmods.databinding.FragmentSubmitDepositBinding
import com.andorid.ninmods.databinding.ItemPaymentMethodBinding
import com.andorid.ninmods.network.AuthApiClient
import com.andorid.ninmods.network.PaymentMethodItem
import com.andorid.ninmods.ui.MainActivity
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Deposit form. Lists the available gateways, shows the chosen gateway's
 * account details for copying, and submits amount + TrxID to the server.
 */
class SubmitDepositFragment : Fragment() {

    private var _binding: FragmentSubmitDepositBinding? = null
    private val binding get() = _binding!!

    private val adapter = PaymentMethodAdapter { method -> select(method) }
    private var selected: PaymentMethodItem? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSubmitDepositBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.methodList.layoutManager = LinearLayoutManager(requireContext())
        binding.methodList.adapter = adapter

        binding.buttonCopyAccount.setOnClickListener { copyAccount() }
        binding.buttonSubmitDeposit.setOnClickListener { submit() }

        loadMethods()
    }

    private fun loadMethods() {
        val host = activity as? MainActivity ?: return
        val token = host.sessionManager.authToken ?: return

        binding.textBalance.text = String.format(
            Locale.US, "$%.2f", host.sessionManager.userBalance
        )

        viewLifecycleOwner.lifecycleScope.launch {
            val result = AuthApiClient.getPaymentMethods(token)
            if (_binding == null) return@launch

            result.fold(
                onSuccess = { list ->
                    adapter.submitList(list, null)
                    binding.textNoMethods.visibility =
                        if (list.isEmpty()) View.VISIBLE else View.GONE
                },
                onFailure = { error ->
                    adapter.submitList(emptyList(), null)
                    binding.textNoMethods.visibility = View.VISIBLE
                    Toast.makeText(
                        requireContext(),
                        error.message ?: getString(R.string.deposit_error_load_methods),
                        Toast.LENGTH_LONG
                    ).show()
                }
            )
        }
    }

    private fun select(method: PaymentMethodItem) {
        selected = method
        adapter.submitList(adapter.currentList, method.methodCode)

        binding.accountDetailsBlock.visibility = View.VISIBLE
        binding.textAccountDetails.text = buildString {
            append(method.name)
            if (method.accountDetails.isNotBlank()) {
                append("\n")
                append(method.accountDetails)
            }
            append("\n")
            append(
                getString(
                    R.string.deposit_limits,
                    method.minDeposit,
                    method.maxDeposit
                )
            )
        }

        binding.inputAmountLayout.helperText =
            getString(R.string.deposit_limits, method.minDeposit, method.maxDeposit)
        binding.inputAmountLayout.error = null
    }

    private fun copyAccount() {
        val method = selected ?: return
        val clipboard = requireContext()
            .getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(
            ClipData.newPlainText(getString(R.string.deposit_account), method.accountDetails)
        )
        Toast.makeText(requireContext(), R.string.deposit_copied, Toast.LENGTH_SHORT).show()
    }

    private fun submit() {
        val method = selected
        if (method == null) {
            Toast.makeText(requireContext(), R.string.deposit_error_method, Toast.LENGTH_SHORT).show()
            return
        }

        val amountText = binding.inputAmount.text?.toString()?.trim().orEmpty()
        val amount = amountText.toDoubleOrNull()
        if (amount == null || amount <= 0.0) {
            binding.inputAmountLayout.error = getString(R.string.deposit_error_amount)
            return
        }
        if (amount < method.minDeposit || amount > method.maxDeposit) {
            binding.inputAmountLayout.error = getString(
                R.string.deposit_error_amount_range, method.minDeposit, method.maxDeposit
            )
            return
        }
        binding.inputAmountLayout.error = null

        val trx = binding.inputTrx.text?.toString()?.trim().orEmpty()
        if (trx.isBlank()) {
            binding.inputTrxLayout.error = getString(R.string.deposit_error_trx)
            return
        }
        binding.inputTrxLayout.error = null

        val note = binding.inputNote.text?.toString()?.trim().orEmpty()

        val host = activity as? MainActivity ?: return
        val token = host.sessionManager.authToken ?: return

        binding.buttonSubmitDeposit.isEnabled = false
        binding.progressSubmit.visibility = View.VISIBLE

        viewLifecycleOwner.lifecycleScope.launch {
            val result = AuthApiClient.submitDeposit(token, method.methodCode, amount, trx, note)
            if (_binding == null) return@launch

            binding.progressSubmit.visibility = View.GONE
            binding.buttonSubmitDeposit.isEnabled = true

            result.fold(
                onSuccess = {
                    Toast.makeText(
                        requireContext(),
                        R.string.deposit_success,
                        Toast.LENGTH_LONG
                    ).show()
                    parentFragmentManager.popBackStack()
                },
                onFailure = { error ->
                    Toast.makeText(
                        requireContext(),
                        error.message ?: getString(R.string.deposit_failed),
                        Toast.LENGTH_LONG
                    ).show()
                }
            )
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding.methodList.adapter = null
        _binding = null
    }

    /** Gateway list with a single selection carried by the radio buttons. */
    private class PaymentMethodAdapter(
        private val onSelect: (PaymentMethodItem) -> Unit
    ) : RecyclerView.Adapter<PaymentMethodAdapter.Holder>() {

        var currentList: List<PaymentMethodItem> = emptyList()
            private set

        private var selectedCode: String? = null

        fun submitList(list: List<PaymentMethodItem>, selected: String?) {
            currentList = list
            selectedCode = selected
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            val binding = ItemPaymentMethodBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
            )
            return Holder(binding)
        }

        override fun getItemCount(): Int = currentList.size

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val method = currentList[position]
            holder.bind(method, method.methodCode == selectedCode) { onSelect(method) }
        }

        inner class Holder(private val binding: ItemPaymentMethodBinding) :
            RecyclerView.ViewHolder(binding.root) {

            fun bind(method: PaymentMethodItem, isSelected: Boolean, onClick: () -> Unit) {
                val context = binding.root.context
                binding.methodName.text = method.name
                binding.methodLimits.text = context.getString(
                    R.string.deposit_limits, method.minDeposit, method.maxDeposit
                )
                binding.methodSelect.isChecked = isSelected
                binding.root.setOnClickListener { onClick() }
            }
        }
    }
}
