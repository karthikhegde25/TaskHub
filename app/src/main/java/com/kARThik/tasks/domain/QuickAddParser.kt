// SPDX-License-Identifier: GPL-3.0-only
// TaskHub, Copyright (C) 2026 Karthik Hegde. Licensed under the GNU General Public License v3.0; see LICENSE.

package com.kARThik.tasks.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.TemporalAdjusters

data class ParsedInput(
    val title: String,
    val date: LocalDate? = null,
    val time: LocalTime? = null,
    val durationMin: Int? = null,
    val priority: Int = 0,
    val tags: List<String> = emptyList(),
    val project: String? = null,
    val reminders: List<Int> = emptyList(),
    val recurrence: RecurrenceRule? = null,
)

/**
 * Natural-language quick add.
 * Examples: "Call mom tomorrow 5pm for 30m p1 @family #Home !30m !1h"
 *           "Gym every mon, wed 7am", "Pay rent every month", "Trip next monday"
 */
object QuickAddParser {
    private val I = RegexOption.IGNORE_CASE
    private const val DOW = "(?:monday|mon|tuesday|tues|tue|wednesday|wed|thursday|thurs|thur|thu|friday|fri|saturday|sat|sunday|sun)"
    private const val MONTHS = "(?:january|jan|february|feb|march|mar|april|apr|may|june|jun|july|jul|august|aug|september|sept|sep|october|oct|november|nov|december|dec)"

    private fun dow(token: String): DayOfWeek = when (token.lowercase().take(3)) {
        "mon" -> DayOfWeek.MONDAY
        "tue" -> DayOfWeek.TUESDAY
        "wed" -> DayOfWeek.WEDNESDAY
        "thu" -> DayOfWeek.THURSDAY
        "fri" -> DayOfWeek.FRIDAY
        "sat" -> DayOfWeek.SATURDAY
        else -> DayOfWeek.SUNDAY
    }

    private fun month(token: String): Int = when (token.lowercase().take(3)) {
        "jan" -> 1; "feb" -> 2; "mar" -> 3; "apr" -> 4; "may" -> 5; "jun" -> 6
        "jul" -> 7; "aug" -> 8; "sep" -> 9; "oct" -> 10; "nov" -> 11; else -> 12
    }

    fun parse(input: String, today: LocalDate = LocalDate.now()): ParsedInput {
        var s = " $input "
        var recurrence: RecurrenceRule? = null
        var date: LocalDate? = null
        var time: LocalTime? = null
        var duration: Int? = null
        var priority = 0
        val reminders = mutableListOf<Int>()
        val tags = mutableListOf<String>()
        var project: String? = null

        fun take(re: Regex, onMatch: (MatchResult) -> Unit): Boolean {
            val m = re.find(s) ?: return false
            onMatch(m)
            s = s.removeRange(m.range).let { it }
            return true
        }

        // --- recurrence ---
        if (!take(Regex("\\bevery\\s+($DOW(?:\\s*(?:,|and|&)?\\s*$DOW)*)\\b", I)) { m ->
                val days = Regex(DOW, I).findAll(m.groupValues[1]).map { dow(it.value) }.toSet()
                recurrence = RecurrenceRule(Freq.WEEKLY, 1, days)
            }) {
            if (!take(Regex("\\bevery\\s+weekdays?\\b", I)) {
                    recurrence = RecurrenceRule(
                        Freq.WEEKLY, 1,
                        setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY),
                    )
                }) {
                if (!take(Regex("\\bevery\\s+(?:(\\d+)\\s+)?(day|week|month|year)s?\\b", I)) { m ->
                        val n = m.groupValues[1].toIntOrNull() ?: 1
                        val f = when (m.groupValues[2].lowercase()) {
                            "day" -> Freq.DAILY; "week" -> Freq.WEEKLY; "month" -> Freq.MONTHLY; else -> Freq.YEARLY
                        }
                        recurrence = RecurrenceRule(f, n)
                    }) {
                    take(Regex("\\b(daily|weekly|monthly|yearly|annually)\\b", I)) { m ->
                        val f = when (m.groupValues[1].lowercase()) {
                            "daily" -> Freq.DAILY; "weekly" -> Freq.WEEKLY; "monthly" -> Freq.MONTHLY; else -> Freq.YEARLY
                        }
                        recurrence = RecurrenceRule(f, 1)
                    }
                }
            }
        }

