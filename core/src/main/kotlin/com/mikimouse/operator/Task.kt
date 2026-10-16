package com.mikimouse.operator

import kotlinx.serialization.Serializable

@Serializable
enum class TaskState {
    CREATED, PLANNING, RUNNING, WAITING_FOR_USER, PAUSED, FAILED, COMPLETED, CLEANING, CANCELLED
}

@Serializable
enum class Risk { LOW, MEDIUM, HIGH }

@Serializable
enum class NetworkPolicy { OFFLINE_ONLY, ASK_BEFORE, ALLOW, TRUSTED_ONLY }

@Serializable
enum class CpuPolicy { CONSERVATIVE, BALANCED, PERFORMANCE }

@Serializable
data class RamBudget(val targetMB: Int) {
    // Target runtime memory budget — never presented as a physical reservation.
    init { require(targetMB in 512..4096 || targetMB == 0) { "RAM target 512-4096 or 0=auto" } }
}

@Serializable
data class ResourceBudget(
    val ramTargetMB: Int = 2048,
    val storageQuotaMB: Int = 5120,
    val cpu: CpuPolicy = CpuPolicy.BALANCED,
    val network: NetworkPolicy = NetworkPolicy.ASK_BEFORE,
) {
    val maxWorkers: Int get() = when (cpu) {
        CpuPolicy.CONSERVATIVE -> 1
        CpuPolicy.BALANCED -> 2
        CpuPolicy.PERFORMANCE -> 3
    }
}

@Serializable
data class PlanStep(
    val id: String,
    val description: String,
    val tool: String,
    val risk: Risk = Risk.LOW,
)

@Serializable
data class TaskPlan(
    val goal: String,
    val steps: List<PlanStep>,
    val requiredTools: List<String>,
    val estimatedStorageMB: Int,
    val overallRisk: Risk,
    val requiredSkills: List<String> = emptyList(),
)

@Serializable
data class StepResult(
    val stepId: String,
    val ok: Boolean,
    val evidence: String = "",
    val detail: String = "",
)

@Serializable
data class TaskSpec(
    val taskId: String,
    val instruction: String,
    val inputNames: List<String> = emptyList(),
    val budget: ResourceBudget = ResourceBudget(),
    val deadlineMs: Long = 300_000L,
)
