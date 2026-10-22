// SPDX-License-Identifier: GPL-3.0-only
// TaskHub, Copyright (C) 2026 Karthik Hegde. Licensed under the GNU General Public License v3.0; see LICENSE.

package com.kARThik.tasks.ui.navigation

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kARThik.tasks.calendar.CalendarImporter
import com.kARThik.tasks.data.repository.PlannerRepository
import com.kARThik.tasks.data.repository.PrefsRepository
import com.kARThik.tasks.data.repository.TimelineRepository
import com.kARThik.tasks.data.repository.UserPrefs
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

data class Badges(val overdue: Int = 0, val today: Int = 0)

/** App-wide preferences, tab badges and calendar import status. */
@HiltViewModel
class AppViewModel @Inject constructor(
    private val repo: PrefsRepository,
    timeline: TimelineRepository,
    planner: PlannerRepository,
    private val importer: CalendarImporter,
) : ViewModel() {
    val prefs: StateFlow<UserPrefs> =
        repo.prefs.stateIn(viewModelScope, SharingStarted.Eagerly, UserPrefs())

    val badges: StateFlow<Badges> = timeline.tasks.map { tasks ->
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now()
        val open = tasks.filter { !it.isCompleted && !it.isCancelled && it.startDateTime != null }
        val days = open.map { Instant.ofEpochMilli(it.startDateTime!!).atZone(zone).toLocalDate() }
        Badges(overdue = days.count { it.isBefore(today) }, today = days.count { it == today })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Badges())

    /** Label colours (lower-cased name -> ARGB) for the coloured dots shown on tasks. */
    val tagColors: StateFlow<Map<String, Long>> = planner.tags
        .map { l -> l.associate { it.name.lowercase() to it.color } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    val importMessage = MutableStateFlow<String?>(null)

    fun update(p: UserPrefs) {
        viewModelScope.launch { repo.save(p) }
    }

    fun hasCalendarPermission(): Boolean = importer.hasPermission()

    fun importCalendar() {
        viewModelScope.launch {
            val n = importer.import()
            importMessage.value = if (!importer.hasPermission()) "Calendar permission not granted" else "Imported $n events"
        }
    }
}
