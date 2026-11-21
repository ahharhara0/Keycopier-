package com.example.ui

import android.content.Context
import android.content.res.Configuration
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.FolderCopy
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.FolderCopy
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.R
import com.example.domain.model.AppLanguage
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.KeyCatalogScreen
import com.example.ui.screens.LibraryScreen
import com.example.ui.screens.PinLockScreen
import com.example.ui.screens.ReportsScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.StudioScreen
import com.example.ui.theme.KeyCopierTheme
import kotlinx.serialization.Serializable
import java.util.Locale

@Serializable
sealed interface AppRoute {
    @Serializable data object Home : AppRoute
    @Serializable data object Library : AppRoute
    @Serializable data object Studio : AppRoute
    @Serializable data object Reports : AppRoute
    @Serializable data object Settings : AppRoute
    @Serializable data object Catalog : AppRoute
}

private data class NavTab(
    val route: AppRoute,
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
)

@Composable
fun KeyCopierApp(
    vaultViewModel: VaultViewModel,
    studioViewModel: StudioViewModel
) {
    val settings by vaultViewModel.settings.collectAsStateWithLifecycle()
    val lockState by vaultViewModel.lockState.collectAsStateWithLifecycle()
    val vaultState by vaultViewModel.vaultState.collectAsStateWithLifecycle()
    val studioState by studioViewModel.state.collectAsStateWithLifecycle()

    val baseContext = LocalContext.current
    val localizedContext = remember(baseContext, settings.language) {
        updateLocaleContext(baseContext, settings.language)
    }
    val layoutDirection = if (settings.language == AppLanguage.ARABIC) {
        LayoutDirection.Rtl
    } else {
        LayoutDirection.Ltr
    }

    val registryOwner = baseContext as? androidx.activity.result.ActivityResultRegistryOwner
    val providers = buildList {
        add(LocalContext provides localizedContext)
        add(LocalLayoutDirection provides layoutDirection)
        if (registryOwner != null) {
            add(androidx.activity.compose.LocalActivityResultRegistryOwner provides registryOwner)
        }
    }.toTypedArray()

    CompositionLocalProvider(*providers) {
        KeyCopierTheme(themeMode = settings.theme) {
            if (!lockState.isUnlocked) {
                PinLockScreen(
                    lockState = lockState,
                    biometricEnabled = settings.biometricEnabled,
                    onPinCreated = { pin -> vaultViewModel.createAndUnlockPin(pin) },
                    onPinAttempt = { pin -> vaultViewModel.attemptPinUnlock(pin) },
                    onBiometricSuccess = { vaultViewModel.unlockWithBiometric() }
                )
            } else {
                MainShell(
                    vaultViewModel = vaultViewModel,
                    studioViewModel = studioViewModel,
                    vaultState = vaultState,
                    studioState = studioState,
                    settings = settings
                )
            }
        }
    }
}

