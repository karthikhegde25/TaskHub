// SPDX-License-Identifier: GPL-3.0-only
// TaskHub, Copyright (C) 2026 Karthik Hegde. Licensed under the GNU General Public License v3.0; see LICENSE.

package com.kARThik.tasks.widget

import android.content.Context
import androidx.glance.appwidget.updateAll

/** Refreshes every placed widget after data changes. Failures are ignored. */
object WidgetUpdater {
    suspend fun refresh(ctx: Context) {
        runCatching { TaskHubWidget().updateAll(ctx) }
    }
}
