package com.mikimouse.operator

import kotlin.test.Test
import kotlin.test.assertTrue

class QuotaEnforcementTest {
    @Test fun storageQuotaDenied() {
        val rm = ResourceManager(ResourceBudget(storageQuotaMB = 100))
        assertTrue(rm.tryAllocStorage(60) is BudgetVerdict.Ok)
        val v = rm.tryAllocStorage(50)
        assertTrue(v is BudgetVerdict.Denied, "expected quota denial, got $v")
    }

    @Test fun cpuPolicyCapsWorkers() {
        val rm = ResourceManager(ResourceBudget(cpu = CpuPolicy.CONSERVATIVE))
        assertTrue(rm.trySpawnWorker() is BudgetVerdict.Ok)
        assertTrue(rm.trySpawnWorker() is BudgetVerdict.Denied)
    }

    @Test fun networkPolicyDefaultsClosed() {
        val off = ResourceManager(ResourceBudget(network = NetworkPolicy.OFFLINE_ONLY))
        assertTrue(!off.networkAllowed())
        val ask = ResourceManager(ResourceBudget(network = NetworkPolicy.ASK_BEFORE))
        assertTrue(!ask.networkAllowed(), "ASK_BEFORE must not auto-allow")
        val allow = ResourceManager(ResourceBudget(network = NetworkPolicy.ALLOW))
        assertTrue(allow.networkAllowed())
    }
}
