// SPDX-License-Identifier: GPL-3.0-only
// TaskHub, Copyright (C) 2026 Karthik Hegde. Licensed under the GNU General Public License v3.0; see LICENSE.

package com.kARThik.tasks.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import com.kARThik.tasks.data.local.ReminderDao
import com.kARThik.tasks.data.local.ReminderEntity
import com.kARThik.tasks.data.local.TaskDao
import com.kARThik.tasks.data.local.TaskEntity
import com.kARThik.tasks.domain.RecurrenceRule
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Schedules task reminders (any number per task) and standalone reminders with AlarmManager.
 * Each alarm is identified by a unique data Uri, so there is no cap on reminders per task.
 */
@Singleton
class ReminderScheduler @Inject constructor(
    @ApplicationContext private val ctx: Context,
    private val taskDao: TaskDao,
    private val reminderDao: ReminderDao,
) {
    private val am: AlarmManager get() = ctx.getSystemService(AlarmManager::class.java)
    private val store = ctx.getSharedPreferences("scheduled_alarms", Context.MODE_PRIVATE)

    private fun pending(uri: String, fill: Intent.() -> Unit = {}): PendingIntent {
        val i = Intent(ctx, ReminderReceiver::class.java).setData(Uri.parse(uri)).apply(fill)
        return PendingIntent.getBroadcast(ctx, 0, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun setAlarm(at: Long, pi: PendingIntent) {
        val exact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
        try {
            if (exact) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        } catch (e: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }
    }

    // ---- tasks ----

    fun scheduleTask(task: TaskEntity) {
        cancelTask(task.id)
        val start = task.startDateTime ?: return
        if (task.isCompleted || task.isCancelled) return
        val now = System.currentTimeMillis()
        task.reminderOffsetsMin.forEachIndexed { idx, offset ->
            val at = start - offset * 60_000L
            if (at > now) {
                setAlarm(at, pending("taskman://task/${task.id}/$idx") {
                    putExtra(EXTRA_KIND, KIND_TASK)
                    putExtra(EXTRA_ID, task.id)
                    putExtra(EXTRA_TITLE, task.title)
                })
            }
        }
        store.edit().putInt("t${task.id}", task.reminderOffsetsMin.size).apply()
    }

    fun cancelTask(id: Long) {
        val n = store.getInt("t$id", 0)
        repeat(n) { am.cancel(pending("taskman://task/$id/$it")) }
        am.cancel(pending("taskman://snooze/$KIND_TASK/$id"))
        store.edit().remove("t$id").apply()
    }

    // ---- standalone reminders ----

    fun scheduleReminder(r: ReminderEntity) {
        cancelReminder(r.id)
        if (!r.enabled || r.triggerAt <= System.currentTimeMillis()) return
        setAlarm(r.triggerAt, pending("taskman://reminder/${r.id}/0") {
            putExtra(EXTRA_KIND, KIND_REMINDER)
            putExtra(EXTRA_ID, r.id)
            putExtra(EXTRA_TITLE, r.title)
        })
    }

    fun cancelReminder(id: Long) {
        am.cancel(pending("taskman://reminder/$id/0"))
        am.cancel(pending("taskman://snooze/$KIND_REMINDER/$id"))
    }

    // ---- snooze / reschedule from the notification ----

    fun snooze(kind: String, id: Long, title: String, minutes: Int) {
        setAlarm(System.currentTimeMillis() + minutes * 60_000L, pending("taskman://snooze/$kind/$id") {
            putExtra(EXTRA_KIND, kind)
            putExtra(EXTRA_ID, id)
            putExtra(EXTRA_TITLE, title)
        })
    }

    /** Re-arms every alarm (after boot, app update, or periodically from WorkManager). */
    suspend fun rescheduleAll() {
        taskDao.getAll().forEach { scheduleTask(it) }
        val now = System.currentTimeMillis()
        reminderDao.getAll().forEach { r0 ->
            var r = r0
            if (r.enabled && r.triggerAt <= now) {
                val next = RecurrenceRule.nextTrigger(r.triggerAt, r.repeatUnit, r.repeatInterval, now)
                if (next != null) {
                    r = r.copy(triggerAt = next)
                    reminderDao.insert(r)
                }
            }
            scheduleReminder(r)
        }
    }

    companion object {
        const val EXTRA_KIND = "kind"
        const val EXTRA_ID = "id"
        const val EXTRA_TITLE = "title"
        const val KIND_TASK = "task"
        const val KIND_REMINDER = "reminder"
    }
}
