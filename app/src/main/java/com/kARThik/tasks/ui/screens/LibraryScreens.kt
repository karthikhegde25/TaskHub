// SPDX-License-Identifier: GPL-3.0-only
// TaskHub, Copyright (C) 2026 Karthik Hegde. Licensed under the GNU General Public License v3.0; see LICENSE.

package com.kARThik.tasks.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.StickyNote2
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kARThik.tasks.data.local.ReminderEntity
import com.kARThik.tasks.data.local.TaskEntity
import com.kARThik.tasks.data.local.TemplateEntity
import com.kARThik.tasks.domain.Checklist
import com.kARThik.tasks.domain.ChecklistItem
import com.kARThik.tasks.domain.Freq
import com.kARThik.tasks.domain.RecurrenceRule
import com.kARThik.tasks.ui.common.ColorChoices
import com.kARThik.tasks.ui.common.ColorPickerRow
import com.kARThik.tasks.ui.common.DatePickerDialogX
import com.kARThik.tasks.ui.common.EmptyState
import com.kARThik.tasks.ui.common.ItemCard
import com.kARThik.tasks.ui.common.TimePickerDialogX
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

// ---------------------------------------------------------------- Unplanned

/** Tasks with no date and no project. Sortable; tap the calendar icon to put one on the Timeline. */
@Composable
fun UnplannedScreen(onOpen: (String, Long) -> Unit, vm: LibraryViewModel = hiltViewModel()) {
    val tasks by vm.tasks.collectAsStateWithLifecycle()
    var sort by rememberSaveable { mutableStateOf(0) }
    var scheduling by remember { mutableStateOf<TaskEntity?>(null) }

    val list = tasks.filter { it.startDateTime == null && it.projectId == null && !it.isCancelled }.let { l ->
        when (sort) {
            1 -> l.sortedWith(compareBy({ com.kARThik.tasks.ui.theme.PriorityColors.rank(it.priority) }, { it.createdAt }))
            2 -> l.sortedBy { it.title.lowercase() }
            else -> l.sortedByDescending { it.createdAt }
        }.sortedBy { it.isCompleted } // completed tasks sink to the bottom
    }
    Scaffold(
        topBar = { SimpleTopBar("Unplanned") },
        floatingActionButton = { FloatingActionButton(onClick = { onOpen("task", -1) }) { Icon(Icons.Default.Add, "New task") } },
    ) { pad ->
        Column(Modifier.padding(pad)) {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Newest", "Priority", "A–Z").forEachIndexed { i, l ->
                    FilterChip(selected = sort == i, onClick = { sort = i }, label = { Text(l) })
                }
            }
            if (list.isEmpty()) EmptyState(Icons.Default.Inbox, "Nothing unplanned", "Tasks without a date or project show up here.")
            else LazyColumn(contentPadding = PaddingValues(bottom = 88.dp)) {
                items(list, key = { it.id }) { t ->
                    ItemCard(
                        title = t.title, subtitle = taskSubtitle(t), done = t.isCompleted, priority = t.priority, tags = t.tags,
                        onClick = { onOpen("task", t.id) },
                        leading = { Checkbox(t.isCompleted, { vm.toggleTask(t) }) },
                        trailing = { IconButton(onClick = { scheduling = t }) { Icon(Icons.Default.CalendarMonth, "Schedule") } },
                    )
                }
            }
        }
    }
    scheduling?.let { t ->
        SchedulePickerFlow({ d, time -> vm.scheduleTask(t, d, time); scheduling = null }, { scheduling = null })
    }
}

// ----------------------------------------------------------------- Overdue

