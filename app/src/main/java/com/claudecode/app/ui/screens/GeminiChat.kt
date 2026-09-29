package com.claudecode.app.ui.screens

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.claudecode.app.feature.chat.Attachments
import kotlinx.coroutines.launch

/** One entry in the top model picker. */
data class ModelOption(
    val id: String,
    val label: String,
    val subtitle: String,
    val isLocal: Boolean,
)

val GeminiSparkle = Brush.linearGradient(
    colors = listOf(Color(0xFF4796E3), Color(0xFF57C785), Color(0xFFFFC857), Color(0xFFFF7A59)),
)

@Composable
fun SparkleMark(size: Int = 34) {
    Text("✦", style = TextStyle(brush = GeminiSparkle, fontSize = size.sp))
}

@Composable
fun AvatarZ() {
    Box(
        Modifier.size(34.dp).clip(CircleShape).background(Color(0xFF7CB342)),
        contentAlignment = Alignment.Center,
    ) {
        Text("Z", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 17.sp)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GeminiTopBar(
    currentLabel: String,
    options: List<ModelOption>,
    onPick: (ModelOption) -> Unit,
    onManageModels: () -> Unit,
    onMenu: () -> Unit,
    nestAction: @Composable (() -> Unit)? = null,
) {
    var expanded by remember { mutableStateOf(false) }
    TopAppBar(
        navigationIcon = {
            IconButton(onClick = onMenu) { Icon(Icons.Filled.Menu, contentDescription = "Menu") }
        },
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable { expanded = true },
            ) {
                Text(currentLabel, fontWeight = FontWeight.Medium, fontSize = 18.sp, maxLines = 1)
                Icon(Icons.Filled.ArrowDropDown, contentDescription = "Pick model")
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                if (options.isEmpty()) {
                    DropdownMenuItem(
                        text = { Text("No models yet — add one") },
                        onClick = {
                            expanded = false
                            onManageModels()
                        },
                    )
                } else {
                    options.forEach { o ->
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(o.label, fontWeight = FontWeight.SemiBold)
                                    Text(
                                        (if (o.isLocal) "Local · " else "") + o.subtitle,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            },
                            onClick = {
                                expanded = false
                                onPick(o)
                            },
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("Manage models…") },
                        onClick = {
                            expanded = false
                            onManageModels()
                        },
                    )
                }
            }
        },
        actions = {
            nestAction?.invoke()
            AvatarZ()
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
    )
}

@Composable
fun GeminiGreeting(name: String = "Zozo") {
    Column(
        Modifier.fillMaxWidth().padding(top = 90.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        SparkleMark(44)
        Spacer(Modifier.height(14.dp))
        Text("Your move, $name!", fontSize = 30.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun AssistantRow(text: String) {
    androidx.compose.foundation.text.selection.SelectionContainer {
        Text(
            text.ifBlank { "…" },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp),
            fontSize = 15.sp,
            lineHeight = 22.sp,
        )
    }
}

@Composable
fun UserBubble(text: String) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), horizontalArrangement = Arrangement.End) {
        Box(
            Modifier.clip(RoundedCornerShape(22.dp)).background(Color(0xFF1E2532)).padding(horizontal = 16.dp, vertical = 10.dp),
        ) {
            Text(text, fontSize = 15.sp)
        }
    }
}

@Composable
fun AttachmentChips(items: List<Attachments.Item>, onRemove: (Int) -> Unit) {
    if (items.isEmpty()) return
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
        items.forEachIndexed { i, a ->
            Row(
                Modifier.clip(RoundedCornerShape(16.dp)).background(Color(0xFF1E2532)).padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text((if (a.isImage) "🖼 " else "📄 ") + a.name.take(18), fontSize = 12.sp, maxLines = 1)
                Spacer(Modifier.width(4.dp))
                IconButton(onClick = { onRemove(i) }, modifier = Modifier.size(20.dp)) {
                    Icon(Icons.Filled.Close, contentDescription = "Remove", modifier = Modifier.size(14.dp))
                }
            }
            Spacer(Modifier.width(6.dp))
        }
    }
}

