package com.dozenesstudio.chunkoid.decryptor

import java.io.InputStream
import java.io.OutputStream

interface IDecryptor {
    fun isEncrypted(inputStream: InputStream): Boolean
    fun deriveKey(currentStream: InputStream, manifestName: String): ByteArray
    fun decryptFile(input: InputStream, output: OutputStream, key: ByteArray): Boolean
}
