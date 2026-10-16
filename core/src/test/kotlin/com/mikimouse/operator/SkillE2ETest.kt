package com.mikimouse.operator

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class SkillE2ETest {
    private val installed = mutableListOf<String>()
    private val fake = object : SkillInstaller {
        override fun install(id: String, source: String, destDir: File): List<String> {
            installed += id
            destDir.mkdirs()
            // Faithful shape: frontmatter with name + description, like the real skills.
            File(destDir, "SKILL.md").writeText(
                "---\nname: $id\ndescription: test double for $id\n---\n\n# $id\n",
            )
            return listOf("SKILL.md")
        }
    }

    private fun frontmatterOk(skillMd: String): Boolean {
        val head = skillMd.lines().take(8).joinToString("\n")
        return head.contains("name:") && head.contains("description:")
    }

    @Test fun everySkillHasValidShape() {
        // Each skill installed through the real path must carry name+description frontmatter.
        val root = createTempDir("skille2e")
        val sm = SkillManager(root, fake)
        listOf("composio", "pr-watch").forEach { id ->
            val rec = sm.ensure(id, "http://example/$id")
            val md = File(root, "skills/tmp/$id/SKILL.md").readText()
            assertTrue(frontmatterOk(md), "$id SKILL.md missing frontmatter")
            assertEquals(1, rec.files.size)
        }
        assertEquals(listOf("composio", "pr-watch"), sm.list().map { it.id })
    }

    @Test fun plannerAttachesSkills() {
        val p = Planner()
        assertTrue(p.plan(TaskSpec("r", "Research this topic and file a report")).requiredSkills.contains("composio"))
        assertTrue(p.plan(TaskSpec("d", "Download these files and organize them")).requiredSkills.contains("composio"))
        assertTrue(p.plan(TaskSpec("v", "Create a video from these files")).requiredSkills.isEmpty())
    }

    @Test fun runtimeAutoInstallsSkillSoWorkCompletes() = runTest {
        val root = createTempDir("rt-skill")
        val wm = WorkspaceManager(root)
        val ws = wm.create("skill-task")
        wm.importBytes(ws, "a.txt", "hello".toByteArray())
        val rt = OperatorRuntime(root, skills = SkillManager(root, fake))
        val res = rt.run(
            TaskSpec("skill-task", "Download these files and organize them"),
            approve = { true },
        )
        assertEquals(TaskState.COMPLETED, res.state)
        assertTrue(installed.contains("composio"), "runtime must auto-install the needed skill")
        val audit = File(root, "tasks/skill-task/logs/audit.log").readText()
        assertTrue(audit.contains("skill.ready"), "audit must trace the install, got:\n$audit")
    }
}
