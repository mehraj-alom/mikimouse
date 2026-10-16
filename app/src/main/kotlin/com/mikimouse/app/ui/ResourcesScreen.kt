package com.mikimouse.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.mikimouse.app.data.SettingsStore
import com.mikimouse.app.runtime.ResourceMonitor
import com.mikimouse.operator.CapabilityDetector
import com.mikimouse.operator.DeviceCaps
import java.io.File

@Composable
fun ResourcesScreen(operatorRoot: File) {
    val ctx = LocalContext.current
    var snap by remember { mutableStateOf<DeviceCaps?>(null) }
    var wsMB by remember { mutableStateOf(0L) }
    LaunchedEffect(Unit) {
        snap = ResourceMonitor.snapshot(ctx)
        wsMB = ResourceMonitor.workspaceSizeMB(File(operatorRoot, "tasks"))
    }
    val s = snap
    GlassCard(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Resource dashboard", style = MaterialTheme.typography.titleMedium)
            if (s == null) {
                Text("Reading…", style = MaterialTheme.typography.bodySmall)
            } else {
                Bar("RAM total", s.ramMB.toDouble(), 12000.0, "${s.ramMB} MB")
                Bar("Cores", s.cpuCores.toDouble(), 8.0, "${s.cpuCores} · ${s.cpuArch}")
                Bar("Free storage", s.freeStorageMB.toDouble(), 20000.0, "${s.freeStorageMB} MB")
                Bar("Workspace", wsMB.toDouble(), 5120.0, "$wsMB MB")
                Text("Android SDK ${s.sdkInt}", style = MaterialTheme.typography.bodySmall)
                val rec = CapabilityDetector.recommend(s)
                Text("Model pick: ${rec.params}", style = MaterialTheme.typography.bodySmall)
                Text(rec.reason, style = MaterialTheme.typography.bodySmall)
            }
            val settings = remember { SettingsStore(ctx) }
            Text(
                "Budget: RAM target ${settings.ramTargetMB} MB · quota ${settings.storageQuotaMB} MB · " +
                    "${settings.cpu} · net ${settings.network}",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun Bar(label: String, used: Double, max: Double, text: String) {
    Column {
        Text("$label — $text", style = MaterialTheme.typography.bodySmall)
        LinearProgressIndicator(
            progress = { (used / max).toFloat().coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
