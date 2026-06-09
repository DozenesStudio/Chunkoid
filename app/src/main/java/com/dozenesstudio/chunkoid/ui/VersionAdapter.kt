package com.dozenesstudio.chunkoid.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.dozenesstudio.chunkoid.R
import com.dozenesstudio.chunkoid.databinding.ItemVersionCardBinding

class VersionAdapter(
    private val versions: List<Pair<String, String>>,
    private val onItemClick: (String, String) -> Unit
) : RecyclerView.Adapter<VersionAdapter.VersionViewHolder>() {

    private var selectedFormat: String = ""

    class VersionViewHolder(
        private val binding: ItemVersionCardBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(version: Pair<String, String>, isSelected: Boolean) {
            binding.tvVersionName.text = version.second

            if (isSelected) {
                binding.cardVersion.strokeWidth = 4
                binding.cardVersion.strokeColor = ContextCompat.getColor(
                    itemView.context,
                    R.color.minecraft_green
                )
                binding.tvVersionName.setTextColor(
                    ContextCompat.getColor(itemView.context, R.color.minecraft_green)
                )
            } else {
                binding.cardVersion.strokeWidth = 0
                binding.tvVersionName.setTextColor(
                    ContextCompat.getColor(itemView.context, R.color.white)
                )
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VersionViewHolder {
        val binding = ItemVersionCardBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return VersionViewHolder(binding)
    }

    override fun onBindViewHolder(holder: VersionViewHolder, position: Int) {
        val version = versions[position]
        holder.bind(version, version.first == selectedFormat)

        holder.itemView.setOnClickListener {
            onItemClick(version.first, version.second)
        }
    }

    override fun getItemCount(): Int = versions.size

    fun setSelectedFormat(format: String) {
        val oldSelected = selectedFormat
        selectedFormat = format

        if (oldSelected.isNotEmpty()) {
            val oldIndex = versions.indexOfFirst { it.first == oldSelected }
            if (oldIndex >= 0) notifyItemChanged(oldIndex)
        }

        if (format.isNotEmpty()) {
            val newIndex = versions.indexOfFirst { it.first == format }
            if (newIndex >= 0) notifyItemChanged(newIndex)
        }
    }
}