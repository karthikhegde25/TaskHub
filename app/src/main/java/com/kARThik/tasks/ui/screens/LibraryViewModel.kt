// SPDX-License-Identifier: GPL-3.0-only
// TaskHub, Copyright (C) 2026 Karthik Hegde. Licensed under the GNU General Public License v3.0; see LICENSE.

package com.kARThik.tasks.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kARThik.tasks.data.local.NoteEntity
import com.kARThik.tasks.data.local.ProjectEntity
import com.kARThik.tasks.data.local.ReminderEntity
import com.kARThik.tasks.data.local.TagEntity
import com.kARThik.tasks.data.local.TaskEntity
import com.kARThik.tasks.data.local.TemplateEntity
import com.kARThik.tasks.data.repository.PlannerRepository
import com.kARThik.tasks.data.repository.TimelineRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.flow.first

/** Shared by Board, Unplanned, Notes, Search, Reminders, Tags, Templates, Repeating and Statistics. */
@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val timeline: TimelineRepository,
    private val planner: PlannerRepository,
    private val prefsRepo: com.kARThik.tasks.data.repository.PrefsRepository,
    @dagger.hilt.android.qualifiers.ApplicationContext private val ctx: android.content.Context,
) : ViewModel() {
    private fun <T> kotlinx.coroutines.flow.Flow<List<T>>.hot(): StateFlow<List<T>> =
        stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val tasks: StateFlow<List<TaskEntity>> = timeline.tasks.hot()
    val notes: StateFlow<List<NoteEntity>> = timeline.notes.hot()
    val projects: StateFlow<List<ProjectEntity>> = planner.projects.hot()
    val tags: StateFlow<List<TagEntity>> = planner.tags.hot()
    val templates: StateFlow<List<TemplateEntity>> = planner.templates.hot()
    val reminders: StateFlow<List<ReminderEntity>> = planner.reminders.hot()

    fun moveOverdueToToday() = viewModelScope.launch { timeline.moveOverdueToToday() }
    fun toggleTask(t: TaskEntity) = viewModelScope.launch {
        if (!t.isCompleted) {
            com.kARThik.tasks.audio.TickSounds.play(ctx, prefsRepo.prefs.first().tickSound)
        }
        timeline.setTaskCompleted(t.id, !t.isCompleted)
    }

    /** Gives a task a date (and optional time), which moves it into the Timeline. */
    fun scheduleTask(t: TaskEntity, date: LocalDate, time: LocalTime?) = viewModelScope.launch {
        val start = date.atTime(time ?: LocalTime.MIDNIGHT).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val oldStart = t.startDateTime
        val oldEnd = t.endDateTime
        val end: Long? = if (oldStart != null && oldEnd != null) start + (oldEnd - oldStart) else null
        timeline.updateTask(t.copy(startDateTime = start, endDateTime = end))
    }

    /** Manual ordering: swap two tasks' positions. */
    fun swapOrder(a: TaskEntity, b: TaskEntity) = viewModelScope.launch {
        timeline.updateTask(a.copy(sortOrder = b.sortOrder))
        timeline.updateTask(b.copy(sortOrder = a.sortOrder))
    }

    fun addProject(name: String, color: Long) = viewModelScope.launch {
        if (name.isNotBlank()) planner.addProject(name, color, projects.value.size)
    }

    fun deleteProject(id: Long) = viewModelScope.launch { planner.deleteProject(id) }

    fun addTag(name: String, color: Long) = viewModelScope.launch { planner.addTag(name, color) }
    fun deleteTag(id: Long) = viewModelScope.launch { planner.deleteTag(id) }

    fun saveTemplate(t: TemplateEntity) = viewModelScope.launch { planner.saveTemplate(t) }
    fun deleteTemplate(id: Long) = viewModelScope.launch { planner.deleteTemplate(id) }
    fun useTemplate(t: TemplateEntity) = viewModelScope.launch { planner.useTemplate(t, LocalDate.now()) }

    fun saveReminder(r: ReminderEntity) = viewModelScope.launch { planner.saveReminder(r) }
    fun deleteReminder(id: Long) = viewModelScope.launch { planner.deleteReminder(id) }
}
