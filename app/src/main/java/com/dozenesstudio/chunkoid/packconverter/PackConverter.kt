package com.dozenesstudio.chunkoid.packconverter

import android.content.Context
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

class PackConverter(private val context: Context) {

    interface ConvertListener {
        fun onProgress(progress: Int)
        fun onLog(message: String)
        fun onSuccess(outputPath: String)
        fun onError(error: String)
    }

    suspend fun convertPack(
        sourceDoc: DocumentFile,
        targetDir: File,
        direction: ConversionDirection,
        listener: ConvertListener
    ): String? = withContext(Dispatchers.IO) {
        try {
            listener.onLog("开始扫描材质包目录结构...")

            if (targetDir.exists()) {
                targetDir.deleteRecursively()
            }
            targetDir.mkdirs()

            val allFiles = mutableListOf<Pair<String, DocumentFile>>()
            collectFiles(sourceDoc, "", allFiles)

            listener.onLog("发现 ${allFiles.size} 个文件")

            if (allFiles.isEmpty()) {
                listener.onError("未找到任何文件，请确保选择正确的材质包文件夹")
                return@withContext null
            }

            val processedBlockTextures = mutableSetOf<String>()
            val processedItemTextures = mutableSetOf<String>()
            val allTargetTextures = mutableSetOf<String>()

            var packName = "Converted Pack"
            var packDescription = "Converted by Chunkoid"
            var headerUuid = UUID.randomUUID().toString()
            var moduleUuid = UUID.randomUUID().toString()

            for ((relativePath, file) in allFiles) {
                if (file.isDirectory) continue

                val targetPath = PackPathRules.getTargetPath(relativePath, direction)

                if (targetPath != null) {
                    val targetFile = File(targetDir, targetPath)
                    targetFile.parentFile?.mkdirs()

                    if (direction == ConversionDirection.JAVA_TO_BEDROCK) {
                        when {
                            relativePath.endsWith(".json") && targetPath.startsWith("texts/") -> {
                                convertJavaLangToBedrock(file, targetFile, listener)
                            }
                            relativePath.endsWith("sounds.json") -> {
                                convertJavaSoundsToBedrock(file, targetFile, listener)
                            }
                            relativePath == "assets/minecraft/texts/splashes.txt" -> {
                                convertSplashesTxtToJson(file, targetFile, listener)
                            }
                            relativePath.endsWith(".png.mcmeta") -> {
                                listener.onLog("跳过动画元数据: $relativePath")
                                continue
                            }
                            relativePath.startsWith("assets/minecraft/textures/block/") -> {
                                copyFile(file, targetFile)
                                processedBlockTextures.add(targetPath)
                            }
                            relativePath.startsWith("assets/minecraft/textures/item/") -> {
                                copyFile(file, targetFile)
                                processedItemTextures.add(targetPath)
                            }
                            else -> {
                                copyFile(file, targetFile)
                            }
                        }
                    } else {
                        when {
                            relativePath.endsWith(".lang") && targetPath.endsWith(".json") -> {
                                convertBedrockLangToJava(file, targetFile, listener)
                            }
                            relativePath == "sounds/sound_definitions.json" -> {
                                convertBedrockSoundsToJava(file, targetFile, listener)
                            }
                            else -> {
                                copyFile(file, targetFile)
                            }
                        }
                    }

                    if (targetPath.endsWith(".png") || targetPath.endsWith(".tga")) {
                        allTargetTextures.add(targetPath)
                    }
                } else {
                    if (direction == ConversionDirection.JAVA_TO_BEDROCK) {
                        when {
                            relativePath == "pack.mcmeta" -> {
                                val mcmetaContent = readFileContent(file)
                                try {
                                    val json = JSONObject(mcmetaContent)
                                    if (json.has("pack")) {
                                        val packObj = json.getJSONObject("pack")
                                        if (packObj.has("description")) {
                                            packDescription = packObj.getString("description")
                                        }
                                    }
                                } catch (e: Exception) {
                                    listener.onLog("解析 pack.mcmeta 失败: ${e.message}")
                                }
                                generateBedrockManifest(targetDir, packName, packDescription, headerUuid, moduleUuid, listener)
                            }
                            relativePath == "pack.png" -> {
                                val targetFile = File(targetDir, "pack_icon.png")
                                copyFile(file, targetFile)
                            }
                            else -> {
                                listener.onLog("跳过未映射文件: $relativePath")
                            }
                        }
                    } else {
                        when {
                            relativePath == "manifest.json" -> {
                                val manifestContent = readFileContent(file)
                                try {
                                    val json = JSONObject(manifestContent)
                                    if (json.has("header")) {
                                        val header = json.getJSONObject("header")
                                        if (header.has("name")) packName = header.getString("name")
                                        if (header.has("description")) packDescription = header.getString("description")
                                        if (header.has("uuid")) headerUuid = header.getString("uuid")
                                        if (json.has("modules")) {
                                            val modules = json.getJSONArray("modules")
                                            if (modules.length() > 0) {
                                                val module = modules.getJSONObject(0)
                                                if (module.has("uuid")) moduleUuid = module.getString("uuid")
                                            }
                                        }
                                    }
                                } catch (e: Exception) {
                                    listener.onLog("解析 manifest.json 失败: ${e.message}")
                                }
                                generateJavaMcmeta(targetDir, packDescription, listener)
                            }
                            relativePath == "pack_icon.png" -> {
                                val targetFile = File(targetDir, "pack.png")
                                copyFile(file, targetFile)
                            }
                            else -> {
                                listener.onLog("跳过未映射文件: $relativePath")
                            }
                        }
                    }
                }

                val progress = ((allFiles.indexOfFirst { it.first == relativePath } + 1) * 100) / allFiles.size
                listener.onProgress(progress)
            }

            if (direction == ConversionDirection.JAVA_TO_BEDROCK) {
                if (processedBlockTextures.isNotEmpty()) {
                    generateTerrainTexture(targetDir, processedBlockTextures, listener)
                    generateBlocksJson(targetDir, processedBlockTextures, listener)
                }
                if (processedItemTextures.isNotEmpty()) {
                    generateItemTexture(targetDir, processedItemTextures, listener)
                }
                if (allTargetTextures.isNotEmpty()) {
                    generateTexturesList(targetDir, allTargetTextures, listener)
                }
            }

            listener.onSuccess(targetDir.absolutePath)
            targetDir.absolutePath

        } catch (e: Exception) {
            e.printStackTrace()
            listener.onError("转换发生未知异常: ${e.message}")
            null
        }
    }

