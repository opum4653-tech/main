package com.andorid.ninmods.ui.dialogs

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.andorid.ninmods.databinding.ItemSheetRowBinding
import com.andorid.ninmods.databinding.SheetSelectionBinding
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

/**
 * Generic bottom sheet list used for driver selection and any other
 * single-choice list the app needs. The caller supplies the title, subtitle,
 * rows and a callback that receives the chosen index.
 */
class SelectionSheet : BottomSheetDialogFragment() {

    private var _binding: SheetSelectionBinding? = null
    private val binding get() = _binding!!

    var onItemSelected: ((Int) -> Unit)? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = SheetSelectionBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val args = requireArguments()
        val title = args.getString(ARG_TITLE).orEmpty()
        val subtitle = args.getString(ARG_SUBTITLE).orEmpty()
        val items = args.getStringArrayList(ARG_ITEMS).orEmpty()

        binding.sheetTitle.text = title
        if (subtitle.isBlank()) {
            binding.sheetSubtitle.visibility = View.GONE
        } else {
            binding.sheetSubtitle.text = subtitle
        }

        binding.sheetList.layoutManager = LinearLayoutManager(requireContext())
        binding.sheetList.adapter = RowAdapter(items) { index ->
            onItemSelected?.invoke(index)
        }

        binding.sheetAction.visibility = View.GONE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding.sheetList.adapter = null
        _binding = null
    }

    private class RowAdapter(
        private val items: List<String>,
        private val onPick: (Int) -> Unit
    ) : RecyclerView.Adapter<RowAdapter.Holder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            val binding = ItemSheetRowBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
            )
            return Holder(binding)
        }

        override fun getItemCount(): Int = items.size

        override fun onBindViewHolder(holder: Holder, position: Int) {
            holder.bind(items[position]) { onPick(position) }
        }

        inner class Holder(private val binding: ItemSheetRowBinding) :
            RecyclerView.ViewHolder(binding.root) {

            fun bind(label: String, onClick: () -> Unit) {
                binding.sheetRowTitle.text = label
                binding.sheetRowSubtitle.visibility = View.GONE
                binding.root.setOnClickListener {
                    onClick()
                }
            }
        }
    }

    companion object {
        private const val ARG_TITLE = "arg_title"
        private const val ARG_SUBTITLE = "arg_subtitle"
        private const val ARG_ITEMS = "arg_items"

        fun newInstance(title: String, subtitle: String, items: List<String>): SelectionSheet =
            SelectionSheet().apply {
                arguments = Bundle().apply {
                    putString(ARG_TITLE, title)
                    putString(ARG_SUBTITLE, subtitle)
                    putStringArrayList(ARG_ITEMS, ArrayList(items))
                }
            }
    }
}
