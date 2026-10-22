// SPDX-License-Identifier: GPL-3.0-only
// TaskHub, Copyright (C) 2026 Karthik Hegde. Licensed under the GNU General Public License v3.0; see LICENSE.

package com.kARThik.tasks.domain

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

enum class Freq { DAILY, WEEKLY, MONTHLY, YEARLY }

/**
 * Recurrence rule, encoded as e.g. "FREQ=WEEKLY;INTERVAL=2;BYDAY=MO,WE".
 * Unlimited: any frequency with any interval and (for weekly) any set of weekdays.
 */
data class RecurrenceRule(
    val freq: Freq,
    val interval: Int = 1,
    val days: Set<DayOfWeek> = emptySet(),
) {
    fun encode(): String = buildString {
        append("FREQ=$freq;INTERVAL=${interval.coerceAtLeast(1)}")
        if (freq == Freq.WEEKLY && days.isNotEmpty()) {
            append(";BYDAY=" + days.sortedBy { it.value }.joinToString(",") { it.name.take(2) })
        }
    }

    /** Next occurrence strictly after [after] (same time of day). */
    fun next(after: LocalDateTime): LocalDateTime {
        val n = interval.coerceAtLeast(1).toLong()
        return when (freq) {
            Freq.DAILY -> after.plusDays(n)
            Freq.WEEKLY -> if (days.isEmpty()) after.plusWeeks(n) else nextWeekday(after, n)
            Freq.MONTHLY -> after.plusMonths(n)
            Freq.YEARLY -> after.plusYears(n)
        }
    }

    private fun nextWeekday(after: LocalDateTime, n: Long): LocalDateTime {
        val base = after.toLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        var d = after.toLocalDate().plusDays(1)
        repeat((7 * n + 7).toInt()) {
            val weekStart = d.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            val weeks = ChronoUnit.WEEKS.between(base, weekStart)
            if (d.dayOfWeek in days && weeks % n == 0L) return LocalDateTime.of(d, after.toLocalTime())
            d = d.plusDays(1)
        }
        return after.plusWeeks(n)
    }

    fun describe(): String {
        val unit = when (freq) {
            Freq.DAILY -> "day"; Freq.WEEKLY -> "week"; Freq.MONTHLY -> "month"; Freq.YEARLY -> "year"
        }
        val base = if (interval <= 1) "Every $unit" else "Every $interval ${unit}s"
        return if (freq == Freq.WEEKLY && days.isNotEmpty())
            base + " on " + days.sortedBy { it.value }.joinToString(", ") { it.name.take(3).lowercase().replaceFirstChar(Char::uppercase) }
        else base
    }

    companion object {
        fun parse(s: String?): RecurrenceRule? {
            if (s.isNullOrBlank()) return null
            val parts = s.split(";").mapNotNull {
                val kv = it.split("=")
                if (kv.size == 2) kv[0] to kv[1] else null
            }.toMap()
            val f = runCatching { Freq.valueOf(parts["FREQ"] ?: return null) }.getOrNull() ?: return null
            val i = parts["INTERVAL"]?.toIntOrNull() ?: 1
            val d = parts["BYDAY"]?.split(",")?.mapNotNull { code ->
                DayOfWeek.values().firstOrNull { it.name.startsWith(code) }
            }?.toSet() ?: emptySet()
            return RecurrenceRule(f, i, d)
        }

        /** Next trigger of a standalone repeating reminder, strictly after [after]; null if not repeating. */
        fun nextTrigger(trigger: Long, unit: String?, interval: Int, after: Long = trigger): Long? {
            val f = runCatching { Freq.valueOf(unit ?: return null) }.getOrNull() ?: return null
            val zone = ZoneId.systemDefault()
            val rule = RecurrenceRule(f, interval.coerceAtLeast(1))
            var t = Instant.ofEpochMilli(trigger).atZone(zone).toLocalDateTime()
            var ms: Long
            do {
                t = rule.next(t)
                ms = t.atZone(zone).toInstant().toEpochMilli()
            } while (ms <= after)
            return ms
        }
    }
}
