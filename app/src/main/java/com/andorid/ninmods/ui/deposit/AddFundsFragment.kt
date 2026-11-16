package com.andorid.ninmods.ui.deposit

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.andorid.ninmods.R
import com.andorid.ninmods.databinding.FragmentAddFundsBinding
import com.andorid.ninmods.databinding.ItemTransactionRowBinding
import com.andorid.ninmods.network.AuthApiClient
import com.andorid.ninmods.network.TransactionItem
import com.andorid.ninmods.ui.MainActivity
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Currency
import java.util.Date
import java.util.Locale

/**
 * Wallet screen. Shows the current balance and the transaction ledger, with
 * pull-to-refresh and a shortcut into the deposit submission form.
 */
class AddFundsFragment : Fragment() {

    private var _binding: FragmentAddFundsBinding? = null
    private val binding get() = _binding!!

    private val adapter = TransactionAdapter()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAddFundsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.transactionList.layoutManager = LinearLayoutManager(requireContext())
        binding.transactionList.adapter = adapter

        binding.buttonAddFunds.setOnClickListener {
            (activity as? MainActivity)?.openSubScreen(
                SubmitDepositFragment(),
                R.string.nav_submit_deposit
            )
        }
        binding.buttonRefresh.setOnClickListener { load() }

        load()
    }

    private fun load() {
        val host = activity as? MainActivity ?: return
        val token = host.sessionManager.authToken ?: run {
            binding.textEmpty.visibility = View.VISIBLE
            return
        }

        viewLifecycleOwner.lifecycleScope.launch {
            val profile = AuthApiClient.getProfileWithEtag(token).getOrNull()?.first
            if (_binding == null) return@launch

            if (profile != null) {
                host.sessionManager.userBalance = profile.balance
                binding.textBalance.text = formatMoney(profile.balance, profile.currency)
            } else {
                binding.textBalance.text = formatMoney(host.sessionManager.userBalance, "USD")
            }

            AuthApiClient.getMyTransactions(token).fold(
                onSuccess = { list ->
                    if (_binding == null) return@fold
                    adapter.submitList(list)
                    binding.textEmpty.visibility =
                        if (list.isEmpty()) View.VISIBLE else View.GONE
                },
                onFailure = {
                    if (_binding == null) return@fold
                    binding.textEmpty.visibility = View.VISIBLE
                }
            )
        }
    }

    private fun formatMoney(value: Double, currency: String): String {
        val format = NumberFormat.getCurrencyInstance(Locale.US)
        format.currency = Currency.getInstance(currency.ifBlank { "USD" })
        return format.format(value)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding.transactionList.adapter = null
        _binding = null
    }
}

/**
 * Transaction ledger rows. Type/amount/status plus a formatted timestamp and
 * the optional admin note.
 */
class TransactionAdapter :
    ListAdapter<TransactionItem, TransactionAdapter.Holder>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val binding = ItemTransactionRowBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return Holder(binding)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(getItem(position))

    inner class Holder(private val binding: ItemTransactionRowBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: TransactionItem) {
            val context = binding.root.context

            binding.trxType.text = item.type.replaceFirstChar { it.uppercase(Locale.US) }
            binding.trxAmount.text = String.format(Locale.US, "$%.2f", item.amount)
            binding.trxStatus.text = item.status.uppercase(Locale.US)
            binding.trxStatus.setTextColor(
                ContextCompat.getColor(
                    context,
                    when (item.status.lowercase(Locale.US)) {
                        "approved", "completed", "success" -> R.color.nin_success
                        "pending" -> R.color.nin_warning
                        "rejected", "failed" -> R.color.nin_error
                        else -> R.color.nin_text_secondary
                    }
                )
            )

            val meta = buildString {
                item.paymentMethod?.takeIf { it.isNotBlank() }?.let { append(it) }
                item.trxId?.takeIf { it.isNotBlank() }?.let {
                    if (isNotEmpty()) append(" · ")
                    append(context.getString(R.string.wallet_trx_id_format, it))
                }
                item.createdAt?.takeIf { it.isNotBlank() }?.let {
                    if (isNotEmpty()) append(" · ")
                    append(formatDate(it))
                }
            }
            binding.trxMeta.text = meta.ifBlank { context.getString(R.string.wallet_no_trx) }

            val note = item.adminNotes?.takeIf { it.isNotBlank() }
            binding.trxNote.visibility = if (note == null) View.GONE else View.VISIBLE
            if (note != null) {
                binding.trxNote.text = context.getString(R.string.wallet_note_format, note)
            }
        }

        private fun formatDate(raw: String): String = try {
            val parser = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
            val date: Date? = parser.parse(raw)
            if (date != null) {
                SimpleDateFormat("dd MMM, hh:mm a", Locale.US).format(date)
            } else {
                raw
            }
        } catch (_: Exception) {
            raw
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<TransactionItem>() {
            override fun areItemsTheSame(oldItem: TransactionItem, newItem: TransactionItem) =
                oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: TransactionItem, newItem: TransactionItem) =
                oldItem == newItem
        }
    }
}
