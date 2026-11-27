package com.lakhyadeep.papai.domain.device

import java.io.File
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

data class GgufMetadata(
    val isValidGguf: Boolean,
    val formatVersion: Int = 0,
    val tensorCount: Long = 0,
    val kvCount: Long = 0,
    val architecture: String? = null,
    val modelName: String? = null,
    val contextLength: Int? = null,
    val errorReason: String? = null
)

object GgufInspector {
    private const val GGUF_MAGIC_LE = 0x46554747 // "GGUF" in little-endian

    fun hasValidMagicBytes(bytes: ByteArray): Boolean {
        if (bytes.size < 4) return false
        return bytes[0] == 'G'.code.toByte() &&
            bytes[1] == 'G'.code.toByte() &&
            bytes[2] == 'U'.code.toByte() &&
            bytes[3] == 'F'.code.toByte()
    }

    fun inspectFile(file: File): GgufMetadata {
        if (!file.exists() || !file.isFile) {
            return GgufMetadata(isValidGguf = false, errorReason = "File does not exist.")
        }
        if (file.length() < 24L) {
            return GgufMetadata(
                isValidGguf = false,
                errorReason = "File is too small (${file.length()} bytes) to be a valid GGUF model."
            )
        }
        return try {
            file.inputStream().buffered(65536).use { inspectStream(it) }
        } catch (e: Exception) {
            GgufMetadata(
                isValidGguf = false,
                errorReason = "Failed to read GGUF header: ${e.message ?: "unknown error"}"
            )
        }
    }

    fun inspectStream(input: InputStream): GgufMetadata {
        val header = ByteArray(24)
        val read = readFully(input, header)
        if (read < 24) {
            return GgufMetadata(isValidGguf = false, errorReason = "Truncated GGUF header.")
        }
        if (!hasValidMagicBytes(header)) {
            return GgufMetadata(
                isValidGguf = false,
                errorReason = "Invalid file signature. Expected 'GGUF' binary header."
            )
        }

        val buf = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
        buf.int // magic
        val version = buf.int
        if (version !in 1..5) {
            return GgufMetadata(
                isValidGguf = false,
                errorReason = "Unsupported GGUF format version: $version"
            )
        }

        val tensorCount = if (version == 1) buf.int.toLong() else buf.long
        val kvCount = if (version == 1) buf.int.toLong() else buf.long

        var architecture: String? = null
        var modelName: String? = null
        var contextLength: Int? = null

        // Parse up to 64 KV metadata pairs safely without buffering huge arrays
        val maxPairs = kvCount.coerceIn(0L, 64L).toInt()
        for (i in 0 until maxPairs) {
            val key = readGgufString(input) ?: break
            val valueType = readIntLE(input) ?: break
            when (valueType) {
                8 -> { // STRING
                    val strVal = readGgufString(input) ?: break
                    when (key) {
                        "general.architecture" -> architecture = strVal
                        "general.name" -> modelName = strVal
                    }
                }
                4 -> { // UINT32
                    val intVal = readIntLE(input) ?: break
                    if (key.endsWith(".context_length")) {
                        contextLength = intVal
                    }
                }
                5 -> { // INT32
                    val intVal = readIntLE(input) ?: break
                    if (key.endsWith(".context_length")) {
                        contextLength = intVal
                    }
                }
                0, 1, 7 -> { // UINT8, INT8, BOOL
                    if (input.read() < 0) break
                }
                2, 3 -> { // UINT16, INT16
                    if (!skipExact(input, 2L)) break
                }
                6 -> { // FLOAT32
                    if (!skipExact(input, 4L)) break
                }
                10, 11, 12 -> { // UINT64, INT64, FLOAT64
                    if (!skipExact(input, 8L)) break
                }
                9 -> { // ARRAY - stop deep parsing safely once we hit large tokenizer arrays
                    break
                }
                else -> break
            }
        }

        return GgufMetadata(
            isValidGguf = true,
            formatVersion = version,
            tensorCount = tensorCount,
            kvCount = kvCount,
            architecture = architecture,
            modelName = modelName,
            contextLength = contextLength
        )
    }

    private fun readGgufString(input: InputStream): String? {
        val len = readLongLE(input) ?: return null
        if (len < 0 || len > 16384L) return null
        val bytes = ByteArray(len.toInt())
        if (readFully(input, bytes) < bytes.size) return null
        return String(bytes, Charsets.UTF_8)
    }

    private fun readIntLE(input: InputStream): Int? {
        val bytes = ByteArray(4)
        if (readFully(input, bytes) < 4) return null
        return ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).int
    }

    private fun readLongLE(input: InputStream): Long? {
        val bytes = ByteArray(8)
        if (readFully(input, bytes) < 8) return null
        return ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).long
    }

    private fun readFully(input: InputStream, target: ByteArray): Int {
        var total = 0
        while (total < target.size) {
            val n = input.read(target, total, target.size - total)
            if (n <= 0) break
            total += n
        }
        return total
    }

    private fun skipExact(input: InputStream, count: Long): Boolean {
        var remaining = count
        val scratch = ByteArray(256)
        while (remaining > 0) {
            val toRead = minOf(remaining, scratch.size.toLong()).toInt()
            val n = input.read(scratch, 0, toRead)
            if (n <= 0) return false
            remaining -= n
        }
        return true
    }

    fun inferQuantizationFromFilename(fileName: String): String {
        val regex = Regex("(IQ[1-4]_[A-Z0-9_]+|Q[2-8]_[0-9A-Z_]+|F16|BF16|F32)", RegexOption.IGNORE_CASE)
        return regex.find(fileName)?.value?.uppercase() ?: "GGUF"
    }

    fun inferParamSizeFromText(text: String): String {
        val regex = Regex("\\b(\\d+(?:\\.\\d+)?[BM])\\b", RegexOption.IGNORE_CASE)
        return regex.find(text)?.value?.uppercase() ?: ""
    }
}