    private fun collectFiles(doc: DocumentFile, prefix: String, result: MutableList<Pair<String, DocumentFile>>) {
        doc.listFiles()?.forEach { child ->
            val childPath = if (prefix.isEmpty()) child.name ?: "" else "$prefix/${child.name}"
            if (child.isDirectory) {
                collectFiles(child, childPath, result)
            } else {
                result.add(childPath to child)
            }
        }
    }

    private fun copyFile(source: DocumentFile, target: File) {
        context.contentResolver.openInputStream(source.uri)?.use { input ->
            FileOutputStream(target).use { output ->
                input.copyTo(output)
            }
        }
    }

    private fun readFileContent(file: DocumentFile): String {
        return context.contentResolver.openInputStream(file.uri)?.use { input ->
            input.bufferedReader().readText()
        } ?: ""
    }

    private fun convertJavaLangToBedrock(source: DocumentFile, target: File, listener: ConvertListener) {
        try {
            val content = readFileContent(source)
            val json = JSONObject(content)
            val langBuilder = StringBuilder()
            json.keys().forEach { key ->
                val value = json.getString(key).replace("\\n", "\n")
                langBuilder.append("$key=$value\n")
            }
            target.writeText(langBuilder.toString())
            listener.onLog("转换语言文件: ${source.name} -> ${target.name}")
        } catch (e: Exception) {
            listener.onLog("转换语言文件失败: ${source.name}, ${e.message}")
            copyFile(source, target)
        }
    }

