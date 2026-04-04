package com.squads.app.ui

import android.view.HapticFeedbackConstants
import androidx.activity.compose.BackHandler
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import com.squads.app.ui.auth.LoginScreen
import com.squads.app.ui.calendar.CalendarScreen
import com.squads.app.ui.chats.ChatDetailScreen
import com.squads.app.ui.chats.ChatsScreen
import com.squads.app.ui.components.LoadingScreen
import com.squads.app.ui.components.LocalIsOnline
import com.squads.app.ui.mail.MailDetailScreen
import com.squads.app.ui.mail.MailScreen
import com.squads.app.ui.navigation.ChatDetail
import com.squads.app.ui.navigation.ChatsList
import com.squads.app.ui.navigation.MailDetail
import com.squads.app.ui.navigation.MailList
import com.squads.app.ui.navigation.Navigator
import com.squads.app.ui.navigation.Profile
import com.squads.app.ui.navigation.parentMetadata
import com.squads.app.ui.navigation.rememberNavigationState
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
import com.squads.app.ui.navigation.Calendar as CalendarRoute
import com.squads.app.ui.navigation.Search as SearchRoute
import com.squads.app.ui.navigation.Teams as TeamsRoute

private data class BottomNavItem(
    val route: NavKey,
    val label: String,
    val icon: ImageVector,
)

private val bottomNavItems =
    listOf(
        BottomNavItem(ChatsList, "Chats", Icons.AutoMirrored.Filled.Chat),
        BottomNavItem(MailList, "Mail", Icons.Default.Email),
        BottomNavItem(CalendarRoute, "Calendar", Icons.Default.CalendarMonth),
        BottomNavItem(TeamsRoute, "Teams", Icons.Default.Groups),
        BottomNavItem(SearchRoute, "Search", Icons.Default.Search),
    )

private val topLevelRoutes: Set<NavKey> = setOf(ChatsList, MailList, CalendarRoute, TeamsRoute, SearchRoute)

private const val CHATS_CONTENT_KEY = "chats"
private const val MAIL_CONTENT_KEY = "mail"

@Composable
fun SquadsApp(authViewModel: AuthViewModel = hiltViewModel()) {
    val isAuthenticated by authViewModel.isAuthenticated.collectAsState()
    val deviceCodeState by authViewModel.deviceCodeState.collectAsState()
    val isLoggingOut by authViewModel.isLoggingOut.collectAsState()

    if (isLoggingOut) {
        LoadingScreen()
    } else if (!isAuthenticated) {
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
    val navigationState =
        rememberNavigationState(
            startRoute = ChatsList,
            topLevelRoutes = topLevelRoutes,
        )
    val navigator = remember { Navigator(navigationState) }
    val view = LocalView.current
    val hazeState = remember { HazeState() }
    val isOnline by authViewModel.isOnline.collectAsState()

    val showBottomBar by remember {
        derivedStateOf {
            val key = navigationState.backStacks[navigationState.topLevelRoute]?.lastOrNull()
            key in topLevelRoutes
        }
    }

    @Suppress("UnusedMaterial3ScaffoldPaddingParameter")
    CompositionLocalProvider(LocalIsOnline provides isOnline) {
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
                        bottomNavItems.forEach { item ->
                            NavigationBarItem(
                                icon = { Icon(item.icon, contentDescription = item.label) },
                                label = { Text(item.label) },
                                selected = item.route == navigationState.topLevelRoute,
                                onClick = {
                                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                    navigator.navigate(item.route)
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
                val provider =
                    entryProvider {
                        entry<ChatsList>(
                            clazzContentKey = { CHATS_CONTENT_KEY },
                        ) {
                            val chatsViewModel: ChatsViewModel = hiltViewModel()
                            ChatsScreen(
                                viewModel = chatsViewModel,
                                onChatClick = { chat ->
                                    navigator.navigate(ChatDetail(chatId = chat.id))
                                },
                                onProfileClick = {
                                    navigator.navigate(Profile)
                                },
                            )
                        }

                        entry<ChatDetail>(
                            metadata = parentMetadata(CHATS_CONTENT_KEY),
                        ) {
                            Surface(modifier = Modifier.fillMaxSize()) {
                                val chatsViewModel: ChatsViewModel = hiltViewModel()
                                ChatDetailScreen(
                                    viewModel = chatsViewModel,
                                    onBack = {
                                        chatsViewModel.stopMessagePolling()
                                        navigator.goBack()
                                    },
                                )
                            }
                        }

                        entry<MailList>(
                            clazzContentKey = { MAIL_CONTENT_KEY },
                        ) {
                            val mailViewModel: MailViewModel = hiltViewModel()
                            MailScreen(
                                viewModel = mailViewModel,
                                onMailClick = {
                                    navigator.navigate(MailDetail(mailId = "selected"))
                                },
                            )
                        }

                        entry<MailDetail>(
                            metadata = parentMetadata(MAIL_CONTENT_KEY),
                        ) {
                            val mailViewModel: MailViewModel = hiltViewModel()
                            val goBack = {
                                navigator.goBack()
                                mailViewModel.clearSelection()
                            }
                            BackHandler(onBack = goBack)
                            Surface(modifier = Modifier.fillMaxSize()) {
                                val selectedMail by mailViewModel.selectedMail.collectAsState()
                                val isDetailLoading by mailViewModel.isDetailLoading.collectAsState()
                                val authToken by mailViewModel.authToken.collectAsState()
                                if (selectedMail != null) {
                                    MailDetailScreen(
                                        mail = selectedMail!!,
                                        onBack = goBack,
                                        isBodyLoading = isDetailLoading,
                                        httpClient = mailViewModel.okHttpClient,
                                        authToken = authToken,
                                    )
                                } else {
                                    LoadingScreen()
                                }
                            }
                        }

                        entry<CalendarRoute> { CalendarScreen() }
                        entry<TeamsRoute> { TeamsScreen() }
                        entry<SearchRoute> { SearchScreen() }

                        entry<Profile> {
                            Surface(modifier = Modifier.fillMaxSize()) {
                                ProfileScreen(
                                    authViewModel = authViewModel,
                                    onBack = { navigator.goBack() },
                                )
                            }
                        }
                    }

                NavDisplay(
                    entries = navigationState.toDecoratedEntries(provider),
                    onBack = { navigator.goBack() },
                    popTransitionSpec = {
                        slideInHorizontally(initialOffsetX = { -it }) togetherWith
                            slideOutHorizontally(targetOffsetX = { it })
                    },
                    predictivePopTransitionSpec = { _ ->
                        slideInHorizontally(initialOffsetX = { -it }) togetherWith
                            slideOutHorizontally(targetOffsetX = { it })
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}
