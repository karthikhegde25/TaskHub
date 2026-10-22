// SPDX-License-Identifier: GPL-3.0-only
// TaskHub, Copyright (C) 2026 Karthik Hegde. Licensed under the GNU General Public License v3.0; see LICENSE.

package com.kARThik.tasks.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** All user-tunable options. Everything is free and unlimited. */
data class UserPrefs(
    val themeMode: Int = 0, // 0 system, 1 light, 2 dark
    val palette: Int = 0,   // see ui.theme.Palettes
    val dayMode: Boolean = false,
    val agendaMode: Boolean = false,
    val showTags: Boolean = true,
    val showTimestamps: Boolean = true,
    val dateDescending: Boolean = false,
    val sortByPriority: Boolean = false,
    /** Up to 3 middle tabs in the bottom nav (route names). */
    val navTabs: List<String> = listOf("board", "unplanned"),
    /** Sound played when a task is ticked: index into TickSounds.all (0 = none, 1 = Hero 1). */
    val tickSound: Int = 1,
)

@Singleton
class PrefsRepository @Inject constructor(private val store: DataStore<Preferences>) {
    private object K {
        val theme = intPreferencesKey("theme")
        val palette = intPreferencesKey("palette")
        val dayMode = booleanPreferencesKey("day_mode")
        val agenda = booleanPreferencesKey("agenda_mode")
        val tags = booleanPreferencesKey("show_tags")
        val times = booleanPreferencesKey("show_times")
        val desc = booleanPreferencesKey("date_desc")
        val prio = booleanPreferencesKey("sort_prio")
        val nav = stringPreferencesKey("nav_tabs")
        val tick = intPreferencesKey("tick_sound")
    }

    val prefs: Flow<UserPrefs> = store.data.map { p ->
        val d = UserPrefs()
        UserPrefs(
            themeMode = p[K.theme] ?: d.themeMode,
            palette = p[K.palette] ?: d.palette,
            dayMode = p[K.dayMode] ?: d.dayMode,
            agendaMode = p[K.agenda] ?: d.agendaMode,
            showTags = p[K.tags] ?: d.showTags,
            showTimestamps = p[K.times] ?: d.showTimestamps,
            dateDescending = p[K.desc] ?: d.dateDescending,
            sortByPriority = p[K.prio] ?: d.sortByPriority,
            navTabs = p[K.nav]?.split(",")?.filter { it.isNotBlank() } ?: d.navTabs,
            tickSound = p[K.tick] ?: d.tickSound,
        )
    }

    suspend fun save(u: UserPrefs) {
        store.edit {
            it[K.theme] = u.themeMode
            it[K.palette] = u.palette
            it[K.dayMode] = u.dayMode
            it[K.agenda] = u.agendaMode
            it[K.tags] = u.showTags
            it[K.times] = u.showTimestamps
            it[K.desc] = u.dateDescending
            it[K.prio] = u.sortByPriority
            it[K.nav] = u.navTabs.take(3).joinToString(",")
            it[K.tick] = u.tickSound
        }
    }
}
