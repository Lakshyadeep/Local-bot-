package com.lakhyadeep.papai.domain.device

import android.app.ActivityManager
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.StatFs
import java.io.File

data class DeviceCapabilities(
    val deviceModel: String,
    val manufacturer: String,
    val totalRamBytes: Long,
    val availableRamBytes: Long,
    val isLowMemory: Boolean,
    val availableStorageBytes: Long,
    val totalStorageBytes: Long,
    val cpuCores: Int,
    val primaryAbi: String,
    val supportedAbis: List<String>,
    val is64Bit: Boolean,
    val hasGpuInferenceSupport: Boolean,
    val gpuBackendInfo: String,
    val isNetworkConnected: Boolean
) {
    val totalRamMb: Long get() = totalRamBytes / (1024L * 1024L)
    val availableRamMb: Long get() = availableRamBytes / (1024L * 1024L)
    val availableStorageMb: Long get() = availableStorageBytes / (1024L * 1024L)
    val totalRamGb: Double get() = totalRamBytes / (1024.0 * 1024.0 * 1024.0)
    val availableRamGb: Double get() = availableRamBytes / (1024.0 * 1024.0 * 1024.0)
    val availableStorageGb: Double get() = availableStorageBytes / (1024.0 * 1024.0 * 1024.0)
}

class DeviceProfiler(private val context: Context) {

    fun getModelsDirectory(): File {
        val baseDir = context.getExternalFilesDir("models") ?: File(context.filesDir, "models")
        if (!baseDir.exists()) {
            baseDir.mkdirs()
        }
        return baseDir
    }

    fun snapshot(): DeviceCapabilities {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        am.getMemoryInfo(memInfo)

        val storagePath = getModelsDirectory()
        val statFs = StatFs(storagePath.absolutePath)
        val availStorage = statFs.availableBlocksLong * statFs.blockSizeLong
        val totalStorage = statFs.blockCountLong * statFs.blockSizeLong

        val abis = Build.SUPPORTED_ABIS.toList()
        val is64Bit = abis.any { it.contains("64") }
        val primaryAbi = abis.firstOrNull() ?: "unknown"

        val cpuCores = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)

        // Llamatik Android prebuilt native binaries use optimized ARM NEON / CPU execution
        // (no Vulkan/OpenCL GPU offload compiled in this Android target), so report accurately.
        val hasGpuSupport = false
        val gpuInfo = "CPU-only build (ARM NEON / CPU backend)"

        val isConnected = checkNetworkConnected()

        return DeviceCapabilities(
            deviceModel = Build.MODEL ?: "Android Device",
            manufacturer = Build.MANUFACTURER ?: "",
            totalRamBytes = memInfo.totalMem,
            availableRamBytes = memInfo.availMem,
            isLowMemory = memInfo.lowMemory,
            availableStorageBytes = availStorage,
            totalStorageBytes = totalStorage,
            cpuCores = cpuCores,
            primaryAbi = primaryAbi,
            supportedAbis = abis,
            is64Bit = is64Bit,
            hasGpuInferenceSupport = hasGpuSupport,
            gpuBackendInfo = gpuInfo,
            isNetworkConnected = isConnected
        )
    }

    private fun checkNetworkConnected(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
}
