package com.dozenesstudio.chunkoid

import br.com.gamemods.nbtmanipulator.NbtCompound
import br.com.gamemods.nbtmanipulator.NbtIO
import java.io.File
import java.io.InputStream

object NbtVersionReader {

    data class VersionInfo(
        val versionName: String?,
        val versionId: Int?,
        val platform: Platform
    )

    fun readLevelDat(inputDir: File): VersionInfo? {
        val levelDat = File(inputDir, "level.dat")
        if (!levelDat.exists()) return null

        return try {
            val nbtFile = NbtIO.readNbtFileDetectingSettings(levelDat)
            parseVersionInfo(nbtFile.tag as? NbtCompound)
        } catch (e: Exception) {
            null
        }
    }

    fun readLevelDatFromStream(inputStream: InputStream): VersionInfo? {
        val bytes = inputStream.readBytes()

        val attempts = listOf(
            Triple(true, false, false),
            Triple(false, true, true),
            Triple(true, false, true),
            Triple(true, true, false),
            Triple(true, true, true),
            Triple(false, false, false),
            Triple(false, false, true),
            Triple(false, true, false)
        )

        for ((compressed, littleEndian, readHeaders) in attempts) {
            try {
                val nbtFile = NbtIO.readNbtFile(bytes.inputStream(), compressed, littleEndian, readHeaders)
                val versionInfo = parseVersionInfo(nbtFile.tag as? NbtCompound)
                if (versionInfo != null) return versionInfo
            } catch (e: Exception) {
                continue
            }
        }

        return null
    }

    private fun parseVersionInfo(rootTag: NbtCompound?): VersionInfo? {
        if (rootTag == null) return null

        val data = rootTag.getNullableCompound("Data")
        if (data != null) {
            val javaVersion = data.getNullableCompound("Version")
            if (javaVersion != null) {
                return VersionInfo(javaVersion.getNullableString("Name"), javaVersion.getNullableInt("Id"), Platform.JAVA)
            }

            val bedrockVersion = data.getNullableCompound("version")
            if (bedrockVersion != null) {
                return VersionInfo(bedrockVersion.getNullableString("Name"), bedrockVersion.getNullableInt("Id"), Platform.BEDROCK)
            }
        }

        val lastOpenedObj = rootTag["lastOpenedWithVersion"]
        if (lastOpenedObj is br.com.gamemods.nbtmanipulator.NbtList<*>) {
            val versionParts = lastOpenedObj.mapNotNull { (it as? br.com.gamemods.nbtmanipulator.NbtInt)?.value }
            if (versionParts.size >= 2) {
                val versionString = "${versionParts[0]}.${versionParts[1]}" +
                    (if (versionParts.size >= 3 && versionParts[2] > 0) ".${versionParts[2]}" else "")
                return VersionInfo(versionString, null, Platform.BEDROCK)
            }
        }

        val lastOpenedVersion = rootTag.getNullableString("lastOpenedWithVersion")
        if (lastOpenedVersion != null) {
            return VersionInfo(lastOpenedVersion, null, Platform.BEDROCK)
        }

        val worldVersionObj = rootTag["WorldVersion"]
        if (worldVersionObj is br.com.gamemods.nbtmanipulator.NbtInt && worldVersionObj.value > 100) {
            val versionName = mapBedrockVersionId(worldVersionObj.value)
            return VersionInfo(versionName, worldVersionObj.value, Platform.BEDROCK)
        }

        val worldVersion = rootTag.getNullableCompound("WorldVersion")
        if (worldVersion != null) {
            return VersionInfo(worldVersion.getNullableString("Name"), worldVersion.getNullableInt("Id"), Platform.BEDROCK)
        }

        return null
    }

    fun getVersionDisplay(info: VersionInfo): String {
        return when {
            info.versionName != null -> info.versionName
            info.versionId != null -> "版本 ${info.versionId}"
            else -> "未知版本"
        }
    }

    private fun mapBedrockVersionId(versionId: Int): String {
        return when (versionId) {
            1134 -> "1.20.0"
            1143 -> "1.20.10"
            1158 -> "1.20.30"
            1189 -> "1.20.50"
            1210 -> "1.20.70"
            1220 -> "1.20.80"
            1230 -> "1.20.90"
            1240 -> "1.21.0"
            1250 -> "1.21.10"
            1260 -> "1.21.20"
            else -> "1.$versionId"
        }
    }
}
