package com.squads.app.ui

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import com.squads.app.ui.auth.LoginScreen
import com.squads.app.ui.calendar.CalendarScreen
import com.squads.app.ui.chats.ChatDetailScreen
import com.squads.app.ui.chats.ChatsScreen
import com.squads.app.ui.components.LoadingScreen
import com.squads.app.ui.mail.MailDetailScreen
import com.squads.app.ui.mail.MailScreen
import com.squads.app.ui.profile.ProfileScreen
import com.squads.app.ui.search.SearchScreen
import com.squads.app.ui.teams.TeamsScreen
import com.squads.app.viewmodel.AuthViewModel
import com.squads.app.viewmodel.ChatsViewModel
import com.squads.app.viewmodel.MailViewModel
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.materials.CupertinoMaterials
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi

sealed class Screen(
    val route: String,
    val label: String,
    val icon: ImageVector,
) {
    data object Chats : Screen("chats", "Chats", Icons.AutoMirrored.Filled.Chat)

    data object Mail : Screen("mail", "Mail", Icons.Default.Email)

    data object Calendar : Screen("calendar", "Calendar", Icons.Default.CalendarMonth)

    data object Teams : Screen("teams", "Teams", Icons.Default.Groups)

    data object Search : Screen("search", "Search", Icons.Default.Search)
}

private val bottomNavItems =
    listOf(
        Screen.Chats,
        Screen.Mail,
        Screen.Calendar,
        Screen.Teams,
        Screen.Search,
    )

private val bottomBarRoutes = setOf("chats_list", "mail_list", "calendar", "teams", "search", "profile")

@Composable
fun SquadsApp(authViewModel: AuthViewModel = hiltViewModel()) {
    val isAuthenticated by authViewModel.isAuthenticated.collectAsState()
    val deviceCodeState by authViewModel.deviceCodeState.collectAsState()

    if (!isAuthenticated) {
        LoginScreen(
            deviceCodeState = deviceCodeState,
            onRequestCode = { authViewModel.requestCode() },
            onOpenBrowser = { activity -> authViewModel.openBrowserAndPoll(activity) },
            onMockLogin = { authViewModel.mockLogin() },
            onReset = { authViewModel.resetState() },
        )
    } else {
        MainApp(authViewModel = authViewModel)
    }
}

@OptIn(ExperimentalHazeMaterialsApi::class)
@Composable
private fun MainApp(authViewModel: AuthViewModel) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val currentRoute = currentDestination?.route
    val view = LocalView.current
    val hazeState = remember { HazeState() }

    val showBottomBar = currentRoute in bottomBarRoutes

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            if (showBottomBar) {
                val hazeStyle = CupertinoMaterials.regular()
                NavigationBar(
                    containerColor = Color.Transparent,
                    modifier =
                        Modifier.hazeEffect(
                            state = hazeState,
                            style = hazeStyle,
                        ),
                ) {
                    bottomNavItems.forEach { screen ->
                        NavigationBarItem(
                            icon = { Icon(screen.icon, contentDescription = screen.label) },
                            label = { Text(screen.label) },
                            selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true,
                            onClick = {
                                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                navController.navigate(screen.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            colors =
                                NavigationBarItemDefaults.colors(
                                    indicatorColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                                ),
                        )
                    }
                }
            }
        },
    ) { _ ->
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .hazeSource(state = hazeState),
        ) {
            NavHost(
                navController = navController,
                startDestination = Screen.Chats.route,
                modifier = Modifier.fillMaxSize(),
            ) {
                // Chats tab — nested graph to share ChatsViewModel
                navigation(startDestination = "chats_list", route = Screen.Chats.route) {
                    composable("chats_list") { entry ->
                        val parentEntry = remember(entry) { navController.getBackStackEntry(Screen.Chats.route) }
                        val chatsViewModel: ChatsViewModel = hiltViewModel(parentEntry)
                        ChatsScreen(
                            viewModel = chatsViewModel,
                            onChatClick = { navController.navigate("chatDetail") },
                            onProfileClick = { navController.navigate("profile") },
                        )
                    }
                    composable("chatDetail") { entry ->
                        val parentEntry = remember(entry) { navController.getBackStackEntry(Screen.Chats.route) }
                        val chatsViewModel: ChatsViewModel = hiltViewModel(parentEntry)
                        ChatDetailScreen(
                            viewModel = chatsViewModel,
                            onBack = {
                                chatsViewModel.stopMessagePolling()
                                navController.popBackStack()
                            },
                        )
                    }
                }

                // Mail tab — nested graph to share MailViewModel
                navigation(startDestination = "mail_list", route = Screen.Mail.route) {
                    composable("mail_list") { entry ->
                        val parentEntry = remember(entry) { navController.getBackStackEntry(Screen.Mail.route) }
                        val mailViewModel: MailViewModel = hiltViewModel(parentEntry)
                        MailScreen(
                            viewModel = mailViewModel,
                            onMailClick = { navController.navigate("mailDetail") },
                        )
                    }
                    composable("mailDetail") { entry ->
                        val parentEntry = remember(entry) { navController.getBackStackEntry(Screen.Mail.route) }
                        val mailViewModel: MailViewModel = hiltViewModel(parentEntry)
                        val selectedMail by mailViewModel.selectedMail.collectAsState()
                        if (selectedMail != null) {
                            MailDetailScreen(
                                mail = selectedMail!!,
                                onBack = {
                                    navController.popBackStack()
                                    mailViewModel.clearSelection()
                                },
                            )
                        } else {
                            LoadingScreen()
                        }
                    }
                }

                composable(Screen.Calendar.route) { CalendarScreen() }
                composable(Screen.Teams.route) { TeamsScreen() }
                composable(Screen.Search.route) { SearchScreen() }
                composable("profile") { entry ->
                    val chatsEntry =
                        try {
                            navController.getBackStackEntry(Screen.Chats.route)
                        } catch (_: IllegalArgumentException) {
                            null
                        }
                    val chatsViewModel: ChatsViewModel? = chatsEntry?.let { hiltViewModel(it) }
                    ProfileScreen(
                        authViewModel = authViewModel,
                        myUserId = chatsViewModel?.myUserId,
                        onBack = { navController.popBackStack() },
                    )
                }
            }
        }
    }
}
