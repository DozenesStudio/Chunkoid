package com.dozenesstudio.chunkoid.ui

import android.graphics.BitmapFactory
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.PopupMenu
import androidx.recyclerview.widget.RecyclerView
import com.dozenesstudio.chunkoid.R
import com.dozenesstudio.chunkoid.databinding.ItemWorldCardBinding
import com.dozenesstudio.chunkoid.model.WorldInfo
import java.io.File

class WorldCardAdapter(
    private val worlds: List<WorldInfo>,
    private val onExportClick: (WorldInfo, ExportAction) -> Unit,
    private val onOpenFolderClick: (WorldInfo) -> Unit
) : RecyclerView.Adapter<WorldCardAdapter.WorldViewHolder>() {

    enum class ExportAction {
        OPEN_FOLDER,
        EXPORT_MCWORLD,
        EXPORT_ZIP,
        DELETE
    }

    class WorldViewHolder(val binding: ItemWorldCardBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): WorldViewHolder {
        val binding = ItemWorldCardBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return WorldViewHolder(binding)
    }

    override fun onBindViewHolder(holder: WorldViewHolder, position: Int) {
        val world = worlds[position]
        val context = holder.itemView.context
        val binding = holder.binding

        binding.tvWorldName.text = world.name

        val platformVersionText = if (world.isJava) {
            context.getString(R.string.converted_to_java, world.version)
        } else {
            context.getString(R.string.converted_to_bedrock, world.version)
        }
        binding.tvPlatformVersion.text = platformVersionText

        binding.cardPlatform.strokeColor = context.getColor(R.color.minecraft_green)

        loadWorldIcon(world, binding)

        binding.btnExport.setOnClickListener { view ->
            val popup = PopupMenu(context, view)
            popup.menuInflater.inflate(R.menu.menu_export, popup.menu)
            popup.setOnMenuItemClickListener { menuItem ->
                when (menuItem.itemId) {
                    R.id.action_open_folder -> {
                        onExportClick(world, ExportAction.OPEN_FOLDER)
                        true
                    }
                    R.id.action_export_mcworld -> {
                        onExportClick(world, ExportAction.EXPORT_MCWORLD)
                        true
                    }
                    R.id.action_export_zip -> {
                        onExportClick(world, ExportAction.EXPORT_ZIP)
                        true
                    }
                    R.id.action_delete -> {
                        onExportClick(world, ExportAction.DELETE)
                        true
                    }
                    else -> false
                }
            }
            val deleteItem = popup.menu.findItem(R.id.action_delete)
            val redTitle = SpannableString(context.getString(R.string.delete_world))
            redTitle.setSpan(ForegroundColorSpan(context.getColor(R.color.minecraft_red)), 0, redTitle.length, Spanned.SPAN_INCLUSIVE_INCLUSIVE)
            deleteItem.title = redTitle
            
            // Show menu above the anchor to avoid overflow
            popup.gravity = android.view.Gravity.TOP or android.view.Gravity.END
            popup.show()
        }
    }

    private fun loadWorldIcon(world: WorldInfo, binding: ItemWorldCardBinding) {
        try {
            val worldDir = File(world.directoryPath)
            if (!worldDir.exists()) {
                binding.ivWorldIcon.setImageResource(R.drawable.unknown)
                return
            }

            val iconFile = worldDir.listFiles()?.find { file ->
                file.name.lowercase() in listOf("icon.png", "world_icon.jpeg", "world_icon.jpg")
            }

            if (iconFile != null && iconFile.isFile) {
                val bitmap = BitmapFactory.decodeFile(iconFile.absolutePath)
                if (bitmap != null) {
                    binding.ivWorldIcon.setImageBitmap(bitmap)
                    return
                }
            }

            binding.ivWorldIcon.setImageResource(R.drawable.unknown)
        } catch (e: Exception) {
            binding.ivWorldIcon.setImageResource(R.drawable.unknown)
        }
    }

    override fun getItemCount(): Int = worlds.size
}