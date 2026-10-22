// SPDX-License-Identifier: GPL-3.0-only
// TaskHub, Copyright (C) 2026 Karthik Hegde. Licensed under the GNU General Public License v3.0; see LICENSE.

package com.kARThik.tasks

import android.app.Application
import com.kARThik.tasks.notifications.Notifications
import com.kARThik.tasks.notifications.RescheduleWorker
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class TaskHubApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Notifications.ensureChannel(this)
        RescheduleWorker.enqueue(this)
    }
}
