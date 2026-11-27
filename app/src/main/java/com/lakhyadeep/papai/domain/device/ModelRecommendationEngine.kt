package com.lakhyadeep.papai.domain.device

import com.lakhyadeep.papai.data.local.db.ModelEntity

enum class CompatibilityStatus {
    OPTIMAL,
    TIGHT_FIT,
    MEMORY_WARNING,
    INSUFFICIENT_RAM,
    INSUFFICIENT_STORAGE,
    ARCH_UNSUPPORTED
}

data class ModelRecommendation(
    val status: CompatibilityStatus,
    val estimatedMemoryMb: Long,
    val kvCacheOverheadMb: Long,
    val badgeText: String,
    val warningMessage: String?,
    val suggestedAction: String?
)

object ModelRecommendationEngine {

    fun evaluate(
        modelSizeBytes: Long,
        quantization: String,
        contextLength: Int,
        device: DeviceCapabilities,
        isAlreadyDownloaded: Boolean = false
    ): ModelRecommendation {
        // Check 64-bit ABI for native inference
        if (!device.is64Bit) {
            return ModelRecommendation(
                status = CompatibilityStatus.ARCH_UNSUPPORTED,
                estimatedMemoryMb = bytesToMb(modelSizeBytes),
                kvCacheOverheadMb = 0L,
                badgeText = "32-bit CPU Unsupported",
                warningMessage = "This device uses ${device.primaryAbi}. Modern GGUF native inference requires a 64-bit arm64-v8a or x86_64 processor.",
                suggestedAction = "Use a 64-bit Android device for local GGUF inference."
            )
        }

        val modelMb = bytesToMb(modelSizeBytes).coerceAtLeast(1L)
        // Conservative KV cache + runtime scratch buffer estimate scaled by context length
        val kvOverheadMb = ((contextLength / 1024.0) * 95.0 + 180.0).toLong()
        val estimatedTotalRamMb = (modelMb * 1.18).toLong() + kvOverheadMb

        // Storage check for non-downloaded models (require model size + 250 MB headroom)
        if (!isAlreadyDownloaded && modelSizeBytes > 0L) {
            val requiredStorageMb = modelMb + 250L
            if (device.availableStorageMb < requiredStorageMb) {
                return ModelRecommendation(
                    status = CompatibilityStatus.INSUFFICIENT_STORAGE,
                    estimatedMemoryMb = estimatedTotalRamMb,
                    kvCacheOverheadMb = kvOverheadMb,
                    badgeText = "Low Storage",
                    warningMessage = "Needs ~${formatMb(requiredStorageMb)} free space, but only ${formatMb(device.availableStorageMb)} is available.",
                    suggestedAction = "Free device storage or choose a smaller Q4_K_M / Q3_K_S variant."
                )
            }
        }

        val availRamMb = device.availableRamMb
        val totalRamMb = device.totalRamMb
        // Android kills background/large apps when process exceeds ~70-80% of free RAM + swap pressure
        val comfortableBudgetMb = (availRamMb * 0.85).toLong().coerceAtLeast(totalRamMb / 3)

        return when {
            estimatedTotalRamMb > totalRamMb * 0.85 -> {
                ModelRecommendation(
                    status = CompatibilityStatus.INSUFFICIENT_RAM,
                    estimatedMemoryMb = estimatedTotalRamMb,
                    kvCacheOverheadMb = kvOverheadMb,
                    badgeText = "Exceeds RAM",
                    warningMessage = "Estimated ~${formatMb(estimatedTotalRamMb)} RAM needed exceeds safe device memory (${formatMb(totalRamMb)} total, ${formatMb(availRamMb)} free).",
                    suggestedAction = recommendSmallerQuantHint(quantization)
                )
            }
            estimatedTotalRamMb > comfortableBudgetMb || device.isLowMemory -> {
                ModelRecommendation(
                    status = CompatibilityStatus.MEMORY_WARNING,
                    estimatedMemoryMb = estimatedTotalRamMb,
                    kvCacheOverheadMb = kvOverheadMb,
                    badgeText = "High Memory Load",
                    warningMessage = "May cause memory pressure (~${formatMb(estimatedTotalRamMb)} estimated vs ${formatMb(availRamMb)} currently free). Closing background apps or reducing context length is recommended.",
                    suggestedAction = "Reduce context length to 2048 or pick a smaller Q4_K_M / Q3_K_M quantization."
                )
            }
            estimatedTotalRamMb > availRamMb * 0.60 -> {
                ModelRecommendation(
                    status = CompatibilityStatus.TIGHT_FIT,
                    estimatedMemoryMb = estimatedTotalRamMb,
                    kvCacheOverheadMb = kvOverheadMb,
                    badgeText = "Good Fit",
                    warningMessage = null,
                    suggestedAction = "Runs best with 2048–4096 context."
                )
            }
            else -> {
                ModelRecommendation(
                    status = CompatibilityStatus.OPTIMAL,
                    estimatedMemoryMb = estimatedTotalRamMb,
                    kvCacheOverheadMb = kvOverheadMb,
                    badgeText = "Recommended",
                    warningMessage = null,
                    suggestedAction = null
                )
            }
        }
    }

    fun evaluateModel(
        model: ModelEntity,
        contextLength: Int,
        device: DeviceCapabilities
    ): ModelRecommendation {
        return evaluate(
            modelSizeBytes = model.fileSizeBytes,
            quantization = model.quantization,
            contextLength = contextLength,
            device = device,
            isAlreadyDownloaded = model.isDownloaded
        )
    }

    private fun recommendSmallerQuantHint(currentQuant: String): String {
        return when {
            currentQuant.contains("Q8", ignoreCase = true) ||
                currentQuant.contains("F16", ignoreCase = true) ->
                "Select a smaller Q4_K_M or Q3_K_M quantized variant (uses ~50% less RAM)."
            currentQuant.contains("Q6", ignoreCase = true) ||
                currentQuant.contains("Q5", ignoreCase = true) ->
                "Try a Q4_K_M or Q4_0 variant or a smaller 0.5B–1.5B parameter model."
            else ->
                "Choose a smaller parameter model (e.g., 0.5B or 1B Q4_K_M) and lower context length to 1024–2048."
        }
    }

    private fun bytesToMb(bytes: Long): Long = bytes / (1024L * 1024L)

    fun formatMb(mb: Long): String {
        return if (mb >= 1024L) {
            String.format("%.1f GB", mb / 1024.0)
        } else {
            "$mb MB"
        }
    }

    fun formatBytes(bytes: Long): String {
        if (bytes <= 0L) return "Unknown size"
        val kb = bytes / 1024.0
        val mb = kb / 1024.0
        val gb = mb / 1024.0
        return when {
            gb >= 1.0 -> String.format("%.2f GB", gb)
            mb >= 1.0 -> String.format("%.1f MB", mb)
            else -> String.format("%.0f KB", kb)
        }
    }
}
