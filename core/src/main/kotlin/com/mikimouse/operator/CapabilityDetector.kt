package com.mikimouse.operator

data class DeviceCaps(
    val ramMB: Long,
    val cpuCores: Int,
    val cpuArch: String,
    val sdkInt: Int,
    val freeStorageMB: Long,
    val hasGpu: Boolean,
)

data class ModelRecommendation(val params: String, val reason: String)

object CapabilityDetector {
    fun recommend(d: DeviceCaps): ModelRecommendation = when {
        d.ramMB < 4000 || d.freeStorageMB < 2048 -> ModelRecommendation(
            "0.5B–1.5B (e.g. Qwen2.5-0.5B/1.5B quant)",
            "low RAM/storage: ram=${d.ramMB}MB free=${d.freeStorageMB}MB",
        )
        d.ramMB < 7000 -> ModelRecommendation(
            "1.5B–3B (e.g. Qwen2.5-3B quant)",
            "medium device: ram=${d.ramMB}MB cores=${d.cpuCores}",
        )
        else -> ModelRecommendation(
            "3B+ (e.g. Llama-3.2-3B quant) or remote",
            "high device: ram=${d.ramMB}MB cores=${d.cpuCores} gpu=${d.hasGpu}",
        )
    }

    fun current(): DeviceCaps {
        val rt = Runtime.getRuntime()
        return DeviceCaps(
            ramMB = rt.maxMemory() / (1024 * 1024),
            cpuCores = rt.availableProcessors(),
            cpuArch = System.getProperty("os.arch") ?: "unknown",
            sdkInt = 0, // filled on Android via Build.VERSION.SDK_INT
            freeStorageMB = 0,
            hasGpu = false,
        )
    }
}
