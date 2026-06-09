package com.dozenesstudio.chunkoid.ui

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import androidx.fragment.app.DialogFragment
import androidx.recyclerview.widget.GridLayoutManager
import com.dozenesstudio.chunkoid.R
import com.dozenesstudio.chunkoid.databinding.DialogVersionPickerBinding
import com.dozenesstudio.chunkoid.utils.ToastUtils

class VersionPickerDialog : DialogFragment() {

    private var _binding: DialogVersionPickerBinding? = null
    private val binding get() = _binding!!

    private var selectedFormat: String = ""
    private var selectedVersionName: String = ""
    private var onVersionSelected: ((String, String) -> Unit)? = null

    private val bedrockVersions = listOf(
        "BEDROCK_1_12_0" to "基岩版 1.12.0",
        "BEDROCK_1_13_0" to "基岩版 1.13.0",
        "BEDROCK_1_14_0" to "基岩版 1.14.0",
        "BEDROCK_1_16_0" to "基岩版 1.16.0",
        "BEDROCK_1_17_0" to "基岩版 1.17.0",
        "BEDROCK_1_18_0" to "基岩版 1.18.0",
        "BEDROCK_1_19_0" to "基岩版 1.19.0",
        "BEDROCK_1_20_0" to "基岩版 1.20.0",
        "BEDROCK_1_20_80" to "基岩版 1.20.80",
        "BEDROCK_1_21_0" to "基岩版 1.21.0",
        "BEDROCK_1_21_130" to "基岩版 1.21.130",
        "BEDROCK_1_26_0" to "基岩版 1.26.0"
    )

    private val javaVersions = listOf(
        "JAVA_1_8_8" to "Java版 1.8.8",
        "JAVA_1_9" to "Java版 1.9",
        "JAVA_1_10" to "Java版 1.10",
        "JAVA_1_11" to "Java版 1.11",
        "JAVA_1_12" to "Java版 1.12",
        "JAVA_1_13" to "Java版 1.13",
        "JAVA_1_14" to "Java版 1.14",
        "JAVA_1_15" to "Java版 1.15",
        "JAVA_1_16" to "Java版 1.16",
        "JAVA_1_17" to "Java版 1.17",
        "JAVA_1_18" to "Java版 1.18",
        "JAVA_1_19" to "Java版 1.19",
        "JAVA_1_20" to "Java版 1.20",
        "JAVA_1_20_5" to "Java版 1.20.5",
        "JAVA_1_21" to "Java版 1.21",
        "JAVA_1_21_11" to "Java版 1.21.11"
    )

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        return dialog
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogVersionPickerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerViews()
        setupConfirmButton()
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        dialog?.window?.setBackgroundDrawableResource(android.R.color.transparent)
    }

    private fun setupRecyclerViews() {
        val bedrockAdapter = VersionAdapter(bedrockVersions) { format, name ->
            selectedFormat = format
            selectedVersionName = name
            updateSelection()
        }
        binding.rvBedrockVersions.apply {
            layoutManager = GridLayoutManager(requireContext(), 3)
            adapter = bedrockAdapter
        }

        val javaAdapter = VersionAdapter(javaVersions) { format, name ->
            selectedFormat = format
            selectedVersionName = name
            updateSelection()
        }
        binding.rvJavaVersions.apply {
            layoutManager = GridLayoutManager(requireContext(), 3)
            adapter = javaAdapter
        }
    }

    private fun updateSelection() {
        (binding.rvBedrockVersions.adapter as? VersionAdapter)?.setSelectedFormat(selectedFormat)
        (binding.rvJavaVersions.adapter as? VersionAdapter)?.setSelectedFormat(selectedFormat)
    }

    private fun setupConfirmButton() {
        binding.btnConfirm.setOnClickListener {
            if (selectedFormat.isNotEmpty()) {
                onVersionSelected?.invoke(selectedFormat, selectedVersionName)
                dismiss()
            } else {
                ToastUtils.show(requireContext(), "请先选择一个版本", isError = true)
            }
        }
    }

    fun setOnVersionSelectedListener(listener: (String, String) -> Unit) {
        onVersionSelected = listener
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "VersionPickerDialog"

        fun newInstance(): VersionPickerDialog {
            return VersionPickerDialog()
        }
    }
}