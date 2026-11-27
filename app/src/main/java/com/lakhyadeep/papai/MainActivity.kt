package com.lakhyadeep.papai

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Face
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.lakhyadeep.papai.ui.components.PapiSplashOverlay
import com.lakhyadeep.papai.ui.navigation.AssistantsRoute
import com.lakhyadeep.papai.ui.navigation.BenchmarkRoute
import com.lakhyadeep.papai.ui.navigation.ChatRoute
import com.lakhyadeep.papai.ui.navigation.ModelsRoute
import com.lakhyadeep.papai.ui.navigation.SettingsRoute
import com.lakhyadeep.papai.ui.screens.AssistantsScreen
import com.lakhyadeep.papai.ui.screens.BenchmarkScreen
import com.lakhyadeep.papai.ui.screens.ChatScreen
import com.lakhyadeep.papai.ui.screens.ModelsScreen
import com.lakhyadeep.papai.ui.screens.SettingsScreen
import com.lakhyadeep.papai.ui.theme.PapiAiTheme
import com.lakhyadeep.papai.ui.viewmodel.AssistantsViewModel
import com.lakhyadeep.papai.ui.viewmodel.BenchmarkViewModel
import com.lakhyadeep.papai.ui.viewmodel.ChatViewModel
import com.lakhyadeep.papai.ui.viewmodel.ModelsViewModel
import com.lakhyadeep.papai.ui.viewmodel.PapiViewModelFactory
import com.lakhyadeep.papai.ui.viewmodel.SettingsViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as PapiAiApplication).container

        setContent {
            val prefs by container.settingsRepository.preferencesFlow.collectAsStateWithLifecycle(
                initialValue = com.lakhyadeep.papai.data.local.prefs.AppPreferences()
            )
            var showSplash by remember { mutableStateOf(true) }

            PapiAiTheme(themeMode = prefs.themeMode) {
                Box(modifier = Modifier.fillMaxSize()) {
                    PapiAiAppRoot(factory = PapiViewModelFactory(container))
                    PapiSplashOverlay(
                        visible = showSplash,
                        onFinished = { showSplash = false }
                    )
                }
            }
        }
    }
}

private data class NavDestinationItem(
    val label: String,
    val route: Any,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
)

@Composable
fun PapiAiAppRoot(factory: PapiViewModelFactory) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDest = backStackEntry?.destination

    val chatVm: ChatViewModel = viewModel(factory = factory)
    val modelsVm: ModelsViewModel = viewModel(factory = factory)
    val assistantsVm: AssistantsViewModel = viewModel(factory = factory)
    val benchmarkVm: BenchmarkViewModel = viewModel(factory = factory)
    val settingsVm: SettingsViewModel = viewModel(factory = factory)

    val destinations = remember {
        listOf(
            NavDestinationItem("Chat", ChatRoute, Icons.Filled.ChatBubble, Icons.Outlined.ChatBubbleOutline, "nav_chat"),
            NavDestinationItem("Models", ModelsRoute, Icons.Filled.Memory, Icons.Outlined.Memory, "nav_models"),
            NavDestinationItem("Pals", AssistantsRoute, Icons.Filled.Face, Icons.Outlined.Face, "nav_assistants"),
            NavDestinationItem("Bench", BenchmarkRoute, Icons.Filled.Speed, Icons.Outlined.Speed, "nav_benchmark"),
            NavDestinationItem("Settings", SettingsRoute, Icons.Filled.Settings, Icons.Outlined.Settings, "nav_settings")
        )
    }

    fun navigateTo(route: Any) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 600.dp

        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            bottomBar = {
                if (!useRail) {
                    NavigationBar(windowInsets = WindowInsets.navigationBars) {
                        destinations.forEach { item ->
                            val selected = currentDest?.hasRoute(item.route::class) == true
                            NavigationBarItem(
                                selected = selected,
                                onClick = { navigateTo(item.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                        contentDescription = item.label
                                    )
                                },
                                label = { Text(item.label) },
                                modifier = Modifier.testTag(item.testTag)
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useRail) {
                    NavigationRail {
                        destinations.forEach { item ->
                            val selected = currentDest?.hasRoute(item.route::class) == true
                            NavigationRailItem(
                                selected = selected,
                                onClick = { navigateTo(item.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                        contentDescription = item.label
                                    )
                                },
                                label = { Text(item.label) },
                                modifier = Modifier.testTag(item.testTag)
                            )
                        }
                    }
                }

                NavHost(
                    navController = navController,
                    startDestination = ChatRoute,
                    modifier = Modifier.weight(1f)
                ) {
                    composable<ChatRoute> {
                        val state by chatVm.uiState.collectAsStateWithLifecycle()
                        ChatScreen(
                            uiState = state,
                            viewModel = chatVm,
                            onNavigateToModels = { navigateTo(ModelsRoute) }
                        )
                    }
                    composable<ModelsRoute> {
                        val state by modelsVm.uiState.collectAsStateWithLifecycle()
                        ModelsScreen(
                            uiState = state,
                            viewModel = modelsVm
                        )
                    }
                    composable<AssistantsRoute> {
                        val state by assistantsVm.uiState.collectAsStateWithLifecycle()
                        AssistantsScreen(
                            uiState = state,
                            viewModel = assistantsVm,
                            onStartChatWithAssistant = { pal ->
                                chatVm.selectAssistant(pal)
                                navigateTo(ChatRoute)
                            }
                        )
                    }
                    composable<BenchmarkRoute> {
                        val state by benchmarkVm.uiState.collectAsStateWithLifecycle()
                        BenchmarkScreen(
                            uiState = state,
                            viewModel = benchmarkVm,
                            onNavigateToModels = { navigateTo(ModelsRoute) }
                        )
                    }
                    composable<SettingsRoute> {
                        val state by settingsVm.uiState.collectAsStateWithLifecycle()
                        SettingsScreen(
                            uiState = state,
                            viewModel = settingsVm
                        )
                    }
                }
            }
        }
    }
}
