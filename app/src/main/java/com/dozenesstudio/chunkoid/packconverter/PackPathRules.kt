package com.dozenesstudio.chunkoid.packconverter

enum class ConversionDirection {
    JAVA_TO_BEDROCK,
    BEDROCK_TO_JAVA
}

data class PathRule(
    val match: Regex,
    val replace: String
)

object PackPathRules {

    private val JAVA_TO_BEDROCK_RULES = listOf(
        PathRule(Regex("^assets/minecraft/textures/blocks?/(.*)$"), "textures/blocks/\$1"),
        PathRule(Regex("^assets/minecraft/textures/items?/(.*)$"), "textures/items/\$1"),
        PathRule(Regex("^assets/minecraft/textures/models/armor/(.*)_layer_([12])\\.png$"), "textures/models/armor/\$1_\$2.png"),
        PathRule(Regex("^assets/minecraft/textures/entity/(.*)$"), "textures/entity/\$1"),
        PathRule(Regex("^assets/minecraft/textures/gui/(.*)$"), "textures/ui/\$1"),
        PathRule(Regex("^assets/minecraft/textures/(.*)\\.mcmeta$"), "textures/\$1.mcmeta"),
        PathRule(Regex("^assets/minecraft/textures/(.*)$"), "textures/\$1"),
        PathRule(Regex("^assets/minecraft/sounds/(.*)$"), "sounds/\$1"),
        PathRule(Regex("^assets/(minecraft|realms)/lang/(.*)\\.json$"), "texts/\$2.lang"),
        PathRule(Regex("^assets/realms/textures/(.*)$"), "textures/gui/realms/\$1"),
        PathRule(Regex("^assets/minecraft/texts/splashes\\.txt$"), "textures/splashes.json"),
        PathRule(Regex("^pack\\.png$"), "pack_icon.png")
    )

    private val BEDROCK_TO_JAVA_RULES = listOf(
        PathRule(Regex("^textures/blocks/(.*)$"), "assets/minecraft/textures/block/\$1"),
        PathRule(Regex("^textures/items/(.*)$"), "assets/minecraft/textures/item/\$1"),
        PathRule(Regex("^textures/models/armor/(.*)_([12])\\.png$"), "assets/minecraft/textures/models/armor/\$1_layer_\$2.png"),
        PathRule(Regex("^textures/entity/(.*)$"), "assets/minecraft/textures/entity/\$1"),
        PathRule(Regex("^textures/gui/realms/(.*)$"), "assets/realms/textures/\$1"),
        PathRule(Regex("^textures/ui/(.*)$"), "assets/minecraft/textures/gui/\$1"),
        PathRule(Regex("^textures/(.*)$"), "assets/minecraft/textures/\$1"),
        PathRule(Regex("^sounds/(.*)$"), "assets/minecraft/sounds/\$1"),
        PathRule(Regex("^texts/(.*)\\.lang$"), "assets/minecraft/lang/\$1.json"),
        PathRule(Regex("^pack_icon\\.png$"), "pack.png")
    )

    fun getTargetPath(path: String, direction: ConversionDirection): String? {
        val rules = if (direction == ConversionDirection.JAVA_TO_BEDROCK) {
            JAVA_TO_BEDROCK_RULES
        } else {
            BEDROCK_TO_JAVA_RULES
        }

        for (rule in rules) {
            if (rule.match.matches(path)) {
                return path.replace(rule.match, rule.replace)
            }
        }

        return null
    }

    fun isJavaPack(path: String): Boolean {
        return path.contains("pack.mcmeta") || path.contains("assets/minecraft/")
    }

    fun isBedrockPack(path: String): Boolean {
        return path.contains("manifest.json") || path.contains("textures/") && !path.contains("assets/")
    }
}