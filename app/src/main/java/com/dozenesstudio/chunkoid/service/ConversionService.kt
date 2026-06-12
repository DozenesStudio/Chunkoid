package com.dozenesstudio.chunkoid.service

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.util.Log
import androidx.lifecycle.MutableLiveData
import com.dozenesstudio.chunkoid.termux.RootFSInstaller
import com.dozenesstudio.chunkoid.termux.TermuxExecutor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.*
import java.util.concurrent.Executors

class ConversionService : Service() {
    private val binder = LocalBinder()
    val progress = MutableLiveData<Int>(0)
    val status = MutableLiveData<String>("")
    val logMessage = MutableLiveData<com.dozenesstudio.chunkoid.model.LogEntry?>()
    val isRunning = MutableLiveData<Boolean>(false)

    private lateinit var termuxExecutor: TermuxExecutor
    private var conversionProcess: Process? = null

    inner class LocalBinder : Binder() {
        fun getService(): ConversionService = this@ConversionService
    }

    override fun onCreate() {
        super.onCreate()
        termuxExecutor = TermuxExecutor(this)
    }

    override fun onBind(intent: Intent): IBinder {
        return binder
    }

    fun stopConversion() {
        conversionProcess?.destroy()
        conversionProcess = null
        isRunning.value = false
        status.value = "转换已停止"
        progress.value = 0
        cleanInputFolder()
        cleanOutputFolder()
    }

    fun cleanInputFolder() {
        val inputDir = File(filesDir, "input")
        if (inputDir.exists()) {
            deleteDir(inputDir)
            Log.d(TAG, "Input folder cleaned successfully")
        }
    }

    fun cleanOutputFolder() {
        val outputDir = File(filesDir, "output")
        if (outputDir.exists()) {
            deleteDir(outputDir)
            Log.d(TAG, "Output folder cleaned successfully")
        }
    }

    fun getOutputDirectory(): File {
        return File(filesDir, "output")
    }

