package com.dozenesstudio.chunkoid.decryptor

import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.StandardCharsets

object NetEaseDecryptor : IDecryptor {

    private val MAGIC_HEADER = byteArrayOf(0x80.toByte(), 0x1D.toByte(), 0x30.toByte(), 0x01.toByte())
    private const val HEADER_SIZE = 4

    override fun isEncrypted(inputStream: InputStream): Boolean {
        if (!inputStream.markSupported()) {
            return false
        }
        inputStream.mark(HEADER_SIZE)
        val header = ByteArray(HEADER_SIZE)
        val bytesRead = inputStream.read(header)
        inputStream.reset()

        if (bytesRead < HEADER_SIZE) return false
        return header.contentEquals(MAGIC_HEADER)
    }

    override fun deriveKey(currentStream: InputStream, manifestName: String): ByteArray {
        val allBytes = currentStream.use { it.readBytes() }
        if (allBytes.size < HEADER_SIZE) {
            throw IllegalArgumentException("CURRENT file is too small or invalid")
        }

        val header = allBytes.copyOfRange(0, HEADER_SIZE)
        if (!header.contentEquals(MAGIC_HEADER)) {
            throw IllegalArgumentException("CURRENT file is not encrypted by NetEase (Magic header mismatch)")
        }

        val encryptedBody = allBytes.copyOfRange(HEADER_SIZE, allBytes.size)
        val manifestBytes = manifestName.toByteArray(StandardCharsets.UTF_8)
        val sourceBytes = ByteArray(manifestBytes.size + 1)
        manifestBytes.copyInto(sourceBytes)
        sourceBytes[sourceBytes.size - 1] = 0x0A.toByte()

        val rawKey = ByteArray(encryptedBody.size)
        for (i in encryptedBody.indices) {
            rawKey[i] = (encryptedBody[i].toInt() xor sourceBytes[i % sourceBytes.size].toInt()).toByte()
        }

        return if (rawKey.size == 16) {
            val firstHalf = rawKey.copyOfRange(0, 8)
            val secondHalf = rawKey.copyOfRange(8, 16)
            if (firstHalf.contentEquals(secondHalf)) firstHalf else rawKey
        } else {
            rawKey
        }
    }

    override fun decryptFile(input: InputStream, output: OutputStream, key: ByteArray): Boolean {
        return try {
            input.use { inputStream ->
                output.use { outputStream ->
                    val fileData = inputStream.readBytes()

                    if (hasMagicHeader(fileData)) {
                        val body = fileData.copyOfRange(HEADER_SIZE, fileData.size)
                        val decrypted = xor(body, key)
                        outputStream.write(decrypted)
                    } else {
                        outputStream.write(fileData)
                    }
                }
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun hasMagicHeader(data: ByteArray): Boolean {
        if (data.size < HEADER_SIZE) return false
        for (i in 0 until HEADER_SIZE) {
            if (data[i] != MAGIC_HEADER[i]) return false
        }
        return true
    }

    private fun xor(data: ByteArray, key: ByteArray): ByteArray {
        val result = ByteArray(data.size)
        for (i in data.indices) {
            result[i] = (data[i].toInt() xor key[i % key.size].toInt()).toByte()
        }
        return result
    }
}
