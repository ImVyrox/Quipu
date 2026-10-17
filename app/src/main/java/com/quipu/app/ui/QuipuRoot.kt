package com.quipu.app.ui

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.quipu.app.R
import com.quipu.app.ui.finance.FinanceScreen
import com.quipu.app.ui.summary.SummaryScreen
import com.quipu.app.ui.tasks.TasksScreen

/** Destinos de la barra inferior. */
enum class Destination(
    val route: String,
    @StringRes val label: Int,
    @DrawableRes val icon: Int,
) {
    Tasks("tasks", R.string.nav_tasks, R.drawable.ic_nav_tasks),
    Finance("finance", R.string.nav_finance, R.drawable.ic_nav_finance),
    Summary("summary", R.string.nav_summary, R.drawable.ic_nav_summary),
}

@Composable
fun QuipuRoot() {
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            NavigationBar {
                Destination.entries.forEach { destination ->
                    NavigationBarItem(
                        selected = currentDestination?.hierarchy?.any { it.route == destination.route } == true,
                        onClick = {
                            navController.navigate(destination.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(painterResource(destination.icon), contentDescription = null) },
                        label = { Text(stringResource(destination.label)) },
                    )
                }
            }
        },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Destination.Tasks.route,
            modifier = Modifier.padding(padding),
        ) {
            composable(Destination.Tasks.route) { TasksScreen(snackbarHostState) }
            composable(Destination.Finance.route) { FinanceScreen(snackbarHostState) }
            composable(Destination.Summary.route) { SummaryScreen(snackbarHostState) }
        }
    }
}
