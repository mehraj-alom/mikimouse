package com.mikimouse.operator

import kotlinx.serialization.Serializable

@Serializable
enum class ToolRisk { LOW, MEDIUM, HIGH }

@Serializable
data class ToolDescriptor(
    val name: String,
    val description: String,
    val permissions: List<String>,
    val resourceCostMB: Int,
    val needsNetwork: Boolean,
    val risk: ToolRisk,
    val workspaceOnly: Boolean = true,
)

class ToolRegistry {
    private val tools = linkedMapOf<String, ToolDescriptor>()

    init {
        register(ToolDescriptor("read_input", "Read an imported input file", listOf("workspace.read"), 1, false, ToolRisk.LOW))
        register(ToolDescriptor("write_workspace", "Write a workspace scratch file", listOf("workspace.write"), 5, false, ToolRisk.LOW))
        register(ToolDescriptor("media_inspect", "Probe image/video dimensions/codec", listOf("workspace.read"), 2, false, ToolRisk.LOW))
        register(ToolDescriptor("run_media_pipeline", "Render timeline to mp4 with streaming", listOf("workspace.read", "workspace.write"), 200, false, ToolRisk.LOW))
        register(ToolDescriptor("render_document", "Render PPTX/PDF/DOCX/TXT/MD/CSV", listOf("workspace.read", "workspace.write"), 50, false, ToolRisk.LOW))
        register(ToolDescriptor("ocr_image", "Extract text from an image (stub; ML Kit on device in P1)", listOf("workspace.read"), 10, false, ToolRisk.LOW))
        register(ToolDescriptor("image_process", "Resize/crop/convert images", listOf("workspace.read", "workspace.write"), 20, false, ToolRisk.LOW))
        register(ToolDescriptor("audio_process", "Trim/mix audio track", listOf("workspace.read", "workspace.write"), 30, false, ToolRisk.LOW))
        register(ToolDescriptor("archive", "Create/extract ZIP", listOf("workspace.read", "workspace.write"), 20, false, ToolRisk.LOW))
        register(ToolDescriptor("download_to_workspace", "Download URL into workspace (size-capped)", listOf("workspace.write", "network"), 50, true, ToolRisk.MEDIUM))
        register(ToolDescriptor("web_search", "Search the web via configured provider", listOf("network"), 2, true, ToolRisk.MEDIUM))
        register(ToolDescriptor("open_external_app", "Open a target app via intent/deep-link", listOf("intent"), 1, false, ToolRisk.MEDIUM, workspaceOnly = false))
        register(ToolDescriptor("share_artifact", "Share/export finished artifact", listOf("workspace.read", "share"), 2, false, ToolRisk.MEDIUM))
        register(ToolDescriptor("calendar", "Create calendar entry (via provider)", listOf("calendar"), 1, false, ToolRisk.MEDIUM, workspaceOnly = false))
        register(ToolDescriptor("notify", "Post task notification", listOf("notification"), 1, false, ToolRisk.LOW, workspaceOnly = false))
        register(ToolDescriptor("shell", "Restricted shell (allowlisted commands only)", listOf("shell"), 10, false, ToolRisk.HIGH))
        register(ToolDescriptor("video_trim", "Trim video segment (start/end)", listOf("workspace.read", "workspace.write"), 100, false, ToolRisk.LOW))
        register(ToolDescriptor("image_resize", "Resize image to target dimensions", listOf("workspace.read", "workspace.write"), 15, false, ToolRisk.LOW))
        register(ToolDescriptor("image_convert", "Convert image format (jpg/png/webp)", listOf("workspace.read", "workspace.write"), 10, false, ToolRisk.LOW))
        register(ToolDescriptor("audio_trim", "Trim audio segment", listOf("workspace.read", "workspace.write"), 30, false, ToolRisk.LOW))
        register(ToolDescriptor("audio_mix", "Mix multiple audio tracks", listOf("workspace.read", "workspace.write"), 40, false, ToolRisk.LOW))
        register(ToolDescriptor("merge_pdf", "Merge multiple PDFs", listOf("workspace.read", "workspace.write"), 30, false, ToolRisk.LOW))
        register(ToolDescriptor("split_pdf", "Split PDF by pages", listOf("workspace.read", "workspace.write"), 30, false, ToolRisk.LOW))
        register(ToolDescriptor("text_to_speech", "Convert text to audio", listOf("workspace.read", "workspace.write"), 20, false, ToolRisk.LOW))
        register(ToolDescriptor("speech_to_text", "Transcribe audio to text", listOf("workspace.read", "workspace.write"), 30, false, ToolRisk.LOW))
        register(ToolDescriptor("browser_open", "Open URL in browser", listOf("network", "intent"), 2, true, ToolRisk.MEDIUM, workspaceOnly = false))
        register(ToolDescriptor("email_send", "Send email with attachment", listOf("network", "share"), 5, true, ToolRisk.MEDIUM, workspaceOnly = false))
        register(ToolDescriptor("sms_send", "Send SMS message", listOf("network", "intent"), 2, false, ToolRisk.MEDIUM, workspaceOnly = false))
        register(ToolDescriptor("file_copy", "Copy file to new location", listOf("workspace.read", "workspace.write"), 5, false, ToolRisk.LOW))
        register(ToolDescriptor("file_move", "Move file", listOf("workspace.read", "workspace.write"), 5, false, ToolRisk.LOW))
        register(ToolDescriptor("file_delete", "Delete workspace file", listOf("workspace.write"), 1, false, ToolRisk.MEDIUM))
        register(ToolDescriptor("workspace_list", "List workspace files", listOf("workspace.read"), 1, false, ToolRisk.LOW))
    }

    fun register(d: ToolDescriptor) { tools[d.name] = d }
    fun get(name: String): ToolDescriptor? = tools[name]
    fun all(): List<ToolDescriptor> = tools.values.toList()
    fun isAllowed(name: String, allowlist: Set<String>): Boolean = tools.containsKey(name) && name in allowlist
}
