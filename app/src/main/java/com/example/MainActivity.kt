package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.components.GroceryTopBar
import com.example.ui.navigation.Screen
import com.example.ui.screens.*
import com.example.ui.theme.SmartGroceryTheme
import com.example.ui.viewmodel.GroceryViewModel
import com.example.ui.viewmodel.GroceryViewModelFactory

class MainActivity : ComponentActivity() {

    private val viewModel: GroceryViewModel by viewModels {
        val app = application as SmartGroceryApp
        GroceryViewModelFactory(application, app.repository, app.firestoreSyncRepository)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SmartGroceryTheme {
                SmartGroceryMainApp(viewModel = viewModel)
            }
        }
    }
}

@Composable
fun SmartGroceryMainApp(viewModel: GroceryViewModel) {
    val currentScreen by viewModel.currentScreen.collectAsStateWithLifecycle()
    val settings by viewModel.shopSettings.collectAsStateWithLifecycle()
    val isHindi = settings.language == "hi"

    // BackHandler: when on a sub-screen, pressing device back returns to Dashboard
    BackHandler(enabled = currentScreen != Screen.DASHBOARD) {
        viewModel.navigateTo(Screen.DASHBOARD)
    }

    // Core bottom destinations
    val primaryNavigationDestinations = remember {
        listOf(
            Screen.DASHBOARD,
            Screen.BILLING,
            Screen.WEIGHT_CALCULATOR,
            Screen.PRODUCTS,
            Screen.KHATA
        )
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isTablet = maxWidth >= 600.dp

        if (isTablet) {
            // Tablet Layout with Side NavigationRail
            Row(modifier = Modifier.fillMaxSize()) {
                NavigationRail(
                    header = {
                        Text(
                            text = "POS",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(vertical = 12.dp)
                        )
                    },
                    modifier = Modifier.windowInsetsPadding(WindowInsets.statusBars)
                ) {
                    primaryNavigationDestinations.forEach { screen ->
                        NavigationRailItem(
                            selected = currentScreen == screen,
                            onClick = { viewModel.navigateTo(screen) },
                            icon = { Icon(screen.icon, contentDescription = screen.title) },
                            label = { Text(screen.getLocalizedTitle(settings.language)) },
                            modifier = Modifier.testTag("nav_rail_${screen.name.lowercase()}")
                        )
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    NavigationRailItem(
                        selected = currentScreen == Screen.REPORTS,
                        onClick = { viewModel.navigateTo(Screen.REPORTS) },
                        icon = { Icon(Screen.REPORTS.icon, contentDescription = Screen.REPORTS.title) },
                        label = { Text(Screen.REPORTS.getLocalizedTitle(settings.language)) }
                    )
                    NavigationRailItem(
                        selected = currentScreen == Screen.ADMIN_PANEL,
                        onClick = { viewModel.navigateTo(Screen.ADMIN_PANEL) },
                        icon = { Icon(Screen.ADMIN_PANEL.icon, contentDescription = Screen.ADMIN_PANEL.title) },
                        label = { Text(Screen.ADMIN_PANEL.getLocalizedTitle(settings.language)) }
                    )
                    NavigationRailItem(
                        selected = currentScreen == Screen.BACKUP_SETTINGS,
                        onClick = { viewModel.navigateTo(Screen.BACKUP_SETTINGS) },
                        icon = { Icon(Screen.BACKUP_SETTINGS.icon, contentDescription = Screen.BACKUP_SETTINGS.title) },
                        label = { Text(Screen.BACKUP_SETTINGS.getLocalizedTitle(settings.language)) }
                    )
                }

                Scaffold(
                    topBar = {
                        GroceryTopBar(
                            shopName = settings.shopName,
                            currentLanguage = settings.language,
                            onSelectLanguage = { chosenLang ->
                                viewModel.updateSettings(settings.copy(language = chosenLang))
                            },
                            onOpenSettings = { viewModel.navigateTo(Screen.BACKUP_SETTINGS) },
                            onOpenAdmin = { viewModel.navigateTo(Screen.ADMIN_PANEL) }
                        )
                    },
                    modifier = Modifier.weight(1f)
                ) { padding ->
                    ScreenContent(
                        currentScreen = currentScreen,
                        viewModel = viewModel,
                        modifier = Modifier.padding(padding)
                    )
                }
            }
        } else {
            // Standard Phone Layout with TopBar & Bottom NavigationBar
            Scaffold(
                topBar = {
                    GroceryTopBar(
                        shopName = settings.shopName,
                        currentLanguage = settings.language,
                        onSelectLanguage = { chosenLang ->
                            viewModel.updateSettings(settings.copy(language = chosenLang))
                        },
                        onOpenSettings = { viewModel.navigateTo(Screen.BACKUP_SETTINGS) },
                        onOpenAdmin = { viewModel.navigateTo(Screen.ADMIN_PANEL) }
                    )
                },
                bottomBar = {
                    NavigationBar(
                        windowInsets = WindowInsets.navigationBars,
                        modifier = Modifier.testTag("bottom_nav_bar")
                    ) {
                        primaryNavigationDestinations.forEach { screen ->
                            NavigationBarItem(
                                selected = currentScreen == screen,
                                onClick = { viewModel.navigateTo(screen) },
                                icon = { Icon(screen.icon, contentDescription = screen.title) },
                                label = {
                                    Text(
                                        text = screen.getLocalizedTitle(settings.language),
                                        maxLines = 1
                                    )
                                },
                                modifier = Modifier.testTag("bottom_nav_${screen.name.lowercase()}")
                            )
                        }
                    }
                },
                modifier = Modifier.fillMaxSize()
            ) { padding ->
                ScreenContent(
                    currentScreen = currentScreen,
                    viewModel = viewModel,
                    modifier = Modifier.padding(padding)
                )
            }
        }
    }
}

@Composable
fun ScreenContent(
    currentScreen: Screen,
    viewModel: GroceryViewModel,
    modifier: Modifier = Modifier
) {
    AnimatedContent(
        targetState = currentScreen,
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        label = "ScreenTransition",
        modifier = modifier.fillMaxSize()
    ) { targetScreen ->
        when (targetScreen) {
            Screen.DASHBOARD -> DashboardScreen(
                viewModel = viewModel,
                onNavigate = { screen -> viewModel.navigateTo(screen) }
            )
            Screen.BILLING -> BillingScreen(
                viewModel = viewModel,
                onNavigate = { screen -> viewModel.navigateTo(screen) }
            )
            Screen.WEIGHT_CALCULATOR -> WeightCalculatorScreen(
                viewModel = viewModel,
                onNavigate = { screen -> viewModel.navigateTo(screen) }
            )
            Screen.PRODUCTS -> ProductManagementScreen(
                viewModel = viewModel
            )
            Screen.KHATA -> CustomerKhataScreen(
                viewModel = viewModel
            )
            Screen.REPORTS -> ReportsScreen(
                viewModel = viewModel
            )
            Screen.BACKUP_SETTINGS -> SettingsBackupScreen(
                viewModel = viewModel
            )
            Screen.ADMIN_PANEL -> AdminPanelScreen(
                viewModel = viewModel
            )
        }
    }
}
