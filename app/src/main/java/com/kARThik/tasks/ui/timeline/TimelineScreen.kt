// SPDX-License-Identifier: GPL-3.0-only
// TaskHub, Copyright (C) 2026 Karthik Hegde. Licensed under the GNU General Public License v3.0; see LICENSE.

package com.kARThik.tasks.ui.timeline

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.Dp
import kotlinx.coroutines.delay
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.StickyNote2
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kARThik.tasks.data.repository.ItemKind
import com.kARThik.tasks.data.repository.TimelineItem
import com.kARThik.tasks.data.repository.UserPrefs
import com.kARThik.tasks.ui.common.LabelDots
import com.kARThik.tasks.ui.common.LocalTagColors
import com.kARThik.tasks.ui.common.labelColor
import com.kARThik.tasks.ui.theme.PriorityColors
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale

private val dateFmt = DateTimeFormatter.ofPattern("EEE, MMM d", Locale.getDefault())
private val timeFmt = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
private val EventOrange = Color(0xFFF28B30)
private val RailTop = 7.dp      // line above a marker, so the marker lines up with the title
private val MarkerSize = 26.dp

private fun dayName(d: LocalDate): String {
    val today = LocalDate.now()
    return when (d) {
        today -> "Today"
        today.plusDays(1) -> "Tomorrow"
        today.minusDays(1) -> "Yesterday"
        else -> d.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())
    }
}

