// SPDX-License-Identifier: GPL-3.0-only
// TaskHub, Copyright (C) 2026 Karthik Hegde. Licensed under the GNU General Public License v3.0; see LICENSE.

package com.kARThik.tasks.data.repository

import android.content.Context
import com.kARThik.tasks.data.local.NoteEntity
import com.kARThik.tasks.data.local.ProjectDao
import com.kARThik.tasks.data.local.ProjectEntity
import com.kARThik.tasks.data.local.NoteDao
import com.kARThik.tasks.data.local.ReminderDao
import com.kARThik.tasks.data.local.ReminderEntity
import com.kARThik.tasks.data.local.TagDao
import com.kARThik.tasks.data.local.TagEntity
import com.kARThik.tasks.data.local.TaskDao
import com.kARThik.tasks.data.local.TaskEntity
import com.kARThik.tasks.data.local.TemplateDao
import com.kARThik.tasks.data.local.TemplateEntity
import com.kARThik.tasks.notifications.ReminderScheduler
import com.kARThik.tasks.widget.WidgetUpdater
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/** Projects, tags, standalone reminders and templates. */
@Singleton
class PlannerRepository @Inject constructor(
    @ApplicationContext private val ctx: Context,
    private val projectDao: ProjectDao,
    private val tagDao: TagDao,
    private val reminderDao: ReminderDao,
    private val templateDao: TemplateDao,
    private val taskDao: TaskDao,
    private val noteDao: NoteDao,
    private val scheduler: ReminderScheduler,
    private val timeline: TimelineRepository,
) {
    val projects: Flow<List<ProjectEntity>> = projectDao.observeAll()
    val tags: Flow<List<TagEntity>> = tagDao.observeAll()
    val reminders: Flow<List<ReminderEntity>> = reminderDao.observeAll()
    val templates: Flow<List<TemplateEntity>> = templateDao.observeAll()

    // ---- projects ----
    suspend fun addProject(name: String, color: Long, sortOrder: Int): Long =
        projectDao.insert(ProjectEntity(name = name.trim(), color = color, sortOrder = sortOrder))

    suspend fun deleteProject(id: Long) {
        taskDao.clearProject(id)
        noteDao.clearProject(id)
        projectDao.delete(id)
        WidgetUpdater.refresh(ctx)
    }

    // ---- tags ----
    suspend fun addTag(name: String, color: Long) {
        if (name.isNotBlank()) tagDao.insert(TagEntity(name = name.trim().removePrefix("@"), color = color))
    }

    suspend fun deleteTag(id: Long) = tagDao.delete(id)

    // ---- reminders ----
    suspend fun saveReminder(r: ReminderEntity) {
        val id = reminderDao.insert(r)
        scheduler.scheduleReminder(r.copy(id = if (r.id == 0L) id else r.id))
        WidgetUpdater.refresh(ctx)
    }

    suspend fun deleteReminder(id: Long) {
        scheduler.cancelReminder(id)
        reminderDao.delete(id)
        WidgetUpdater.refresh(ctx)
    }

    // ---- templates ----
    suspend fun saveTemplate(t: TemplateEntity) { templateDao.insert(t) }

    suspend fun deleteTemplate(id: Long) = templateDao.delete(id)

    /** Spawns a task or note from a template on [date] (all-day). */
    suspend fun useTemplate(t: TemplateEntity, date: LocalDate) {
        val start = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        if (t.isNote) {
            timeline.addNote(
                NoteEntity(title = t.title, description = t.description, startDateTime = start,
                    tags = t.tags, checklistJson = t.checklistJson)
            )
        } else {
            timeline.addTask(
                TaskEntity(title = t.title, description = t.description, startDateTime = start,
                    tags = t.tags, checklistJson = t.checklistJson)
            )
        }
    }
}