/**
 * Gemini pill composer: [+] attach · text field · mic · blue send/stop button.
 */
@Composable
fun GeminiPill(
    busy: Boolean,
    streaming: Boolean,
    attachments: List<Attachments.Item>,
    onRemoveAttachment: (Int) -> Unit,
    onPickPhoto: () -> Unit,
    onPickFile: () -> Unit,
    onSend: (String) -> Unit,
    onStop: () -> Unit,
    hint: String,
) {
    var value by remember { mutableStateOf("") }
    var attachOpen by remember { mutableStateOf(false) }
    val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        if (res.resultCode == Activity.RESULT_OK) {
            val heard = res.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!heard.isNullOrBlank()) value = (if (value.isBlank()) "" else "$value ") + heard
        }
    }

    Column {
        AttachmentChips(attachments, onRemoveAttachment)
        Box(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp)
                .clip(RoundedCornerShape(28.dp)).background(Color(0xFF1E2532)).padding(horizontal = 6.dp, vertical = 6.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { attachOpen = true }) {
                    Icon(Icons.Filled.Add, contentDescription = "Attach")
                }
                DropdownMenu(expanded = attachOpen, onDismissRequest = { attachOpen = false }) {
                    DropdownMenuItem(text = { Text("Upload photo") }, onClick = { attachOpen = false; onPickPhoto() })
                    DropdownMenuItem(text = { Text("Upload file") }, onClick = { attachOpen = false; onPickFile() })
                }
                TextField(
                    value = value,
                    onValueChange = { value = it },
                    placeholder = { Text(hint, color = Color(0xFF8E99AB)) },
                    modifier = Modifier.weight(1f),
                    maxLines = 5,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                    ),
                )
                IconButton(onClick = {
                    try {
                        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                        }
                        micLauncher.launch(intent)
                    } catch (_: Exception) {
                    }
                }) {
                    Icon(Icons.Filled.Mic, contentDescription = "Voice", tint = Color(0xFFB0BACB))
                }
                if (streaming) {
                    IconButton(
                        onClick = onStop,
                        modifier = Modifier.size(44.dp).clip(CircleShape).background(Color(0xFF3B82F6)),
                    ) {
                        Icon(Icons.Filled.Stop, contentDescription = "Stop", tint = Color.White)
                    }
                } else {
                    IconButton(
                        onClick = {
                            val t = value.trim()
                            if (t.isNotEmpty() || attachments.isNotEmpty()) {
                                value = ""
                                onSend(t)
                            }
                        },
                        enabled = !busy,
                        modifier = Modifier.size(44.dp).clip(CircleShape).background(Color(0xFF3B82F6)),
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send", tint = Color.White)
                    }
                }
            }
        }
    }
}

@Composable
fun GeminiDrawer(
    drawerState: androidx.compose.material3.DrawerState,
    scope: kotlinx.coroutines.CoroutineScope,
    onNewChat: () -> Unit,
    onOpenModels: () -> Unit,
    onOpenEngines: () -> Unit,
    onOpenSettings: () -> Unit,
    extra: @Composable (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    fun closeAnd(run: () -> Unit) {
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch { drawerState.close() }
        run()
    }
    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Spacer(Modifier.height(12.dp))
                Text("  zclaw", fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.padding(12.dp))
                NavigationDrawerItem(label = { Text("New chat") }, selected = false, onClick = { closeAnd(onNewChat) })
                extra?.invoke()
                NavigationDrawerItem(label = { Text("Models") }, selected = false, onClick = { closeAnd(onOpenModels) })
                NavigationDrawerItem(label = { Text("Agent") }, selected = false, onClick = { closeAnd(onOpenEngines) })
                NavigationDrawerItem(label = { Text("Settings") }, selected = false, onClick = { closeAnd(onOpenSettings) })
            }
        },
    ) { content() }
}
