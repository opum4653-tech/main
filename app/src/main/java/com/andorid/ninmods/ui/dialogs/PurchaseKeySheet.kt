package com.andorid.ninmods.ui.dialogs

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.andorid.ninmods.R
import com.andorid.ninmods.databinding.ItemPlanRowBinding
import com.andorid.ninmods.databinding.SheetPurchaseKeyBinding
import com.andorid.ninmods.network.AuthApiClient
import com.andorid.ninmods.network.SubscriptionPlanItem
import com.andorid.ninmods.ui.MainActivity
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Buy-key sheet. Lists the subscription plans, tracks the selection, and buys
 * the chosen plan straight out of the wallet balance.
 */
class PurchaseKeySheet : BottomSheetDialogFragment() {

    private var _binding: SheetPurchaseKeyBinding? = null
    private val binding get() = _binding!!

    private val adapter = PlanAdapter { plan ->
        selected = plan
        binding.buttonBuy.isEnabled = true
        binding.buttonBuy.text = getString(
            R.string.purchase_buy_now
        ) + " · $" + String.format(Locale.US, "%.2f", plan.price)
    }

    private var plans: List<SubscriptionPlanItem> = emptyList()
    private var selected: SubscriptionPlanItem? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = SheetPurchaseKeyBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.planList.layoutManager = LinearLayoutManager(requireContext())
        binding.planList.adapter = adapter

        binding.buttonBuy.setOnClickListener { buy() }

        loadPlans()
    }

    private fun loadPlans() {
        val host = activity as? MainActivity ?: return
        val token = host.sessionManager.authToken ?: return

        binding.progressBuy.visibility = View.VISIBLE

        viewLifecycleOwner.lifecycleScope.launch {
            val profile = AuthApiClient.getProfileWithEtag(token).getOrNull()?.first
            if (_binding == null) return@launch
            profile?.let { data ->
                binding.textBalance.text =
                    "$" + String.format(Locale.US, "%.2f", data.balance)
            }

            val result = AuthApiClient.getAvailablePlans(token)
            if (_binding == null) return@launch
            binding.progressBuy.visibility = View.GONE

            result.fold(
                onSuccess = { list ->
                    plans = list
                    adapter.submitList(list)
                    binding.textEmpty.visibility =
                        if (list.isEmpty()) View.VISIBLE else View.GONE
                },
                onFailure = { error ->
                    plans = emptyList()
                    adapter.submitList(emptyList())
                    binding.textEmpty.visibility = View.VISIBLE
                    Toast.makeText(
                        requireContext(),
                        error.message ?: getString(R.string.purchase_load_failed),
                        Toast.LENGTH_LONG
                    ).show()
                }
            )
        }
    }

    private fun buy() {
        val plan = selected ?: return
        val host = activity as? MainActivity ?: return
        val token = host.sessionManager.authToken ?: return

        binding.buttonBuy.isEnabled = false
        binding.progressBuy.visibility = View.VISIBLE

        viewLifecycleOwner.lifecycleScope.launch {
            val result = AuthApiClient.purchasePlan(token, plan.id)
            if (_binding == null) return@launch

            binding.progressBuy.visibility = View.GONE
            result.fold(
                onSuccess = {
                    Toast.makeText(
                        requireContext(),
                        R.string.purchase_success,
                        Toast.LENGTH_LONG
                    ).show()
                    dismiss()
                },
                onFailure = { error ->
                    val message = error.message.orEmpty()
                    val insufficient = message.contains("insufficient", ignoreCase = true) ||
                        message.contains("balance", ignoreCase = true)
                    Toast.makeText(
                        requireContext(),
                        if (insufficient) {
                            getString(
                                R.string.purchase_insufficient,
                                String.format(Locale.US, "%.2f", plan.price)
                            )
                        } else {
                            message.ifBlank { getString(R.string.purchase_failed) }
                        },
                        Toast.LENGTH_LONG
                    ).show()
                    binding.buttonBuy.isEnabled = true
                }
            )
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding.planList.adapter = null
        _binding = null
    }

    private class PlanAdapter(
        private val onSelect: (SubscriptionPlanItem) -> Unit
    ) : RecyclerView.Adapter<PlanAdapter.Holder>() {

        private val items = mutableListOf<SubscriptionPlanItem>()
        private var selectedId = -1

        fun submitList(list: List<SubscriptionPlanItem>) {
            items.clear()
            items.addAll(list)
            selectedId = -1
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            val binding = ItemPlanRowBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
            )
            return Holder(binding)
        }

        override fun getItemCount(): Int = items.size

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val plan = items[position]
            holder.bind(plan, plan.id == selectedId) {
                selectedId = plan.id
                notifyDataSetChanged()
                onSelect(plan)
            }
        }

        inner class Holder(private val binding: ItemPlanRowBinding) :
            RecyclerView.ViewHolder(binding.root) {

            fun bind(plan: SubscriptionPlanItem, isSelected: Boolean, onClick: () -> Unit) {
                val context = binding.root.context
                binding.planTitle.text = plan.title.ifBlank { plan.planCode }
                binding.planDescription.text = plan.description.ifBlank {
                    context.getString(R.string.purchase_select_duration)
                }
                binding.planPrice.text = "$" + String.format(Locale.US, "%.2f", plan.price)

                val accent = if (isSelected) R.color.nin_primary else R.color.nin_text_secondary
                binding.planSelected.setColorFilter(
                    androidx.core.content.ContextCompat.getColor(context, accent)
                )
                binding.planSelected.alpha = if (isSelected) 1f else 0.35f

                binding.root.setOnClickListener { onClick() }
            }
        }
    }
}
