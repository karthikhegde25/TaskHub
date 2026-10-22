// SPDX-License-Identifier: GPL-3.0-only
// TaskHub, Copyright (C) 2026 Karthik Hegde. Licensed under the GNU General Public License v3.0; see LICENSE.

package com.kARThik.tasks.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks ORDER BY startDateTime")
    fun observeAll(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks")
    suspend fun getAll(): List<TaskEntity>

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun get(id: Long): TaskEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(task: TaskEntity): Long

    @Update
    suspend fun update(task: TaskEntity)

    @Query("DELETE FROM tasks WHERE id = :id")
    suspend fun delete(id: Long)

    /** Open (not completed) instances of a repeating series, other than [exceptId]. */
    @Query("SELECT id FROM tasks WHERE seriesId = :sid AND isCompleted = 0 AND id != :exceptId")
    suspend fun openIdsInSeries(sid: Long, exceptId: Long): List<Long>

    @Query("DELETE FROM tasks WHERE id IN (:ids)")
    suspend fun deleteAll(ids: List<Long>)

    @Query("UPDATE tasks SET projectId = NULL WHERE projectId = :projectId")
    suspend fun clearProject(projectId: Long)
}

@Dao
interface NoteDao {
    @Query("SELECT * FROM notes ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE id = :id")
    suspend fun get(id: Long): NoteEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(note: NoteEntity): Long

    @Update
    suspend fun update(note: NoteEntity)

    @Query("DELETE FROM notes WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("UPDATE notes SET projectId = NULL WHERE projectId = :projectId")
    suspend fun clearProject(projectId: Long)
}

@Dao
interface ProjectDao {
    @Query("SELECT * FROM projects ORDER BY sortOrder")
    fun observeAll(): Flow<List<ProjectEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(project: ProjectEntity): Long

    @Query("DELETE FROM projects WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface TagDao {
    @Query("SELECT * FROM tags ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<TagEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(tag: TagEntity): Long

    @Query("DELETE FROM tags WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface ReminderDao {
    @Query("SELECT * FROM reminders ORDER BY triggerAt")
    fun observeAll(): Flow<List<ReminderEntity>>

    @Query("SELECT * FROM reminders")
    suspend fun getAll(): List<ReminderEntity>

    @Query("SELECT * FROM reminders WHERE id = :id")
    suspend fun get(id: Long): ReminderEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(reminder: ReminderEntity): Long

    @Query("DELETE FROM reminders WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface TemplateDao {
    @Query("SELECT * FROM templates ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<TemplateEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(template: TemplateEntity): Long

    @Query("DELETE FROM templates WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface CalendarEventDao {
    @Query("SELECT * FROM calendar_events ORDER BY startMillis")
    fun observeAll(): Flow<List<CalendarEventEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(events: List<CalendarEventEntity>)

    @Query("DELETE FROM calendar_events")
    suspend fun clear()
}
