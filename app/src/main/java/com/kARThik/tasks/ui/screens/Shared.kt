// SPDX-License-Identifier: GPL-3.0-only
// TaskHub, Copyright (C) 2026 Karthik Hegde. Licensed under the GNU General Public License v3.0; see LICENSE.

package com.kARThik.tasks.ui.screens

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.RowScope
import com.kARThik.tasks.data.local.TaskEntity
import com.kARThik.tasks.domain.RecurrenceRule
import com.kARThik.tasks.ui.common.DatePickerDialogX
import com.kARThik.tasks.ui.common.TimePickerDialogX
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private val dateFmt = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
private val timeFmt = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SimpleTopBar(title: String, actions: @Composable RowScope.() -> Unit = {}) {
    TopAppBar(title = { Text(title) }, actions = actions)
}

/** Subtitle with date, time and repeat info for a task row. */
fun taskSubtitle(t: TaskEntity): String? {
    val parts = ArrayList<String>()
    t.startDateTime?.let {
        val z = Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault())
        parts += dateFmt.format(z)
        if (z.toLocalTime().toSecondOfDay() != 0) parts += timeFmt.format(z)
    }
    RecurrenceRule.parse(t.recurrenceRule)?.let { parts += it.describe() }
    if (t.tags.isNotEmpty()) parts += t.tags.joinToString(" ") { "@$it" }
    return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
}

/**
 * Date picker followed by an optional time picker. Dismissing the time picker keeps the item
 * all-day. Used to move Board / Unplanned tasks into the Timeline.
 */
@Composable
fun SchedulePickerFlow(onDone: (LocalDate, LocalTime?) -> Unit, onDismiss: () -> Unit) {
    var date by remember { mutableStateOf<LocalDate?>(null) }
    val d = date
    if (d == null) {
        DatePickerDialogX(LocalDate.now(), { date = it }, onDismiss)
    } else {
        TimePickerDialogX(null, { onDone(d, it) }, { onDone(d, null) })
    }
}
