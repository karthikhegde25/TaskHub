// SPDX-License-Identifier: GPL-3.0-only
// TaskHub, Copyright (C) 2026 Karthik Hegde. Licensed under the GNU General Public License v3.0; see LICENSE.

package com.kARThik.tasks.data.repository

import android.content.Context
import com.kARThik.tasks.data.local.CalendarEventDao
import com.kARThik.tasks.data.local.NoteDao
import com.kARThik.tasks.data.local.NoteEntity
import com.kARThik.tasks.data.local.ReminderDao
import com.kARThik.tasks.data.local.TaskDao
import com.kARThik.tasks.data.local.TaskEntity
import com.kARThik.tasks.domain.Checklist
import com.kARThik.tasks.domain.RecurrenceRule
import com.kARThik.tasks.notifications.ReminderScheduler
import com.kARThik.tasks.widget.WidgetUpdater
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

enum class ItemKind { TASK, NOTE, EVENT, REMINDER }

/** Unified row shown in the Timeline and Agenda: tasks, notes, calendar events and reminders. */
data class TimelineItem(
    val id: Long,
    val kind: ItemKind,
    val title: String,
    val description: String,
    val startMillis: Long?,
    val endMillis: Long?,
    val priority: Int,
    val tags: List<String>,
    val completed: Boolean,
    val projectId: Long? = null,
    val isRecurring: Boolean = false,
    val color: Long? = null,
    val checklistDone: Int = 0,
    val checklistTotal: Int = 0,
    val hasReminder: Boolean = false,
) {
    /** False for all-day items (stored at local midnight). */
    val hasTime: Boolean
        get() = startMillis != null &&
            Instant.ofEpochMilli(startMillis).atZone(ZoneId.systemDefault()).toLocalTime().toSecondOfDay() != 0

    val editable: Boolean get() = kind == ItemKind.TASK || kind == ItemKind.NOTE
}

