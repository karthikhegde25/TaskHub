// SPDX-License-Identifier: GPL-3.0-only
// TaskHub, Copyright (C) 2026 Karthik Hegde. Licensed under the GNU General Public License v3.0; see LICENSE.

package com.kARThik.tasks.ui.timeline

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kARThik.tasks.data.repository.ItemKind
import com.kARThik.tasks.data.repository.PrefsRepository
import com.kARThik.tasks.data.repository.TimelineItem
import com.kARThik.tasks.data.repository.TimelineRepository
import com.kARThik.tasks.data.repository.UserPrefs
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

data class DayGroup(val date: LocalDate, val items: List<TimelineItem>)

data class TimelineUiState(
    val selectedDate: LocalDate = LocalDate.now(),
    /** One group per day that has entries, plus today and the next six days. Past days sit above, future days below. */
    val groups: List<DayGroup> = emptyList(),
    /** Everything on the selected day, for the Agenda View. */
    val dayItems: List<TimelineItem> = emptyList(),
    val datesWithItems: Set<LocalDate> = emptySet(),
    /** Past days that still have incomplete tasks (red dot in the calendar). */
    val overdueDates: Set<LocalDate> = emptySet(),
    /** Incomplete tasks on the selected day. */
    val todoCount: Int = 0,
    /** Incomplete tasks dated before today. */
    val overdueCount: Int = 0,
    /** Incomplete tasks with no date and no project. */
    val unplannedCount: Int = 0,
)

@HiltViewModel
class TimelineViewModel @Inject constructor(
    private val repo: TimelineRepository,
    private val prefsRepo: PrefsRepository,
    @dagger.hilt.android.qualifiers.ApplicationContext private val ctx: android.content.Context,
) : ViewModel() {

    private val selected = MutableStateFlow(LocalDate.now())

    /** How many days before / after today the list currently shows. It only grows, so scrolling never hits an end. */
    private val window = MutableStateFlow(14 to 30)

    fun extendPast() { window.value = window.value.let { it.first + 30 to it.second } }
    fun extendFuture() { window.value = window.value.let { it.first to it.second + 30 } }

    val state: StateFlow<TimelineUiState> =
        combine(repo.observeTimeline(), selected, prefsRepo.prefs, window) { items, date, prefs, win ->
            build(items, date, prefs, win)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TimelineUiState())

    fun select(date: LocalDate) { selected.value = date }
    fun shiftDay(delta: Long) { selected.value = selected.value.plusDays(delta) }
    fun goToday() { selected.value = LocalDate.now() }

    private var stoppedAt = 0L

    /** App went to the background. */
    fun onAppStopped() { stoppedAt = System.currentTimeMillis() }

    /**
     * App came back to the foreground: if it was away for a while (or the day changed),
     * start on today again. Quick trips such as screen rotation keep the current day.
     */
    fun onAppStarted() {
        val away = stoppedAt != 0L && System.currentTimeMillis() - stoppedAt > 30_000
        if (away) goToday()
    }

    fun toggleComplete(item: TimelineItem) =
        viewModelScope.launch {
            if (!item.completed && item.kind == ItemKind.TASK) {
                com.kARThik.tasks.audio.TickSounds.play(ctx, prefsRepo.prefs.first().tickSound)
            }
            repo.setCompleted(item, !item.completed)
        }

    fun delete(item: TimelineItem) = viewModelScope.launch { repo.delete(item) }

    fun moveOverdueToToday() = viewModelScope.launch { repo.moveOverdueToToday() }

    /** Agenda drag/resize result: new start (minutes from midnight of the selected day) and duration. */
    fun reschedule(item: TimelineItem, startMin: Int, durationMin: Int) {
        val zone = ZoneId.systemDefault()
        val start = selected.value.atStartOfDay(zone).plusMinutes(startMin.toLong()).toInstant().toEpochMilli()
        val end = start + durationMin * 60_000L
        viewModelScope.launch { repo.reschedule(item, start, end) }
    }

    private fun build(items: List<TimelineItem>, date: LocalDate, p: UserPrefs, win: Pair<Int, Int>): TimelineUiState {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now()
        fun TimelineItem.day(): LocalDate = Instant.ofEpochMilli(startMillis!!).atZone(zone).toLocalDate()

        val dated = items.filter { it.startMillis != null }
        val byDate = dated.groupBy { it.day() }

        val comparator: Comparator<TimelineItem> =
            if (p.sortByPriority)
                compareBy<TimelineItem>({ com.kARThik.tasks.ui.theme.PriorityColors.rank(it.priority) }, { it.startMillis })
            else compareBy { it.startMillis }
        // Completed items sink to the bottom of their day.
        val ordering = compareBy<TimelineItem> { it.completed }.then(comparator)

        val dates: List<LocalDate> =
            if (p.dayMode) listOf(date)
            else {
                // Every day in a continuous range, so the list can be scrolled endlessly in both directions.
                var start = today.minusDays(win.first.toLong())
                var end = today.plusDays(win.second.toLong())
                byDate.keys.minOrNull()?.let { if (it < start) start = maxOf(it, today.minusDays(365)) }
                byDate.keys.maxOrNull()?.let { if (it > end) end = minOf(it, today.plusDays(365)) }
                if (date.minusDays(7) < start) start = date.minusDays(7)
                if (date.plusDays(14) > end) end = date.plusDays(14)
                generateSequence(start) { it.plusDays(1) }.takeWhile { !it.isAfter(end) }.toList()
                    .let { if (p.dateDescending) it.reversed() else it }
            }
        val groups = dates.map { d -> DayGroup(d, (byDate[d] ?: emptyList()).sortedWith(ordering)) }

        val openTasks = dated.filter { it.kind == ItemKind.TASK && !it.completed }
        return TimelineUiState(
            selectedDate = date,
            groups = groups,
            dayItems = dated.filter { it.day() == date },
            datesWithItems = byDate.keys,
            overdueDates = openTasks.map { it.day() }.filter { it.isBefore(today) }.toSet(),
            todoCount = openTasks.count { it.day() == date },
            overdueCount = openTasks.count { it.day().isBefore(today) },
            unplannedCount = items.count {
                it.kind == ItemKind.TASK && it.startMillis == null && it.projectId == null && !it.completed
            },
        )
    }
}
