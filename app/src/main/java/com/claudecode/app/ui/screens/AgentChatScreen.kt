package com.claudecode.app.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.claudecode.app.ui.state.AgentChatViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgentChatScreen(
    vm: AgentChatViewModel = viewModel(),
    onOpenSettings: () -> Unit = {},
) {
    val ui by vm.ui.collectAsState()
    LaunchedEffect(Unit) { vm.refresh() }
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    LaunchedEffect(ui.rows.size) {
        if (ui.rows.isNotEmpty()) listState.animateScrollToItem(ui.rows.size - 1)
    }

    GeminiDrawer(
        drawerState = drawer,
        scope = scope,
        onNewChat = { vm.newSession() },
        onOpenModels = {},
        onOpenEngines = {},
        onOpenSettings = onOpenSettings,
        extra = {
            if (ui.sessions.isNotEmpty()) {
                Text("  Sessions", modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
                ui.sessions.take(20).forEach { s ->
                    NavigationDrawerItem(
                        label = { Text((if (s.id == ui.sessionId) "● " else "") + s.title.take(34), maxLines = 1) },
                        selected = s.id == ui.sessionId,
                        onClick = {
                            scope.launch { drawer.close() }
                            vm.openSession(s.id)
                        },
                    )
                }
            }
        },
    ) {
        Scaffold(
            topBar = {
                GeminiTopBar(
                    currentLabel = "Claude agent",
                    options = emptyList(),
                    onPick = {},
                    onManageModels = {},
                    onMenu = { scope.launch { drawer.open() } },
                    nestAction = {
                        IconButton(onClick = { vm.newSession() }) {
                            Icon(Icons.Filled.Add, contentDescription = "New session")
                        }
                    },
                )
            },
            bottomBar = {
                GeminiPill(
                    busy = ui.busy,
                    streaming = ui.busy,
                    attachments = emptyList(),
                    onRemoveAttachment = {},
                    onPickPhoto = { vm.soon() },
                    onPickFile = { vm.soon() },
                    onSend = { vm.send(it) },
                    onStop = { vm.stop() },
                    hint = "Ask Claude to code…",
                )
            },
        ) { pad ->
            Column(Modifier.fillMaxSize().padding(pad)) {
                if (!ui.hasKey) {
                    Text(
                        "Add your Anthropic API key in Settings to enable the agent.",
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(16.dp),
                    )
                }
                if (ui.status.isNotEmpty()) {
                    Text(
                        ui.status,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 20.dp),
                    )
                }
                if (ui.rows.isEmpty()) {
                    GeminiGreeting(name = "coder")
                } else {
                    Spacer(Modifier.height(4.dp))
                    LazyColumn(state = listState, modifier = Modifier.weight(1f)) {
                        items(ui.rows) { r ->
                            when (r) {
                                is AgentChatViewModel.Row.User -> UserBubble(r.text)
                                is AgentChatViewModel.Row.Assistant -> AssistantRow(r.text)
                                is AgentChatViewModel.Row.Tool -> ToolRow(r.name, r.summary)
                                is AgentChatViewModel.Row.ToolOut -> ToolOutRow(r.name, r.text)
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

@Composable
private fun ToolRow(name: String, summary: String) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 3.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Text("🔧 $name", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
            if (summary.isNotBlank()) {
                Text(summary, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun ToolOutRow(name: String, text: String) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 2.dp)) {
        androidx.compose.foundation.text.selection.SelectionContainer {
            Text(
                (if (name.isNotBlank()) "✓ $name\n" else "") + text.take(800),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    .padding(8.dp),
            )
        }
    }
    Spacer(Modifier.width(0.dp))
}
