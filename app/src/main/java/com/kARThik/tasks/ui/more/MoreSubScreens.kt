// SPDX-License-Identifier: GPL-3.0-only
// TaskHub, Copyright (C) 2026 Karthik Hegde. Licensed under the GNU General Public License v3.0; see LICENSE.

package com.kARThik.tasks.ui.more

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kARThik.tasks.R
import com.kARThik.tasks.audio.TickSounds
import com.kARThik.tasks.data.repository.UserPrefs

/** Every task-complete sound in one list. Tapping one selects it and plays a preview. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SoundsScreen(prefs: UserPrefs, onPrefsChange: (UserPrefs) -> Unit, onBack: () -> Unit) {
    val ctx = LocalContext.current
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Task complete sound") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
    ) { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize()) {
            itemsIndexed(TickSounds.all) { i, sound ->
                ListItem(
                    headlineContent = { Text(sound.name) },
                    supportingContent = if (i == TickSounds.DEFAULT) ({ Text("Default") }) else null,
                    leadingContent = { RadioButton(selected = prefs.tickSound == i, onClick = null) },
                    modifier = Modifier.clickable {
                        onPrefsChange(prefs.copy(tickSound = i))
                        TickSounds.play(ctx, i)
                    },
                )
            }
        }
    }
}

/** App logo, name, version and developer. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val version = remember(ctx) {
        try { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName } catch (_: Exception) { null }
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("About") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
    ) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(32.dp))
            Image(
                painterResource(R.drawable.about_logo), "TaskHub logo",
                Modifier.size(120.dp).clip(RoundedCornerShape(28.dp)),
            )
            Spacer(Modifier.height(20.dp))
            Text("TaskHub", style = MaterialTheme.typography.headlineMedium)
            if (version != null) {
                Text("Version $version", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(24.dp))
            Text("Developer", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Text("Karthik Hegde", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(24.dp))
            Text("License", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Text("GNU General Public License v3.0", style = MaterialTheme.typography.titleMedium)
            Text(
                "TaskHub is free software registered under GPL v3.0. You may use, study, share and modify it under those terms.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            )
            val uri = androidx.compose.ui.platform.LocalUriHandler.current
            androidx.compose.material3.TextButton(onClick = { uri.openUri("https://github.com/karthikhegde25/TaskHub") }) {
                Icon(painterResource(R.drawable.ic_github), "GitHub", Modifier.size(22.dp))
                Spacer(Modifier.width(10.dp))
                Text("github.com/karthikhegde25/TaskHub")
            }
            Spacer(Modifier.height(16.dp))
            Text(
                "Free and unlimited. No accounts and no cloud: everything stays on your device.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
