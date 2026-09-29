package com.claudecode.app.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
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
import com.claudecode.app.BuildConfig
import com.claudecode.app.core.paths.AppPaths
import com.claudecode.app.ui.state.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: SettingsViewModel = viewModel()) {
    val ui by vm.ui.collectAsState()
    LaunchedEffect(Unit) { vm.refresh() }
    val ctxOptions = listOf(2048, 4096, 8192, 16384)

    Scaffold(
        topBar = { TopAppBar(title = { Text("Settings", fontWeight = FontWeight.Bold) }) },
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).padding(16.dp).verticalScroll(rememberScrollState())) {
            SectionTitle("Claude API")
            OutlinedTextField(
                value = ui.claudeKey,
                onValueChange = { vm.setClaudeKey(it) },
                label = { Text("Anthropic API key (sk-ant-…)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
            )
            Text(
                "Stored only on this device. Get one at console.anthropic.com.",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Gap(4)
            Text("Model", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Row {
                vm.models.forEach { m ->
                    val short = m.removePrefix("claude-")
                    Button(
                        onClick = { vm.setClaudeModel(m) },
                        modifier = Modifier.weight(1f).padding(horizontal = 2.dp),
                        enabled = m != ui.claudeModel,
                    ) { Text(short.take(12), fontSize = 11.sp, maxLines = 1) }
                }
            }
            GenSlider("Max tokens", ui.claudeMax.toFloat(), 1024f, 16384f, 1024f) { vm.setClaudeMax(it.toInt()) }
            Gap()
            SectionTitle("Context size (tokens)")
            Row {
                ctxOptions.forEach { c ->
                    Button(
                        onClick = { vm.setCtx(c) },
                        modifier = Modifier.weight(1f).padding(horizontal = 2.dp),
                        enabled = c != ui.ctx,
                    ) { Text(if (c >= 1024) "${c / 1024}k" else "$c", fontSize = 12.sp) }
                }
            }
            Text("Current: ${ui.ctx} — applies on next activation", fontSize = 11.sp)
            Gap()
            SectionTitle("Worker threads: ${ui.threads}")
            Slider(
                value = ui.threads.toFloat(),
                onValueChange = { vm.setThreads(it.toInt().coerceAtLeast(1)) },
                valueRange = 1f..ui.maxThreads.toFloat(),
                steps = (ui.maxThreads - 1).coerceAtLeast(0),
                modifier = Modifier.fillMaxWidth(),
            )
            Gap()
            SectionTitle("Generation (pocketbrain merge)")
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    Text("Show thinking blocks", fontWeight = FontWeight.Bold)
                    Text(
                        "Display <think> reasoning, else strip it",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = ui.thinking, onCheckedChange = { vm.setThinking(it) })
            }
            GenSlider("Temperature", ui.temperature, 0f, 2f, 0.05f) { vm.setTemperature(it) }
            GenSlider("Top-p", ui.topP, 0f, 1f, 0.05f) { vm.setTopP(it) }
            GenSlider("Top-k", ui.topK, 1f, 100f, 1f) { vm.setTopK(it) }
            GenSlider("Min-p", ui.minP, 0f, 1f, 0.01f) { vm.setMinP(it) }
            GenSlider("Repeat penalty", ui.repeatPenalty, 1f, 1.5f, 0.05f) { vm.setRepeatPenalty(it) }
            GenSlider("Max tokens", ui.maxTokens.toFloat(), 256f, 8192f, 256f) { vm.setMaxTokens(it.toInt()) }
            Gap()
            SectionTitle("Info")
            InfoLine("App version", BuildConfig.VERSION_NAME)
            InfoLine("Local API", AppPaths.LLAMA_BASE_URL)
            InfoLine("Ports", "local GGUF ${AppPaths.LLAMA_PORT}")
            InfoLine("Build", "targetSdk 28 (native exec) · minSdk 26")
            Spacer(Modifier.height(8.dp))
            Text(
                "Context/thread changes apply on next model activation.",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun GenSlider(
    label: String,
    value: Float,
    min: Float,
    max: Float,
    step: Float,
    onChange: (Float) -> Unit,
) {
    val steps = ((max - min) / step).toInt().coerceAtLeast(1) - 1
    Column(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text("$label: ${if (value >= 10) value.toInt().toString() else String.format("%.2f", value)}", fontSize = 12.sp)
        Slider(
            value = value.coerceIn(min, max),
            onValueChange = onChange,
            valueRange = min..max,
            steps = steps.coerceAtLeast(0),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun InfoLine(k: String, v: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text("$k: ", fontWeight = FontWeight.Bold, fontSize = 12.sp)
        Text(v, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
