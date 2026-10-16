package com.mikimouse.operator

sealed interface BudgetVerdict {
    data object Ok : BudgetVerdict
    data class Denied(val reason: String) : BudgetVerdict
}

class ResourceManager(val budget: ResourceBudget = ResourceBudget()) {
    var usedStorageMB: Int = 0
        private set
    var workers: Int = 0
        private set
    var elapsedMs: Long = 0
        private set

    fun addElapsed(ms: Long) { elapsedMs += ms }

    fun tryAllocStorage(mb: Int): BudgetVerdict {
        if (mb < 0) return BudgetVerdict.Denied("negative alloc")
        return if (usedStorageMB + mb > budget.storageQuotaMB) {
            BudgetVerdict.Denied("storage ${usedStorageMB + mb}MB exceeds quota ${budget.storageQuotaMB}MB")
        } else {
            usedStorageMB += mb
            BudgetVerdict.Ok
        }
    }

    fun trySpawnWorker(): BudgetVerdict =
        if (workers + 1 > budget.maxWorkers) {
            BudgetVerdict.Denied("workers ${workers + 1} exceed policy ${budget.cpu} (max ${budget.maxWorkers})")
        } else {
            workers += 1
            BudgetVerdict.Ok
        }

    fun releaseWorker() { if (workers > 0) workers -= 1 }

    fun releaseStorage(mb: Int) { usedStorageMB = (usedStorageMB - mb).coerceAtLeast(0) }

    fun networkAllowed(isTrusted: Boolean = false): Boolean = when (budget.network) {
        NetworkPolicy.OFFLINE_ONLY -> false
        NetworkPolicy.ASK_BEFORE -> false // caller must obtain approval first
        NetworkPolicy.ALLOW -> true
        NetworkPolicy.TRUSTED_ONLY -> isTrusted
    }
}
