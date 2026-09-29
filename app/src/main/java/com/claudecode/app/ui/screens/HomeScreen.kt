package com.claudecode.app.ui.screens

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
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.claudecode.app.ui.state.HomeViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    vm: HomeViewModel = viewModel(),
    onOpenTab: (String) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val ui by vm.ui.collectAsState()
    LaunchedEffect(Unit) { vm.refresh() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Claude Code", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = { vm.refresh() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = "Settings")
                    }
                },
            )
        },
    ) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).padding(16.dp).verticalScroll(rememberScrollState()),
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        StatusDot(ui.hasKey)
                        Spacer(Modifier.width(8.dp))
                        Text("Claude agent", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        if (ui.hasKey) "${ui.sessions} saved session(s) — ready to code"
                        else "Add your Anthropic API key in Settings to start",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(10.dp))
                    Row {
                        Button(onClick = { onOpenTab("agent") }) { Text("New task") }
                        Spacer(Modifier.width(8.dp))
                        OutlinedButton(onClick = { onOpenTab("chat") }) { Text("Local chat") }
                    }
                }
            }
            Gap()
            SectionTitle("On-device")
            EngineRow(
                name = "Local AI (GGUF)",
                desc = if (ui.llamaRunning) "Serving: ${ui.activeModel ?: "?"}" else "Stopped",
                running = ui.llamaRunning,
                actionLabel = "Models",
                onAction = { onOpenTab("models") },
            )
            Gap()
            SectionTitle("Shortcuts")
            Row {
                OutlinedButton(onClick = { onOpenTab("agent") }, modifier = Modifier.weight(1f)) {
                    Text("Agent chat")
                }
                Spacer(Modifier.width(8.dp))
                OutlinedButton(onClick = { onOpenTab("models") }, modifier = Modifier.weight(1f)) {
                    Text("+ GGUF model")
                }
            }
            if (ui.loading) {
                Gap()
                Text("Checking…", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
