package com.dozenesstudio.chunkoid.decryptor

import android.content.Context
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class WorldDecryptor(private val context: Context) {

    suspend fun decryptWorld(
        sourceDoc: DocumentFile,
        targetDir: File,
        listener: DecryptListener
    ): String? = withContext(Dispatchers.IO) {
        try {
            listener.onLog("开始扫描存档目录结构...")

            var dbDoc: DocumentFile? = null
            sourceDoc.listFiles()?.forEach { child ->
                if (child.isDirectory && child.name == "db") {
                    dbDoc = child
                }
            }

            if (dbDoc == null) {
                listener.onError("未找到有效的 db 数据库文件夹，请确保选择正确的网易存档根目录")
                return@withContext null
            }

            val dbFiles = dbDoc!!.listFiles() ?: emptyArray()
            var currentDoc: DocumentFile? = null
            var manifestDoc: DocumentFile? = null

            for (file in dbFiles) {
                if (file.isFile) {
                    val name = file.name ?: continue
                    if (name == "CURRENT") currentDoc = file
                    if (name.startsWith("MANIFEST")) manifestDoc = file
                }
            }

            if (currentDoc == null || manifestDoc == null) {
                listener.onError("存档 db 目录下缺失 CURRENT 或 MANIFEST 核心数据库指针文件")
                return@withContext null
            }

            listener.onLog("开始读取 CURRENT 指针文件，计算解密密钥...")
            val decryptKey = try {
                context.contentResolver.openInputStream(currentDoc!!.uri)?.use { inputStream ->
                    NetEaseDecryptor.deriveKey(inputStream, manifestDoc!!.name!!)
                }
            } catch (e: Exception) {
                listener.onLog("密钥提取失败或未加密：${e.message}。如果未加密，将视为原样复制。")
                null
            }

            if (targetDir.exists()) {
                targetDir.deleteRecursively()
            }
            targetDir.mkdirs()

            val targetDbDir = File(targetDir, "db")
            if (!targetDbDir.exists()) {
                targetDbDir.mkdirs()
            }

            listener.onLog("正在复制外部基本元数据文件...")
            sourceDoc.listFiles()?.forEach { child ->
                if (child.isFile) {
                    val outFile = File(targetDir, child.name ?: "")
                    context.contentResolver.openInputStream(child.uri)?.use { input ->
                        FileOutputStream(outFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                }
            }

            listener.onLog("开始逐个解密并导出核心 LevelDB 数据库...")
            val totalFiles = dbFiles.size
            var processed = 0

            for (file in dbFiles) {
                if (file.isFile) {
                    val fileName = file.name ?: continue
                    val outFile = File(targetDbDir, fileName)

                    if (decryptKey != null) {
                        context.contentResolver.openInputStream(file.uri)?.use { input ->
                            FileOutputStream(outFile).use { output ->
                                NetEaseDecryptor.decryptFile(input, output, decryptKey)
                            }
                        }
                    } else {
                        context.contentResolver.openInputStream(file.uri)?.use { input ->
                            FileOutputStream(outFile).use { output ->
                                input.copyTo(output)
                            }
                        }
                    }
                }
                processed++
                listener.onProgress((processed * 100) / totalFiles)
            }

            listener.onSuccess(targetDir.absolutePath)
            targetDir.absolutePath

        } catch (e: Exception) {
            e.printStackTrace()
            listener.onError("解密发生未知异常: ${e.message}")
            null
        }
    }

    interface DecryptListener {
        fun onProgress(progress: Int)
        fun onLog(message: String)
        fun onSuccess(exportPath: String)
        fun onError(error: String)
    }
}
