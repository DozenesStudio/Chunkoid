package com.dozenesstudio.chunkoid.model

data class LogEntry(
    val message: String,
    val level: Level = Level.INFO,
    val timestamp: Long = System.currentTimeMillis()
) {
    enum class Level {
        DEBUG,
        INFO,
        WARNING,
        ERROR
    }
}
