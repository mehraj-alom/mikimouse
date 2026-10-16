package com.mikimouse.operator

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse

class ToolAuthorizationTest {
    @Test fun allowlistEnforcedAndSchemasDeclared() {
        val reg = ToolRegistry()
        assertTrue(reg.all().size >= 16)
        val allow = setOf("media_inspect", "run_media_pipeline")
        assertTrue(reg.isAllowed("run_media_pipeline", allow))
        assertFalse(reg.isAllowed("shell", allow))
        assertFalse(reg.isAllowed("nope", allow))
        val shell = reg.get("shell")!!
        assertEquals(ToolRisk.HIGH, shell.risk)
        assertTrue(shell.workspaceOnly)
    }

    @Test fun approvalGate() {
        val gate = ApprovalGate()
        assertEquals(GateDecision.ALLOW, gate.decide(PlanStep("1", "Inspect inputs", "media_inspect", Risk.LOW)))
        assertEquals(GateDecision.ASK, gate.decide(PlanStep("2", "Export file", "share_artifact", Risk.MEDIUM)))
        assertEquals(GateDecision.DENY, gate.decide(PlanStep("3", "extract password please", "read_input", Risk.LOW)))
        assertEquals(GateDecision.ASK, gate.decide(PlanStep("4", "Run cmd", "shell", Risk.LOW)))
    }
}
