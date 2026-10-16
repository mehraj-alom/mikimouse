package com.mikimouse.operator

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SkillManagerTest {
    private val fake = object : SkillInstaller {
        override fun install(id: String, source: String, destDir: File): List<String> {
            destDir.mkdirs()
            File(destDir, "SKILL.md").writeText("# $id")
            return listOf("SKILL.md")
        }
    }

    @Test fun ensureInstallsTempKeepsListAndPromotes() {
        val root = createTempDir("skills")
        val sm = SkillManager(root, fake)
        val rec = sm.ensure("composio", "http://example/s")
        assertEquals(SkillKind.TEMP, rec.kind)
        assertTrue(File(root, "skills/tmp/composio/SKILL.md").exists())
        // List kept for later reference.
        assertEquals(listOf("composio"), sm.list().map { it.id })
        // Second ensure reuses without reinstalling.
        sm.ensure("composio", "http://example/s")
        assertEquals(1, sm.list().size)
        sm.promote("composio")
        assertEquals(SkillKind.PERMANENT, sm.get("composio")!!.kind)
        assertTrue(File(root, "skills/composio/SKILL.md").exists())
        sm.remove("composio")
        assertTrue(sm.list().isEmpty())
    }

    @Test fun unknownSkillWithoutSourceRejected() {
        val sm = SkillManager(createTempDir("skills2"), fake)
        try {
            sm.ensure("nope")
            throw AssertionError("expected failure")
        } catch (t: IllegalArgumentException) {
            assertTrue(t.message!!.contains("unknown skill"))
        }
    }
}