    fun startConversionFromUri(inputUri: android.net.Uri, settings: com.dozenesstudio.chunkoid.model.ConversionSettings) {
        isRunning.value = true
        progress.value = 0

        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Step 1: Initialize Termux environment
                status.postValue("正在初始化环境...")
                initializeEnvironment()

                // Step 1.5: Set memory limit from settings
                termuxExecutor.setMaxMemory(settings.maxMemory)

                // Step 2: Copy input world to internal storage
                status.postValue("准备输入文件...")
                progress.postValue(10)
                val inputDir = copyUriToInternal(inputUri, "input")

                // Step 3: Prepare output directory
                progress.postValue(15)
                val outputDir = File(filesDir, "output")
                if (outputDir.exists()) {
                    deleteDir(outputDir)
                }
                outputDir.mkdirs()

                // Step 4: Run Chunker conversion
                status.postValue("正在转换...")
                progress.postValue(20)
                runChunkerConversion(inputDir, outputDir, settings)

                // Step 5: Move result to documents directory
                status.postValue("正在整理输出文件...")
                progress.postValue(90)
                moveOutputToDocuments(outputDir, settings.inputWorldName)

                // Step 6: Clean up
                withContext(Dispatchers.Main) {
                    onConversionComplete()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Conversion failed", e)
                withContext(Dispatchers.Main) {
                    onConversionError(e)
                }
            }
        }
    }

    private fun initializeEnvironment() {
        termuxExecutor.initialize()
    }

    private suspend fun copyUriToInternal(uri: android.net.Uri, folder: String): File {
        return withContext(Dispatchers.IO) {
            val targetDir = File(filesDir, folder)

            // Check if the target directory already has valid world data
            // (extracted by MainActivity for archive files)
            val hasValidWorld = targetDir.exists() && (
                File(targetDir, "db").exists() || File(targetDir, "region").exists()
            )

            if (hasValidWorld) {
                Log.d(TAG, "Input directory already has valid world data, using existing: ${targetDir.absolutePath}")
                return@withContext targetDir
            }

            // Target directory doesn't have valid data, need to prepare it
            if (targetDir.exists()) {
                deleteDir(targetDir)
            }
            targetDir.mkdirs()

            try {
                // Try to open as directory first
                val documentFile = androidx.documentfile.provider.DocumentFile.fromTreeUri(this@ConversionService, uri)
                if (documentFile != null && documentFile.isDirectory) {
                    copyDirectory(documentFile, targetDir)
                    Log.d(TAG, "Copied directory to: ${targetDir.absolutePath}")
                } else {
                    // URI is not a tree URI or not a directory
                    // This shouldn't happen for valid inputs, but handle it gracefully
                    Log.e(TAG, "URI is not a valid directory: $uri")
                }
            } catch (e: IllegalArgumentException) {
                // URI is not a tree URI (e.g., archive file URI)
                // This is expected for archive files that were already extracted by MainActivity
                Log.d(TAG, "URI is not a tree URI: ${e.message}")
            }

            targetDir
        }
    }

    private fun copyDirectory(source: androidx.documentfile.provider.DocumentFile, target: File) {
        target.mkdirs()
        source.listFiles()?.forEach { file ->
            val destFile = File(target, file.name ?: "unknown")
            if (file.isDirectory) {
                copyDirectory(file, destFile)
            } else {
                try {
                    contentResolver.openInputStream(file.uri)?.use { input ->
                        FileOutputStream(destFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to copy file: ${file.name}", e)
                }
            }
        }
    }

    private suspend fun runChunkerConversion(inputDir: File, outputDir: File, settings: com.dozenesstudio.chunkoid.model.ConversionSettings) {
        withContext(Dispatchers.IO) {
            val jarPath = getChunkerJarPath()
            if (!File(jarPath).exists()) {
                throw FileNotFoundException("Chunker JAR not found at: $jarPath")
            }

            val args = mutableListOf(
                "-jar", jarPath,
                "-i", inputDir.absolutePath,
                "-o", outputDir.absolutePath,
                "-f", settings.targetFormat
            )

            if (settings.blockMappings != null) {
                args.add("-bm")
                args.add(settings.blockMappings)
            }

            if (settings.worldSettings != null) {
                args.add("-ws")
                args.add(settings.worldSettings)
            }

            if (settings.pruningSettings != null) {
                args.add("-p")
                args.add(settings.pruningSettings)
            }

            if (settings.converterSettings != null) {
                args.add("-c")
                args.add(settings.converterSettings)
            }

            if (settings.dimensionMappings != null) {
                args.add("-dm")
                args.add(settings.dimensionMappings)
            }

            if (settings.keepOriginalNbt) {
                args.add("-k")
            }

            status.postValue("正在执行转换...")
            progress.postValue(25)

            // Use TermuxExecutor to execute Java command
            val process = termuxExecutor.executeJava(*args.toTypedArray())
            conversionProcess = process

            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val errorReader = BufferedReader(InputStreamReader(process.errorStream))
            var line: String?
            var lastProgress = 25

            fun processLine(lineStr: String) {
                Log.d(TAG, "Chunker: $lineStr")

                // Parse simple percentage format like "3.21%" or "22.24%"
                val progressMatch = Regex("(\\d+\\.\\d+)%").find(lineStr)
                if (progressMatch != null) {
                    // Only update progress bar, don't show percentage lines in log
                    val chunkProgress = progressMatch.groupValues[1].toDouble()
                    val calculatedProgress = (25 + chunkProgress * 0.65).toInt()
                    if (calculatedProgress > lastProgress) {
                        progress.postValue(calculatedProgress)
                        lastProgress = calculatedProgress
                    }
                } else {
                    // Show all non-progress messages in log
                    logMessage.postValue(com.dozenesstudio.chunkoid.model.LogEntry(lineStr, com.dozenesstudio.chunkoid.model.LogEntry.Level.INFO))
                }
            }

            // Read both stdout and stderr
            var stdoutActive = true
            var stderrActive = true

            while (stdoutActive || stderrActive) {
                // Read stdout
                if (stdoutActive) {
                    line = reader.readLine()
                    if (line != null) {
                        processLine(line)
                    } else {
                        stdoutActive = false
                    }
                }

                // Read stderr
                if (stderrActive) {
                    line = errorReader.readLine()
                    if (line != null) {
                        processLine(line)
                    } else {
                        stderrActive = false
                    }
                }

                // If both streams are still active but process is dead, wait a bit for buffered data
                if (!process.isAlive && stdoutActive && stderrActive) {
                    Thread.sleep(100)
                    // After process dies, try to drain both streams
                    while (true) {
                        var drained = false
                        line = reader.readLine()
                        if (line != null) {
                            processLine(line)
                            drained = true
                        }
                        line = errorReader.readLine()
                        if (line != null) {
                            processLine(line)
                            drained = true
                        }
                        if (!drained) break
                    }
                    break
                }
            }

            val exitCode = process.waitFor()
            progress.postValue(85)

            if (exitCode != 0) {
                throw RuntimeException("Chunker exited with code: $exitCode")
            }
        }
    }

    private fun getChunkerJarPath(): String {
        return File(filesDir, "chunker-cli-1.18.1.jar").absolutePath
    }

    private fun moveOutputToDocuments(sourceDir: File, worldName: String) {
        val documentsDir = File("/storage/emulated/0/Documents")
        if (!documentsDir.exists()) {
            documentsDir.mkdirs()
        }

        val chunkoidOutputDir = File(documentsDir, "chunkoid output")
        if (!chunkoidOutputDir.exists()) {
            chunkoidOutputDir.mkdirs()
        }

        val safeWorldName = worldName.replace(Regex("[^\\p{L}\\p{N}_\\- ]"), "_")
        val worldOutputDir = File(chunkoidOutputDir, safeWorldName)

        if (worldOutputDir.exists()) {
            deleteDir(worldOutputDir)
        }

        if (!sourceDir.renameTo(worldOutputDir)) {
            copyDirectory(sourceDir, worldOutputDir)
            deleteDir(sourceDir)
        }

        Log.d(TAG, "Output moved to: ${worldOutputDir.absolutePath}")
    }

    private fun copyDirectory(sourceDir: File, targetDir: File) {
        targetDir.mkdirs()
        sourceDir.listFiles()?.forEach { file ->
            if (file.isDirectory) {
                copyDirectory(file, File(targetDir, file.name))
            } else {
                try {
                    file.inputStream().use { input ->
                        FileOutputStream(File(targetDir, file.name)).use { output ->
                            input.copyTo(output)
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to copy file: ${file.name}", e)
                }
            }
        }
    }

    private fun deleteDir(dir: File) {
        if (dir.isDirectory) {
            dir.listFiles()?.forEach { deleteDir(it) }
        }
        dir.delete()
    }

    private fun onConversionComplete() {
        isRunning.value = false
        status.value = "转换成功!"
        progress.value = 100
        cleanInputFolder()
    }

    private fun onConversionError(error: Exception) {
        isRunning.value = false
        status.value = "转换失败: ${error.message}"
        progress.value = 0
    }

    override fun onDestroy() {
        stopConversion()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "ConversionService"
    }
}
