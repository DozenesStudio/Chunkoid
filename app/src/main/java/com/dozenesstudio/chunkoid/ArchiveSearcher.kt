package com.dozenesstudio.chunkoid

import android.content.ContentResolver
import android.net.Uri
import java.io.File
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

class ArchiveSearcher(private val contentResolver: ContentResolver, private val filesDir: File) {

    interface SearchCallback {
        fun onProgress(progress: Int, message: String)
        fun onSuccess(worldDir: File)
        fun onError(errorMessage: String)
    }

    fun searchAndExtractArchive(uri: Uri, callback: SearchCallback) {
        Thread {
            try {
                val inputDir = File(filesDir, "input")
                if (inputDir.exists()) {
                    inputDir.deleteRecursively()
                }
                inputDir.mkdirs()

                callback.onProgress(0, "正在解压归档...")

                contentResolver.openInputStream(uri)?.use { inputStream ->
                    val zipInputStream = ZipInputStream(inputStream)
                    var entry = zipInputStream.nextEntry

                    var totalEntries = 0
                    while (entry != null) {
                        totalEntries++
                        entry = zipInputStream.nextEntry
                    }

                    contentResolver.openInputStream(uri)?.use { inputStream2 ->
                        val zipInputStream2 = ZipInputStream(inputStream2)
                        entry = zipInputStream2.nextEntry
                        var currentEntry = 0

                        while (entry != null) {
                            val entryName = entry.name
                            val entryFile = File(inputDir, entryName)

                            if (entry.isDirectory) {
                                entryFile.mkdirs()
                                entryFile.setReadable(true)
                                entryFile.setWritable(true)
                            } else {
                                entryFile.parentFile?.mkdirs()
                                entryFile.outputStream().use { outputStream ->
                                    zipInputStream2.copyTo(outputStream)
                                }
                                entryFile.setReadable(true)
                                entryFile.setWritable(true)
                            }

                            zipInputStream2.closeEntry()
                            entry = zipInputStream2.nextEntry

                            currentEntry++
                            val progress = (currentEntry * 50 / totalEntries).coerceAtMost(49)
                            callback.onProgress(progress, "正在解压归档...")
                        }
                        zipInputStream2.close()
                    }
                    zipInputStream.close()
                }

                callback.onProgress(50, "正在搜索世界存档...")

                val levelDatFile = findLevelDat(inputDir)
                if (levelDatFile == null) {
                    inputDir.deleteRecursively()
                    callback.onError("未找到有效的世界存档")
                    return@Thread
                }

                val actualWorldDir = levelDatFile.parentFile
                if (actualWorldDir == null || actualWorldDir.absolutePath == inputDir.absolutePath) {
                    callback.onProgress(100, "世界存档已就绪")
                    callback.onSuccess(inputDir)
                    return@Thread
                }

                callback.onProgress(75, "正在整理世界存档...")

                moveWorldToRoot(inputDir, actualWorldDir)

                callback.onProgress(100, "世界存档已就绪")
                callback.onSuccess(inputDir)

            } catch (e: Exception) {
                e.printStackTrace()
                val inputDir = File(filesDir, "input")
                if (inputDir.exists()) {
                    inputDir.deleteRecursively()
                }
                callback.onError("归档处理失败: ${e.message}")
            }
        }.start()
    }

    private fun findLevelDat(searchDir: File): File? {
        val files = searchDir.listFiles() ?: return null
        for (file in files) {
            if (file.isDirectory) {
                val found = findLevelDat(file)
                if (found != null) {
                    return found
                }
            } else if (file.name.equals("level.dat", ignoreCase = true)) {
                return file
            }
        }
        return null
    }

    private fun moveWorldToRoot(inputDir: File, worldDir: File) {
        val worldFiles = worldDir.listFiles() ?: return

        for (file in worldFiles) {
            val targetFile = File(inputDir, file.name)
            if (targetFile.exists()) {
                targetFile.deleteRecursively()
            }
            file.renameTo(targetFile)
        }

        val emptyDirs = mutableListOf<File>()
        collectEmptyDirs(inputDir, emptyDirs)
        emptyDirs.forEach { it.delete() }
    }

