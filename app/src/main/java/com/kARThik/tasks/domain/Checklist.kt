// SPDX-License-Identifier: GPL-3.0-only
// TaskHub, Copyright (C) 2026 Karthik Hegde. Licensed under the GNU General Public License v3.0; see LICENSE.

package com.kARThik.tasks.domain

import org.json.JSONArray
import org.json.JSONObject

data class ChecklistItem(val text: String, val done: Boolean = false)

/** Checklists are stored as a JSON array string on tasks, notes and templates. */
object Checklist {
    fun parse(json: String): List<ChecklistItem> = try {
        val a = JSONArray(json)
        (0 until a.length()).map {
            val o = a.getJSONObject(it)
            ChecklistItem(o.optString("text"), o.optBoolean("done"))
        }
    } catch (e: Exception) {
        emptyList()
    }

    fun toJson(items: List<ChecklistItem>): String {
        val a = JSONArray()
        items.forEach { a.put(JSONObject().put("text", it.text).put("done", it.done)) }
        return a.toString()
    }
}
