package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.model.LocationType
import com.example.ui.AppScreen
import com.example.ui.WeatherViewModel
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.LocationsScreen
import com.example.ui.screens.ReliabilityScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.CyanPrecip
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.SlateBackground
import com.example.ui.theme.SlateSurface
import com.example.ui.theme.SlateSurfaceBright
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                MeteoPrecisionApp()
            }
        }
    }
}

@Composable
fun MeteoPrecisionApp(
    viewModel: WeatherViewModel = viewModel(factory = WeatherViewModel.provideFactory(LocalContext.current))
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val savedLocations by viewModel.savedLocations.collectAsStateWithLifecycle()
    val runningSettings by viewModel.runningSettings.collectAsStateWithLifecycle()

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        viewModel.resolveGpsLocation(context, granted)
    }

    LaunchedEffect(Unit) {
        val hasFine = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (hasFine || hasCoarse) {
            viewModel.resolveGpsLocation(context, true)
        } else {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = SlateBackground,
        contentWindowInsets = WindowInsets.safeDrawing,
        bottomBar = {
            NavigationBar(
                containerColor = SlateSurface,
                contentColor = TextPrimary
            ) {
                val navColors = NavigationBarItemDefaults.colors(
                    selectedIconColor = CyanPrecip,
                    selectedTextColor = CyanPrecip,
                    indicatorColor = SlateSurfaceBright,
                    unselectedIconColor = TextSecondary,
                    unselectedTextColor = TextSecondary
                )

                NavigationBarItem(
                    selected = uiState.currentScreen == AppScreen.DASHBOARD,
                    onClick = { viewModel.navigateTo(AppScreen.DASHBOARD) },
                    icon = { Icon(Icons.Default.Cloud, contentDescription = stringResource(R.string.nav_dashboard)) },
                    label = { Text(stringResource(R.string.nav_dashboard)) },
                    colors = navColors,
                    modifier = Modifier.testTag("nav_tab_dashboard")
                )
                NavigationBarItem(
                    selected = uiState.currentScreen == AppScreen.LOCATIONS,
                    onClick = { viewModel.navigateTo(AppScreen.LOCATIONS) },
                    icon = { Icon(Icons.Default.Place, contentDescription = stringResource(R.string.nav_locations)) },
                    label = { Text(stringResource(R.string.nav_locations)) },
                    colors = navColors,
                    modifier = Modifier.testTag("nav_tab_locations")
                )
                NavigationBarItem(
                    selected = uiState.currentScreen == AppScreen.RELIABILITY,
                    onClick = { viewModel.navigateTo(AppScreen.RELIABILITY) },
                    icon = { Icon(Icons.Default.Analytics, contentDescription = stringResource(R.string.nav_reliability)) },
                    label = { Text(stringResource(R.string.nav_reliability)) },
                    colors = navColors,
                    modifier = Modifier.testTag("nav_tab_reliability")
                )
                NavigationBarItem(
                    selected = uiState.currentScreen == AppScreen.SETTINGS,
                    onClick = { viewModel.navigateTo(AppScreen.SETTINGS) },
                    icon = { Icon(Icons.Default.Tune, contentDescription = stringResource(R.string.nav_settings)) },
                    label = { Text(stringResource(R.string.nav_settings)) },
                    colors = navColors,
                    modifier = Modifier.testTag("nav_tab_settings")
                )
            }
        }
    ) { innerPadding ->
        when (uiState.currentScreen) {
            AppScreen.DASHBOARD -> {
                DashboardScreen(
                    dashboard = uiState.dashboard,
                    isLoading = uiState.isLoadingWeather,
                    errorMessage = uiState.weatherError,
                    statusBanner = uiState.statusBannerMessage,
                    onRefresh = { viewModel.refreshActiveLocationWeather(forceReliabilityRecalc = false) },
                    onSelectModelOverride = { model -> viewModel.selectModelOverride(model) },
                    onOpenLocations = { viewModel.navigateTo(AppScreen.LOCATIONS) },
                    onOpenReliability = { viewModel.navigateTo(AppScreen.RELIABILITY) },
                    onQuickSaveCurrentCity = {
                        val loc = uiState.activeLocation
                        viewModel.saveLocation(
                            existingId = 0L,
                            name = loc.name,
                            adminRegion = loc.adminRegion,
                            latitude = loc.latitude,
                            longitude = loc.longitude,
                            type = LocationType.OTHER,
                            commuteMorningStart = 7,
                            commuteMorningEnd = 9,
                            commuteEveningStart = 17,
                            commuteEveningEnd = 19,
                            selectImmediately = true
                        )
                    },
                    modifier = Modifier.padding(innerPadding)
                )
            }

            AppScreen.LOCATIONS -> {
                LocationsScreen(
                    savedLocations = savedLocations,
                    activeLocation = uiState.activeLocation,
                    searchQuery = uiState.searchQuery,
                    isSearching = uiState.isSearchingCities,
                    searchResults = uiState.searchResults,
                    onSearchQueryChange = viewModel::updateSearchQuery,
                    onPreviewCityWithoutSaving = viewModel::previewSearchedCityWithoutSaving,
                    onSelectSavedLocation = viewModel::selectSavedLocation,
                    onUseGpsPosition = {
                        viewModel.resolveGpsLocation(context, true)
                        viewModel.navigateTo(AppScreen.DASHBOARD)
                    },
                    onSaveOrUpdateLocation = { id, name, region, lat, lon, type, ms, me, es, ee ->
                        viewModel.saveLocation(
                            existingId = id,
                            name = name,
                            adminRegion = region,
                            latitude = lat,
                            longitude = lon,
                            type = type,
                            commuteMorningStart = ms,
                            commuteMorningEnd = me,
                            commuteEveningStart = es,
                            commuteEveningEnd = ee,
                            selectImmediately = true
                        )
                    },
                    onDeleteLocation = viewModel::deleteSavedLocation,
                    onBackToDashboard = { viewModel.navigateTo(AppScreen.DASHBOARD) },
                    modifier = Modifier.padding(innerPadding)
                )
            }

            AppScreen.RELIABILITY -> {
                ReliabilityScreen(
                    activeLocation = uiState.activeLocation,
                    report = uiState.reliabilityReport,
                    isLoading = uiState.isLoadingReliability,
                    selectedLeadDays = uiState.selectedReliabilityLeadDays,
                    selectedModel = uiState.selectedReliabilityModel,
                    onSelectLeadDays = viewModel::selectReliabilityLeadDay,
                    onSelectModel = viewModel::selectReliabilityModel,
                    onForceRecalculate = { viewModel.refreshActiveLocationWeather(forceReliabilityRecalc = true) },
                    onSelectModelOverride = viewModel::selectModelOverride,
                    onBackToDashboard = { viewModel.navigateTo(AppScreen.DASHBOARD) },
                    modifier = Modifier.padding(innerPadding)
                )
            }

            AppScreen.SETTINGS -> {
                SettingsScreen(
                    currentSettings = runningSettings,
                    onSaveSettings = viewModel::updateRunningSettings,
                    onBackToDashboard = { viewModel.navigateTo(AppScreen.DASHBOARD) },
                    modifier = Modifier.padding(innerPadding)
                )
            }
        }
    }
}