@Composable
private fun accentOf(item: TimelineItem): Color {
    val cs = MaterialTheme.colorScheme
    return when (item.kind) {
        ItemKind.TASK -> PriorityColors.of(item.priority) ?: cs.primary
        ItemKind.NOTE -> cs.tertiary
        ItemKind.EVENT -> item.color?.let { Color(it) } ?: EventOrange
        ItemKind.REMINDER -> EventOrange
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun TimelineScreen(
    prefs: UserPrefs,
    onPrefsChange: (UserPrefs) -> Unit,
    onOpenItem: (TimelineItem) -> Unit,
    onAddTask: (LocalDate) -> Unit,
    onSearch: () -> Unit,
    onOpenUnplanned: () -> Unit,
    onOpenOverdue: () -> Unit,
    vm: TimelineViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    var menuOpen by remember { mutableStateOf(false) }
    var firstScroll by remember { mutableStateOf(true) }
    val focusDay = prefs.dayMode || prefs.agendaMode
    val density = LocalDensity.current

    // The bottom calendar slides away while the list is moving and comes back once it stops.
    var calendarShown by remember { mutableStateOf(true) }
    val scrolling = listState.isScrollInProgress
    LaunchedEffect(scrolling, prefs.agendaMode) {
        if (prefs.agendaMode) { calendarShown = true; return@LaunchedEffect }
        if (scrolling) calendarShown = false
        else { delay(450); calendarShown = true }
    }
    // Day currently at the top of the list, so the calendar can offer "Today" after scrolling far away.
    var jumpTick by remember { mutableStateOf(0) }
    val visibleDate by remember(state.groups) {
        androidx.compose.runtime.derivedStateOf {
            var idx = listState.firstVisibleItemIndex
            var found: LocalDate? = state.groups.lastOrNull()?.date
            for (g in state.groups) {
                val n = 2 + g.items.size
                if (idx < n) { found = g.date; break }
                idx -= n
            }
            found
        }
    }
    val scrolledAway = !prefs.dayMode && !prefs.agendaMode && visibleDate != null && visibleDate != LocalDate.now()
    var calendarHeight by remember { mutableStateOf(0.dp) }
    // The + button rides down into the calendar's place while it is hidden, and back up when it returns.
    val fabBottom by animateDpAsState(if (calendarShown) calendarHeight + 16.dp else 16.dp, label = "fab-offset")

    // Reopening the app after being away starts on today again.
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            when (event) {
                androidx.lifecycle.Lifecycle.Event.ON_STOP -> vm.onAppStopped()
                androidx.lifecycle.Lifecycle.Event.ON_START -> vm.onAppStarted()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Each day is: one header item (the big Today card for the selected day), its entries, and an "Add task" row.
    // Scroll so the selected day's header sits at the top; past days stay above, future days below.
    LaunchedEffect(state.selectedDate, jumpTick, prefs.dayMode, prefs.dateDescending, prefs.agendaMode, state.groups.isNotEmpty()) {
        if (prefs.dayMode) { listState.scrollToItem(0); return@LaunchedEffect }
        if (prefs.agendaMode || state.groups.isEmpty()) return@LaunchedEffect
        val target = state.groups.firstOrNull { it.date == state.selectedDate } ?: return@LaunchedEffect
        val index = state.groups.takeWhile { it !== target }.sumOf { 2 + it.items.size }
        if (firstScroll) { listState.scrollToItem(index); firstScroll = false } else listState.animateScrollToItem(index)
    }

    // Endless scrolling: when the user nears either end, add another month of days there.
    LaunchedEffect(listState, prefs.dayMode, prefs.agendaMode, prefs.dateDescending) {
        if (prefs.dayMode || prefs.agendaMode) return@LaunchedEffect
        snapshotFlow {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            Triple(listState.firstVisibleItemIndex < 12, last >= info.totalItemsCount - 12, info.totalItemsCount)
        }.collect { (nearTop, nearBottom, _) ->
            if (nearTop) { if (prefs.dateDescending) vm.extendFuture() else vm.extendPast() }
            if (nearBottom) { if (prefs.dateDescending) vm.extendPast() else vm.extendFuture() }
        }
    }

    // Days that have entries, oldest first. Horizontal swipes jump to the next/previous one.
    val dayStops = state.groups.map { it.date }.sorted()
    fun swipeDay(forward: Boolean) {
        if (focusDay) vm.shiftDay(if (forward) 1 else -1)
        else {
            val sel = state.selectedDate
            val target = if (forward) dayStops.firstOrNull { it.isAfter(sel) } else dayStops.lastOrNull { it.isBefore(sel) }
            if (target != null) vm.select(target)
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text("TaskHub", style = MaterialTheme.typography.headlineSmall) },
                actions = {
                    IconButton(onClick = onSearch) { Icon(Icons.Default.Search, "Search") }
                    IconButton(onClick = { onPrefsChange(prefs.copy(agendaMode = !prefs.agendaMode)) }) {
                        Icon(
                            if (prefs.agendaMode) Icons.Default.Timeline else Icons.Default.ViewAgenda,
                            if (prefs.agendaMode) "Switch to Timeline" else "Switch to Agenda View",
                        )
                    }
                    IconButton(onClick = { menuOpen = true }) { Icon(Icons.Default.MoreVert, "Timeline options") }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        OptionItem("Day Mode", prefs.dayMode) { onPrefsChange(prefs.copy(dayMode = !prefs.dayMode)) }
                        OptionItem("Show tags", prefs.showTags) { onPrefsChange(prefs.copy(showTags = !prefs.showTags)) }
                        OptionItem("Show timestamps", prefs.showTimestamps) {
                            onPrefsChange(prefs.copy(showTimestamps = !prefs.showTimestamps))
                        }
                        OptionItem("Newest date first", prefs.dateDescending) {
                            onPrefsChange(prefs.copy(dateDescending = !prefs.dateDescending))
                        }
                        OptionItem("Sort items by priority", prefs.sortByPriority) {
                            onPrefsChange(prefs.copy(sortByPriority = !prefs.sortByPriority))
                        }
                        DropdownMenuItem(text = { Text("Move overdue to today") }, onClick = { vm.moveOverdueToToday(); menuOpen = false })
                        DropdownMenuItem(text = { Text("Go to today") }, onClick = { vm.goToday(); menuOpen = false })
                    }
                },
            )
        },
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            Crossfade(
                targetState = prefs.agendaMode,
                label = "timeline-agenda",
                modifier = Modifier.fillMaxSize().pointerInput(focusDay, state.selectedDate, dayStops) {
                    val threshold = with(density) { 72.dp.toPx() }
                    var dragX = 0f
                    detectHorizontalDragGestures(
                        onDragStart = { dragX = 0f },
                        onDragEnd = {
                            if (dragX <= -threshold) swipeDay(forward = true)
                            else if (dragX >= threshold) swipeDay(forward = false)
                            dragX = 0f
                        },
                        onDragCancel = { dragX = 0f },
                        onHorizontalDrag = { change, amount -> change.consume(); dragX += amount },
                    )
                },
            ) { agenda ->
                if (agenda) {
                    Column(Modifier.fillMaxSize().padding(bottom = calendarHeight)) {
                        AgendaView(
                            items = state.dayItems,
                            date = state.selectedDate,
                            onOpen = onOpenItem,
                            onReschedule = vm::reschedule,
                            modifier = Modifier.weight(1f),
                        )
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = calendarHeight + 96.dp),
                    ) {
                        state.groups.forEach { group ->
                            val isSelected = group.date == state.selectedDate
                            if (isSelected) {
                                item(key = "h-${group.date}") { DayHeader(state, onOpenUnplanned, onOpenOverdue) }
                            } else {
                                stickyHeader(key = "h-${group.date}") {
                                    Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
                                        Text(
                                            dateFmt.format(group.date),
                                            style = MaterialTheme.typography.titleLarge,
                                            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 8.dp),
                                        )
                                    }
                                }
                            }
                            itemsIndexed(group.items, key = { _, entry -> "${entry.kind}-${entry.id}-${group.date}" }) { index, item ->
                                TimelineRow(
                                    item = item,
                                    prev = group.items.getOrNull(index - 1),
                                    prefs = prefs,
                                    onToggle = { vm.toggleComplete(item) },
                                    onClick = { onOpenItem(item) },
                                    modifier = Modifier.animateItem(),
                                )
                            }
                            item(key = "a-${group.date}") {
                                AddRow(
                                    label = if (group.date == LocalDate.now()) "Add task for today" else "Add task",
                                    prev = group.items.lastOrNull(),
                                    onClick = { onAddTask(group.date) },
                                )
                            }
                        }
                    }
                }
            }

            FloatingActionButton(
                onClick = { onAddTask(state.selectedDate) },
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = fabBottom),
            ) { Icon(Icons.Default.Add, "Add") }

            AnimatedVisibility(
                visible = calendarShown,
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter).onSizeChanged { calendarHeight = with(density) { it.height.toDp() } },
            ) {
                MiniCalendar(
                    selected = state.selectedDate,
                    markedDates = state.datesWithItems,
                    overdueDates = state.overdueDates,
                    onSelect = vm::select,
                    scrolledAway = scrolledAway,
                    onToday = { vm.goToday(); jumpTick++ },
                )
            }
        }
    }
}

