package com.dozenesstudio.chunkoid.model

data class WorldInfo(
    val name: String,
    val directoryPath: String,
    val platform: String,
    val version: String,
    val iconPath: String? = null
) {
    companion object {
        const val PLATFORM_JAVA = "java"
        const val PLATFORM_BEDROCK = "bedrock"
    }

    val isJava: Boolean
        get() = platform.equals(PLATFORM_JAVA, ignoreCase = true)

    val isBedrock: Boolean
        get() = platform.equals(PLATFORM_BEDROCK, ignoreCase = true)
}