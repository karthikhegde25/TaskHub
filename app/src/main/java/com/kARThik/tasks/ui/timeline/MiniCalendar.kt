// SPDX-License-Identifier: GPL-3.0-only
// TaskHub, Copyright (C) 2026 Karthik Hegde. Licensed under the GNU General Public License v3.0; see LICENSE.

package com.kARThik.tasks.ui.timeline

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.time.temporal.TemporalAdjusters
import java.time.temporal.WeekFields
import java.util.Locale

/**
 * Bottom calendar: a week strip that expands into a month grid.
 * Swipe left/right to change week (or month); tap a day to jump the Timeline there.
 * Dots under a day: red = it still has incomplete past tasks, dark = it has entries.
 */
@Composable
fun MiniCalendar(
    selected: LocalDate,
    markedDates: Set<LocalDate>,
    overdueDates: Set<LocalDate>,
    onSelect: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
    /** True when the list behind the calendar has been scrolled to some other day than today. */
    scrolledAway: Boolean = false,
    onToday: () -> Unit = { onSelect(LocalDate.now()) },
) {
    var expanded by remember { mutableStateOf(false) }
    var anchor by remember(selected) { mutableStateOf(selected) }
    val today = LocalDate.now()
    val firstDow: DayOfWeek = WeekFields.of(Locale.getDefault()).firstDayOfWeek
    val lastDow: DayOfWeek = firstDow.minus(1)

    val gridStart: LocalDate
    val gridEnd: LocalDate
    if (expanded) {
        val first = anchor.withDayOfMonth(1)
        val last = anchor.withDayOfMonth(anchor.lengthOfMonth())
        gridStart = first.with(TemporalAdjusters.previousOrSame(firstDow))
        gridEnd = last.with(TemporalAdjusters.nextOrSame(lastDow))
    } else {
        gridStart = anchor.with(TemporalAdjusters.previousOrSame(firstDow))
        gridEnd = gridStart.plusDays(6)
    }
    val days = generateSequence(gridStart) { it.plusDays(1) }.takeWhile { !it.isAfter(gridEnd) }.toList()

    val density = LocalDensity.current
    var drag by remember { mutableFloatStateOf(0f) }

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            Modifier
                .padding(horizontal = 12.dp)
                .padding(top = 4.dp, bottom = 6.dp)
                .animateContentSize()
                // Swipe left/right to move to the next/previous week (or month when expanded).
                .pointerInput(expanded, anchor) {
                    val threshold = with(density) { 56.dp.toPx() }
                    detectHorizontalDragGestures(
                        onDragStart = { drag = 0f },
                        onDragEnd = {
                            if (drag <= -threshold) anchor = if (expanded) anchor.plusMonths(1) else anchor.plusWeeks(1)
                            else if (drag >= threshold) anchor = if (expanded) anchor.minusMonths(1) else anchor.minusWeeks(1)
                            drag = 0f
                        },
                        onDragCancel = { drag = 0f },
                        onHorizontalDrag = { change, amount -> change.consume(); drag += amount },
                    )
                },
        ) {
            Row(
                Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End,
            ) {
                // Blue "Today" chip on the left whenever the view has moved away from today.
                val visibleHasToday = today in days
                if (selected != today || !visibleHasToday || scrolledAway) {
                    Text(
                        "Today",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.primary)
                            .clickable { anchor = today; onToday() }
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                    )
                }
                androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
                Text(
                    "${anchor.month.getDisplayName(TextStyle.FULL, Locale.getDefault())} ${anchor.year}",
                    style = MaterialTheme.typography.titleMedium,
                )
                Icon(
                    if (expanded) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
                    if (expanded) "Collapse calendar" else "Expand calendar",
                    Modifier.padding(start = 12.dp),
                )
            }
            if (expanded) {
                Row(Modifier.fillMaxWidth()) {
                    (0..6).map { firstDow.plus(it.toLong()) }.forEach { dow ->
                        Text(
                            dow.getDisplayName(TextStyle.NARROW, Locale.getDefault()),
                            modifier = Modifier.weight(1f),
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            days.chunked(7).forEach { week ->
                Row(Modifier.fillMaxWidth()) {
                    week.forEach { d ->
                        DayCell(
                            date = d,
                            isSelected = d == selected,
                            isToday = d == today,
                            dimmed = expanded && d.month != anchor.month,
                            marked = d in markedDates,
                            overdue = d in overdueDates,
                            showLetter = !expanded,
                            onClick = { onSelect(d) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    date: LocalDate,
    isSelected: Boolean,
    isToday: Boolean,
    dimmed: Boolean,
    marked: Boolean,
    overdue: Boolean,
    showLetter: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    val textColor = when {
        isSelected -> cs.onPrimary
        dimmed -> cs.onSurface.copy(alpha = 0.38f)
        isToday -> cs.primary
        else -> cs.onSurface
    }
    Column(
        modifier.padding(vertical = 2.dp).clip(RoundedCornerShape(18.dp)).clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            Modifier
                .clip(RoundedCornerShape(18.dp))
                .background(if (isSelected) cs.primary else Color.Transparent)
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (showLetter) {
                Text(
                    date.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.getDefault()),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isSelected) cs.onPrimary else cs.onSurfaceVariant,
                )
            }
            Text(
                date.dayOfMonth.toString(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.Normal,
                color = textColor,
            )
        }
        Box(
            Modifier
                .padding(top = 2.dp)
                .size(5.dp)
                .clip(CircleShape)
                .background(
                    when {
                        overdue -> Color(0xFFE53935)
                        marked -> cs.onSurface
                        else -> Color.Transparent
                    },
                ),
        )
    }
}
