package com.mikimouse.operator

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ToolsRealTest {
    @Test fun zipRoundTrip() {
        val root = createTempDir("arc")
        val a = File(root, "a.txt").apply { writeText("hello operator") }
        val z = ArchiveTool.zip(listOf(a), File(root, "out.zip"))
        assertTrue(z.length() > 0)
        val back = ArchiveTool.unzip(z, File(root, "back"))
        assertEquals(1, back.size)
        assertEquals("hello operator", back[0].readText())
    }

    @Test fun zipRejectsPathEscape() {
        val root = createTempDir("arc2")
        val evil = File(root, "evil.zip")
        java.util.zip.ZipOutputStream(evil.outputStream()).use {
            it.putNextEntry(java.util.zip.ZipEntry("../evil.txt"))
            it.write("x".toByteArray())
            it.closeEntry()
        }
        try {
            ArchiveTool.unzip(evil, File(root, "dest"))
            throw AssertionError("expected rejection")
        } catch (t: IllegalArgumentException) {
            assertTrue(t.message!!.contains("escape"))
        }
    }

    @Test fun htmlTextExtract() {
        val html = "<html><head><title>T</title></head><body><p>Hello <b>world</b></p><script>evil()</script></body></html>".toByteArray()
        val p = WebFetch.extractText(html)
        assertEquals("T", p.title)
        assertTrue(p.text.contains("Hello world") && !p.text.contains("evil"))
    }

    @Test fun htmlTablesExtract() {
        val html = "<table><tr><th>A</th><th>B</th></tr><tr><td>1</td><td>2</td></tr></table>".toByteArray()
        val t = WebFetch.extractTables(html)
        assertEquals(1, t.size)
        assertEquals(listOf("A", "B"), t[0][0])
        assertEquals(listOf("1", "2"), t[0][1])
    }
}
