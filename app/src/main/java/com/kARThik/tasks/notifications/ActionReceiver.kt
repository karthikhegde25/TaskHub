// SPDX-License-Identifier: GPL-3.0-only
// TaskHub, Copyright (C) 2026 Karthik Hegde. Licensed under the GNU General Public License v3.0; see LICENSE.

package com.kARThik.tasks.notifications

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.kARThik.tasks.data.repository.TimelineRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Handles the notification buttons: snooze / reschedule (+10m, +1h) and mark done. */
@AndroidEntryPoint
class ActionReceiver : BroadcastReceiver() {
    @Inject lateinit var scheduler: ReminderScheduler
    @Inject lateinit var repo: TimelineRepository

    override fun onReceive(context: Context, intent: Intent) {
        val kind = intent.getStringExtra(ReminderScheduler.EXTRA_KIND) ?: return
        val id = intent.getLongExtra(ReminderScheduler.EXTRA_ID, -1)
        val title = intent.getStringExtra(ReminderScheduler.EXTRA_TITLE) ?: ""
        val minutes = intent.getIntExtra(Notifications.EXTRA_MINUTES, 10)
        context.getSystemService(NotificationManager::class.java).cancel(Notifications.notificationId(kind, id))

        when (intent.action) {
            Notifications.ACTION_SNOOZE -> scheduler.snooze(kind, id, title, minutes)
            Notifications.ACTION_DONE -> {
                if (kind == ReminderScheduler.KIND_TASK) {
                    val pending = goAsync()
                    CoroutineScope(Dispatchers.IO).launch {
                        try { repo.setTaskCompleted(id, true) } finally { pending.finish() }
                    }
                }
            }
        }
    }
}
