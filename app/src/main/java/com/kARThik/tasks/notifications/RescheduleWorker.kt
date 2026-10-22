// SPDX-License-Identifier: GPL-3.0-only
// TaskHub, Copyright (C) 2026 Karthik Hegde. Licensed under the GNU General Public License v3.0; see LICENSE.

package com.kARThik.tasks.notifications

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit

@EntryPoint
@InstallIn(SingletonComponent::class)
interface SchedulerEntryPoint {
    fun scheduler(): ReminderScheduler
}

/** Safety net: periodically re-arms alarms the OS may have dropped (battery savers, OEM killers). */
class RescheduleWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        EntryPointAccessors.fromApplication(applicationContext, SchedulerEntryPoint::class.java)
            .scheduler().rescheduleAll()
        return Result.success()
    }

    companion object {
        fun enqueue(ctx: Context) {
            val req = PeriodicWorkRequestBuilder<RescheduleWorker>(12, TimeUnit.HOURS).build()
            WorkManager.getInstance(ctx)
                .enqueueUniquePeriodicWork("reschedule_alarms", ExistingPeriodicWorkPolicy.KEEP, req)
        }
    }
}
