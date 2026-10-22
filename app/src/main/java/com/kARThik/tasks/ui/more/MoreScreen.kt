// SPDX-License-Identifier: GPL-3.0-only
// TaskHub, Copyright (C) 2026 Karthik Hegde. Licensed under the GNU General Public License v3.0; see LICENSE.

package com.kARThik.tasks.ui.more

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kARThik.tasks.data.repository.UserPrefs
import com.kARThik.tasks.ui.navigation.AppViewModel
import com.kARThik.tasks.ui.navigation.Dest
import com.kARThik.tasks.ui.theme.Palettes

/** More tab: every screen, settings, theme, nav personalization, calendar import, notification help. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MoreScreen(
    prefs: UserPrefs,
    onPrefsChange: (UserPrefs) -> Unit,
    app: AppViewModel,
    onOpen: (Dest) -> Unit,
    onOpenRoute: (String) -> Unit,
) {
    val ctx = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val message by app.importMessage.collectAsStateWithLifecycle()
    LaunchedEffect(message) {
        message?.let { snackbar.showSnackbar(it); app.importMessage.value = null }
    }

    val calendarPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) app.importCalendar() else app.importMessage.value = "Calendar permission not granted"
    }
    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    Scaffold(
        topBar = { TopAppBar(title = { Text("More") }) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad), contentPadding = PaddingValues(bottom = 16.dp)) {
            item { Section("Screens") }
            items(Dest.pinnable) { d ->
                ListItem(
                    headlineContent = { Text(d.label) },
                    leadingContent = { Icon(d.icon, null) },
                    modifier = Modifier.clickable { onOpen(d) },
                )
            }
            item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }

            item { Section("Theme") }
            item {
                FlowRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("System", "Light", "Dark").forEachIndexed { i, label ->
                        FilterChip(prefs.themeMode == i, { onPrefsChange(prefs.copy(themeMode = i)) }, { Text(label) })
                    }
                }
            }
            item {
                FlowRow(
                    Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Palettes.all.forEachIndexed { i, p ->
                        FilterChip(prefs.palette == i, { onPrefsChange(prefs.copy(palette = i)) }, { Text(p.name) })
                    }
                }
            }

            item { Section("Timeline display") }
            item { Toggle("Day Mode", prefs.dayMode) { onPrefsChange(prefs.copy(dayMode = it)) } }
            item { Toggle("Show tags", prefs.showTags) { onPrefsChange(prefs.copy(showTags = it)) } }
            item { Toggle("Show timestamps", prefs.showTimestamps) { onPrefsChange(prefs.copy(showTimestamps = it)) } }
            item { Toggle("Newest date first", prefs.dateDescending) { onPrefsChange(prefs.copy(dateDescending = it)) } }
            item { Toggle("Sort items by priority", prefs.sortByPriority) { onPrefsChange(prefs.copy(sortByPriority = it)) } }

            item { Section("Sounds") }
            item {
                ListItem(
                    headlineContent = { Text("Task complete sound") },
                    supportingContent = { Text(com.kARThik.tasks.audio.TickSounds.all.getOrNull(prefs.tickSound)?.name ?: "None") },
                    modifier = Modifier.clickable { onOpenRoute("sounds") },
                )
            }

            item { Section("Calendar") }
            item {
                ListItem(
                    headlineContent = { Text("Import calendar events") },
                    supportingContent = { Text("Reads local and Google calendars on this device and shows them in the Timeline and Agenda.") },
                    modifier = Modifier.clickable {
                        if (app.hasCalendarPermission()) app.importCalendar()
                        else calendarPermission.launch(Manifest.permission.READ_CALENDAR)
                    },
                )
            }

            item { Section("Notifications") }
            item {
                ListItem(
                    headlineContent = { Text("Allow notifications") },
                    modifier = Modifier.clickable {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    },
                )
            }
            item {
                ListItem(
                    headlineContent = { Text("Exact alarms") },
                    supportingContent = { Text("Needed on Android 12+ so reminders fire on time.") },
                    modifier = Modifier.clickable {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            ctx.startActivity(
                                Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${ctx.packageName}"))
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        }
                    },
                )
            }
            item {
                val ignoring = ctx.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(ctx.packageName)
                ListItem(
                    headlineContent = { Text("Battery optimization") },
                    supportingContent = {
                        Text(
                            if (ignoring) "TaskHub is exempt, so reminders should arrive reliably."
                            else "Some phones delay or kill alarms to save battery. Tap to allow TaskHub to run unrestricted. On Xiaomi, OnePlus, Samsung and others, also enable auto-start and set battery to \"Unrestricted\" in system app settings.",
                        )
                    },
                    modifier = Modifier.clickable {
                        val i = if (ignoring) Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                        else Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${ctx.packageName}"))
                        runCatching { ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                    },
                )
            }

            item { Section("Bottom navigation (choose up to 3)") }
            items(Dest.pinnable) { d ->
                val on = d.route in prefs.navTabs
                ListItem(
                    headlineContent = { Text(d.label) },
                    trailingContent = {
                        Checkbox(
                            checked = on,
                            onCheckedChange = { checked ->
                                val next = if (checked) prefs.navTabs + d.route else prefs.navTabs - d.route
                                onPrefsChange(prefs.copy(navTabs = next.take(3)))
                            },
                            enabled = on || prefs.navTabs.size < 3,
                        )
                    },
                )
            }

            item { Section("About") }
            item {
                ListItem(
                    headlineContent = { Text("About TaskHub") },
                    supportingContent = { Text("App info and developer") },
                    modifier = Modifier.clickable { onOpenRoute("about") },
                )
            }
        }
    }
}

@Composable
private fun Section(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp),
    )
}

@Composable
private fun Toggle(label: String, value: Boolean, onChange: (Boolean) -> Unit) {
    ListItem(
        headlineContent = { Text(label) },
        trailingContent = { Switch(value, onChange) },
        modifier = Modifier.clickable { onChange(!value) },
    )
}
