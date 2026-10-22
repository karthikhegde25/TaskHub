// SPDX-License-Identifier: GPL-3.0-only
// TaskHub, Copyright (C) 2026 Karthik Hegde. Licensed under the GNU General Public License v3.0; see LICENSE.

package com.kARThik.tasks.ui.common

import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.kARThik.tasks.ui.theme.PriorityColors
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

/** Colors offered for projects and tags. */
val ColorChoices: List<Long> = listOf(
    0xFF6750A4, 0xFFE53935, 0xFFFB8C00, 0xFFFDD835, 0xFF43A047,
    0xFF00897B, 0xFF1E88E5, 0xFF3949AB, 0xFFD81B60, 0xFF6D4C41,
)

@Composable
fun ColorPickerRow(selected: Long, onSelect: (Long) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ColorChoices.forEach { c ->
            Spacer(
                Modifier.size(if (c == selected) 30.dp else 24.dp)
                    .clip(CircleShape)
                    .background(Color(c))
                    .clickable { onSelect(c) },
            )
        }
    }
}

/** Label (tag) colours keyed by lower-cased name; provided once at the app root. */
val LocalTagColors = androidx.compose.runtime.compositionLocalOf<Map<String, Long>> { emptyMap() }

fun labelColor(map: Map<String, Long>, name: String): Color = Color(map[name.lowercase()] ?: 0xFF625B71)

/** Row of small coloured circles, one per label. */
@Composable
fun LabelDots(tags: List<String>, modifier: Modifier = Modifier) {
    if (tags.isEmpty()) return
    val colors = LocalTagColors.current
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        tags.take(8).forEach { Spacer(Modifier.size(9.dp).clip(CircleShape).background(labelColor(colors, it))) }
    }
}

@Composable
fun PriorityDot(priority: Int, modifier: Modifier = Modifier) {
    val c = PriorityColors.of(priority) ?: return
    Icon(Icons.Default.Flag, PriorityColors.name(priority), modifier.size(16.dp), tint = c)
}

@Composable
fun EmptyState(icon: ImageVector, title: String, hint: String = "", modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, null, Modifier.size(56.dp), tint = MaterialTheme.colorScheme.outline)
        Spacer(Modifier.height(12.dp))
        Text(title, style = MaterialTheme.typography.titleMedium)
        if (hint.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(hint, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Generic list card used by the supporting screens. */
@Composable
fun ItemCard(
    title: String,
    subtitle: String? = null,
    done: Boolean = false,
    priority: Int = 0,
    onClick: () -> Unit = {},
    leading: @Composable () -> Unit = {},
    trailing: @Composable RowScope.() -> Unit = {},
    tags: List<String> = emptyList(),
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            leading()
            Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
                Text(
                    title,
                    style = MaterialTheme.typography.bodyLarge,
                    textDecoration = if (done) TextDecoration.LineThrough else null,
                    color = if (done) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface,
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(subtitle, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                LabelDots(tags, Modifier.padding(top = 4.dp))
            }
            PriorityDot(priority)
            trailing()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatePickerDialogX(initial: LocalDate?, onPick: (LocalDate) -> Unit, onDismiss: () -> Unit) {
    val st = rememberDatePickerState(
        initialSelectedDateMillis = (initial ?: LocalDate.now()).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                st.selectedDateMillis?.let { onPick(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
            }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    ) { DatePicker(state = st) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimePickerDialogX(initial: LocalTime?, onPick: (LocalTime) -> Unit, onDismiss: () -> Unit) {
    val is24 = DateFormat.is24HourFormat(LocalContext.current)
    val st = rememberTimePickerState(initial?.hour ?: 9, initial?.minute ?: 0, is24)
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = { onPick(LocalTime.of(st.hour, st.minute)) }) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        text = { TimePicker(state = st) },
    )
}
