// SPDX-License-Identifier: GPL-3.0-only
// TaskHub, Copyright (C) 2026 Karthik Hegde. Licensed under the GNU General Public License v3.0; see LICENSE.

package com.kARThik.tasks.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import com.kARThik.tasks.data.local.AppDatabase
import com.kARThik.tasks.data.local.MIGRATION_1_2
import com.kARThik.tasks.data.local.MIGRATION_2_3
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "taskman_prefs")

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides @Singleton
    fun database(@ApplicationContext c: Context): AppDatabase =
        Room.databaseBuilder(c, AppDatabase::class.java, "taskman.db").addMigrations(MIGRATION_1_2, MIGRATION_2_3).fallbackToDestructiveMigration().build()

    @Provides @Singleton
    fun dataStore(@ApplicationContext c: Context): DataStore<Preferences> = c.dataStore

    @Provides fun taskDao(db: AppDatabase) = db.taskDao()
    @Provides fun noteDao(db: AppDatabase) = db.noteDao()
    @Provides fun projectDao(db: AppDatabase) = db.projectDao()
    @Provides fun tagDao(db: AppDatabase) = db.tagDao()
    @Provides fun reminderDao(db: AppDatabase) = db.reminderDao()
    @Provides fun templateDao(db: AppDatabase) = db.templateDao()
    @Provides fun calendarEventDao(db: AppDatabase) = db.calendarEventDao()
}