@Composable
private fun OptionItem(label: String, checked: Boolean, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(label) },
        onClick = onClick,
        trailingIcon = { if (checked) Icon(Icons.Default.Done, null) },
    )
}

/** Date, big day name and the To Do / Overdue / Unplanned summary cards. */
@Composable
private fun DayHeader(state: TimelineUiState, onOpenUnplanned: () -> Unit, onOpenOverdue: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 12.dp)) {
        Text(dateFmt.format(state.selectedDate), style = MaterialTheme.typography.titleLarge)
        Text(
            dayName(state.selectedDate),
            style = MaterialTheme.typography.headlineLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard("To Do", state.todoCount, Modifier.weight(1f))
            StatCard("Overdue", state.overdueCount, Modifier.weight(1f), highlight = state.overdueCount > 0, onClick = onOpenOverdue)
            StatCard("Unplanned", state.unplannedCount, Modifier.weight(1f), onClick = onOpenUnplanned)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StatCard(label: String, value: Int, modifier: Modifier = Modifier, highlight: Boolean = false, onClick: (() -> Unit)? = null) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        onClick = onClick ?: {},
        enabled = onClick != null,
    ) {
        Column(Modifier.padding(vertical = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                value.toString(),
                style = MaterialTheme.typography.headlineMedium,
                color = if (highlight) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            )
            Text(label, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

/** A vertical rail segment of the given height; invisible when there is nothing to connect to. */
@Composable
private fun RailLine(color: Color?, modifier: Modifier) {
    Box(modifier.width(2.dp).background(color ?: Color.Transparent))
}

/**
 * One Timeline entry on the vertical rail: circle checkbox (tasks), icon (notes, events, reminders),
 * title, a clock line with the time range, and coloured label pills. The rail line runs from the
 * previous marker to this one and on to the next row, so consecutive entries are joined.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TimelineRow(
    item: TimelineItem,
    prev: TimelineItem?,
    prefs: UserPrefs,
    onToggle: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    val tagColors = LocalTagColors.current
    val accent = accentOf(item)
    val prevAccent = prev?.let { accentOf(it) }
    val zone = ZoneId.systemDefault()

    Row(
        modifier.fillMaxWidth().height(IntrinsicSize.Min).clickable(onClick = onClick).padding(horizontal = 16.dp),
    ) {
        Column(Modifier.width(32.dp).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
            RailLine(prevAccent, Modifier.height(RailTop))
            Box(
                Modifier.size(MarkerSize).then(if (item.kind == ItemKind.TASK) Modifier.clip(CircleShape).clickable(onClick = onToggle) else Modifier),
                contentAlignment = Alignment.Center,
            ) {
                when (item.kind) {
                    ItemKind.TASK -> Box(
                        Modifier.fillMaxSize().clip(CircleShape)
                            .background(if (item.completed) accent else Color.Transparent)
                            .border(2.dp, accent, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (item.completed) Icon(
                            Icons.Default.Check, "Completed", Modifier.size(17.dp),
                            tint = if (accent.luminance() > 0.5f) Color(0xFF10131A) else Color.White,
                        )
                    }
                    ItemKind.NOTE -> Icon(Icons.Outlined.StickyNote2, "Note", Modifier.size(24.dp), tint = accent)
                    ItemKind.EVENT -> Icon(Icons.Outlined.CalendarToday, "Calendar event", Modifier.size(24.dp), tint = accent)
                    ItemKind.REMINDER -> Icon(Icons.Outlined.Alarm, "Reminder", Modifier.size(24.dp), tint = accent)
                }
            }
            RailLine(accent, Modifier.weight(1f))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f).padding(top = 8.dp, bottom = 12.dp)) {
            Text(
                item.title,
                style = MaterialTheme.typography.titleMedium,
                textDecoration = if (item.completed) TextDecoration.LineThrough else null,
                color = if (item.completed) cs.onSurfaceVariant else cs.onSurface,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.padding(top = 4.dp),
            ) {
                if (prefs.showTimestamps && item.hasTime) {
                    val start = Instant.ofEpochMilli(item.startMillis!!).atZone(zone)
                    val range = timeFmt.format(start) +
                        (item.endMillis?.let { " - " + timeFmt.format(Instant.ofEpochMilli(it).atZone(zone)) } ?: "")
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Schedule, null, Modifier.size(16.dp), tint = cs.onSurfaceVariant)
                        Text("  $range", style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
                    }
                }
                if (item.priority > 0) {
                    Icon(
                        Icons.Default.Flag, PriorityColors.name(item.priority) + " priority", Modifier.size(16.dp),
                        tint = PriorityColors.of(item.priority) ?: cs.onSurfaceVariant,
                    )
                }
                if (item.hasReminder) Icon(Icons.Outlined.Notifications, "Reminder set", Modifier.size(16.dp), tint = cs.onSurfaceVariant)
                if (item.checklistTotal > 0) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Checklist, null, Modifier.size(16.dp), tint = cs.onSurfaceVariant)
                        Text(" ${item.checklistDone}/${item.checklistTotal}", style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
                    }
                }
                if (item.isRecurring) Icon(Icons.Default.Repeat, "Repeats", Modifier.size(16.dp), tint = cs.onSurfaceVariant)
                if (item.tags.isNotEmpty()) {
                    if (prefs.showTags) {
                        item.tags.take(4).forEach { tag ->
                            val bg = labelColor(tagColors, tag)
                            Text(
                                tag,
                                style = MaterialTheme.typography.labelMedium,
                                color = if (bg.luminance() > 0.6f) Color.Black else Color.White,
                                modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(bg).padding(horizontal = 8.dp, vertical = 2.dp),
                            )
                        }
                    } else {
                        LabelDots(item.tags)
                    }
                }
            }
        }
    }
}

/** The grey "+ Add task" row that closes each day and joins onto the last entry's rail line. */
@Composable
private fun AddRow(label: String, prev: TimelineItem?, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val prevAccent = prev?.let { accentOf(it) }
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).clickable(onClick = onClick).padding(horizontal = 16.dp)) {
        Column(Modifier.width(32.dp).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
            RailLine(prevAccent, Modifier.height(RailTop))
            Box(
                Modifier.size(MarkerSize).clip(CircleShape).background(cs.outline.copy(alpha = 0.55f)),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Default.Add, null, Modifier.size(18.dp), tint = cs.surface) }
        }
        Spacer(Modifier.width(12.dp))
        Text(
            label,
            style = MaterialTheme.typography.titleMedium,
            color = cs.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp, bottom = 12.dp),
        )
    }
}