@Singleton
class TimelineRepository @Inject constructor(
    @ApplicationContext private val ctx: Context,
    private val taskDao: TaskDao,
    private val noteDao: NoteDao,
    private val eventDao: CalendarEventDao,
    private val reminderDao: ReminderDao,
    private val scheduler: ReminderScheduler,
) {
    private val zone: ZoneId get() = ZoneId.systemDefault()

    val tasks: Flow<List<TaskEntity>> = taskDao.observeAll()
    val notes: Flow<List<NoteEntity>> = noteDao.observeAll()

    fun observeTimeline(): Flow<List<TimelineItem>> =
        combine(taskDao.observeAll(), noteDao.observeAll(), eventDao.observeAll(), reminderDao.observeAll()) { t, n, e, r ->
            val items = ArrayList<TimelineItem>(t.size + n.size + e.size + r.size)
            t.mapTo(items) { it.toItem() }
            n.mapTo(items) { it.toItem() }
            e.mapTo(items) {
                TimelineItem(
                    it.id, ItemKind.EVENT, it.title, it.description, it.startMillis,
                    it.endMillis.takeIf { end -> end > it.startMillis }, 0, emptyList(), false, color = it.color,
                )
            }
            r.filter { it.showInTimeline && it.enabled }.mapTo(items) {
                TimelineItem(it.id, ItemKind.REMINDER, it.title, "", it.triggerAt, null, 0, emptyList(), false,
                    isRecurring = it.repeatUnit != null)
            }
            items
        }

    // ---------- tasks ----------

    suspend fun addTask(task: TaskEntity): Long {
        val id = taskDao.insert(task)
        var saved = task.copy(id = id)
        if (task.isRecurring && task.seriesId == null) {
            saved = saved.copy(seriesId = id)
            taskDao.update(saved)
        }
        scheduler.scheduleTask(saved)
        WidgetUpdater.refresh(ctx)
        return id
    }

    suspend fun updateTask(task: TaskEntity) {
        val t = if (task.isRecurring && task.seriesId == null) task.copy(seriesId = task.id) else task
        taskDao.update(t.copy(updatedAt = System.currentTimeMillis()))
        scheduler.scheduleTask(t)
        WidgetUpdater.refresh(ctx)
    }

    suspend fun getTask(id: Long): TaskEntity? = taskDao.get(id)

    /**
     * Marks a task done/undone. Completing a recurring task creates the next instance, so editing
     * a series only ever affects future instances; completed history is left untouched.
     */
    suspend fun setTaskCompleted(id: Long, done: Boolean) {
        val t = taskDao.get(id) ?: return
        val series = if (t.isRecurring) t.seriesId ?: t.id else t.seriesId
        val updated = t.copy(isCompleted = done, seriesId = series, updatedAt = System.currentTimeMillis())
        taskDao.update(updated)
        scheduler.scheduleTask(updated)
        if (done && t.isRecurring) spawnNext(updated)
        // Un-ticking a repeating task removes the next instance that ticking created.
        if (!done && t.isRecurring && series != null) removeTasks(taskDao.openIdsInSeries(series, t.id))
        WidgetUpdater.refresh(ctx)
    }

    private suspend fun spawnNext(t: TaskEntity) {
        val rule = RecurrenceRule.parse(t.recurrenceRule) ?: return
        val start = t.startDateTime ?: return
        val next = rule.next(Instant.ofEpochMilli(start).atZone(zone).toLocalDateTime())
        val newStart = next.atZone(zone).toInstant().toEpochMilli()
        val resetChecklist = Checklist.toJson(Checklist.parse(t.checklistJson).map { it.copy(done = false) })
        val copy = t.copy(
            id = 0,
            startDateTime = newStart,
            endDateTime = t.endDateTime?.let { newStart + (it - start) },
            isCompleted = false,
            seriesId = t.seriesId ?: t.id,
            checklistJson = resetChecklist,
            sortOrder = System.currentTimeMillis(),
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
        )
        addTask(copy)
    }

    /** Moves tasks that are overdue (incomplete, dated before today) to today, keeping time of day. */
    suspend fun moveOverdueToToday() {
        val today = LocalDate.now()
        val startOfToday = today.atStartOfDay(zone).toInstant().toEpochMilli()
        taskDao.getAll()
            .filter { !it.isCompleted && !it.isCancelled && (it.startDateTime ?: Long.MAX_VALUE) < startOfToday }
            .forEach { t ->
                val old = t.startDateTime!!
                val time = Instant.ofEpochMilli(old).atZone(zone).toLocalTime()
                val newStart = LocalDateTime.of(today, time).atZone(zone).toInstant().toEpochMilli()
                updateTask(t.copy(startDateTime = newStart, endDateTime = t.endDateTime?.let { newStart + (it - old) }))
            }
    }

    // ---------- notes ----------

    suspend fun addNote(note: NoteEntity): Long {
        val id = noteDao.insert(note)
        WidgetUpdater.refresh(ctx)
        return id
    }

    suspend fun updateNote(note: NoteEntity) {
        noteDao.update(note.copy(updatedAt = System.currentTimeMillis()))
        WidgetUpdater.refresh(ctx)
    }

    suspend fun getNote(id: Long): NoteEntity? = noteDao.get(id)

    // ---------- shared ----------

    suspend fun setCompleted(item: TimelineItem, done: Boolean) {
        when (item.kind) {
            ItemKind.TASK -> setTaskCompleted(item.id, done)
            ItemKind.REMINDER -> completeReminder(item.id)
            else -> Unit
        }
    }

    /** Dismissing a repeating reminder advances it; one-off reminders are switched off. */
    private suspend fun completeReminder(id: Long) {
        val r = reminderDao.get(id) ?: return
        if (r.createCompletedTask) {
            taskDao.insert(TaskEntity(title = r.title, startDateTime = r.triggerAt, isCompleted = true))
        }
        val next = RecurrenceRule.nextTrigger(r.triggerAt, r.repeatUnit, r.repeatInterval, System.currentTimeMillis())
        val updated = if (next != null) r.copy(triggerAt = next) else r.copy(enabled = false)
        reminderDao.insert(updated)
        scheduler.scheduleReminder(updated)
        WidgetUpdater.refresh(ctx)
    }

    suspend fun delete(item: TimelineItem) {
        when (item.kind) {
            ItemKind.TASK -> deleteTask(item.id)
            ItemKind.NOTE -> noteDao.delete(item.id)
            ItemKind.REMINDER -> { scheduler.cancelReminder(item.id); reminderDao.delete(item.id) }
            ItemKind.EVENT -> Unit // imported calendar events are read-only
        }
        WidgetUpdater.refresh(ctx)
    }

    private suspend fun removeTasks(ids: List<Long>) {
        if (ids.isEmpty()) return
        ids.forEach { scheduler.cancelTask(it) }
        taskDao.deleteAll(ids)
    }

    /** Deleting a repeating task removes the whole series (all its open instances), not just one day. */
    private suspend fun deleteTask(id: Long) {
        val t = taskDao.get(id)
        val ids = mutableListOf(id)
        if (t != null && t.isRecurring) t.seriesId?.let { ids += taskDao.openIdsInSeries(it, id) }
        removeTasks(ids)
    }

    /** Agenda drag/resize: set new start/end for a task or note. */
    suspend fun reschedule(item: TimelineItem, startMillis: Long, endMillis: Long) {
        when (item.kind) {
            ItemKind.TASK -> taskDao.get(item.id)?.let { updateTask(it.copy(startDateTime = startMillis, endDateTime = endMillis)) }
            ItemKind.NOTE -> noteDao.get(item.id)?.let { updateNote(it.copy(startDateTime = startMillis, endDateTime = endMillis)) }
            else -> Unit
        }
    }

    private fun TaskEntity.toItem(): TimelineItem {
        val cl = Checklist.parse(checklistJson)
        return TimelineItem(
            id, ItemKind.TASK, title, description, startDateTime, endDateTime, priority, tags,
            isCompleted, projectId, isRecurring, null, cl.count { it.done }, cl.size,
            hasReminder = reminderOffsetsMin.isNotEmpty(),
        )
    }

    private fun NoteEntity.toItem(): TimelineItem {
        val cl = Checklist.parse(checklistJson)
        return TimelineItem(
            id, ItemKind.NOTE, title, description, startDateTime, endDateTime, priority, tags,
            false, projectId, false, null, cl.count { it.done }, cl.size,
        )
    }
}
