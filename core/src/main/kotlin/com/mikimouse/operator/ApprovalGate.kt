package com.mikimouse.operator

enum class GateDecision { ALLOW, ASK, DENY }

class ApprovalGate(
    private val deniedPhrases: Set<String> = setOf(
        "extract password", "read password", "steal password",
        "extract otp", "read otp", "bypass permission",
        "hidden record", "background record", "surveillance",
    ),
    private val dangerousTools: Set<String> = setOf("shell", "send_message", "publish", "install_apk"),
) {
    fun decide(step: PlanStep): GateDecision {
        if (deniedPhrases.any { step.description.contains(it, ignoreCase = true) }) return GateDecision.DENY
        if (step.tool in dangerousTools) return GateDecision.ASK
        return when (step.risk) {
            Risk.LOW -> GateDecision.ALLOW
            Risk.MEDIUM -> GateDecision.ASK
            Risk.HIGH -> GateDecision.ASK
        }
    }

    fun requiresExplicitApproval(step: PlanStep): Boolean = decide(step) != GateDecision.ALLOW
}
