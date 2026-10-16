package com.mikimouse.operator

import kotlin.test.Test
import kotlin.test.assertTrue

class ModelSwitchingTest {
    @Test fun routerFallsBackAndSwitches() {
        val failing = object : ModelProvider {
            override val id = "dead"
            override fun chat(req: ChatRequest): String = throw IllegalStateException("down")
        }
        val ok = LocalStubProvider()
        val router = ModelRouter(primary = failing, fallback = ok)
        assertTrue(router.chat(ChatRequest(user = "hello")).contains("LOCAL-DRAFT"))
        router.switch("openai") { LocalStubProvider() }
        assertTrue(router.chat(ChatRequest(user = "hi")).isNotBlank())
    }

    @Test fun capabilityDetectorTiers() {
        assertTrue(CapabilityDetector.recommend(DeviceCaps(2000, 4, "arm64", 34, 1000, false)).params.contains("0.5B"))
        assertTrue(CapabilityDetector.recommend(DeviceCaps(6000, 8, "arm64", 34, 8000, true)).params.contains("1.5B"))
        assertTrue(CapabilityDetector.recommend(DeviceCaps(11000, 8, "arm64", 36, 20000, true)).params.contains("3B"))
    }
}
