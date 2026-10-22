// SPDX-License-Identifier: GPL-3.0-only
// TaskHub, Copyright (C) 2026 Karthik Hegde. Licensed under the GNU General Public License v3.0; see LICENSE.

package com.kARThik.tasks.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.kARThik.tasks.data.local.ReminderDao
import com.kARThik.tasks.data.local.TaskDao
import com.kARThik.tasks.domain.RecurrenceRule
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Fires when an alarm goes off: posts the notification and arms the next repeat of a reminder. */
@AndroidEntryPoint
class ReminderReceiver : BroadcastReceiver() {
    @Inject lateinit var taskDao: TaskDao
    @Inject lateinit var reminderDao: ReminderDao
    @Inject lateinit var scheduler: ReminderScheduler

    override fun onReceive(context: Context, intent: Intent) {
        val kind = intent.getStringExtra(ReminderScheduler.EXTRA_KIND) ?: return
        val id = intent.getLongExtra(ReminderScheduler.EXTRA_ID, -1)
        val extraTitle = intent.getStringExtra(ReminderScheduler.EXTRA_TITLE) ?: "Reminder"
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                when (kind) {
                    ReminderScheduler.KIND_TASK -> {
                        val t = taskDao.get(id)
                        if (t != null && !t.isCompleted && !t.isCancelled) Notifications.show(context, kind, id, t.title)
                    }
                    ReminderScheduler.KIND_REMINDER -> {
                        val r = reminderDao.get(id)
                        if (r != null && r.enabled) {
                            Notifications.show(context, kind, id, r.title.ifBlank { extraTitle })
                            // Repeating reminders have no overdue logic: just arm the next one.
                            val next = RecurrenceRule.nextTrigger(r.triggerAt, r.repeatUnit, r.repeatInterval, System.currentTimeMillis())
                            val updated = if (next != null) r.copy(triggerAt = next) else r.copy(enabled = false)
                            reminderDao.insert(updated)
                            scheduler.scheduleReminder(updated)
                        }
                    }
                }
            } finally {
                pending.finish()
            }
        }
    }
}
