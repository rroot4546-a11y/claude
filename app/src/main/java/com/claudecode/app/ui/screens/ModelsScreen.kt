package com.claudecode.app.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.claudecode.app.ui.state.ModelsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelsScreen(vm: ModelsViewModel = viewModel()) {
    val ui by vm.ui.collectAsState()
    LaunchedEffect(Unit) { vm.refresh() }
    var showUrl by remember { mutableStateOf(false) }
    var urlText by remember { mutableStateOf("") }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) vm.importUri(uri)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Local GGUF models", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = { vm.refresh() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
                    }
                },
            )
        },
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).padding(16.dp).verticalScroll(rememberScrollState())) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            ) {
                Column(Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        StatusDot(ui.runtimeInstalled)
                        Spacer(Modifier.width(8.dp))
                        Text("llama.cpp runtime", fontWeight = FontWeight.Bold)
                    }
                    Text(
                        if (ui.runtimeInstalled) "Installed (${ui.runtimeVersion}) — ${if (ui.serverRunning) "server live on :18925" else "server stopped"}"
                        else "Not installed — one-time download (~78MB)",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    Row {
                        if (!ui.runtimeInstalled) {
                            Button(onClick = { vm.installRuntime() }, enabled = ui.busy == null) {
                                Text("Install runtime")
                            }
                        } else if (ui.serverRunning) {
                            OutlinedButton(onClick = { vm.stopServer() }, enabled = ui.busy == null) {
                                Text("Stop server & unlink")
                            }
                        }
                    }
                }
            }
            Gap()
            if (ui.busy != null) {
                Text(ui.busy!!, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                if (ui.progress >= 0) {
                    LinearProgressIndicator(
                        progress = { ui.progress / 100f },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    )
                }
                if (ui.progressText.isNotEmpty()) Text(ui.progressText, fontSize = 12.sp)
                Gap()
            }
            Row {
                Button(
                    onClick = { picker.launch("*/*") },
                    enabled = ui.busy == null,
                    modifier = Modifier.weight(1f),
                ) { Text("+ From files") }
                Spacer(Modifier.width(8.dp))
                OutlinedButton(
                    onClick = { showUrl = true },
                    enabled = ui.busy == null,
                    modifier = Modifier.weight(1f),
                ) { Text("+ From URL") }
            }
            Gap(4)
            Row {
                OutlinedButton(
                    onClick = { vm.downloadStarter() },
                    enabled = ui.busy == null,
                    modifier = Modifier.weight(1f),
                ) { Text("Starter model (~400MB)", fontSize = 12.sp) }
                Spacer(Modifier.width(8.dp))
                OutlinedButton(
                    onClick = { vm.testServer() },
                    enabled = ui.busy == null,
                    modifier = Modifier.weight(1f),
                ) { Text("Test server", fontSize = 12.sp) }
            }
            if (showUrl) {
                Gap()
                OutlinedTextField(
                    value = urlText,
                    onValueChange = { urlText = it },
                    label = { Text("Direct .gguf link (or HuggingFace /resolve/)") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 3,
                )
                Gap(4)
                Row {
                    Button(onClick = {
                        showUrl = false
                        vm.downloadUrl(urlText)
                        urlText = ""
                    }) { Text("Download") }
                    Spacer(Modifier.width(8.dp))
                    OutlinedButton(onClick = { showUrl = false }) { Text("Cancel") }
                }
            }
            Gap()
            SectionTitle("Library (${ui.models.size})")
            if (ui.models.isEmpty() && !ui.loading) {
                Text(
                    "No models yet. Tap Starter model for a one-tap ~400MB download.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            ui.models.forEach { m ->
                val active = m.id == ui.activeId
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                        else MaterialTheme.colorScheme.surfaceVariant,
                    ),
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            StatusDot(active && ui.serverRunning, warn = active && !ui.serverRunning)
                            Spacer(Modifier.width(8.dp))
                            Column(Modifier.weight(1f)) {
                                Text(m.fileName, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text(
                                    "${com.claudecode.app.feature.localai.GgufMeta.formatSize(m.sizeBytes)} · ${com.claudecode.app.feature.localai.GgufMeta.inferQuantization(m.fileName)} · ${m.source}" + if (active) " · ★ active" else "",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            IconButton(onClick = { vm.delete(m.id) }, enabled = ui.busy == null) {
                                Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                        if (!active) {
                            Spacer(Modifier.height(6.dp))
                            Button(
                                onClick = { vm.activate(m.id) },
                                enabled = ui.busy == null && ui.runtimeInstalled,
                            ) {
                                Icon(Icons.Filled.PlayArrow, contentDescription = null)
                                Spacer(Modifier.width(4.dp))
                                Text("Activate & link")
                            }
                        }
                    }
                }
            }
        }
    }
    if (ui.notice != null) {
        NoticeDialog(ui.notice!!) { vm.dismissNotice() }
    }
}
