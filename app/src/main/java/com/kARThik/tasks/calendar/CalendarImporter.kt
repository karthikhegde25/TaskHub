// SPDX-License-Identifier: GPL-3.0-only
// TaskHub, Copyright (C) 2026 Karthik Hegde. Licensed under the GNU General Public License v3.0; see LICENSE.

package com.kARThik.tasks.calendar

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import com.kARThik.tasks.data.local.CalendarEventDao
import com.kARThik.tasks.data.local.CalendarEventEntity
import com.kARThik.tasks.widget.WidgetUpdater
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import javax.inject.Inject
import javax.inject.Singleton

/** Imports local/Google calendar events (via CalendarContract) so they show next to tasks. */
@Singleton
class CalendarImporter @Inject constructor(
    @ApplicationContext private val ctx: Context,
    private val dao: CalendarEventDao,
) {
    fun hasPermission(): Boolean =
        ctx.checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED

    /** Replaces the cached events with the next year of instances. Returns how many were imported. */
    suspend fun import(): Int = withContext(Dispatchers.IO) {
        if (!hasPermission()) return@withContext 0
        val day = 24L * 60 * 60 * 1000
        val from = System.currentTimeMillis() - 30 * day
        val to = System.currentTimeMillis() + 365 * day
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().also {
            ContentUris.appendId(it, from)
            ContentUris.appendId(it, to)
        }.build()
        val proj = arrayOf(
            CalendarContract.Instances.EVENT_ID,
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.DESCRIPTION,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.ALL_DAY,
            CalendarContract.Instances.CALENDAR_DISPLAY_NAME,
            CalendarContract.Instances.CALENDAR_COLOR,
        )
        val out = ArrayList<CalendarEventEntity>()
        try {
            ctx.contentResolver.query(uri, proj, null, null, "${CalendarContract.Instances.BEGIN} ASC")?.use { c ->
                while (c.moveToNext()) {
                    val eventId = c.getLong(0)
                    var begin = c.getLong(3)
                    var end = c.getLong(4)
                    val allDay = c.getInt(5) == 1
                    if (allDay) {
                        // All-day instances are stored as UTC midnight; show them on the local day.
                        val d = Instant.ofEpochMilli(begin).atZone(ZoneOffset.UTC).toLocalDate()
                        begin = d.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                        end = begin
                    }
                    out += CalendarEventEntity(
                        id = eventId * 100_000_000L + (begin / 60_000L) % 100_000_000L,
                        title = c.getString(1)?.ifBlank { null } ?: "(No title)",
                        description = c.getString(2) ?: "",
                        startMillis = begin,
                        endMillis = end,
                        allDay = allDay,
                        calendarName = c.getString(6) ?: "",
                        color = 0xFF000000L or (c.getInt(7).toLong() and 0xFFFFFFL),
                    )
                }
            }
        } catch (e: SecurityException) {
            return@withContext 0
        }
        dao.clear()
        dao.insertAll(out)
        WidgetUpdater.refresh(ctx)
        out.size
    }
}
