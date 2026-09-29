package com.claudecode.app.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Scaffold
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.claudecode.app.ui.state.LocalChatViewModel
import kotlinx.coroutines.launch

@Composable
fun LocalChatScreen(
    vm: LocalChatViewModel = viewModel(),
    onOpenModels: () -> Unit = {},
    onOpenEngine: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
) {
    val ui by vm.ui.collectAsState()
    LaunchedEffect(Unit) { vm.refresh() }
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    LaunchedEffect(ui.messages.size) {
        if (ui.messages.isNotEmpty()) listState.animateScrollToItem(ui.messages.size - 1)
    }
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) vm.addAttachment(uri)
    }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) vm.addAttachment(uri)
    }

    GeminiDrawer(
        drawerState = drawer,
        scope = scope,
        onNewChat = { vm.clear() },
        onOpenModels = onOpenModels,
        onOpenEngines = onOpenEngine,
        onOpenSettings = onOpenSettings,
    ) {
        Scaffold(
            topBar = {
                GeminiTopBar(
                    currentLabel = ui.modelId ?: "Pick a model",
                    options = ui.options,
                    onPick = { vm.pickModel(it) },
                    onManageModels = onOpenModels,
                    onMenu = { scope.launch { drawer.open() } },
                    nestAction = {
                        androidx.compose.material3.TextButton(onClick = { vm.toggleThinking() }) {
                            androidx.compose.material3.Text(
                                "Thinking",
                                fontSize = 12.sp,
                                fontWeight = if (ui.showThinking) androidx.compose.ui.text.font.FontWeight.Bold
                                else androidx.compose.ui.text.font.FontWeight.Normal,
                                color = if (ui.showThinking) androidx.compose.ui.graphics.Color(0xFF3B82F6)
                                else androidx.compose.ui.graphics.Color(0xFF5B6472),
                            )
                        }
                    },
                )
            },
            bottomBar = {
                GeminiPill(
                    busy = ui.busy,
                    streaming = ui.streaming,
                    attachments = ui.attachments,
                    onRemoveAttachment = { vm.removeAttachment(it) },
                    onPickPhoto = { photoPicker.launch("image/*") },
                    onPickFile = { filePicker.launch("*/*") },
                    onSend = { vm.send(it) },
                    onStop = { vm.stop() },
                    hint = "Ask locally",
                )
            },
        ) { pad ->
            Column(Modifier.fillMaxSize().padding(pad)) {
                if (ui.messages.isEmpty()) {
                    GeminiGreeting()
                } else {
                    Spacer(Modifier.height(4.dp))
                    LazyColumn(state = listState, modifier = Modifier.weight(1f)) {
                        items(ui.messages) { m ->
                            if (m.role == "user") UserBubble(m.text) else AssistantRow(m.text)
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
