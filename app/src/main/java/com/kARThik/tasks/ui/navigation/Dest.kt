// SPDX-License-Identifier: GPL-3.0-only
// TaskHub, Copyright (C) 2026 Karthik Hegde. Licensed under the GNU General Public License v3.0; see LICENSE.

package com.kARThik.tasks.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.StickyNote2
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.ui.graphics.vector.ImageVector

/** Every top-level destination. Timeline is always first, More always last. */
enum class Dest(val route: String, val label: String, val icon: ImageVector) {
    Timeline("timeline", "Timeline", Icons.Default.Timeline),
    Board("board", "Board", Icons.Default.Dashboard),
    Reminders("reminders", "Reminders", Icons.Default.Alarm),
    Unplanned("unplanned", "Unplanned", Icons.Default.Inbox),
    Notes("notes", "Notes", Icons.Default.StickyNote2),
    Search("search", "Search", Icons.Default.Search),
    Tags("tags", "Tags", Icons.Default.Label),
    Templates("templates", "Templates", Icons.Default.ContentCopy),
    Repeating("repeating", "Repeating Tasks", Icons.Default.Repeat),
    Statistics("stats", "Statistics", Icons.Default.BarChart),
    More("more", "More", Icons.Default.MoreHoriz);

    companion object {
        /** Destinations a user may pin as one of the 3 middle tabs. */
        val pinnable: List<Dest> = entries.filter { it != Timeline && it != More }
    }
}
