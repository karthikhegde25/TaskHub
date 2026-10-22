// SPDX-License-Identifier: GPL-3.0-only
// TaskHub, Copyright (C) 2026 Karthik Hegde. Licensed under the GNU General Public License v3.0; see LICENSE.

package com.kARThik.tasks.widget

import android.content.Context

/** Per-widget settings (each placed widget is independent), stored by appWidgetId. */
data class WidgetConfig(
    val mode: Int = 0,          // 0 Timeline, 1 Project, 2 Notes, 3 Unplanned
    val projectId: Long = -1L,
    val theme: Int = 0,         // 0 system, 1 light, 2 dark
    val fontSize: Int = 14,
    val opacity: Int = 100,     // percent
    val rangeDays: Int = 7,     // Timeline: today .. today+N (0 = today only; -1 = all)
    val showCompleted: Boolean = false,
    val showTags: Boolean = true,
    val showTime: Boolean = true,
    val showDescription: Boolean = false,
    val showChecklist: Boolean = true,
    val showRepeating: Boolean = true,
    val sortByPriority: Boolean = false,
)

object WidgetPrefs {
    private fun sp(ctx: Context) = ctx.getSharedPreferences("widget_prefs", Context.MODE_PRIVATE)

    fun load(ctx: Context, id: Int): WidgetConfig {
        val p = sp(ctx)
        val d = WidgetConfig()
        fun k(n: String) = "${id}_$n"
        return WidgetConfig(
            mode = p.getInt(k("mode"), d.mode),
            projectId = p.getLong(k("project"), d.projectId),
            theme = p.getInt(k("theme"), d.theme),
            fontSize = p.getInt(k("font"), d.fontSize),
            opacity = p.getInt(k("opacity"), d.opacity),
            rangeDays = p.getInt(k("range"), d.rangeDays),
            showCompleted = p.getBoolean(k("completed"), d.showCompleted),
            showTags = p.getBoolean(k("tags"), d.showTags),
            showTime = p.getBoolean(k("time"), d.showTime),
            showDescription = p.getBoolean(k("desc"), d.showDescription),
            showChecklist = p.getBoolean(k("check"), d.showChecklist),
            showRepeating = p.getBoolean(k("repeat"), d.showRepeating),
            sortByPriority = p.getBoolean(k("prio"), d.sortByPriority),
        )
    }

    fun save(ctx: Context, id: Int, c: WidgetConfig) {
        fun k(n: String) = "${id}_$n"
        sp(ctx).edit()
            .putInt(k("mode"), c.mode).putLong(k("project"), c.projectId).putInt(k("theme"), c.theme)
            .putInt(k("font"), c.fontSize).putInt(k("opacity"), c.opacity).putInt(k("range"), c.rangeDays)
            .putBoolean(k("completed"), c.showCompleted).putBoolean(k("tags"), c.showTags)
            .putBoolean(k("time"), c.showTime).putBoolean(k("desc"), c.showDescription)
            .putBoolean(k("check"), c.showChecklist).putBoolean(k("repeat"), c.showRepeating)
            .putBoolean(k("prio"), c.sortByPriority)
            .apply()
    }

    fun delete(ctx: Context, id: Int) {
        val p = sp(ctx)
        val e = p.edit()
        p.all.keys.filter { it.startsWith("${id}_") }.forEach { e.remove(it) }
        e.apply()
    }
}
