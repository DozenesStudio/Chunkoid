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
import com.dozenesstudio.chunkoid.utils.ToastUtils
import java.io.File
import java.io.FileInputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class OutputManagerFragment : Fragment() {

    private var _binding: FragmentOutputManagerBinding? = null
    private val binding get() = _binding!!

    private lateinit var worldAdapter: WorldCardAdapter
    private val worlds = mutableListOf<WorldInfo>()

    private var pendingExportWorld: WorldInfo? = null
    private var pendingExportIsMcworld: Boolean = false

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
        
        val drawableRes = when (selectedTheme) {
            "bg1" -> R.drawable.bg1
            "bg2" -> R.drawable.bg2
            "bg3" -> R.drawable.bg3
            "bg4" -> R.drawable.bg4
            else -> R.drawable.bg
        }
        
        binding.ivBackground.setImageResource(drawableRes)
    }

    private fun setupRecyclerView() {
        worldAdapter = WorldCardAdapter(
            worlds = worlds,
            onExportClick = { world, action -> handleExportClick(world, action) },
            onOpenFolderClick = { world -> openWorldFolder(world) }
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
            WorldCardAdapter.ExportAction.OPEN_FOLDER -> openWorldFolder(world)
            WorldCardAdapter.ExportAction.EXPORT_MCWORLD -> startExport(world, isMcworld = true)
            WorldCardAdapter.ExportAction.EXPORT_ZIP -> startExport(world, isMcworld = false)
            WorldCardAdapter.ExportAction.DELETE -> deleteWorld(world)
        }
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

    private fun openWorldFolder(world: WorldInfo) {
        try {
            val worldDir = File(world.directoryPath)
            val uri = androidx.core.content.FileProvider.getUriForFile(
                requireContext(),
                "${requireContext().packageName}.fileprovider",
                worldDir
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "resource/folder")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, getString(R.string.open_folder)))
        } catch (e: Exception) {
            ToastUtils.show(requireContext(), "Error: ${e.message}", isError = true)
        }
    }

    private fun showGameLauncherMenu(anchor: View) {
        val popup = PopupMenu(requireContext(), anchor)
        popup.menuInflater.inflate(R.menu.menu_game_launcher, popup.menu)
        popup.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.action_bedrock_netease -> {
                    launchMinecraft("com.netease.x19")
                    true
                }
                R.id.action_bedrock_international -> {
                    launchMinecraft("com.mojang.minecraftpe")
                    true
                }
                R.id.action_java_pojav -> {
                    launchMinecraft("net.kdt.pojavlaunch")
                    true
                }
                R.id.action_java_fcl -> {
                    launchMinecraft("com.tungsten.fcl")
                    true
                }
                R.id.action_java_hmcl -> {
                    launchMinecraft("com.tungsten.fcl")
                    true
                }
                else -> false
            }
        }
        popup.show()
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
