// SPDX-License-Identifier: GPL-3.0-only
// TaskHub, Copyright (C) 2026 Karthik Hegde. Licensed under the GNU General Public License v3.0; see LICENSE.

package com.kARThik.tasks.ui.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Done
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kARThik.tasks.domain.ChecklistItem
import com.kARThik.tasks.domain.Freq
import com.kARThik.tasks.ui.common.ColorChoices
import com.kARThik.tasks.ui.common.ColorPickerRow
import com.kARThik.tasks.ui.common.DatePickerDialogX
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import com.kARThik.tasks.ui.common.TimePickerDialogX
import com.kARThik.tasks.ui.theme.PriorityColors
import java.time.DayOfWeek
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale

private val dateFmt = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
private val timeFmt = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EditorScreen(onBack: () -> Unit, vm: EditorViewModel = hiltViewModel()) {
    val projects by vm.projects.collectAsStateWithLifecycle()
    var pickDate by remember { mutableStateOf(false) }
    var pickTime by remember { mutableStateOf(false) }
    var newReminder by remember { mutableStateOf("") }
    val allTags by vm.allTags.collectAsStateWithLifecycle()
    var newLabel by remember { mutableStateOf(false) }
    var smart by remember { mutableStateOf("") }
    var confirmDelete by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (vm.isNew) (if (vm.isNote) "New note" else "New task") else if (vm.isNote) "Edit note" else "Edit task") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = {
                    if (!vm.isNew) IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Default.Delete, "Delete") }
                    IconButton(onClick = { vm.save(onBack) }, enabled = vm.title.isNotBlank()) { Icon(Icons.Default.Done, "Save") }
                },
            )
        },
    ) { pad ->
        if (confirmDelete) {
            val what = if (vm.isNote) "note" else "task"
            AlertDialog(
                onDismissRequest = { confirmDelete = false },
                title = { Text("Delete $what?") },
                text = {
                    Text(
                        if (vm.isNote) "This note will be permanently deleted."
                        else "This task will be permanently deleted. If it repeats, upcoming repeats are removed too.",
                    )
                },
                confirmButton = {
                    TextButton(onClick = { confirmDelete = false; vm.delete(onBack) }) {
                        Text("Delete", color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
            )
        }
        Column(
            Modifier.fillMaxSize().padding(pad).imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (vm.isNew) {
                // Optional smart fill: type naturally, tap Apply, then fine-tune the fields below.
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    OutlinedTextField(
                        smart, { smart = it },
                        label = { Text("Smart fill: tomorrow 5pm p1 @home #Work !30m") },
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(enabled = smart.isNotBlank(), onClick = { vm.applySmartText(smart); smart = "" }) { Text("Apply") }
                }
            }
            OutlinedTextField(vm.title, { vm.title = it }, label = { Text("Title") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(vm.description, { vm.description = it }, label = { Text("Description") },
                minLines = 2, modifier = Modifier.fillMaxWidth())

            Label("When")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { pickDate = true }) { Text(vm.date?.let { dateFmt.format(it) } ?: "No date") }
                OutlinedButton(onClick = { pickTime = true }) { Text(vm.time?.let { timeFmt.format(it) } ?: "All day") }
                if (vm.date != null || vm.time != null) {
                    IconButton(onClick = { vm.date = null; vm.time = null }) { Icon(Icons.Default.Close, "Clear date") }
                }
            }
            OutlinedTextField(
                value = vm.durationMin?.toString() ?: "",
                onValueChange = { v -> vm.durationMin = v.filter { it.isDigit() }.take(4).toIntOrNull() },
                label = { Text("Duration (minutes)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )

            Label("Priority")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PriorityColors.names.forEachIndexed { i, l ->
                    FilterChip(
                        selected = vm.priority == i, onClick = { vm.priority = i },
                        label = { Text(if (i == 0) "None" else l) },
                        leadingIcon = PriorityColors.of(i)?.let { c ->
                            { Icon(Icons.Default.Flag, null, Modifier.size(16.dp), tint = c) }
                        },
                    )
                }
            }

            Label("Labels")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // Saved labels plus any selected ones that have no saved colour yet.
                val names = (allTags.map { it.name } + vm.labels).distinctBy { it.lowercase() }
                names.forEach { n ->
                    val selected = vm.labels.any { it.equals(n, true) }
                    val color = allTags.firstOrNull { it.name.equals(n, true) }?.color ?: 0xFF625B71
                    FilterChip(
                        selected = selected,
                        onClick = { vm.toggleLabel(n) },
                        label = { Text(n) },
                        leadingIcon = {
                            androidx.compose.foundation.layout.Box(
                                Modifier.size(12.dp).background(androidx.compose.ui.graphics.Color(color), androidx.compose.foundation.shape.CircleShape),
                            )
                        },
                    )
                }
                AssistChip(onClick = { newLabel = true }, label = { Text("New label") },
                    leadingIcon = { Icon(Icons.Default.Add, null) })
            }

            Label("Project")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = vm.projectId == null, onClick = { vm.projectId = null }, label = { Text("None") })
                projects.forEach { p ->
                    FilterChip(selected = vm.projectId == p.id, onClick = { vm.projectId = p.id }, label = { Text(p.name) })
                }
            }

            Label("Checklist")
            vm.checklist.forEachIndexed { i, item ->
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Checkbox(item.done, { c -> vm.checklist = vm.checklist.toMutableList().also { it[i] = item.copy(done = c) } })
                    Text(item.text, Modifier.weight(1f))
                    IconButton(onClick = { vm.checklist = vm.checklist.toMutableList().also { it.removeAt(i) } }) {
                        Icon(Icons.Default.Close, "Remove")
                    }
                }
            }
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                OutlinedTextField(
                    vm.checkDraft, { vm.checkDraft = it }, label = { Text("Add checklist item") },
                    singleLine = true, modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Done),
                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { vm.addCheckItem() }),
                )
                IconButton(onClick = { vm.addCheckItem() }, enabled = vm.checkDraft.isNotBlank()) { Icon(Icons.Default.Add, "Add item") }
            }

            if (!vm.isNote) {
                Label("Reminders (minutes before start)")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    vm.reminders.forEach { m ->
                        AssistChip(
                            onClick = { vm.reminders = vm.reminders - m },
                            label = { Text(if (m == 0) "At start" else if (m % 1440 == 0) "${m / 1440}d" else if (m % 60 == 0) "${m / 60}h" else "${m}m") },
                            trailingIcon = { Icon(Icons.Default.Close, null) },
                        )
                    }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(0, 5, 10, 30, 60, 1440).filter { it !in vm.reminders }.forEach { m ->
                        TextButton(onClick = { vm.reminders = vm.reminders + m }) {
                            Text("+ " + if (m == 0) "At start" else if (m == 1440) "1d" else if (m >= 60) "${m / 60}h" else "${m}m")
                        }
                    }
                }
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    OutlinedTextField(
                        newReminder, { newReminder = it.filter(Char::isDigit).take(6) },
                        label = { Text("Custom (min)") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.width(160.dp),
                    )
                    TextButton(onClick = {
                        newReminder.toIntOrNull()?.let { vm.reminders = vm.reminders + it }
                        newReminder = ""
                    }) { Text("Add") }
                }

                Label("Repeat")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = vm.freq == null, onClick = { vm.freq = null }, label = { Text("Never") })
                    Freq.values().forEach { f ->
                        FilterChip(selected = vm.freq == f, onClick = { vm.freq = f },
                            label = { Text(f.name.lowercase().replaceFirstChar(Char::uppercase)) })
                    }
                }
                if (vm.freq != null) {
                    OutlinedTextField(
                        value = vm.interval.toString(),
                        onValueChange = { v -> vm.interval = v.filter(Char::isDigit).take(3).toIntOrNull() ?: 1 },
                        label = { Text("Every N (interval)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (vm.freq == Freq.WEEKLY) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        DayOfWeek.values().forEach { d ->
                            FilterChip(
                                selected = d in vm.days,
                                onClick = { vm.days = if (d in vm.days) vm.days - d else vm.days + d },
                                label = { Text(d.getDisplayName(TextStyle.SHORT, Locale.getDefault())) },
                            )
                        }
                    }
                }
                if (vm.freq != null) {
                    Text(
                        "Editing a repeating task changes this and all future instances; completed ones stay as they are.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    if (newLabel) {
        var name by remember { mutableStateOf("") }
        var color by remember { mutableStateOf(ColorChoices[6]) }
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { newLabel = false },
            title = { Text("New label") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(name, { name = it }, label = { Text("Label name") }, singleLine = true)
                    ColorPickerRow(color) { color = it }
                }
            },
            confirmButton = {
                TextButton(enabled = name.isNotBlank(), onClick = { vm.createLabel(name, color); newLabel = false }) { Text("Create") }
            },
            dismissButton = { TextButton(onClick = { newLabel = false }) { Text("Cancel") } },
        )
    }
    if (pickDate) DatePickerDialogX(vm.date, { vm.date = it; pickDate = false }, { pickDate = false })
    if (pickTime) TimePickerDialogX(vm.time, { vm.time = it; pickTime = false }, { pickTime = false })
}

@Composable
private fun Label(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
}