    private fun convertBedrockLangToJava(source: DocumentFile, target: File, listener: ConvertListener) {
        try {
            val content = readFileContent(source)
            val json = JSONObject()
            content.lines().forEach { line ->
                if (line.isNotEmpty() && line.contains("=")) {
                    val parts = line.split("=", limit = 2)
                    if (parts.size == 2) {
                        json.put(parts[0], parts[1].replace("\n", "\\n"))
                    }
                }
            }
            target.writeText(json.toString(2))
            listener.onLog("转换语言文件: ${source.name} -> ${target.name}")
        } catch (e: Exception) {
            listener.onLog("转换语言文件失败: ${source.name}, ${e.message}")
            copyFile(source, target)
        }
    }

    private fun convertJavaSoundsToBedrock(source: DocumentFile, target: File, listener: ConvertListener) {
        try {
            val content = readFileContent(source)
            val json = JSONObject(content)
            val result = JSONObject()

            json.keys().forEach { soundKey ->
                val soundValue = json.get(soundKey)
                if (soundValue is JSONObject) {
                    val soundsArray = mutableListOf<JSONObject>()
                    if (soundValue.has("sounds")) {
                        val sounds = soundValue.getJSONArray("sounds")
                        for (i in 0 until sounds.length()) {
                            val sound = sounds.get(i)
                            val soundObj = JSONObject()
                            if (sound is String) {
                                soundObj.put("name", sound)
                            } else if (sound is JSONObject) {
                                if (sound.has("name")) soundObj.put("name", sound.getString("name"))
                                if (sound.has("volume")) soundObj.put("volume", sound.getDouble("volume"))
                                if (sound.has("pitch")) soundObj.put("pitch", sound.getDouble("pitch"))
                                if (sound.has("weight")) soundObj.put("weight", sound.getInt("weight"))
                            }
                            soundsArray.add(soundObj)
                        }
                    }
                    result.put(soundKey, JSONArray(soundsArray))
                }
            }

            target.writeText(result.toString(2))
            listener.onLog("转换音效定义: ${target.name}")
        } catch (e: Exception) {
            listener.onLog("转换音效定义失败: ${e.message}")
            copyFile(source, target)
        }
    }

    private fun convertBedrockSoundsToJava(source: DocumentFile, target: File, listener: ConvertListener) {
        try {
            val content = readFileContent(source)
            val json = JSONObject(content)
            val result = JSONObject()

            json.keys().forEach { soundKey ->
                val soundValue = json.get(soundKey)
                if (soundValue is JSONArray) {
                    val soundObj = JSONObject()
                    val soundsArray = JSONArray()
                    for (i in 0 until soundValue.length()) {
                        val sound = soundValue.getJSONObject(i)
                        val javaSoundObj = JSONObject()
                        if (sound.has("name")) javaSoundObj.put("name", sound.getString("name"))
                        if (sound.has("volume")) javaSoundObj.put("volume", sound.getDouble("volume"))
                        if (sound.has("pitch")) javaSoundObj.put("pitch", sound.getDouble("pitch"))
                        if (sound.has("weight")) javaSoundObj.put("weight", sound.getInt("weight"))
                        soundsArray.put(javaSoundObj)
                    }
                    soundObj.put("sounds", soundsArray)
                    result.put(soundKey, soundObj)
                }
            }

            target.writeText(result.toString(2))
            listener.onLog("转换音效定义: ${target.name}")
        } catch (e: Exception) {
            listener.onLog("转换音效定义失败: ${e.message}")
            copyFile(source, target)
        }
    }

    private fun convertSplashesTxtToJson(source: DocumentFile, target: File, listener: ConvertListener) {
        try {
            val content = readFileContent(source)
            val lines = content.lines().filter { it.isNotEmpty() }
            val json = JSONObject()
            json.put("splashes", JSONArray(lines))
            target.writeText(json.toString(2))
            listener.onLog("转换彩蛋文本: ${target.name}")
        } catch (e: Exception) {
            listener.onLog("转换彩蛋文本失败: ${e.message}")
            copyFile(source, target)
        }
    }

