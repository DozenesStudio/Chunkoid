package com.dozenesstudio.chunkoid

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.view.MenuItem
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.preference.PreferenceManager
import com.dozenesstudio.chunkoid.databinding.ActivityConversionBinding
import com.dozenesstudio.chunkoid.model.ConversionSettings
import com.dozenesstudio.chunkoid.model.LogEntry
import com.dozenesstudio.chunkoid.service.ConversionService
import com.dozenesstudio.chunkoid.ui.LogAdapter
import com.dozenesstudio.chunkoid.utils.BackgroundUtils
import com.dozenesstudio.chunkoid.utils.ToastUtils
import kotlinx.coroutines.launch
import java.io.File
import java.util.zip.ZipInputStream

class ConversionActivity : AppCompatActivity() {

    private lateinit var binding: ActivityConversionBinding
    private lateinit var settings: ConversionSettings
    private lateinit var logAdapter: LogAdapter
    private var isConversionRunning = false
    private var hasConversionStarted = false
    private var conversionService: ConversionService? = null
    private var isBound = false
    private var worldIconBitmap: android.graphics.Bitmap? = null
    private val logEntries = mutableListOf<LogEntry>()
    private var rawLogContent = "" // 原始日志内容

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityConversionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Make status bar transparent and allow content to draw under it
        window.setFlags(
            android.view.WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            android.view.WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        )
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        
        // Set status bar icons to light mode for visibility on dark backgrounds
        val decorView = window.decorView
        decorView.systemUiVisibility = decorView.systemUiVisibility or 
            android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR

        settings = intent.getParcelableExtra(EXTRA_SETTINGS)
            ?: throw IllegalArgumentException("Settings required")

