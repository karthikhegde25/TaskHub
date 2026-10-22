// SPDX-License-Identifier: GPL-3.0-only
// TaskHub, Copyright (C) 2026 Karthik Hegde. Licensed under the GNU General Public License v3.0; see LICENSE.

package com.kARThik.tasks.widget

import android.content.Context
import android.content.res.Configuration
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextDecoration
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.kARThik.tasks.MainActivity
import com.kARThik.tasks.data.local.AppDatabase
import com.kARThik.tasks.domain.Checklist
import com.kARThik.tasks.domain.RecurrenceRule
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {
    fun database(): AppDatabase
}

private class Row_(
    val title: String,
    val whenText: String?,
    val extra: String?,
    val description: String?,
    val done: Boolean,
    val priority: Int,
    val sortKey: Long,
    val header: Boolean = false,
)

private val dateFmt = DateTimeFormatter.ofPattern("EEE, MMM d")
private val timeFmt = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)

/** All-in-one widget: Timeline, Project, Notes or Unplanned, configured per placed widget. */
class TaskHubWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)
        val cfg = WidgetPrefs.load(context, appWidgetId)
        val rows = load(context, cfg)
        val systemDark = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val dark = when (cfg.theme) { 1 -> false; 2 -> true; else -> systemDark }

        provideContent {
            val bg = (if (dark) Color(0xFF1C1B1F) else Color(0xFFFFFBFE)).copy(alpha = cfg.opacity / 100f)
            val fg = if (dark) Color(0xFFE6E1E5) else Color(0xFF1C1B1F)
            val dim = if (dark) Color(0xFFCAC4D0) else Color(0xFF49454F)
            val accent = if (dark) Color(0xFFB8C0FF) else Color(0xFF4F5BD5)
            val fs = cfg.fontSize.sp

            Column(
                GlanceModifier.fillMaxSize().background(ColorProvider(bg)).padding(8.dp)
                    .clickable(actionStartActivity<MainActivity>()),
            ) {
                Text(
                    when (cfg.mode) { 1 -> "Project"; 2 -> "Notes"; 3 -> "Unplanned"; else -> "Timeline" },
                    style = TextStyle(color = ColorProvider(accent), fontSize = (cfg.fontSize + 2).sp, fontWeight = FontWeight.Bold),
                    modifier = GlanceModifier.padding(bottom = 4.dp),
                )
                if (rows.isEmpty()) {
                    Text("Nothing here", style = TextStyle(color = ColorProvider(dim), fontSize = fs))
                } else {
                    LazyColumn(GlanceModifier.fillMaxSize()) {
                        items(rows) { r ->
                            if (r.header) {
                                Text(
                                    r.title,
                                    style = TextStyle(color = ColorProvider(accent), fontSize = fs, fontWeight = FontWeight.Medium),
                                    modifier = GlanceModifier.fillMaxWidth().padding(top = 6.dp, bottom = 2.dp),
                                )
                            } else {
                                Column(GlanceModifier.fillMaxWidth().padding(vertical = 2.dp)) {
                                    val prefix = if (r.priority > 0) "⚑ " else ""
                                    Row {
                                        Text(
                                            prefix + r.title,
                                            style = TextStyle(
                                                color = ColorProvider(if (r.done) dim else fg),
                                                fontSize = fs,
                                                textDecoration = if (r.done) TextDecoration.LineThrough else TextDecoration.None,
                                            ),
                                        )
                                    }
                                    val meta = listOfNotNull(r.whenText, r.extra).joinToString(" · ")
                                    if (meta.isNotEmpty()) Text(meta, style = TextStyle(color = ColorProvider(dim), fontSize = (cfg.fontSize - 3).coerceAtLeast(8).sp))
                                    r.description?.let {
                                        Text(it, maxLines = 2, style = TextStyle(color = ColorProvider(dim), fontSize = (cfg.fontSize - 2).coerceAtLeast(8).sp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private suspend fun load(ctx: Context, c: WidgetConfig): List<Row_> {
        val db = EntryPointAccessors.fromApplication(ctx, WidgetEntryPoint::class.java).database()
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now()
        val out = ArrayList<Row_>()

        fun extra(tags: List<String>, checkJson: String, rule: String?): String? {
            val parts = ArrayList<String>()
            if (c.showTags && tags.isNotEmpty()) parts += tags.joinToString(" ") { "@$it" }
            if (c.showChecklist) Checklist.parse(checkJson).let { if (it.isNotEmpty()) parts += "${it.count { x -> x.done }}/${it.size}" }
            if (c.showRepeating) RecurrenceRule.parse(rule)?.let { parts += "↻" }
            return parts.takeIf { it.isNotEmpty() }?.joinToString(" ")
        }

        when (c.mode) {
            2 -> db.noteDao().observeAll().first().forEach { n ->
                out += Row_(n.title, null, extra(n.tags, n.checklistJson, null),
                    n.description.takeIf { c.showDescription && it.isNotBlank() }, false, n.priority, -n.updatedAt)
            }
            3 -> db.taskDao().observeAll().first()
                .filter { it.startDateTime == null && it.projectId == null && !it.isCancelled && (c.showCompleted || !it.isCompleted) }
                .forEach { t ->
                    out += Row_(t.title, null, extra(t.tags, t.checklistJson, t.recurrenceRule),
                        t.description.takeIf { c.showDescription && it.isNotBlank() }, t.isCompleted, t.priority, -t.createdAt)
                }
            1 -> db.taskDao().observeAll().first()
                .filter { it.projectId == c.projectId && !it.isCancelled && (c.showCompleted || !it.isCompleted) }
                .forEach { t ->
                    val z = t.startDateTime?.let { Instant.ofEpochMilli(it).atZone(zone) }
                    out += Row_(t.title, z?.let { dateFmt.format(it) + if (c.showTime && it.toLocalTime().toSecondOfDay() != 0) " " + timeFmt.format(it) else "" },
                        extra(t.tags, t.checklistJson, t.recurrenceRule),
                        t.description.takeIf { c.showDescription && it.isNotBlank() }, t.isCompleted, t.priority, t.sortOrder)
                }
            else -> {
                val limit = if (c.rangeDays < 0) null else today.plusDays(c.rangeDays.toLong())
                db.taskDao().observeAll().first()
                    .filter { it.startDateTime != null && !it.isCancelled && (c.showCompleted || !it.isCompleted) && (c.showRepeating || !it.isRecurring) }
                    .forEach { t ->
                        val z = Instant.ofEpochMilli(t.startDateTime!!).atZone(zone)
                        val d = z.toLocalDate()
                        // Overdue incomplete tasks stay visible, grouped with today.
                        if (limit == null || !d.isAfter(limit)) {
                            val showAs = if (d.isBefore(today) && !t.isCompleted) today else d
                            out += Row_(t.title, if (c.showTime && z.toLocalTime().toSecondOfDay() != 0) timeFmt.format(z) else null,
                                extra(t.tags, t.checklistJson, t.recurrenceRule),
                                t.description.takeIf { c.showDescription && it.isNotBlank() }, t.isCompleted, t.priority,
                                showAs.toEpochDay() * 86_400_000L + (t.startDateTime % 86_400_000L).coerceAtLeast(0))
                        }
                    }
                db.noteDao().observeAll().first().filter { it.startDateTime != null }.forEach { n ->
                    val z = Instant.ofEpochMilli(n.startDateTime!!).atZone(zone)
                    val d = z.toLocalDate()
                    if (!d.isBefore(today) && (limit == null || !d.isAfter(limit))) {
                        out += Row_(n.title, if (c.showTime && z.toLocalTime().toSecondOfDay() != 0) timeFmt.format(z) else null,
                            extra(n.tags, n.checklistJson, null), n.description.takeIf { c.showDescription && it.isNotBlank() },
                            false, n.priority, d.toEpochDay() * 86_400_000L + (n.startDateTime % 86_400_000L).coerceAtLeast(0))
                    }
                }
            }
        }

        val sorted = if (c.sortByPriority) out.sortedWith(compareBy({ com.kARThik.tasks.ui.theme.PriorityColors.rank(it.priority) }, { it.sortKey }))
        else out.sortedBy { it.sortKey }

        if (c.mode != 0 || c.sortByPriority) return sorted
        // Timeline mode: insert a date header before each new day.
        val withHeaders = ArrayList<Row_>()
        var last: Long? = null
        sorted.forEach { r ->
            val day = r.sortKey / 86_400_000L
            if (day != last) {
                val d = LocalDate.ofEpochDay(day)
                withHeaders += Row_(
                    if (d == today) "Today" else if (d == today.plusDays(1)) "Tomorrow" else dateFmt.format(d),
                    null, null, null, false, 0, r.sortKey, header = true,
                )
                last = day
            }
            withHeaders += r
        }
        return withHeaders
    }
}

class TaskHubWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TaskHubWidget()

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        appWidgetIds.forEach { WidgetPrefs.delete(context, it) }
    }
}
