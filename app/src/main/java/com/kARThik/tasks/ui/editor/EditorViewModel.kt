// SPDX-License-Identifier: GPL-3.0-only
// TaskHub, Copyright (C) 2026 Karthik Hegde. Licensed under the GNU General Public License v3.0; see LICENSE.

package com.kARThik.tasks.ui.editor

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kARThik.tasks.data.local.NoteEntity
import com.kARThik.tasks.data.local.ProjectEntity
import com.kARThik.tasks.data.local.TagEntity
import com.kARThik.tasks.data.local.TaskEntity
import com.kARThik.tasks.data.repository.ItemKind
import com.kARThik.tasks.data.repository.PlannerRepository
import com.kARThik.tasks.data.repository.TimelineItem
import com.kARThik.tasks.data.repository.TimelineRepository
import com.kARThik.tasks.domain.Checklist
import com.kARThik.tasks.domain.ChecklistItem
import com.kARThik.tasks.domain.Freq
import com.kARThik.tasks.domain.QuickAddParser
import com.kARThik.tasks.domain.RecurrenceRule
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import javax.inject.Inject

/** Editor for tasks and notes. Route: editor/{kind}/{id}; id = -1 creates a new item. */
@HiltViewModel
class EditorViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val timeline: TimelineRepository,
    private val planner: PlannerRepository,
) : ViewModel() {
    val isNote: Boolean = (savedState.get<String>("kind") ?: "task") == "note"
    val id: Long = savedState.get<Long>("id") ?: -1L
    val isNew: Boolean get() = id < 0
    private val presetDate: Long = savedState.get<Long>("date") ?: -1L
    private val presetProject: Long = savedState.get<Long>("project") ?: -1L

    var loaded by mutableStateOf(isNew)
    var title by mutableStateOf("")
    var description by mutableStateOf("")
    var date by mutableStateOf<LocalDate?>(null)
    var time by mutableStateOf<LocalTime?>(null)
    var durationMin by mutableStateOf<Int?>(null)
    var priority by mutableStateOf(0)
    /** Selected label names; their colours come from the saved tags. */
    var labels by mutableStateOf<List<String>>(emptyList())
    var projectId by mutableStateOf<Long?>(null)
    var checklist by mutableStateOf<List<ChecklistItem>>(emptyList())
    /** Text typed in the "add checklist item" box but not yet added. */
    var checkDraft by mutableStateOf("")

    /** Adds the draft as a checklist item (called by +, the keyboard's Done key, and on Save). */
    fun addCheckItem() {
        val t = checkDraft.trim()
        if (t.isNotEmpty()) checklist = checklist + ChecklistItem(t)
        checkDraft = ""
    }
    var reminders by mutableStateOf<List<Int>>(emptyList())
    var freq by mutableStateOf<Freq?>(null)
    var interval by mutableStateOf(1)
    var days by mutableStateOf<Set<DayOfWeek>>(emptySet())

    val allTags: StateFlow<List<TagEntity>> = planner.tags
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun toggleLabel(name: String) {
        labels = if (labels.any { it.equals(name, true) }) labels.filterNot { it.equals(name, true) } else labels + name
    }

    /** Creates a coloured label and selects it. */
    fun createLabel(name: String, color: Long) {
        val n = name.trim().removePrefix("@")
        if (n.isEmpty()) return
        viewModelScope.launch {
            planner.addTag(n, color)
            if (labels.none { it.equals(n, true) }) labels = labels + n
        }
    }

    val projects: StateFlow<List<ProjectEntity>> = planner.projects
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private var originalTask: TaskEntity? = null
    private var originalNote: NoteEntity? = null
    private val zone = ZoneId.systemDefault()

    init {
        if (isNew) {
            if (presetDate >= 0) date = LocalDate.ofEpochDay(presetDate)
            if (presetProject >= 0) projectId = presetProject
        }
        if (!isNew) viewModelScope.launch {
            if (isNote) timeline.getNote(id)?.let { n ->
                originalNote = n
                fill(n.title, n.description, n.startDateTime, n.endDateTime, n.priority, n.tags, n.projectId, n.checklistJson)
            } else timeline.getTask(id)?.let { t ->
                originalTask = t
                fill(t.title, t.description, t.startDateTime, t.endDateTime, t.priority, t.tags, t.projectId, t.checklistJson)
                reminders = t.reminderOffsetsMin
                RecurrenceRule.parse(t.recurrenceRule)?.let { r -> freq = r.freq; interval = r.interval; days = r.days }
            }
            loaded = true
        }
    }

    private fun fill(
        title: String, desc: String, start: Long?, end: Long?, prio: Int,
        tags: List<String>, project: Long?, checklistJson: String,
    ) {
        this.title = title
        description = desc
        priority = prio
        labels = tags
        projectId = project
        checklist = Checklist.parse(checklistJson)
        if (start != null) {
            val z = Instant.ofEpochMilli(start).atZone(zone)
            date = z.toLocalDate()
            time = z.toLocalTime().takeIf { it.toSecondOfDay() != 0 }
            if (end != null && end > start) durationMin = ((end - start) / 60_000L).toInt()
        }
    }

    /** Smart fill: parses text like "Call mom tomorrow 5pm p1 @family #Work !30m every mon" into the fields. */
    fun applySmartText(text: String) {
        val p = QuickAddParser.parse(text)
        if (p.title.isNotBlank()) title = p.title
        p.date?.let { date = it }
        p.time?.let { time = it }
        p.durationMin?.let { durationMin = it }
        if (p.priority > 0) priority = p.priority
        if (p.tags.isNotEmpty()) labels = (labels + p.tags).distinctBy { it.lowercase() }
        p.project?.let { name -> projects.value.firstOrNull { it.name.equals(name, true) }?.let { projectId = it.id } }
        if (p.reminders.isNotEmpty()) reminders = (reminders + p.reminders).distinct().sorted()
        p.recurrence?.let { freq = it.freq; interval = it.interval; days = it.days }
    }

    fun save(onDone: () -> Unit) {
        if (title.isBlank()) return
        addCheckItem() // don't lose an item that was typed but not yet added
        viewModelScope.launch {
            val effectiveDate = date ?: if (time != null || freq != null) LocalDate.now() else null
            val start = effectiveDate?.atTime(time ?: LocalTime.MIDNIGHT)?.atZone(zone)?.toInstant()?.toEpochMilli()
            val end = if (start != null && (durationMin ?: 0) > 0) start + durationMin!! * 60_000L else null
            val tags = labels.map { it.trim().removePrefix("@") }.filter { it.isNotEmpty() }.distinctBy { it.lowercase() }
            tags.forEach { planner.addTag(it, 0xFF625B71) } // keeps any new label in the saved list (existing ones keep their colour)
            val cl = Checklist.toJson(checklist.filter { it.text.isNotBlank() })
            val rule = freq?.let { RecurrenceRule(it, interval.coerceAtLeast(1), if (it == Freq.WEEKLY) days else emptySet()) }

            if (isNote) {
                val base = originalNote ?: NoteEntity(title = title)
                val n = base.copy(title = title.trim(), description = description, startDateTime = start, endDateTime = end,
                    priority = priority, tags = tags, projectId = projectId, checklistJson = cl)
                if (isNew) timeline.addNote(n) else timeline.updateNote(n)
            } else {
                val base = originalTask ?: TaskEntity(title = title)
                val t = base.copy(title = title.trim(), description = description, startDateTime = start, endDateTime = end,
                    priority = priority, tags = tags, projectId = projectId, checklistJson = cl,
                    reminderOffsetsMin = reminders.distinct().sorted(),
                    isRecurring = rule != null, recurrenceRule = rule?.encode())
                if (isNew) timeline.addTask(t) else timeline.updateTask(t)
            }
            onDone()
        }
    }

    fun delete(onDone: () -> Unit) {
        viewModelScope.launch {
            val item = TimelineItem(id, if (isNote) ItemKind.NOTE else ItemKind.TASK, title, "", null, null, 0, emptyList(), false)
            timeline.delete(item)
            onDone()
        }
    }
}
