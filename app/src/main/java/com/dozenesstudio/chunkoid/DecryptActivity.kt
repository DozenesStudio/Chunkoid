package com.dozenesstudio.chunkoid

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.dozenesstudio.chunkoid.databinding.ActivityDecrypterBinding
import com.dozenesstudio.chunkoid.decryptor.WorldDecryptor
import com.dozenesstudio.chunkoid.model.LogEntry
import com.dozenesstudio.chunkoid.ui.LogAdapter
import com.dozenesstudio.chunkoid.utils.ToastUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

class DecryptActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDecrypterBinding
    private lateinit var logAdapter: LogAdapter
    private var decryptedWorldPath: String? = null
    private var selectedWorldDoc: DocumentFile? = null

    private val inputDir get() = File(filesDir, "input")
    private val outputDir get() = File(filesDir, "output")

    private val openDocumentTreeLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let { treeUri ->
            val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            contentResolver.takePersistableUriPermission(treeUri, takeFlags)
            val docFile = DocumentFile.fromTreeUri(this, treeUri)
            selectedWorldDoc = docFile
            
            val displayName = getDisplayNameFromDocFile(docFile)
            binding.tvSelectWorld.text = displayName ?: getString(R.string.select_decrypt_world)
            
            startDecryption()
        }
    }

    private val exportDocumentTreeLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let { treeUri ->
            val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            contentResolver.takePersistableUriPermission(treeUri, takeFlags)
            decryptedWorldPath?.let { decryptPath ->
                exportWorldToTree(decryptPath, treeUri)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDecrypterBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupRecyclerView()
        setupClickListeners()
        applyNoElevationToCards()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    private fun setupRecyclerView() {
        logAdapter = LogAdapter()
        binding.recyclerLogs.apply {
            layoutManager = LinearLayoutManager(this@DecryptActivity)
            adapter = logAdapter
        }
    }

    private fun setupClickListeners() {
        binding.tvSelectWorld.setOnClickListener {
            openDocumentTreeLauncher.launch(null)
        }

        binding.btnExport.setOnClickListener {
            exportDocumentTreeLauncher.launch(null)
        }
    }

    private fun startDecryption() {
        val worldDoc = selectedWorldDoc ?: return

        binding.tvStatus.text = getString(R.string.decrypting)
        binding.progressBar.isIndeterminate = false
        binding.progressBar.progress = 0
        binding.tvProgressText.text = "0%"

        lifecycleScope.launch {
            WorldDecryptor(this@DecryptActivity).decryptWorld(
                sourceDoc = worldDoc,
                targetDir = inputDir,
                listener = object : WorldDecryptor.DecryptListener {
                    override fun onProgress(progress: Int) {
                        lifecycleScope.launch(Dispatchers.Main) {
                            binding.progressBar.progress = progress
                            binding.tvProgressText.text = "$progress%"
                        }
                    }

                    override fun onLog(message: String) {
                        lifecycleScope.launch(Dispatchers.Main) {
                            addLog(message, LogEntry.Level.INFO)
                        }
                    }

                    override fun onSuccess(exportPath: String) {
                        lifecycleScope.launch(Dispatchers.Main) {
                            decryptedWorldPath = exportPath
                            binding.tvStatus.text = getString(R.string.decrypt_complete)
                            binding.btnExport.visibility = android.view.View.VISIBLE
                            addLog("解密完成，输出目录: $exportPath", LogEntry.Level.INFO)
                            ToastUtils.show(this@DecryptActivity, getString(R.string.decrypt_complete))
                        }
                    }

                    override fun onError(error: String) {
                        lifecycleScope.launch(Dispatchers.Main) {
                            binding.tvStatus.text = getString(R.string.decrypt_failed)
                            addLog("解密失败: $error", LogEntry.Level.ERROR)
                            ToastUtils.show(this@DecryptActivity, "${getString(R.string.decrypt_failed)}: $error", isError = true)
                            cleanTempFolders()
                        }
                    }
                }
            )
        }
    }

    private fun exportWorldToTree(sourcePath: String, treeUri: Uri) {
        try {
            val sourceDir = File(sourcePath)
            if (!sourceDir.exists() || !sourceDir.isDirectory) {
                ToastUtils.show(this, "源文件夹不存在", isError = true)
                return
            }

            val targetRoot = DocumentFile.fromTreeUri(this, treeUri)
                ?: run {
                    ToastUtils.show(this, "无法访问目标目录", isError = true)
                    return
                }

            val worldName = getWorldName(sourceDir)
            val targetWorldDir = targetRoot.createDirectory(worldName)
                ?: run {
                    ToastUtils.show(this, "无法创建目标文件夹", isError = true)
                    return
                }

            copyDirectoryToDocumentFile(sourceDir, targetWorldDir)
            ToastUtils.show(this, getString(R.string.export_success))
            cleanTempFolders()
        } catch (e: Exception) {
            ToastUtils.show(this, "${getString(R.string.export_failed)}: ${e.message}", isError = true)
        }
    }

    private fun getDisplayNameFromDocFile(docFile: DocumentFile?): String? {
        docFile ?: return null
        
        val levelNameFile = docFile.findFile("levelname.txt")
        if (levelNameFile != null && levelNameFile.isFile) {
            return try {
                contentResolver.openInputStream(levelNameFile.uri)?.use { inputStream ->
                    inputStream.bufferedReader().readText().trim().takeIf { it.isNotEmpty() }
                } ?: docFile.name
            } catch (e: Exception) {
                docFile.name
            }
        }
        return docFile.name
    }

    private fun getWorldName(sourceDir: File): String {
        val levelNameFile = File(sourceDir, "levelname.txt")
        if (levelNameFile.exists() && levelNameFile.isFile) {
            return try {
                levelNameFile.readText().trim().takeIf { it.isNotEmpty() } ?: sourceDir.name
            } catch (e: Exception) {
                sourceDir.name
            }
        }
        return sourceDir.name
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
                    contentResolver.openOutputStream(targetFile.uri)?.use { outputStream ->
                        java.io.FileInputStream(sourceFile).use { inputStream ->
                            inputStream.copyTo(outputStream)
                        }
                    }
                }
            }
        }
    }

    private fun addLog(message: String, level: LogEntry.Level = LogEntry.Level.INFO) {
        logAdapter.addLog(LogEntry(message, level))
        binding.recyclerLogs.scrollToPosition(logAdapter.itemCount - 1)
    }

    private fun cleanTempFolders() {
        if (inputDir.exists()) {
            inputDir.deleteRecursively()
        }
        if (outputDir.exists()) {
            outputDir.deleteRecursively()
        }
    }

    private fun applyNoElevationToCards() {
        val rootView = binding.root
        val cards = mutableListOf<com.google.android.material.card.MaterialCardView>()
        rootView.post {
            findCards(rootView, cards)
            cards.forEach { card ->
                card.cardElevation = 0f
            }
        }
    }

    private fun findCards(view: android.view.View, cards: MutableList<com.google.android.material.card.MaterialCardView>) {
        if (view is com.google.android.material.card.MaterialCardView) {
            cards.add(view)
        }
        if (view is android.view.ViewGroup) {
            for (i in 0 until view.childCount) {
                findCards(view.getChildAt(i), cards)
            }
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onDestroy() {
        super.onDestroy()
        cleanTempFolders()
    }
}
