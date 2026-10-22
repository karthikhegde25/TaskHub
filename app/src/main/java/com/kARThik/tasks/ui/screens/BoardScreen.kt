// SPDX-License-Identifier: GPL-3.0-only
// TaskHub, Copyright (C) 2026 Karthik Hegde. Licensed under the GNU General Public License v3.0; see LICENSE.

package com.kARThik.tasks.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import com.kARThik.tasks.data.local.ProjectEntity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kARThik.tasks.data.local.TaskEntity
import com.kARThik.tasks.ui.common.ColorPickerRow
import com.kARThik.tasks.ui.common.ColorChoices
import com.kARThik.tasks.ui.common.EmptyState
import com.kARThik.tasks.ui.common.ItemCard

/**
 * Kanban-style project board: one tab per color-coded project, tasks grouped into
 * To do / Scheduled / Done. Sort by manual order (up/down), priority or due date.
 * "Move to Timeline" assigns a date/time.
 */
@Composable
fun BoardScreen(onOpen: (String, Long) -> Unit, onAddTask: (Long) -> Unit, vm: LibraryViewModel = hiltViewModel()) {
    val projects by vm.projects.collectAsStateWithLifecycle()
    val tasks by vm.tasks.collectAsStateWithLifecycle()
    var sort by rememberSaveable { mutableStateOf(0) } // 0 manual, 1 priority, 2 due date
    var showAddProject by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var scheduling by remember { mutableStateOf<TaskEntity?>(null) }

    val pagerState = rememberPagerState(pageCount = { projects.size })
    val scope = rememberCoroutineScope()
    val project = projects.getOrNull(pagerState.currentPage)

    Scaffold(
        topBar = {
            SimpleTopBar("Board") {
                IconButton(onClick = { showAddProject = true }) { Icon(Icons.Default.CreateNewFolder, "New project") }
                if (project != null) IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Default.Delete, "Delete project") }
            }
        },
        floatingActionButton = {
            if (project != null) FloatingActionButton(onClick = { onAddTask(project.id) }) { Icon(Icons.Default.Add, "Add task") }
        },
    ) { pad ->
        if (project == null) {
            Column(Modifier.fillMaxSize().padding(pad), horizontalAlignment = Alignment.CenterHorizontally) {
                EmptyState(Icons.Default.Dashboard, "No projects yet", "Create a color-coded project to organise tasks on a board.", Modifier.weight(1f))
                Button(onClick = { showAddProject = true }, modifier = Modifier.padding(bottom = 32.dp)) { Text("Create project") }
            }
        } else {
            Column(Modifier.padding(pad)) {
                ScrollableTabRow(selectedTabIndex = pagerState.currentPage.coerceIn(0, projects.lastIndex), edgePadding = 8.dp) {
                    projects.forEachIndexed { i, p ->
                        Tab(
                            selected = i == pagerState.currentPage,
                            onClick = { scope.launch { pagerState.animateScrollToPage(i) } },
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(Modifier.size(10.dp).background(Color(p.color), CircleShape))
                                    Text("  ${p.name}")
                                }
                            },
                        )
                    }
                }
                Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Manual", "Priority", "Due date").forEachIndexed { i, l ->
                        FilterChip(selected = sort == i, onClick = { sort = i }, label = { Text(l) })
                    }
                }
                // Swipe left/right to move between boards.
                HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize(), key = { projects[it].id }) { page ->
                    BoardPage(projects[page], tasks, sort, onOpen, vm) { scheduling = it }
                }
            }
        }
    }

    if (showAddProject) {
        var name by remember { mutableStateOf("") }
        var color by remember { mutableStateOf(ColorChoices.first()) }
        AlertDialog(
            onDismissRequest = { showAddProject = false },
            title = { Text("New project") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true)
                    ColorPickerRow(color) { color = it }
                }
            },
            confirmButton = {
                TextButton(enabled = name.isNotBlank(), onClick = { vm.addProject(name, color); showAddProject = false }) { Text("Create") }
            },
            dismissButton = { TextButton(onClick = { showAddProject = false }) { Text("Cancel") } },
        )
    }
    if (confirmDelete && project != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete ${project.name}?") },
            text = { Text("Its tasks are kept and move to Unplanned or the Timeline.") },
            confirmButton = {
                TextButton(onClick = { vm.deleteProject(project.id); confirmDelete = false }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
    scheduling?.let { t ->
        SchedulePickerFlow(
            onDone = { d, time -> vm.scheduleTask(t, d, time); scheduling = null },
            onDismiss = { scheduling = null },
        )
    }
}


/** One board (project) page: To do / Scheduled / Done sections. */
@Composable
private fun BoardPage(
    project: ProjectEntity,
    tasks: List<TaskEntity>,
    sort: Int,
    onOpen: (String, Long) -> Unit,
    vm: LibraryViewModel,
    onSchedule: (TaskEntity) -> Unit,
) {
    val mine = tasks.filter { it.projectId == project.id }
    fun ordered(l: List<TaskEntity>): List<TaskEntity> = when (sort) {
        1 -> l.sortedWith(compareBy({ com.kARThik.tasks.ui.theme.PriorityColors.rank(it.priority) }, { it.sortOrder }))
        2 -> l.sortedWith(compareBy({ it.startDateTime ?: Long.MAX_VALUE }, { it.sortOrder }))
        else -> l.sortedBy { it.sortOrder }
    }
    val todo = ordered(mine.filter { !it.isCompleted && it.startDateTime == null })
    val scheduled = ordered(mine.filter { !it.isCompleted && it.startDateTime != null })
    val done = ordered(mine.filter { it.isCompleted })

    if (mine.isEmpty()) {
        EmptyState(Icons.Default.Dashboard, "Empty board", "Tap + to add a task to ${project.name}.")
        return
    }
    LazyColumn(contentPadding = PaddingValues(bottom = 88.dp)) {
        listOf("To do" to todo, "Scheduled" to scheduled, "Done" to done).forEach { (label, list) ->
            if (list.isNotEmpty()) {
                item(key = "h-$label") {
                    Text("$label · ${list.size}", style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp))
                }
                items(list, key = { "t-${it.id}" }) { t ->
                    val idx = list.indexOf(t)
                    ItemCard(
                        title = t.title,
                        subtitle = taskSubtitle(t),
                        done = t.isCompleted,
                        priority = t.priority,
                        onClick = { onOpen("task", t.id) },
                        leading = { Checkbox(t.isCompleted, { vm.toggleTask(t) }) },
                        trailing = {
                            if (sort == 0 && !t.isCompleted) {
                                IconButton(onClick = { vm.swapOrder(t, list[idx - 1]) }, enabled = idx > 0) {
                                    Icon(Icons.Default.ArrowUpward, "Move up")
                                }
                                IconButton(onClick = { vm.swapOrder(t, list[idx + 1]) }, enabled = idx < list.lastIndex) {
                                    Icon(Icons.Default.ArrowDownward, "Move down")
                                }
                            }
                            if (!t.isCompleted) IconButton(onClick = { onSchedule(t) }) {
                                Icon(Icons.Default.CalendarMonth, "Move to Timeline")
                            }
                        },
                        tags = t.tags,
                    )
                }
            }
        }
    }
}
