package com.claudecode.app.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun StatusDot(on: Boolean, warn: Boolean = false) {
    val c = when {
        on -> Color(0xFF34D399)
        warn -> Color(0xFFFBBF24)
        else -> Color(0xFF64748B)
    }
    Box(Modifier.size(10.dp).clip(CircleShape).background(c))
}

@Composable
fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(vertical = 8.dp),
    )
}

@Composable
fun EngineRow(
    name: String,
    desc: String,
    running: Boolean,
    warn: Boolean = false,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    secondLabel: String? = null,
    onSecond: (() -> Unit)? = null,
    busy: Boolean = false,
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StatusDot(running, warn)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(name, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Text(desc, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (busy) {
                CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
            } else {
                if (secondLabel != null && onSecond != null) {
                    TextButton(onClick = onSecond) { Text(secondLabel, fontSize = 12.sp) }
                }
                if (actionLabel != null && onAction != null) {
                    Spacer(Modifier.width(4.dp))
                    Button(onClick = onAction) { Text(actionLabel, fontSize = 12.sp) }
                }
            }
        }
    }
}

@Composable
fun NoticeDialog(text: String, onDismiss: () -> Unit) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } },
        text = { Text(text) },
    )
}

@Composable
fun Gap(h: Int = 8) = Spacer(Modifier.height(h.dp))
