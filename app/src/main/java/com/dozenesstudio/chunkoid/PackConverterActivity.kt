package com.dozenesstudio.chunkoid

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.dozenesstudio.chunkoid.databinding.ActivityPackConverterBinding
import com.dozenesstudio.chunkoid.model.LogEntry
import com.dozenesstudio.chunkoid.packconverter.ConversionDirection
import com.dozenesstudio.chunkoid.packconverter.PackConverter
import com.dozenesstudio.chunkoid.ui.LogAdapter
import com.dozenesstudio.chunkoid.utils.ToastUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileInputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class PackConverterActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPackConverterBinding
    private lateinit var logAdapter: LogAdapter
    private var convertedPackPath: String? = null
    private var selectedPackDoc: DocumentFile? = null
    private var currentDirection: ConversionDirection? = null

    private val inputDir get() = File(filesDir, "input")
    private val outputDir get() = File(filesDir, "output")

    private val openDocumentTreeLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let { treeUri ->
            val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            contentResolver.takePersistableUriPermission(treeUri, takeFlags)
            val docFile = DocumentFile.fromTreeUri(this, treeUri)
            selectedPackDoc = docFile
            binding.tvSelectPack.text = docFile?.name ?: getString(R.string.select_input_pack_folder)
            binding.tvStatus.text = getString(R.string.ready_to_convert)
        }
    }

    private val createDocumentLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.CreateDocument("*/*")
    ) { uri ->
        uri?.let { destinationUri ->
            convertedPackPath?.let { convertPath ->
                exportPackAsCompressed(convertPath, destinationUri)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPackConverterBinding.inflate(layoutInflater)
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
            layoutManager = LinearLayoutManager(this@PackConverterActivity)
            adapter = logAdapter
        }
    }

    private fun setupClickListeners() {
        binding.tvSelectPack.setOnClickListener {
            openDocumentTreeLauncher.launch(null)
        }

        binding.btnJavaToBedrock.setOnClickListener {
            startConversion(ConversionDirection.JAVA_TO_BEDROCK)
        }

        binding.btnBedrockToJava.setOnClickListener {
            startConversion(ConversionDirection.BEDROCK_TO_JAVA)
        }

        binding.btnExportCompressed.setOnClickListener {
            val packName = convertedPackPath?.let { File(it).name } ?: "pack"
            val extension = if (currentDirection == ConversionDirection.JAVA_TO_BEDROCK) ".mcpack" else ".zip"
            createDocumentLauncher.launch(packName + extension)
        }
    }

    private fun startConversion(direction: ConversionDirection) {
        val packDoc = selectedPackDoc ?: run {
            ToastUtils.show(this, getString(R.string.error_no_input), isError = true)
            return
        }

        currentDirection = direction
        binding.tvStatus.text = getString(R.string.converting)
        binding.progressBar.isIndeterminate = false
        binding.progressBar.progress = 0
        binding.tvProgressText.text = "0%"
        binding.btnExportCompressed.visibility = android.view.View.GONE
        logAdapter.clear()

        lifecycleScope.launch {
            PackConverter(this@PackConverterActivity).convertPack(
                sourceDoc = packDoc,
                targetDir = outputDir,
                direction = direction,
                listener = object : PackConverter.ConvertListener {
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

                    override fun onSuccess(outputPath: String) {
                        lifecycleScope.launch(Dispatchers.Main) {
                            convertedPackPath = outputPath
                            binding.tvStatus.text = getString(R.string.convert_complete)
                            
                            binding.btnExportCompressed.visibility = android.view.View.VISIBLE
                            binding.btnExportCompressed.text = if (direction == ConversionDirection.JAVA_TO_BEDROCK) {
                                getString(R.string.export_as_mcpack)
                            } else {
                                getString(R.string.export_as_zip)
                            }
                            
                            addLog("转换完成，输出目录: $outputPath", LogEntry.Level.INFO)
                            ToastUtils.show(this@PackConverterActivity, getString(R.string.convert_complete))
                        }
                    }

                    override fun onError(error: String) {
                        lifecycleScope.launch(Dispatchers.Main) {
                            binding.tvStatus.text = getString(R.string.convert_failed)
                            addLog("转换失败: $error", LogEntry.Level.ERROR)
                            ToastUtils.show(this@PackConverterActivity, "${getString(R.string.convert_failed)}: $error", isError = true)
                            cleanTempFolders()
                        }
                    }
                }
            )
        }
    }

    private fun exportPackAsCompressed(sourcePath: String, destinationUri: Uri) {
        try {
            val sourceDir = File(sourcePath)
            if (!sourceDir.exists() || !sourceDir.isDirectory) {
                ToastUtils.show(this, "源文件夹不存在", isError = true)
                return
            }

            contentResolver.openOutputStream(destinationUri)?.use { outputStream ->
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
            ToastUtils.show(this, getString(R.string.export_success))
        } catch (e: Exception) {
            ToastUtils.show(this, "${getString(R.string.export_failed)}: ${e.message}", isError = true)
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