        // --- reminders: !30m !1h !2d ---
        while (take(Regex("(?:^|\\s)!(\\d+)\\s?(m|min|mins|h|hr|hrs|d|days?)\\b", I)) { m ->
                val n = m.groupValues[1].toInt()
                reminders += when (m.groupValues[2].lowercase().first()) {
                    'h' -> n * 60
                    'd' -> n * 1440
                    else -> n
                }
            }) { /* keep taking */ }

        // --- duration: "for 30m", "for 1.5h" ---
        take(Regex("\\bfor\\s+(\\d+(?:\\.\\d+)?)\\s?(m|min|mins|minutes?|h|hr|hrs|hours?)\\b", I)) { m ->
            val n = m.groupValues[1].toDouble()
            duration = if (m.groupValues[2].lowercase().startsWith("h")) (n * 60).toInt() else n.toInt()
        }

        // --- priority ---
        // p1 = urgent ... p4 = low
        take(Regex("(?:^|\\s)p([1234])\\b", I)) { priority = 5 - it.groupValues[1].toInt() }

        // --- tags and project ---
        while (take(Regex("(?:^|\\s)@([\\w-]+)", I)) { tags += it.groupValues[1] }) { }
        take(Regex("(?:^|\\s)#([\\w-]+)", I)) { project = it.groupValues[1] }

        // --- time ---
        if (!take(Regex("\\b(?:at\\s+)?(\\d{1,2})(?::(\\d{2}))?\\s?(am|pm)\\b", I)) { m ->
                var h = m.groupValues[1].toInt() % 12
                if (m.groupValues[3].equals("pm", true)) h += 12
                time = LocalTime.of(h, m.groupValues[2].toIntOrNull() ?: 0)
            }) {
            take(Regex("\\b(?:at\\s+)?([01]?\\d|2[0-3]):([0-5]\\d)\\b")) { m ->
                time = LocalTime.of(m.groupValues[1].toInt(), m.groupValues[2].toInt())
            }
        }

        // --- date ---
        val isoOk = take(Regex("\\b(\\d{4})-(\\d{2})-(\\d{2})\\b")) { m ->
            date = runCatching {
                LocalDate.of(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt())
            }.getOrNull()
        }
        if (!isoOk) {
            val handled =
                take(Regex("\\b(today|tonight)\\b", I)) { date = today } ||
                take(Regex("\\b(tomorrow|tmrw|tmr)\\b", I)) { date = today.plusDays(1) } ||
                take(Regex("\\bnext\\s+week\\b", I)) { date = today.plusDays(7) } ||
                take(Regex("\\bin\\s+(\\d+)\\s+(day|days|week|weeks)\\b", I)) { m ->
                    val n = m.groupValues[1].toLong()
                    date = if (m.groupValues[2].startsWith("w", true)) today.plusWeeks(n) else today.plusDays(n)
                } ||
                take(Regex("\\bnext\\s+($DOW)\\b", I)) { m ->
                    date = today.with(TemporalAdjusters.next(dow(m.groupValues[1])))
                } ||
                take(Regex("\\b($MONTHS)\\s+(\\d{1,2})(?:st|nd|rd|th)?\\b", I)) { m ->
                    val mo = month(m.groupValues[1])
                    val day = m.groupValues[2].toInt()
                    date = runCatching {
                        var d = LocalDate.of(today.year, mo, day)
                        if (d.isBefore(today)) d = d.plusYears(1)
                        d
                    }.getOrNull()
                } ||
                take(Regex("\\b($DOW)\\b", I)) { m ->
                    date = today.with(TemporalAdjusters.nextOrSame(dow(m.groupValues[1])))
                }
            if (!handled && time != null) date = today
        }
        // Recurring items need an anchor date.
        if (recurrence != null && date == null) date = today

        val title = s.replace(Regex("\\s+"), " ").trim()
        return ParsedInput(
            title = title,
            date = date,
            time = time,
            durationMin = duration,
            priority = priority,
            tags = tags.distinct(),
            project = project,
            reminders = reminders.distinct().sorted(),
            recurrence = recurrence,
        )
    }
}