    private fun generateBedrockManifest(targetDir: File, name: String, description: String, headerUuid: String, moduleUuid: String, listener: ConvertListener) {
        try {
            val manifest = JSONObject().apply {
                put("format_version", 2)
                put("header", JSONObject().apply {
                    put("description", "$description\nConverted by Chunkoid")
                    put("name", name)
                    put("uuid", headerUuid)
                    put("version", JSONArray(listOf(1, 0, 0)))
                    put("min_engine_version", JSONArray(listOf(1, 21, 0)))
                })
                put("modules", JSONArray().apply {
                    put(JSONObject().apply {
                        put("description", "$description\nConverted by Chunkoid")
                        put("type", "resources")
                        put("uuid", moduleUuid)
                        put("version", JSONArray(listOf(1, 0, 0)))
                    })
                })
            }

            File(targetDir, "manifest.json").writeText(manifest.toString(2))
            listener.onLog("生成 Bedrock manifest.json")
        } catch (e: Exception) {
            listener.onLog("生成 manifest.json 失败: ${e.message}")
        }
    }

    private fun generateJavaMcmeta(targetDir: File, description: String, listener: ConvertListener) {
        try {
            val mcmeta = JSONObject().apply {
                put("pack", JSONObject().apply {
                    put("pack_format", 18)
                    put("description", "$description\nConverted with Chunkoid")
                })
            }

            File(targetDir, "pack.mcmeta").writeText(mcmeta.toString(2))
            listener.onLog("生成 Java pack.mcmeta")
        } catch (e: Exception) {
            listener.onLog("生成 pack.mcmeta 失败: ${e.message}")
        }
    }

    private fun generateTerrainTexture(targetDir: File, textures: Set<String>, listener: ConvertListener) {
        try {
            val terrainTexture = JSONObject().apply {
                put("resource_pack_name", "vanilla")
                put("texture_name", "atlas.terrain")
                put("texture_data", JSONObject().apply {
                    textures.forEach { texturePath ->
                        val textureName = texturePath.replace("textures/blocks/", "").replace(".png", "")
                        put(textureName, JSONObject().apply {
                            put("textures", texturePath.replace(".png", ""))
                        })
                    }
                })
            }

            File(targetDir, "textures/terrain_texture.json").writeText(terrainTexture.toString(2))
            listener.onLog("生成 terrain_texture.json (${textures.size} 个方块纹理)")
        } catch (e: Exception) {
            listener.onLog("生成 terrain_texture.json 失败: ${e.message}")
        }
    }

    private fun generateBlocksJson(targetDir: File, textures: Set<String>, listener: ConvertListener) {
        try {
            val blocks = JSONObject()
            textures.forEach { texturePath ->
                val textureName = texturePath.replace("textures/blocks/", "").replace(".png", "")
                blocks.put(textureName, JSONObject().apply {
                    put("sound", "stone")
                    put("textures", JSONObject().apply {
                        put("up", textureName)
                        put("down", textureName)
                        put("north", textureName)
                        put("south", textureName)
                        put("west", textureName)
                        put("east", textureName)
                    })
                })
            }

            File(targetDir, "blocks.json").writeText(blocks.toString(2))
            listener.onLog("生成 blocks.json (${textures.size} 个方块)")
        } catch (e: Exception) {
            listener.onLog("生成 blocks.json 失败: ${e.message}")
        }
    }

    private fun generateItemTexture(targetDir: File, textures: Set<String>, listener: ConvertListener) {
        try {
            val itemTexture = JSONObject().apply {
                put("resource_pack_name", "vanilla")
                put("texture_name", "atlas.items")
                put("texture_data", JSONObject().apply {
                    textures.forEach { texturePath ->
                        val textureName = texturePath.replace("textures/items/", "").replace(".png", "")
                        put(textureName, JSONObject().apply {
                            put("textures", texturePath.replace(".png", ""))
                        })
                    }
                })
            }

            File(targetDir, "textures/item_texture.json").writeText(itemTexture.toString(2))
            listener.onLog("生成 item_texture.json (${textures.size} 个物品纹理)")
        } catch (e: Exception) {
            listener.onLog("生成 item_texture.json 失败: ${e.message}")
        }
    }

    private fun generateTexturesList(targetDir: File, textures: Set<String>, listener: ConvertListener) {
        try {
            val json = JSONArray(textures.toList())
            File(targetDir, "textures/textures_list.json").writeText(json.toString(2))
            listener.onLog("生成 textures_list.json (${textures.size} 个纹理)")
        } catch (e: Exception) {
            listener.onLog("生成 textures_list.json 失败: ${e.message}")
        }
    }
}