// SPDX-License-Identifier: GPL-3.0-only
// TaskHub, Copyright (C) 2026 Karthik Hegde. Licensed under the GNU General Public License v3.0; see LICENSE.

package com.kARThik.tasks.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.kARThik.tasks.data.local.AppDatabase
import com.kARThik.tasks.data.local.ProjectEntity
import com.kARThik.tasks.ui.theme.TaskHubTheme
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Opens when a widget is placed. Every placed widget keeps its own settings. */
class WidgetConfigActivity : ComponentActivity() {
    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    @OptIn(ExperimentalLayoutApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(Activity.RESULT_CANCELED)
        appWidgetId = intent?.extras?.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) { finish(); return }

        val db = EntryPointAccessors.fromApplication(applicationContext, WidgetEntryPoint::class.java).database()
        val initial = WidgetPrefs.load(this, appWidgetId)

        setContent {
            TaskHubTheme {
                var c by remember { mutableStateOf(initial) }
                var projects by remember { mutableStateOf<List<ProjectEntity>>(emptyList()) }
                androidx.compose.runtime.LaunchedEffect(Unit) { projects = db.projectDao().observeAll().first() }

                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text("Widget settings", style = MaterialTheme.typography.headlineSmall)

                    Section("Mode")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("Timeline", "Project", "Notes", "Unplanned").forEachIndexed { i, l ->
                            FilterChip(c.mode == i, { c = c.copy(mode = i) }, { Text(l) })
                        }
                    }
                    if (c.mode == 1) {
                        Section("Project")
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            projects.forEach { p -> FilterChip(c.projectId == p.id, { c = c.copy(projectId = p.id) }, { Text(p.name) }) }
                            if (projects.isEmpty()) Text("No projects yet")
                        }
                    }
                    if (c.mode == 0) {
                        Section("Date range")
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("Today" to 0, "3 days" to 3, "7 days" to 7, "30 days" to 30, "All" to -1).forEach { (l, v) ->
                                FilterChip(c.rangeDays == v, { c = c.copy(rangeDays = v) }, { Text(l) })
                            }
                        }
                    }
                    Section("Theme")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("System", "Light", "Dark").forEachIndexed { i, l -> FilterChip(c.theme == i, { c = c.copy(theme = i) }, { Text(l) }) }
                    }
                    Section("Font size: ${c.fontSize}sp")
                    Slider(c.fontSize.toFloat(), { c = c.copy(fontSize = it.toInt()) }, valueRange = 10f..22f, steps = 11)
                    Section("Opacity: ${c.opacity}%")
                    Slider(c.opacity.toFloat(), { c = c.copy(opacity = it.toInt()) }, valueRange = 10f..100f)

                    Toggle("Show completed", c.showCompleted) { c = c.copy(showCompleted = it) }
                    Toggle("Show tags", c.showTags) { c = c.copy(showTags = it) }
                    Toggle("Show time", c.showTime) { c = c.copy(showTime = it) }
                    Toggle("Show description", c.showDescription) { c = c.copy(showDescription = it) }
                    Toggle("Show checklists", c.showChecklist) { c = c.copy(showChecklist = it) }
                    Toggle("Show repeating items", c.showRepeating) { c = c.copy(showRepeating = it) }
                    Toggle("Sort by priority", c.sortByPriority) { c = c.copy(sortByPriority = it) }

                    Button(
                        onClick = {
                            WidgetPrefs.save(this@WidgetConfigActivity, appWidgetId, c)
                            lifecycleScope.launch {
                                runCatching { TaskHubWidget().updateAll(applicationContext) }
                                setResult(Activity.RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId))
                                finish()
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Save") }
                }
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun Section(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
}

@androidx.compose.runtime.Composable
private fun Toggle(label: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        Switch(value, onChange)
    }
}
