// SPDX-License-Identifier: GPL-3.0-only
// TaskHub, Copyright (C) 2026 Karthik Hegde. Licensed under the GNU General Public License v3.0; see LICENSE.

package com.kARThik.tasks.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        TaskEntity::class, NoteEntity::class, ProjectEntity::class, TagEntity::class,
        ReminderEntity::class, TemplateEntity::class, CalendarEventEntity::class,
    ],
    version = 3,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao
    abstract fun noteDao(): NoteDao
    abstract fun projectDao(): ProjectDao
    abstract fun tagDao(): TagDao
    abstract fun reminderDao(): ReminderDao
    abstract fun templateDao(): TemplateDao
    abstract fun calendarEventDao(): CalendarEventDao
}

/** v1 -> v2: repeating tasks get a series id. */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE tasks ADD COLUMN seriesId INTEGER")
    }
}

/** v2 -> v3: priorities become None/Low/Medium/High/Urgent (0-4). Old P1/P2/P3 become High/Medium/Low. */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        for (t in listOf("tasks", "notes")) {
            db.execSQL("UPDATE $t SET priority = CASE priority WHEN 1 THEN 3 WHEN 2 THEN 2 WHEN 3 THEN 1 ELSE priority END")
        }
    }
}
