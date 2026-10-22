// SPDX-License-Identifier: GPL-3.0-only
// TaskHub, Copyright (C) 2026 Karthik Hegde. Licensed under the GNU General Public License v3.0; see LICENSE.

package com.kARThik.tasks.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** A task shown in the Timeline. Times are epoch millis (UTC); null start = unplanned. */
@Entity(tableName = "tasks")
data class TaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val description: String = "",
    val startDateTime: Long? = null,
    val endDateTime: Long? = null,
    /** 0 = none, 1 = P1 (highest) .. 3 = P3 */
    val priority: Int = 0,
    val tags: List<String> = emptyList(),
    val projectId: Long? = null,
    /** JSON array: [{"text":"..","done":false}] */
    val checklistJson: String = "[]",
    /** Minutes before start for each reminder */
    val reminderOffsetsMin: List<Int> = emptyList(),
    val isCompleted: Boolean = false,
    val isCancelled: Boolean = false,
    val isRecurring: Boolean = false,
    val recurrenceRule: String? = null,
    /** Shared by all instances of one repeating task, so deleting/uncompleting can find the others. */
    val seriesId: Long? = null,
    /** Manual ordering inside project boards (swap to reorder). */
    val sortOrder: Long = System.currentTimeMillis(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "notes")
data class NoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val description: String = "",
    val startDateTime: Long? = null,
    val endDateTime: Long? = null,
    val priority: Int = 0,
    val tags: List<String> = emptyList(),
    val projectId: Long? = null,
    val checklistJson: String = "[]",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val color: Long = 0xFF6750A4,
    val sortOrder: Int = 0,
)

@Entity(tableName = "tags", indices = [Index(value = ["name"], unique = true)])
data class TagEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val color: Long = 0xFF625B71,
)

/** Standalone repeating reminder (no overdue logic). */
@Entity(tableName = "reminders")
data class ReminderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val triggerAt: Long,
    /** DAILY, WEEKLY, MONTHLY, YEARLY or null for one-off */
    val repeatUnit: String? = null,
    val repeatInterval: Int = 1,
    val showInTimeline: Boolean = true,
    val createCompletedTask: Boolean = false,
    val enabled: Boolean = true,
)

@Entity(tableName = "templates")
data class TemplateEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val title: String,
    val description: String = "",
    val tags: List<String> = emptyList(),
    val checklistJson: String = "[]",
    val isNote: Boolean = false,
)

@Entity(tableName = "calendar_events")
data class CalendarEventEntity(
    @PrimaryKey val id: Long,
    val title: String,
    val description: String = "",
    val startMillis: Long,
    val endMillis: Long,
    val allDay: Boolean = false,
    val calendarName: String = "",
    val color: Long = 0xFF1E88E5,
)