/** Incomplete tasks dated before today, oldest first. Opened from the Overdue card on the Timeline. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun OverdueScreen(onBack: () -> Unit, onOpen: (String, Long) -> Unit, vm: LibraryViewModel = hiltViewModel()) {
    val tasks by vm.tasks.collectAsStateWithLifecycle()
    var scheduling by remember { mutableStateOf<TaskEntity?>(null) }
    val todayStart = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    val list = tasks.filter {
        val s = it.startDateTime
        s != null && s < todayStart && !it.isCompleted && !it.isCancelled
    }.sortedBy { it.startDateTime }

    Scaffold(
        topBar = {
            androidx.compose.material3.TopAppBar(
                title = { Text("Overdue") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(androidx.compose.material.icons.Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
                actions = {
                    if (list.isNotEmpty()) TextButton(onClick = { vm.moveOverdueToToday() }) { Text("Move all to today") }
                },
            )
        },
    ) { pad ->
        Column(Modifier.padding(pad)) {
            if (list.isEmpty()) EmptyState(Icons.Default.Inbox, "Nothing overdue", "Tasks you haven't finished by their day show up here.")
            else LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                items(list, key = { it.id }) { t ->
                    ItemCard(
                        title = t.title, subtitle = taskSubtitle(t), done = t.isCompleted, priority = t.priority, tags = t.tags,
                        onClick = { onOpen("task", t.id) },
                        leading = { Checkbox(t.isCompleted, { vm.toggleTask(t) }) },
                        trailing = { IconButton(onClick = { scheduling = t }) { Icon(Icons.Default.CalendarMonth, "Reschedule") } },
                    )
                }
            }
        }
    }
    scheduling?.let { t ->
        SchedulePickerFlow({ d, time -> vm.scheduleTask(t, d, time); scheduling = null }, { scheduling = null })
    }
}

// -------------------------------------------------------------------- Notes

@Composable
fun NotesScreen(onOpen: (String, Long) -> Unit, vm: LibraryViewModel = hiltViewModel()) {
    val notes by vm.notes.collectAsStateWithLifecycle()
    Scaffold(
        topBar = { SimpleTopBar("Notes") },
        floatingActionButton = { FloatingActionButton(onClick = { onOpen("note", -1) }) { Icon(Icons.Default.Add, "New note") } },
    ) { pad ->
        if (notes.isEmpty()) EmptyState(Icons.Default.StickyNote2, "No notes yet", "Tap + to write one.", Modifier.padding(pad))
        else LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(bottom = 88.dp)) {
            items(notes, key = { it.id }) { n ->
                ItemCard(
                    title = n.title,
                    subtitle = n.description.lineSequence().firstOrNull { it.isNotBlank() },
                    priority = n.priority, tags = n.tags,
                    onClick = { onOpen("note", n.id) },
                    leading = { Icon(Icons.Default.StickyNote2, null, Modifier.padding(8.dp), tint = MaterialTheme.colorScheme.tertiary) },
                )
            }
        }
    }
}

// ------------------------------------------------------------------- Search

@Composable
fun SearchScreen(onOpen: (String, Long) -> Unit, vm: LibraryViewModel = hiltViewModel()) {
    val tasks by vm.tasks.collectAsStateWithLifecycle()
    val notes by vm.notes.collectAsStateWithLifecycle()
    var q by rememberSaveable { mutableStateOf("") }
    val query = q.trim()
    val taskHits = if (query.isEmpty()) emptyList() else tasks.filter {
        it.title.contains(query, true) || it.description.contains(query, true) || it.tags.any { t -> t.contains(query.removePrefix("@"), true) }
    }.sortedBy { it.isCompleted }
    val noteHits = if (query.isEmpty()) emptyList() else notes.filter {
        it.title.contains(query, true) || it.description.contains(query, true) || it.tags.any { t -> t.contains(query.removePrefix("@"), true) }
    }
    Scaffold(topBar = { SimpleTopBar("Search") }) { pad ->
        Column(Modifier.padding(pad)) {
            OutlinedTextField(
                q, { q = it }, label = { Text("Search tasks and notes") }, singleLine = true,
                leadingIcon = { Icon(Icons.Default.Search, null) },
                modifier = Modifier.fillMaxWidth().padding(16.dp),
            )
            if (query.isEmpty()) EmptyState(Icons.Default.Search, "Search everything", "Titles, descriptions and tags.")
            else if (taskHits.isEmpty() && noteHits.isEmpty()) EmptyState(Icons.Default.Search, "No results")
            else LazyColumn {
                items(taskHits, key = { "t${it.id}" }) { t ->
                    ItemCard(t.title, taskSubtitle(t), t.isCompleted, t.priority, { onOpen("task", t.id) }, tags = t.tags,
                        leading = { Checkbox(t.isCompleted, { vm.toggleTask(t) }) })
                }
                items(noteHits, key = { "n${it.id}" }) { n ->
                    ItemCard(n.title, n.description.lineSequence().firstOrNull { it.isNotBlank() }, false, n.priority,
                        { onOpen("note", n.id) }, tags = n.tags,
                        leading = { Icon(Icons.Default.StickyNote2, null, Modifier.padding(8.dp)) })
                }
            }
        }
    }
}

// --------------------------------------------------------------------- Tags

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TagsScreen(onOpen: (String, Long) -> Unit, vm: LibraryViewModel = hiltViewModel()) {
    val tags by vm.tags.collectAsStateWithLifecycle()
    val tasks by vm.tasks.collectAsStateWithLifecycle()
    val notes by vm.notes.collectAsStateWithLifecycle()
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    var showAdd by remember { mutableStateOf(false) }

    // Tags created implicitly via @tag still show up, even without a saved colour.
    val names = (tags.map { it.name } + tasks.flatMap { it.tags } + notes.flatMap { it.tags }).distinctBy { it.lowercase() }.sortedBy { it.lowercase() }
    fun colorOf(n: String) = tags.firstOrNull { it.name.equals(n, true) }?.color ?: 0xFF625B71

    Scaffold(
        topBar = { SimpleTopBar("Tags") },
        floatingActionButton = { FloatingActionButton(onClick = { showAdd = true }) { Icon(Icons.Default.Add, "New tag") } },
    ) { pad ->
        Column(Modifier.padding(pad)) {
            if (names.isEmpty()) EmptyState(Icons.Default.Label, "No tags yet", "Add @tags when you create items, or tap +.")
            else {
                FlowRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    names.forEach { n ->
                        FilterChip(
                            selected = selected.equals(n, true),
                            onClick = { selected = if (selected.equals(n, true)) null else n },
                            label = { Text("@$n") },
                            leadingIcon = { Spacer(Modifier.size(10.dp).background(Color(colorOf(n)), CircleShape)) },
                        )
                    }
                }
                val sel = selected
                if (sel == null) EmptyState(Icons.Default.Label, "Pick a tag", "See every task and note that uses it.")
                else {
                    val t = tasks.filter { it.tags.any { x -> x.equals(sel, true) } }.sortedBy { it.isCompleted }
                    val n = notes.filter { it.tags.any { x -> x.equals(sel, true) } }
                    LazyColumn(contentPadding = PaddingValues(top = 8.dp, bottom = 88.dp)) {
                        item {
                            val saved = tags.firstOrNull { it.name.equals(sel, true) }
                            if (saved != null) TextButton(onClick = { vm.deleteTag(saved.id); selected = null }, modifier = Modifier.padding(horizontal = 8.dp)) {
                                Text("Delete tag @$sel (items keep their text)")
                            }
                        }
                        items(t, key = { "t${it.id}" }) { x ->
                            ItemCard(x.title, taskSubtitle(x), x.isCompleted, x.priority, { onOpen("task", x.id) }, tags = x.tags,
                                leading = { Checkbox(x.isCompleted, { vm.toggleTask(x) }) })
                        }
                        items(n, key = { "n${it.id}" }) { x ->
                            ItemCard(x.title, null, false, x.priority, { onOpen("note", x.id) }, tags = x.tags,
                                leading = { Icon(Icons.Default.StickyNote2, null, Modifier.padding(8.dp)) })
                        }
                    }
                }
            }
        }
    }
    if (showAdd) {
        var name by remember { mutableStateOf("") }
        var color by remember { mutableStateOf(ColorChoices[6]) }
        AlertDialog(
            onDismissRequest = { showAdd = false },
            title = { Text("New tag") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true)
                    ColorPickerRow(color) { color = it }
                }
            },
            confirmButton = { TextButton(enabled = name.isNotBlank(), onClick = { vm.addTag(name, color); showAdd = false }) { Text("Create") } },
            dismissButton = { TextButton(onClick = { showAdd = false }) { Text("Cancel") } },
        )
    }
}

// ---------------------------------------------------------------- Templates

@Composable
fun TemplatesScreen(vm: LibraryViewModel = hiltViewModel()) {
    val templates by vm.templates.collectAsStateWithLifecycle()
    var showAdd by remember { mutableStateOf(false) }
    Scaffold(
        topBar = { SimpleTopBar("Templates") },
        floatingActionButton = { FloatingActionButton(onClick = { showAdd = true }) { Icon(Icons.Default.Add, "New template") } },
    ) { pad ->
        if (templates.isEmpty()) EmptyState(Icons.Default.ContentCopy, "No templates", "Save a reusable task or note, then create copies with one tap.", Modifier.padding(pad))
        else LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(bottom = 88.dp)) {
            items(templates, key = { it.id }) { t ->
                ItemCard(
                    title = t.name,
                    subtitle = buildString {
                        append(if (t.isNote) "Note" else "Task")
                        val n = Checklist.parse(t.checklistJson).size
                        if (n > 0) append(" · $n checklist items")
                    },
                    leading = { Icon(Icons.Default.ContentCopy, null, Modifier.padding(8.dp)) },
                    trailing = {
                        IconButton(onClick = { vm.useTemplate(t) }) { Icon(Icons.Default.PlayArrow, "Create today") }
                        IconButton(onClick = { vm.deleteTemplate(t.id) }) { Icon(Icons.Default.Delete, "Delete") }
                    },
                )
            }
        }
    }
    if (showAdd) {
        var name by remember { mutableStateOf("") }
        var title by remember { mutableStateOf("") }
        var desc by remember { mutableStateOf("") }
        var tags by remember { mutableStateOf("") }
        var checks by remember { mutableStateOf("") }
        var isNote by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { showAdd = false },
            title = { Text("New template") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(name, { name = it }, label = { Text("Template name") }, singleLine = true)
                    OutlinedTextField(title, { title = it }, label = { Text("Item title") }, singleLine = true)
                    OutlinedTextField(desc, { desc = it }, label = { Text("Description") })
                    OutlinedTextField(tags, { tags = it }, label = { Text("Tags (space separated)") }, singleLine = true)
                    OutlinedTextField(checks, { checks = it }, label = { Text("Checklist (one item per line)") }, minLines = 2)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(!isNote, { isNote = false }, { Text("Task") })
                        FilterChip(isNote, { isNote = true }, { Text("Note") })
                    }
                }
            },
            confirmButton = {
                TextButton(enabled = name.isNotBlank(), onClick = {
                    vm.saveTemplate(
                        TemplateEntity(
                            name = name.trim(), title = title.ifBlank { name }.trim(), description = desc,
                            tags = tags.split(" ", ",").map { it.trim().removePrefix("@") }.filter { it.isNotEmpty() },
                            checklistJson = Checklist.toJson(checks.lines().filter { it.isNotBlank() }.map { ChecklistItem(it.trim()) }),
                            isNote = isNote,
                        )
                    )
                    showAdd = false
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showAdd = false }) { Text("Cancel") } },
        )
    }
}

// ---------------------------------------------------------------- Reminders

private val dtFmt = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RemindersScreen(vm: LibraryViewModel = hiltViewModel()) {
    val reminders by vm.reminders.collectAsStateWithLifecycle()
    var showAdd by remember { mutableStateOf(false) }
    Scaffold(
        topBar = { SimpleTopBar("Reminders") },
        floatingActionButton = { FloatingActionButton(onClick = { showAdd = true }) { Icon(Icons.Default.Add, "New reminder") } },
    ) { pad ->
        if (reminders.isEmpty()) EmptyState(Icons.Default.Alarm, "No reminders", "Repeating reminders never become overdue.", Modifier.padding(pad))
        else LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(bottom = 88.dp)) {
            items(reminders, key = { it.id }) { r ->
                ItemCard(
                    title = r.title,
                    subtitle = dtFmt.format(Instant.ofEpochMilli(r.triggerAt).atZone(ZoneId.systemDefault())) +
                        (r.repeatUnit?.let { u -> " · " + RecurrenceRule(Freq.valueOf(u), r.repeatInterval).describe() } ?: ""),
                    leading = { Icon(Icons.Default.Alarm, null, Modifier.padding(8.dp)) },
                    trailing = {
                        Switch(r.enabled, { vm.saveReminder(r.copy(enabled = it)) })
                        IconButton(onClick = { vm.deleteReminder(r.id) }) { Icon(Icons.Default.Delete, "Delete") }
                    },
                )
            }
        }
    }
    if (showAdd) {
        var title by remember { mutableStateOf("") }
        var date by remember { mutableStateOf(LocalDate.now()) }
        var time by remember { mutableStateOf(LocalTime.now().plusHours(1).withMinute(0).withSecond(0).withNano(0)) }
        var freq by remember { mutableStateOf<Freq?>(null) }
        var interval by remember { mutableStateOf("1") }
        var inTimeline by remember { mutableStateOf(true) }
        var createDone by remember { mutableStateOf(false) }
        var pickDate by remember { mutableStateOf(false) }
        var pickTime by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { showAdd = false },
            title = { Text("New reminder") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(title, { title = it }, label = { Text("Remind me to…") }, singleLine = true)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { pickDate = true }) { Text(date.toString()) }
                        TextButton(onClick = { pickTime = true }) { Text(time.toString()) }
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(freq == null, { freq = null }, { Text("Once") })
                        Freq.values().forEach { f -> FilterChip(freq == f, { freq = f }, { Text(f.name.lowercase().replaceFirstChar(Char::uppercase)) }) }
                    }
                    if (freq != null) OutlinedTextField(interval, { interval = it.filter(Char::isDigit).take(3) },
                        label = { Text("Every N") }, singleLine = true)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(inTimeline, { inTimeline = it }); Text("  Show in Timeline")
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(createDone, { createDone = it }); Text("  Create completed task when done")
                    }
                }
            },
            confirmButton = {
                TextButton(enabled = title.isNotBlank(), onClick = {
                    vm.saveReminder(
                        ReminderEntity(
                            title = title.trim(),
                            triggerAt = date.atTime(time).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                            repeatUnit = freq?.name,
                            repeatInterval = interval.toIntOrNull()?.coerceAtLeast(1) ?: 1,
                            showInTimeline = inTimeline,
                            createCompletedTask = createDone,
                        )
                    )
                    showAdd = false
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showAdd = false }) { Text("Cancel") } },
        )
        if (pickDate) DatePickerDialogX(date, { date = it; pickDate = false }, { pickDate = false })
        if (pickTime) TimePickerDialogX(time, { time = it; pickTime = false }, { pickTime = false })
    }
}

// ---------------------------------------------------------- Repeating tasks

@Composable
fun RepeatingScreen(onOpen: (String, Long) -> Unit, vm: LibraryViewModel = hiltViewModel()) {
    val tasks by vm.tasks.collectAsStateWithLifecycle()
    // One row per series: the open (not yet completed) instance.
    val list = tasks.filter { it.isRecurring && !it.isCompleted }.sortedBy { it.startDateTime ?: Long.MAX_VALUE }
    Scaffold(topBar = { SimpleTopBar("Repeating Tasks") }) { pad ->
        if (list.isEmpty()) EmptyState(Icons.Default.Repeat, "No repeating tasks", "Try \"Water plants every 3 days\" in quick add.", Modifier.padding(pad))
        else LazyColumn(Modifier.padding(pad)) {
            items(list, key = { it.id }) { t ->
                ItemCard(t.title, taskSubtitle(t), false, t.priority, { onOpen("task", t.id) }, tags = t.tags,
                    leading = { Icon(Icons.Default.Repeat, null, Modifier.padding(8.dp)) })
            }
        }
    }
}

// --------------------------------------------------------------- Statistics

@Composable
fun StatsScreen(vm: LibraryViewModel = hiltViewModel()) {
    val tasks by vm.tasks.collectAsStateWithLifecycle()
    val notes by vm.notes.collectAsStateWithLifecycle()
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now()
    val total = tasks.size
    val done = tasks.count { it.isCompleted }
    val overdue = tasks.count {
        !it.isCompleted && it.startDateTime != null && Instant.ofEpochMilli(it.startDateTime).atZone(zone).toLocalDate().isBefore(today)
    }
    val doneWeek = tasks.count {
        it.isCompleted && Instant.ofEpochMilli(it.updatedAt).atZone(zone).toLocalDate().isAfter(today.minusDays(7))
    }
    Scaffold(topBar = { SimpleTopBar("Statistics") }) { pad ->
        Column(Modifier.padding(pad).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Stat("Completion", if (total == 0) "–" else "${done * 100 / total}%", if (total == 0) 0f else done.toFloat() / total)
            Stat("Completed in the last 7 days", doneWeek.toString())
            Stat("Overdue", overdue.toString())
            Stat("Open tasks", (total - done).toString())
            Stat("Notes", notes.size.toString())
            listOf(4, 3, 2, 1).forEach { p ->
                Stat("Open · ${com.kARThik.tasks.ui.theme.PriorityColors.name(p)}", tasks.count { !it.isCompleted && it.priority == p }.toString())
            }
        }
    }
}

@Composable
private fun Stat(label: String, value: String, progress: Float? = null) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                Text(value, style = MaterialTheme.typography.titleLarge)
            }
            if (progress != null) LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
        }
    }
}
