package com.dozenesstudio.chunkoid

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.dozenesstudio.chunkoid.databinding.FragmentOutputManagerBinding
import com.dozenesstudio.chunkoid.model.WorldInfo
import com.dozenesstudio.chunkoid.ui.WorldCardAdapter
import com.dozenesstudio.chunkoid.utils.BackgroundUtils
import com.dozenesstudio.chunkoid.utils.ToastUtils
import androidx.documentfile.provider.DocumentFile
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class OutputManagerFragment : Fragment() {

    private var _binding: FragmentOutputManagerBinding? = null
    private val binding get() = _binding!!

    private lateinit var worldAdapter: WorldCardAdapter
    private val worlds = mutableListOf<WorldInfo>()

    private var pendingExportWorld: WorldInfo? = null
    private var pendingExportIsMcworld: Boolean = false
    private var pendingExportIsFolder: Boolean = false

    companion object {
        private const val CHUNKOID_OUTPUT_DIR = "chunkoid output"
    }

    private val createDocumentLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("*/*")
    ) { uri ->
        uri?.let { destinationUri ->
            pendingExportWorld?.let { world ->
                exportToUri(world, destinationUri, pendingExportIsMcworld)
            }
        }
        pendingExportWorld = null
        pendingExportIsMcworld = false
    }

    private val openDocumentTreeLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let { treeUri ->
            pendingExportWorld?.let { world ->
                exportFolderToTree(world, treeUri)
            }
        }
        pendingExportWorld = null
        pendingExportIsFolder = false
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentOutputManagerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupBackground()
        setupRecyclerView()
        setupFab()
        loadWorlds()
    }

    private fun setupBackground() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
        val selectedTheme = prefs.getString("selected_theme", "bg")
        
        if (selectedTheme == SettingsActivity.THEME_CUSTOM) {
            val customBgFile = File(requireContext().filesDir, SettingsActivity.CUSTOM_BG_FILE_NAME)
            if (customBgFile.exists()) {
                try {
                    val bitmap = android.graphics.BitmapFactory.decodeFile(customBgFile.path)
                    val darkenedBitmap = BackgroundUtils.applyDarkening(bitmap)
                    binding.ivBackground.setImageBitmap(darkenedBitmap)
                } catch (e: Exception) {
                    binding.ivBackground.setImageResource(R.drawable.bg)
                }
            } else {
                binding.ivBackground.setImageResource(R.drawable.bg)
            }
        } else {
            val drawableRes = when (selectedTheme) {
                "bg2" -> R.drawable.bg2
                "bg4" -> R.drawable.bg4
                else -> R.drawable.bg
            }
            binding.ivBackground.setImageResource(drawableRes)
        }
    }

    private fun setupRecyclerView() {
        worldAdapter = WorldCardAdapter(
            worlds = worlds,
            onExportClick = { world, action -> handleExportClick(world, action) }
        )
        binding.rvWorlds.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = worldAdapter
        }
    }

    private fun setupFab() {
        binding.fabStartGame.setOnClickListener { showGameLauncherMenu(it) }
    }

    private fun loadWorlds() {
        worlds.clear()
        val documentsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
        val chunkoidOutputDir = File(documentsDir, CHUNKOID_OUTPUT_DIR)

        if (!chunkoidOutputDir.exists() || !chunkoidOutputDir.isDirectory) {
            showEmptyState()
            return
        }

        chunkoidOutputDir.listFiles()?.filter { it.isDirectory }?.forEach { worldDir ->
            val convertInfoFile = File(worldDir, "Convertinfo.txt")
            if (convertInfoFile.exists()) {
                parseWorldInfo(worldDir, convertInfoFile)?.let { worlds.add(it) }
            }
        }

        if (worlds.isEmpty()) {
            showEmptyState()
        } else {
            showWorldList()
        }
    }

    private fun parseWorldInfo(worldDir: File, convertInfoFile: File): WorldInfo? {
        return try {
            val lines = convertInfoFile.readLines()
            var platform = ""
            var version = ""
            var levelname = worldDir.name

            for (line in lines) {
                when {
                    line.startsWith("platform ", ignoreCase = true) -> {
                        platform = line.substringAfter("platform ").trim()
                    }
                    line.startsWith("version ", ignoreCase = true) -> {
                        version = line.substringAfter("version ").trim()
                    }
                    line.startsWith("levelname ", ignoreCase = true) -> {
                        levelname = line.substringAfter("levelname ").trim()
                    }
                }
            }

            if (platform.isNotEmpty() && version.isNotEmpty()) {
                WorldInfo(
                    name = levelname,
                    directoryPath = worldDir.absolutePath,
                    platform = platform,
                    version = version
                )
            } else {
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun showEmptyState() {
        binding.rvWorlds.visibility = View.GONE
        binding.tvEmpty.visibility = View.VISIBLE
    }

    private fun showWorldList() {
        binding.rvWorlds.visibility = View.VISIBLE
        binding.tvEmpty.visibility = View.GONE
        worldAdapter.notifyDataSetChanged()
    }

    private fun handleExportClick(world: WorldInfo, action: WorldCardAdapter.ExportAction) {
        when (action) {
            WorldCardAdapter.ExportAction.EXPORT_MCWORLD -> startExport(world, isMcworld = true)
            WorldCardAdapter.ExportAction.EXPORT_ZIP -> startExport(world, isMcworld = false)
            WorldCardAdapter.ExportAction.EXPORT_FOLDER -> startFolderExport(world)
            WorldCardAdapter.ExportAction.VIEW_LOG -> viewConversionLog(world)
            WorldCardAdapter.ExportAction.DELETE -> deleteWorld(world)
        }
    }

    private fun startFolderExport(world: WorldInfo) {
        pendingExportWorld = world
        pendingExportIsFolder = true
        openDocumentTreeLauncher.launch(null)
    }

    private fun startExport(world: WorldInfo, isMcworld: Boolean) {
        pendingExportWorld = world
        pendingExportIsMcworld = isMcworld
        val extension = if (isMcworld) ".mcworld" else ".zip"
        val mimeType = if (isMcworld) "application/x-mcworld" else "application/zip"
        createDocumentLauncher.launch(world.name + extension)
    }

    private fun exportToUri(world: WorldInfo, destinationUri: Uri, isMcworld: Boolean) {
        val sourceDir = File(world.directoryPath)

        try {
            requireContext().contentResolver.openOutputStream(destinationUri)?.use { outputStream ->
                ZipOutputStream(outputStream).use { zos ->
                    sourceDir.walkTopDown().forEach { file ->
                        val relativePath = file.relativeTo(sourceDir)
                        if (!file.isDirectory) {
                            zos.putNextEntry(ZipEntry(relativePath.toString()))
                            FileInputStream(file).use { fis ->
                                fis.copyTo(zos)
                            }
                            zos.closeEntry()
                        }
                    }
                }
            }
            ToastUtils.show(requireContext(), getString(R.string.export_success))
        } catch (e: Exception) {
            ToastUtils.show(requireContext(), getString(R.string.export_failed) + ": ${e.message}", isError = true)
        }
    }

    private fun exportFolderToTree(world: WorldInfo, treeUri: Uri) {
        try {
            val sourceDir = File(world.directoryPath)
            if (!sourceDir.exists() || !sourceDir.isDirectory) {
                ToastUtils.show(requireContext(), "源文件夹不存在", isError = true)
                return
            }

            val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            requireContext().contentResolver.takePersistableUriPermission(
                treeUri,
                takeFlags
            )

            val targetRoot = DocumentFile.fromTreeUri(requireContext(), treeUri)
                ?: run {
                    ToastUtils.show(requireContext(), "无法访问目标目录", isError = true)
                    return
                }

            val targetWorldDir = targetRoot.createDirectory(world.name)
                ?: run {
                    ToastUtils.show(requireContext(), "无法创建目标文件夹", isError = true)
                    return
                }

            copyDirectoryToDocumentFile(sourceDir, targetWorldDir)
            ToastUtils.show(requireContext(), getString(R.string.export_success))
        } catch (e: Exception) {
            ToastUtils.show(requireContext(), getString(R.string.export_failed) + ": ${e.message}", isError = true)
        }
    }

    private fun copyDirectoryToDocumentFile(sourceDir: File, targetDir: DocumentFile) {
        sourceDir.listFiles()?.forEach { sourceFile ->
            if (sourceFile.isDirectory) {
                val newTargetDir = targetDir.createDirectory(sourceFile.name)
                if (newTargetDir != null) {
                    copyDirectoryToDocumentFile(sourceFile, newTargetDir)
                }
            } else {
                val targetFile = targetDir.createFile("*/*", sourceFile.name)
                if (targetFile != null) {
                    requireContext().contentResolver.openOutputStream(targetFile.uri)?.use { outputStream ->
                        FileInputStream(sourceFile).use { inputStream ->
                            inputStream.copyTo(outputStream)
                        }
                    }
                }
            }
        }
    }

    private fun viewConversionLog(world: WorldInfo) {
        try {
            val logFile = File(world.directoryPath, "converse_log.txt")
            if (!logFile.exists() || !logFile.isFile) {
                ToastUtils.show(requireContext(), "转换日志不存在", isError = true)
                return
            }

            val uri = androidx.core.content.FileProvider.getUriForFile(
                requireContext(),
                requireContext().packageName + ".fileprovider",
                logFile
            )
            
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "text/plain")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            if (intent.resolveActivity(requireContext().packageManager) != null) {
                startActivity(intent)
            } else {
                ToastUtils.show(requireContext(), "未找到可打开文本文件的应用", isError = true)
            }
        } catch (e: Exception) {
            ToastUtils.show(requireContext(), "打开日志失败: ${e.message}", isError = true)
        }
    }

    private fun deleteWorld(world: WorldInfo) {
        try {
            val worldDir = File(world.directoryPath)
            if (worldDir.exists() && worldDir.isDirectory) {
                worldDir.deleteRecursively()
                worlds.remove(world)
                worldAdapter.notifyDataSetChanged()
                if (worlds.isEmpty()) {
                    showEmptyState()
                }
                ToastUtils.show(requireContext(), getString(R.string.delete_world) + ": " + world.name)
            }
        } catch (e: Exception) {
            ToastUtils.show(requireContext(), "Delete failed: ${e.message}", isError = true)
        }
    }

    private fun showGameLauncherMenu(anchor: View) {
       data class GameInfo(val iconRes: Int, val packageName: String, val displayName: String)
        
       val games = listOf(
            GameInfo(R.drawable.ne, "com.netease.x19", "基岩版：网易"),
            GameInfo(R.drawable.be, "com.mojang.minecraftpe", "基岩版：国际版"),
            GameInfo(R.drawable.pojav, "net.kdt.pojavlaunch", "Java版：Pojav"),
            GameInfo(R.drawable.fcl, "com.tungsten.fcl", "Java版：Fcl"),
            GameInfo(R.drawable.zl2, "com.movtery.zalithlauncher.v2", "Java版：ZL2")
       )

       val items = games.map { it.displayName }.toTypedArray()
       val icons = games.map { it.iconRes }.toIntArray()

       val builder = android.app.AlertDialog.Builder(requireContext(), R.style.DialogTheme)
      builder.setTitle("选择启动项")
        
     // 使用自定义适配器显示图标
      builder.setAdapter(object : android.widget.BaseAdapter() {
          override fun getCount(): Int = games.size

          override fun getItem(position: Int): Any = games[position]

          override fun getItemId(position: Int): Long = position.toLong()

          override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
              val view = convertView ?: LayoutInflater.from(requireContext())
                  .inflate(R.layout.launcher_menu_item, parent, false)

              val icon = view.findViewById<android.widget.ImageView>(R.id.iv_icon)
              val title = view.findViewById<android.widget.TextView>(R.id.tv_title)

              icon.setImageResource(games[position].iconRes)
              title.text = games[position].displayName

              return view
        }
       }) { _, which ->
           launchMinecraft(games[which].packageName)
       }

       val dialog = builder.create()
       dialog.window?.setBackgroundDrawableResource(R.drawable.dialog_rounded_background)
      dialog.show()
    }

    private fun launchMinecraft(packageName: String) {
        try {
            val intent = requireContext().packageManager.getLaunchIntentForPackage(packageName)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
            } else {
                ToastUtils.show(requireContext(), getString(R.string.app_not_installed), isError = true)
            }
        } catch (e: Exception) {
            ToastUtils.show(requireContext(), getString(R.string.launch_failed) + ": ${e.message}", isError = true)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