@Composable
private fun MainShell(
    vaultViewModel: VaultViewModel,
    studioViewModel: StudioViewModel,
    vaultState: VaultUiState,
    studioState: StudioUiState,
    settings: com.example.domain.model.AppSettings
) {
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    var selectedReportKeyId by remember { mutableStateOf<Long?>(null) }

    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    val useNavRail = screenWidthDp >= 640

    val tabs = remember {
        listOf(
            NavTab(AppRoute.Home, R.string.nav_home, Icons.Filled.Home, Icons.Outlined.Home, "nav_home"),
            NavTab(AppRoute.Library, R.string.nav_library, Icons.Filled.FolderCopy, Icons.Outlined.FolderCopy, "nav_library"),
            NavTab(AppRoute.Studio, R.string.nav_studio, Icons.Filled.Build, Icons.Outlined.Build, "nav_studio"),
            NavTab(AppRoute.Reports, R.string.nav_reports, Icons.Filled.PictureAsPdf, Icons.Outlined.PictureAsPdf, "nav_reports"),
            NavTab(AppRoute.Settings, R.string.nav_settings, Icons.Filled.Settings, Icons.Outlined.Settings, "nav_settings")
        )
    }

    fun navigateToTab(route: AppRoute) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        bottomBar = {
            if (!useNavRail) {
                NavigationBar {
                    tabs.forEach { tab ->
                        val selected = currentDestination?.hasRoute(tab.route::class) == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = { navigateToTab(tab.route) },
                            icon = {
                                Icon(
                                    imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                    contentDescription = stringResource(tab.labelRes)
                                )
                            },
                            label = {
                                Text(
                                    text = stringResource(tab.labelRes),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            modifier = Modifier.testTag(tab.testTag)
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
            if (useNavRail) {
                NavigationRail(
                    containerColor = MaterialTheme.colorScheme.surface
                ) {
                    tabs.forEach { tab ->
                        val selected = currentDestination?.hasRoute(tab.route::class) == true
                        NavigationRailItem(
                            selected = selected,
                            onClick = { navigateToTab(tab.route) },
                            icon = {
                                Icon(
                                    imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                    contentDescription = stringResource(tab.labelRes)
                                )
                            },
                            label = { Text(stringResource(tab.labelRes)) },
                            modifier = Modifier.testTag(tab.testTag)
                        )
                    }
                }
            }

            Box(modifier = Modifier.weight(1f).fillMaxSize()) {
                NavHost(
                    navController = navController,
                    startDestination = AppRoute.Home
                ) {
                    composable<AppRoute.Home> {
                        HomeScreen(
                            vaultState = vaultState,
                            settings = settings,
                            onOpenStudio = { mode ->
                                studioViewModel.startNewKey(mode)
                                navigateToTab(AppRoute.Studio)
                            },
                            onOpenCatalog = { navController.navigate(AppRoute.Catalog) },
                            onOpenLibrary = { navigateToTab(AppRoute.Library) },
                            onOpenReports = { navigateToTab(AppRoute.Reports) },
                            onSelectKey = { record ->
                                studioViewModel.loadRecordForEditing(record)
                                navigateToTab(AppRoute.Studio)
                            },
                            onLockNow = { vaultViewModel.lockNow() }
                        )
                    }
                    composable<AppRoute.Library> {
                        LibraryScreen(
                            vaultState = vaultState,
                            settings = settings,
                            onSearchChange = { vaultViewModel.updateSearchQuery(it) },
                            onSelectCategory = { vaultViewModel.selectCategory(it) },
                            onToggleGridView = { vaultViewModel.toggleGridView() },
                            onToggleFavorite = { vaultViewModel.toggleFavorite(it) },
                            onEditInStudio = { record ->
                                studioViewModel.loadRecordForEditing(record)
                                navigateToTab(AppRoute.Studio)
                            },
                            onOpenInReports = { record ->
                                selectedReportKeyId = record.id
                                navigateToTab(AppRoute.Reports)
                            },
                            onShareSpec = { record -> vaultViewModel.shareKeySpecText(record) },
                            onDeleteKey = { id -> vaultViewModel.deleteKey(id) }
                        )
                    }
                    composable<AppRoute.Studio> {
                        StudioScreen(
                            state = studioState,
                            settings = settings,
                            viewModel = studioViewModel,
                            snackbarHostState = snackbarHostState,
                            onOpenCatalog = { navController.navigate(AppRoute.Catalog) },
                            onSavedToVault = { vaultViewModel.refreshStorageStats() }
                        )
                    }
                    composable<AppRoute.Catalog> {
                        KeyCatalogScreen(
                            settings = settings,
                            onSelectPreset = { preset, openPad ->
                                studioViewModel.applyCatalogPreset(
                                    preset = preset,
                                    isArabic = settings.language == AppLanguage.ARABIC,
                                    openLongitudinalPad = openPad
                                )
                                navController.popBackStack()
                                navigateToTab(AppRoute.Studio)
                            },
                            onBack = { navController.popBackStack() }
                        )
                    }
                    composable<AppRoute.Reports> {
                        ReportsScreen(
                            vaultState = vaultState,
                            preselectedKeyId = selectedReportKeyId,
                            viewModel = vaultViewModel,
                            snackbarHostState = snackbarHostState
                        )
                    }
                    composable<AppRoute.Settings> {
                        SettingsScreen(
                            settings = settings,
                            vaultState = vaultState,
                            viewModel = vaultViewModel
                        )
                    }
                }
            }
        }
    }
}

private fun updateLocaleContext(context: Context, language: AppLanguage): Context {
    val locale = Locale(language.code)
    Locale.setDefault(locale)
    val config = Configuration(context.resources.configuration)
    config.setLocale(locale)
    config.setLayoutDirection(locale)
    val configContext = context.createConfigurationContext(config)
    return object : android.content.ContextWrapper(context) {
        override fun getResources(): android.content.res.Resources = configContext.resources
        override fun getAssets(): android.content.res.AssetManager = configContext.assets
    }
}