    private fun collectEmptyDirs(dir: File, emptyDirs: MutableList<File>) {
        val files = dir.listFiles() ?: return

        var isEmpty = true
        for (file in files) {
            if (file.isDirectory) {
                collectEmptyDirs(file, emptyDirs)
                if (!emptyDirs.contains(file)) {
                    isEmpty = false
                }
            } else {
                isEmpty = false
            }
        }

        if (isEmpty && dir.absolutePath != File(filesDir, "input").absolutePath) {
            emptyDirs.add(dir)
        }
    }

    fun isZipFile(uri: Uri): Boolean {
        val fileName = getFileName(uri)
        return fileName?.lowercase()?.endsWith(".zip") == true
    }

    fun isMcWorldFile(uri: Uri): Boolean {
        val fileName = getFileName(uri)
        return fileName?.lowercase()?.endsWith(".mcworld") == true
    }

    private fun getFileName(uri: Uri): String? {
        return try {
            val cursor = contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val nameIndex = it.getColumnIndex("_display_name")
                    if (nameIndex != -1) {
                        return it.getString(nameIndex)
                    }
                }
            }
            uri.path?.substringAfterLast('/')
        } catch (e: Exception) {
            uri.path?.substringAfterLast('/')
        }
    }

    fun extractArchiveToInput(uri: Uri, progressCallback: ((Int, String) -> Unit)? = null): Boolean {
        try {
            val inputDir = File(filesDir, "input")
            if (inputDir.exists()) {
                inputDir.deleteRecursively()
            }
            inputDir.mkdirs()

            contentResolver.openInputStream(uri)?.use { inputStream ->
                val zipInputStream = ZipInputStream(inputStream)
                var entry = zipInputStream.nextEntry

                var totalEntries = 0
                while (entry != null) {
                    totalEntries++
                    entry = zipInputStream.nextEntry
                }

                contentResolver.openInputStream(uri)?.use { inputStream2 ->
                    val zipInputStream2 = ZipInputStream(inputStream2)
                    entry = zipInputStream2.nextEntry
                    var currentEntry = 0

                    while (entry != null) {
                        val entryName = entry.name
                        val entryFile = File(inputDir, entryName)

                        if (entry.isDirectory) {
                            entryFile.mkdirs()
                            entryFile.setReadable(true)
                            entryFile.setWritable(true)
                        } else {
                            entryFile.parentFile?.mkdirs()
                            entryFile.outputStream().use { outputStream ->
                                zipInputStream2.copyTo(outputStream)
                            }
                            entryFile.setReadable(true)
                            entryFile.setWritable(true)
                        }

                        zipInputStream2.closeEntry()
                        entry = zipInputStream2.nextEntry

                        currentEntry++
                        val progress = (currentEntry * 100 / totalEntries).coerceAtMost(99)
                        progressCallback?.invoke(progress, "正在解压归档...")
                    }

                    zipInputStream2.close()
                }

                zipInputStream.close()
            }

            progressCallback?.invoke(100, "解压完成")
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }

    fun detectPlatformFromDirectory(): Platform {
        val inputDir = File(filesDir, "input")
        val regionFolder = File(inputDir, "region")
        val dimensionsFolder = File(inputDir, "dimensions")
        return if ((regionFolder.exists() && regionFolder.isDirectory) ||
                   (dimensionsFolder.exists() && dimensionsFolder.isDirectory)) {
            Platform.JAVA
        } else {
            Platform.BEDROCK
        }
    }

    fun detectPlatformFromArchive(uri: Uri): Platform {
        try {
            contentResolver.openInputStream(uri)?.use { inputStream ->
                val zipInputStream = ZipInputStream(inputStream)
                var entry = zipInputStream.nextEntry
                var hasRegionFolder = false
                var hasDimensionsFolder = false

                while (entry != null) {
                    val entryName = entry.name.lowercase()

                    if (entryName.startsWith("region/") || entryName == "region") {
                        hasRegionFolder = true
                    }
                    if (entryName.startsWith("dimensions/") || entryName == "dimensions") {
                        hasDimensionsFolder = true
                    }

                    entry = zipInputStream.nextEntry
                }

                zipInputStream.close()

                return if (hasRegionFolder || hasDimensionsFolder) Platform.JAVA else Platform.BEDROCK
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return Platform.BEDROCK
    }
}
