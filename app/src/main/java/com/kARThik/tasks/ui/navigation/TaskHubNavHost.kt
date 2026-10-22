// SPDX-License-Identifier: GPL-3.0-only
// TaskHub, Copyright (C) 2026 Karthik Hegde. Licensed under the GNU General Public License v3.0; see LICENSE.

package com.kARThik.tasks.ui.navigation

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import com.kARThik.tasks.ui.common.LocalTagColors
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.kARThik.tasks.data.repository.ItemKind
import com.kARThik.tasks.data.repository.UserPrefs
import com.kARThik.tasks.ui.editor.EditorScreen
import com.kARThik.tasks.ui.more.MoreScreen
import com.kARThik.tasks.ui.screens.BoardScreen
import com.kARThik.tasks.ui.screens.NotesScreen
import com.kARThik.tasks.ui.screens.RemindersScreen
import com.kARThik.tasks.ui.screens.RepeatingScreen
import com.kARThik.tasks.ui.screens.SearchScreen
import com.kARThik.tasks.ui.screens.StatsScreen
import com.kARThik.tasks.ui.screens.TagsScreen
import com.kARThik.tasks.ui.screens.TemplatesScreen
import com.kARThik.tasks.ui.screens.OverdueScreen
import com.kARThik.tasks.ui.screens.UnplannedScreen
import com.kARThik.tasks.ui.timeline.TimelineScreen

private const val EDITOR = "editor/{kind}/{id}?date={date}&project={project}"

@Composable
fun TaskHubNavHost(
    prefs: UserPrefs,
    badges: Badges,
    onPrefsChange: (UserPrefs) -> Unit,
    app: AppViewModel,
) {
    val nav = rememberNavController()
    val tagColors by app.tagColors.collectAsStateWithLifecycle()
    val backStack by nav.currentBackStackEntryAsState()
    val current = backStack?.destination?.route

    fun go(d: Dest) = nav.navigate(d.route) {
        popUpTo(nav.graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
    fun edit(kind: String, id: Long) = nav.navigate("editor/$kind/$id")
    // "+" buttons open the full editor; date (epoch day) and project are optional prefills.
    fun newTask(epochDay: Long = -1L, projectId: Long = -1L) = nav.navigate("editor/task/-1?date=$epochDay&project=$projectId")

    // Timeline first, up to 3 user-chosen tabs, More last.
    val tabs = listOf(Dest.Timeline) +
        prefs.navTabs.mapNotNull { r -> Dest.pinnable.firstOrNull { it.route == r } }.take(3) +
        listOf(Dest.More)
    val showBar = current?.startsWith("editor/") != true

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (showBar) NavigationBar {
                tabs.forEach { d ->
                    NavigationBarItem(
                        selected = current == d.route,
                        onClick = { go(d) },
                        icon = {
                            // Badge counts: overdue (red) and today on the Timeline tab.
                            val count = if (d == Dest.Timeline) badges.overdue + badges.today else 0
                            BadgedBox(badge = {
                                if (count > 0) Badge(
                                    containerColor = if (badges.overdue > 0) androidx.compose.material3.MaterialTheme.colorScheme.error
                                    else androidx.compose.material3.MaterialTheme.colorScheme.primary,
                                ) { Text(if (count > 99) "99+" else count.toString()) }
                            }) { Icon(d.icon, contentDescription = d.label) }
                        },
                        label = { Text(d.label, maxLines = 1) },
                    )
                }
            }
        },
    ) { inner ->
        CompositionLocalProvider(LocalTagColors provides tagColors) {
        NavHost(
            navController = nav,
            startDestination = Dest.Timeline.route,
            modifier = Modifier.padding(bottom = if (showBar) inner.calculateBottomPadding() else androidx.compose.ui.unit.Dp.Hairline),
        ) {
            composable(Dest.Timeline.route) {
                TimelineScreen(prefs, onPrefsChange, onOpenItem = { item ->
                    when (item.kind) {
                        ItemKind.TASK -> edit("task", item.id)
                        ItemKind.NOTE -> edit("note", item.id)
                        ItemKind.REMINDER -> go(Dest.Reminders)
                        ItemKind.EVENT -> Unit
                    }
                }, onAddTask = { d -> newTask(epochDay = d.toEpochDay()) },
                    onSearch = { go(Dest.Search) }, onOpenUnplanned = { go(Dest.Unplanned) },
                    onOpenOverdue = { nav.navigate("overdue") { launchSingleTop = true } })
            }
            composable(Dest.Board.route) { BoardScreen(::edit, onAddTask = { pid -> newTask(projectId = pid) }) }
            composable(Dest.Reminders.route) { RemindersScreen() }
            composable(Dest.Unplanned.route) { UnplannedScreen(::edit) }
            composable("overdue") { OverdueScreen(onBack = { nav.popBackStack() }, onOpen = ::edit) }
            composable(Dest.Notes.route) { NotesScreen(::edit) }
            composable(Dest.Search.route) { SearchScreen(::edit) }
            composable(Dest.Tags.route) { TagsScreen(::edit) }
            composable(Dest.Templates.route) { TemplatesScreen() }
            composable(Dest.Repeating.route) { RepeatingScreen(::edit) }
            composable(Dest.Statistics.route) { StatsScreen() }
            composable(Dest.More.route) { MoreScreen(prefs, onPrefsChange, app, onOpen = { d -> nav.navigate(d.route) { launchSingleTop = true } },
                onOpenRoute = { r -> nav.navigate(r) { launchSingleTop = true } }) }
            composable("sounds") { com.kARThik.tasks.ui.more.SoundsScreen(prefs, onPrefsChange, onBack = { nav.popBackStack() }) }
            composable("about") { com.kARThik.tasks.ui.more.AboutScreen(onBack = { nav.popBackStack() }) }
            composable(
                EDITOR,
                arguments = listOf(
                    navArgument("kind") { type = NavType.StringType },
                    navArgument("id") { type = NavType.LongType },
                    navArgument("date") { type = NavType.LongType; defaultValue = -1L },
                    navArgument("project") { type = NavType.LongType; defaultValue = -1L },
                ),
            ) { EditorScreen(onBack = { nav.popBackStack() }) }
        }
        }
    }
}