        setupBackground()
        setupToolbar()
        setupRecyclerView()
        setupClickListeners()
        displayConversionDetails()
        bindToService()
        applyNoElevationToCards()
    }

    private fun setupBackground() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val selectedTheme = prefs.getString("selected_theme", "bg")
        
        if (selectedTheme == SettingsActivity.THEME_CUSTOM) {
            val customBgFile = File(filesDir, SettingsActivity.CUSTOM_BG_FILE_NAME)
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

    private fun applyNoElevationToCards() {
        val rootView = findViewById<ViewGroup>(android.R.id.content)
        removeCardElevation(rootView)
    }

    private fun removeCardElevation(viewGroup: ViewGroup) {
        for (i in 0 until viewGroup.childCount) {
            val child = viewGroup.getChildAt(i)
            if (child is com.google.android.material.card.MaterialCardView) {
                child.cardElevation = 0f
            } else if (child is ViewGroup) {
                removeCardElevation(child)
            }
        }
    }

    private fun bindToService() {
        val intent = Intent(this, ConversionService::class.java)
        bindService(intent, serviceConnection, BIND_AUTO_CREATE)
    }

    private val serviceConnection = object : android.content.ServiceConnection {
        override fun onServiceConnected(name: android.content.ComponentName?, service: IBinder?) {
            val binder = service as ConversionService.LocalBinder
            conversionService = binder.getService()
            isBound = true
            setupServiceObservers()
            startConversion()
        }

        override fun onServiceDisconnected(name: android.content.ComponentName?) {
            isBound = false
            conversionService = null
        }
    }

    private fun setupServiceObservers() {
        conversionService?.progress?.observe(this) { progress ->
            updateProgress(progress)
        }

        conversionService?.status?.observe(this) { status ->
            binding.tvStatus.text = status
            addLogEntry(LogEntry(status, LogEntry.Level.INFO))
        }

        conversionService?.logMessage?.observe(this) { logEntry ->
            logEntry?.let { addLogEntry(it) }
        }

        conversionService?.rawLogOutput?.observe(this) { rawLog ->
            if (!rawLog.isNullOrEmpty()) {
                rawLogContent = rawLog
            }
        }

        conversionService?.isRunning?.observe(this) { running ->
            isConversionRunning = running
            if (!running && hasConversionStarted) {
                onConversionComplete(true)
            }
        }
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(false)
        supportActionBar?.hide()
    }

    private fun setupRecyclerView() {
        logAdapter = LogAdapter()
        binding.recyclerLogs.apply {
            layoutManager = LinearLayoutManager(this@ConversionActivity)
            adapter = logAdapter
        }
    }

    private fun setupClickListeners() {
        binding.btnCancel.setOnClickListener {
            if (isConversionRunning) {
                cancelConversion()
            } else {
                navigateBackToInitialState()
            }
        }

        binding.btnOpenOutput.setOnClickListener {
            openOutputFolder()
        }
    }

    private fun displayConversionDetails() {
        binding.tvWorldName.text = settings.inputWorldName

        val sourcePlatform = if (settings.sourcePlatform == "JAVA") {
            getString(R.string.java_edition)
        } else {
            getString(R.string.bedrock_edition)
        }
        binding.tvPlatform.text = sourcePlatform

        val displayTargetFormat = formatTargetFormat(settings.targetFormat)
        val targetPlatform = if (settings.targetFormat.startsWith("JAVA")) getString(R.string.java_edition) else getString(R.string.bedrock_edition)
        binding.tvTargetVersion.text = getString(R.string.convert_to) + " " + targetPlatform + " " + displayTargetFormat

        loadWorldIcon()
    }
    
    private fun loadWorldIcon() {
        val uri = settings.inputUri
        val mimeType = contentResolver.getType(uri)
        
        if (mimeType == "application/zip" || uri.lastPathSegment?.endsWith(".zip") == true || 
            uri.lastPathSegment?.endsWith(".mcworld") == true) {
            loadWorldIconFromArchive(uri)
        } else {
            loadWorldIconFromDirectory(uri)
        }
    }
    
    private fun loadWorldIconFromDirectory(uri: Uri) {
        try {
            val documentFile = DocumentFile.fromTreeUri(this, uri)
            documentFile?.let { dir ->
                var iconFile = dir.findFile("icon.png")
                if (iconFile != null && iconFile.isFile) {
                    loadIconFromUri(iconFile.uri)
                    return
                }
                iconFile = dir.findFile("world_icon.jpeg")
                if (iconFile != null && iconFile.isFile) {
                    loadIconFromUri(iconFile.uri)
                    return
                }
                iconFile = dir.findFile("world_icon.jpg")
                if (iconFile != null && iconFile.isFile) {
                    loadIconFromUri(iconFile.uri)
                    return
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    private fun loadWorldIconFromArchive(uri: Uri) {
        try {
            contentResolver.openInputStream(uri)?.use { inputStream ->
                val zipInputStream = ZipInputStream(inputStream)
                var entry = zipInputStream.nextEntry
                
                while (entry != null) {
                    val entryName = entry.name.lowercase()
                    if (entryName.contains("icon.png") ||
                        entryName.contains("world_icon.jpeg") ||
                        entryName.contains("world_icon.jpg")) {
                        val bitmap = BitmapFactory.decodeStream(zipInputStream)
                        if (bitmap != null) {
                            worldIconBitmap = bitmap
                            binding.ivWorldIcon.setImageBitmap(bitmap)
                        }
                        zipInputStream.closeEntry()
                        zipInputStream.close()
                        return
                    }
                    entry = zipInputStream.nextEntry
                }
                zipInputStream.closeEntry()
                zipInputStream.close()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    private fun loadIconFromUri(uri: Uri) {
        try {
            contentResolver.openInputStream(uri)?.use { inputStream ->
                val bitmap = BitmapFactory.decodeStream(inputStream)
                if (bitmap != null) {
                    worldIconBitmap = bitmap
                    binding.ivWorldIcon.setImageBitmap(bitmap)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    private fun formatTargetFormat(format: String): String {
        val result = when (format) {
            // Java版
            "JAVA_1_8_8" -> getString(R.string.java_1_8_8)
            "JAVA_1_9" -> getString(R.string.java_1_9)
            "JAVA_1_9_1" -> getString(R.string.java_1_9_1)
            "JAVA_1_9_2" -> getString(R.string.java_1_9_2)
            "JAVA_1_9_3" -> getString(R.string.java_1_9_3)
            "JAVA_1_10" -> getString(R.string.java_1_10)
            "JAVA_1_10_1" -> getString(R.string.java_1_10_1)
            "JAVA_1_10_2" -> getString(R.string.java_1_10_2)
            "JAVA_1_11" -> getString(R.string.java_1_11)
            "JAVA_1_11_1" -> getString(R.string.java_1_11_1)
            "JAVA_1_11_2" -> getString(R.string.java_1_11_2)
            "JAVA_1_12" -> getString(R.string.java_1_12)
            "JAVA_1_12_1" -> getString(R.string.java_1_12_1)
            "JAVA_1_12_2" -> getString(R.string.java_1_12_2)
            "JAVA_1_13" -> getString(R.string.java_1_13)
            "JAVA_1_13_1" -> getString(R.string.java_1_13_1)
            "JAVA_1_13_2" -> getString(R.string.java_1_13_2)
            "JAVA_1_14" -> getString(R.string.java_1_14)
            "JAVA_1_14_1" -> getString(R.string.java_1_14_1)
            "JAVA_1_14_2" -> getString(R.string.java_1_14_2)
            "JAVA_1_14_3" -> getString(R.string.java_1_14_3)
            "JAVA_1_14_4" -> getString(R.string.java_1_14_4)
            "JAVA_1_15" -> getString(R.string.java_1_15)
            "JAVA_1_15_1" -> getString(R.string.java_1_15_1)
            "JAVA_1_15_2" -> getString(R.string.java_1_15_2)
            "JAVA_1_16" -> getString(R.string.java_1_16)
            "JAVA_1_16_1" -> getString(R.string.java_1_16_1)
            "JAVA_1_16_2" -> getString(R.string.java_1_16_2)
            "JAVA_1_16_3" -> getString(R.string.java_1_16_3)
            "JAVA_1_16_4" -> getString(R.string.java_1_16_4)
            "JAVA_1_16_5" -> getString(R.string.java_1_16_5)
            "JAVA_1_17" -> getString(R.string.java_1_17)
            "JAVA_1_17_1" -> getString(R.string.java_1_17_1)
            "JAVA_1_18" -> getString(R.string.java_1_18)
            "JAVA_1_18_1" -> getString(R.string.java_1_18_1)
            "JAVA_1_18_2" -> getString(R.string.java_1_18_2)
            "JAVA_1_19" -> getString(R.string.java_1_19)
            "JAVA_1_19_1" -> getString(R.string.java_1_19_1)
            "JAVA_1_19_2" -> getString(R.string.java_1_19_2)
            "JAVA_1_19_3" -> getString(R.string.java_1_19_3)
            "JAVA_1_19_4" -> getString(R.string.java_1_19_4)
            "JAVA_1_20" -> getString(R.string.java_1_20)
            "JAVA_1_20_1" -> getString(R.string.java_1_20_1)
            "JAVA_1_20_2" -> getString(R.string.java_1_20_2)
            "JAVA_1_20_3" -> getString(R.string.java_1_20_3)
            "JAVA_1_20_4" -> getString(R.string.java_1_20_4)
            "JAVA_1_20_5" -> getString(R.string.java_1_20_5)
            "JAVA_1_20_6" -> getString(R.string.java_1_20_6)
            "JAVA_1_21" -> getString(R.string.java_1_21)
            "JAVA_1_21_1" -> getString(R.string.java_1_21_1)
            "JAVA_1_21_2" -> getString(R.string.java_1_21_2)
            "JAVA_1_21_3" -> getString(R.string.java_1_21_3)
            "JAVA_1_21_4" -> getString(R.string.java_1_21_4)
            "JAVA_1_21_5" -> getString(R.string.java_1_21_5)
            "JAVA_1_21_6" -> getString(R.string.java_1_21_6)
            "JAVA_1_21_7" -> getString(R.string.java_1_21_7)
            "JAVA_1_21_8" -> getString(R.string.java_1_21_8)
            "JAVA_1_21_9" -> getString(R.string.java_1_21_9)
            "JAVA_1_21_10" -> getString(R.string.java_1_21_10)
            "JAVA_1_21_11" -> getString(R.string.java_1_21_11)
            "JAVA_26_1" -> getString(R.string.java_26_1)
            "JAVA_26_2" -> getString(R.string.java_26_2)
            // 基岩版
            "BEDROCK_R12" -> getString(R.string.bedrock_1_12)
            "BEDROCK_R13" -> getString(R.string.bedrock_1_13)
            "BEDROCK_R14" -> getString(R.string.bedrock_1_14)
            "BEDROCK_R14_1" -> getString(R.string.bedrock_1_14_1)
            "BEDROCK_R14_20" -> getString(R.string.bedrock_1_14_20)
            "BEDROCK_R14_30" -> getString(R.string.bedrock_1_14_30)
            "BEDROCK_R14_60" -> getString(R.string.bedrock_1_14_60)
            "BEDROCK_R16" -> getString(R.string.bedrock_1_16)
            "BEDROCK_R16_20" -> getString(R.string.bedrock_1_16_20)
            "BEDROCK_R16_100" -> getString(R.string.bedrock_1_16_100)
            "BEDROCK_R16_200" -> getString(R.string.bedrock_1_16_200)
            "BEDROCK_R16_210" -> getString(R.string.bedrock_1_16_210)
            "BEDROCK_R16_220" -> getString(R.string.bedrock_1_16_220)
            "BEDROCK_R17" -> getString(R.string.bedrock_1_17)
            "BEDROCK_R17_10" -> getString(R.string.bedrock_1_17_10)
            "BEDROCK_R17_20" -> getString(R.string.bedrock_1_17_20)
            "BEDROCK_R17_30" -> getString(R.string.bedrock_1_17_30)
            "BEDROCK_R17_40" -> getString(R.string.bedrock_1_17_40)
            "BEDROCK_R18" -> getString(R.string.bedrock_1_18)
            "BEDROCK_R18_10" -> getString(R.string.bedrock_1_18_10)
            "BEDROCK_R18_20" -> getString(R.string.bedrock_1_18_20)
            "BEDROCK_R18_30" -> getString(R.string.bedrock_1_18_30)
            "BEDROCK_R19" -> getString(R.string.bedrock_1_19)
            "BEDROCK_R19_10" -> getString(R.string.bedrock_1_19_10)
            "BEDROCK_R19_20" -> getString(R.string.bedrock_1_19_20)
            "BEDROCK_R19_30" -> getString(R.string.bedrock_1_19_30)
            "BEDROCK_R19_40" -> getString(R.string.bedrock_1_19_40)
            "BEDROCK_R19_50" -> getString(R.string.bedrock_1_19_50)
            "BEDROCK_R19_60" -> getString(R.string.bedrock_1_19_60)
            "BEDROCK_R19_70" -> getString(R.string.bedrock_1_19_70)
            "BEDROCK_R19_80" -> getString(R.string.bedrock_1_19_80)
            "BEDROCK_R20" -> getString(R.string.bedrock_1_20)
            "BEDROCK_R20_10" -> getString(R.string.bedrock_1_20_10)
            "BEDROCK_R20_20" -> getString(R.string.bedrock_1_20_20)
            "BEDROCK_R20_30" -> getString(R.string.bedrock_1_20_30)
            "BEDROCK_R20_40" -> getString(R.string.bedrock_1_20_40)
            "BEDROCK_R20_50" -> getString(R.string.bedrock_1_20_50)
            "BEDROCK_R20_60" -> getString(R.string.bedrock_1_20_60)
            "BEDROCK_R20_70" -> getString(R.string.bedrock_1_20_70)
            "BEDROCK_R20_80" -> getString(R.string.bedrock_1_20_80)
            "BEDROCK_R21" -> getString(R.string.bedrock_1_21)
            "BEDROCK_R21_20" -> getString(R.string.bedrock_1_21_20)
            "BEDROCK_R21_30" -> getString(R.string.bedrock_1_21_30)
            "BEDROCK_R21_40" -> getString(R.string.bedrock_1_21_40)
            "BEDROCK_R21_50" -> getString(R.string.bedrock_1_21_50)
            "BEDROCK_R21_60" -> getString(R.string.bedrock_1_21_60)
            "BEDROCK_R21_70" -> getString(R.string.bedrock_1_21_70)
            "BEDROCK_R21_80" -> getString(R.string.bedrock_1_21_80)
            "BEDROCK_R21_90" -> getString(R.string.bedrock_1_21_90)
            "BEDROCK_R21_100" -> getString(R.string.bedrock_1_21_100)
            "BEDROCK_R21_110" -> getString(R.string.bedrock_1_21_110)
            "BEDROCK_R21_120" -> getString(R.string.bedrock_1_21_120)
            "BEDROCK_R21_130" -> getString(R.string.bedrock_1_21_130)
            "BEDROCK_R26" -> getString(R.string.bedrock_1_26)
            "BEDROCK_R26_10" -> getString(R.string.bedrock_1_26_10)
            "BEDROCK_R26_20" -> getString(R.string.bedrock_1_26_20)
            "BEDROCK_R26_30" -> getString(R.string.bedrock_1_26_30)
            else -> {
                if (format.startsWith("JAVA_")) {
                    getString(R.string.java_edition) + " " + format.removePrefix("JAVA_").replace("_", ".")
                } else if (format.startsWith("BEDROCK_R")) {
                    val rVersion = format.removePrefix("BEDROCK_R")
                    val versionStr = rVersion.replace("_", ".")
                    getString(R.string.bedrock_edition) + " 1." + versionStr
                } else if (format.startsWith("BEDROCK_")) {
                    getString(R.string.bedrock_edition) + " " + format.removePrefix("BEDROCK_").replace("_", ".")
                } else {
                    format
                }
            }
        }
        Log.d("ConversionActivity", "formatTargetFormat: $format -> $result")
        return result
    }

    private fun startConversion() {
        if (!isBound || conversionService == null) {
            addLogEntry(LogEntry(getString(R.string.service_not_bound), LogEntry.Level.ERROR))
            return
        }

        hasConversionStarted = true
        isConversionRunning = true
        updateUIForConversionStart()

        try {
            conversionService?.startConversionFromUri(settings.inputUri, settings)
        } catch (e: Exception) {
            onConversionError(e)
        }
    }

    private fun openOutputFolder() {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(MainActivity.EXTRA_SHOW_OUTPUT_MANAGER, true)
            putExtra(MainActivity.EXTRA_RESET_TO_INITIAL_STATE, true)
        }
        startActivity(intent)
        finish()
    }

    private fun cancelConversion() {
        conversionService?.stopConversion()
        isConversionRunning = false
        updateUIForConversionComplete(false)
        addLogEntry(LogEntry(getString(R.string.conversion_cancelled), LogEntry.Level.WARNING))
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isBound) {
            unbindService(serviceConnection)
            isBound = false
        }
    }

    private fun updateUIForConversionStart() {
        binding.tvStatus.text = getString(R.string.converting)
        binding.progressBar.isIndeterminate = true
        binding.btnCancel.text = getString(R.string.cancel_conversion)
        binding.btnOpenOutput.visibility = android.view.View.GONE
    }

    private fun updateProgress(progress: Int) {
        binding.progressBar.isIndeterminate = false
        binding.progressBar.progress = progress
        binding.tvProgressText.text = "$progress%"
    }

    private fun addLogEntry(entry: LogEntry) {
        logEntries.add(entry)
        logAdapter.addLog(entry)
        binding.recyclerLogs.scrollToPosition(logAdapter.itemCount - 1)
    }

    private fun onConversionComplete(success: Boolean) {
        isConversionRunning = false
        updateUIForConversionComplete(success)

        // 直接从 Service 获取原始日志
        rawLogContent = conversionService?.getRawLog() ?: ""

        if (success) {
            binding.tvStatus.text = getString(R.string.conversion_complete)
            binding.tvStatus.setTextColor(getColor(R.color.minecraft_green))
            ToastUtils.show(this, getString(R.string.conversion_complete))
            saveConversionInfo()
        } else {
            binding.tvStatus.text = getString(R.string.conversion_failed)
            binding.tvStatus.setTextColor(getColor(R.color.minecraft_red))
            ToastUtils.show(this, getString(R.string.conversion_failed), isError = true)
        }
    }

    private fun saveConversionInfo() {
        try {
            val documentsDir = File("/storage/emulated/0/Documents")
            val chunkoidOutputDir = File(documentsDir, "chunkoid output")
            if (!chunkoidOutputDir.exists()) {
                chunkoidOutputDir.mkdirs()
                Log.d("ConversionActivity", "Created output directory: ${chunkoidOutputDir.absolutePath}")
            }
            
            val safeWorldName = settings.inputWorldName.replace(Regex("[^\\p{L}\\p{N}_\\- ]"), "_")
            val worldOutputDir = File(chunkoidOutputDir, safeWorldName)
            
            if (!worldOutputDir.exists()) {
                worldOutputDir.mkdirs()
                Log.d("ConversionActivity", "Created world directory: ${worldOutputDir.absolutePath}")
            }

            val isBedrock = settings.targetFormat.startsWith("BEDROCK")
            val platform = if (isBedrock) "bedrock" else "java"
            val version = formatTargetFormat(settings.targetFormat)

            val convertInfoContent = buildString {
                appendLine("platform $platform")
                appendLine("version $version")
                appendLine("levelname ${settings.inputWorldName}")
            }

            val convertInfoFile = File(worldOutputDir, "Convertinfo.txt")
            convertInfoFile.writeText(convertInfoContent)

            worldIconBitmap?.let { bitmap ->
                if (isBedrock) {
                    val iconFile = File(worldOutputDir, "world_icon.jpeg")
                    saveBitmapAsJpeg(bitmap, iconFile)
                    Log.d("ConversionActivity", "Saved world_icon.jpeg to: ${iconFile.absolutePath}")
                } else {
                    val iconFile = File(worldOutputDir, "icon.png")
                    saveBitmapAsPng(bitmap, iconFile)
                    Log.d("ConversionActivity", "Saved icon.png to: ${iconFile.absolutePath}")
                }
            } ?: Log.d("ConversionActivity", "No world icon to save")

            if (isBedrock) {
                val levelNameFile = File(worldOutputDir, "levelname.txt")
                levelNameFile.writeText(settings.inputWorldName)
                Log.d("ConversionActivity", "Saved levelname.txt: ${levelNameFile.absolutePath}")
            }

            Log.d("ConversionActivity", "Conversion info saved to: ${convertInfoFile.absolutePath}")

            saveConversionLog(worldOutputDir)
        } catch (e: Exception) {
            Log.e("ConversionActivity", "Failed to save conversion info", e)
        }
    }

    private fun saveConversionLog(outputDir: File) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val enableLogOutput = prefs.getBoolean("enable_conversion_log", false)
        
        if (!enableLogOutput || rawLogContent.isEmpty()) {
            return
        }

        try {
            val logFile = File(outputDir, "converse_log.txt")
            val logContent = buildString {
                appendLine("世界名称: ${settings.inputWorldName}")
                appendLine("时间戳: ${java.time.LocalDateTime.now()}")
                appendLine("=== 以下为Chunker-Cli的输出记录 ===")
                append(rawLogContent)
            }
            logFile.writeText(logContent)
            Log.d("ConversionActivity", "Conversion log saved to: ${logFile.absolutePath}")
        } catch (e: Exception) {
            Log.e("ConversionActivity", "Failed to save conversion log", e)
        }
    }

    private fun saveBitmapAsPng(bitmap: android.graphics.Bitmap, file: File) {
        try {
            file.outputStream().use { out ->
                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
            }
        } catch (e: Exception) {
            Log.e("ConversionActivity", "Failed to save PNG: ${file.absolutePath}", e)
        }
    }

    private fun saveBitmapAsJpeg(bitmap: android.graphics.Bitmap, file: File) {
        try {
            file.outputStream().use { out ->
                bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, out)
            }
        } catch (e: Exception) {
            Log.e("ConversionActivity", "Failed to save JPEG: ${file.absolutePath}", e)
        }
    }

    private fun onConversionError(error: Exception) {
        isConversionRunning = false
        updateUIForConversionComplete(false)
        binding.tvStatus.text = getString(R.string.conversion_failed)
        binding.tvStatus.setTextColor(getColor(R.color.minecraft_red))
        addLogEntry(LogEntry(getString(R.string.error_log, error.message), LogEntry.Level.ERROR))
        ToastUtils.show(this, getString(R.string.error_log, error.message), isError = true)
    }

    private fun updateUIForConversionComplete(success: Boolean) {
        binding.progressBar.isIndeterminate = false
        binding.btnCancel.text = getString(android.R.string.ok)
        if (success) {
            binding.btnOpenOutput.visibility = android.view.View.VISIBLE
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return super.onOptionsItemSelected(item)
    }

    private fun navigateBackToInitialState() {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(MainActivity.EXTRA_RESET_TO_INITIAL_STATE, true)
        }
        startActivity(intent)
        finish()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
    }

    companion object {
        const val EXTRA_SETTINGS = "extra_settings"
    }
}
