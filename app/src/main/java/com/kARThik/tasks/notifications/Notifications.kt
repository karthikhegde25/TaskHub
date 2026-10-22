// SPDX-License-Identifier: GPL-3.0-only
// TaskHub, Copyright (C) 2026 Karthik Hegde. Licensed under the GNU General Public License v3.0; see LICENSE.

package com.kARThik.tasks.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.kARThik.tasks.MainActivity
import com.kARThik.tasks.R

object Notifications {
    const val CHANNEL_ID = "taskman_reminders"
    const val ACTION_SNOOZE = "com.kARThik.tasks.SNOOZE"
    const val ACTION_DONE = "com.kARThik.tasks.DONE"
    const val EXTRA_MINUTES = "minutes"

    fun ensureChannel(ctx: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val ch = NotificationChannel(CHANNEL_ID, "Task reminders", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Reminders for tasks and standalone reminders"
            enableVibration(true)
            lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
        }
        ctx.getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
    }

    fun notificationId(kind: String, id: Long): Int = (kind.hashCode() * 31 + id).toInt()

    /** Shows a reminder with full-screen intent and Snooze / Reschedule / Done actions. */
    fun show(ctx: Context, kind: String, id: Long, title: String) {
        ensureChannel(ctx)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return

        val open = PendingIntent.getActivity(
            ctx, 0,
            Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        fun action(act: String, minutes: Int, label: String): NotificationCompat.Action {
            val i = Intent(ctx, ActionReceiver::class.java).setAction(act)
                .setData(Uri.parse("taskman://action/$act/$minutes/$kind/$id"))
                .putExtra(ReminderScheduler.EXTRA_KIND, kind)
                .putExtra(ReminderScheduler.EXTRA_ID, id)
                .putExtra(ReminderScheduler.EXTRA_TITLE, title)
                .putExtra(EXTRA_MINUTES, minutes)
            val pi = PendingIntent.getBroadcast(ctx, 0, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            return NotificationCompat.Action.Builder(0, label, pi).build()
        }

        val builder = NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(title)
            .setContentText(if (kind == ReminderScheduler.KIND_TASK) "Task reminder" else "Reminder")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(open)
            .setFullScreenIntent(open, true)
            .addAction(action(ACTION_SNOOZE, 10, "Snooze 10m"))
            .addAction(action(ACTION_SNOOZE, 60, "In 1h"))
            .addAction(action(ACTION_DONE, 0, "Done"))

        NotificationManagerCompat.from(ctx).notify(notificationId(kind, id), builder.build())
    }
}
