package com.example.ui

import android.annotation.SuppressLint
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.WeatherRepository
import com.example.data.local.AppDatabase
import com.example.data.local.RunningScoreSettingsEntity
import com.example.data.local.SavedLocationEntity
import com.example.data.remote.GeocodingResultItem
import com.example.data.remote.OpenMeteoClient
import com.example.data.remote.IrmStationObservationSource
import com.example.data.remote.OpenMeteoHistoricalReanalysisSource
import com.example.data.remote.StationThenReanalysisSource
import com.example.model.ActiveLocation
import com.example.model.CompleteWeatherDashboard
import com.example.model.LocationReliabilityReport
import com.example.model.LocationType
import com.example.model.WeatherModel
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class AppScreen {
    DASHBOARD,
    LOCATIONS,
    RELIABILITY,
    SETTINGS
}

data class WeatherAppUiState(
    val currentScreen: AppScreen = AppScreen.DASHBOARD,
    val activeLocation: ActiveLocation = defaultParisGpsFallback(),
    val isLoadingWeather: Boolean = true,
    val weatherError: String? = null,
    val dashboard: CompleteWeatherDashboard? = null,
    val isLoadingReliability: Boolean = false,
    val reliabilityReport: LocationReliabilityReport? = null,
    val selectedReliabilityLeadDays: Int = 1,
    val selectedReliabilityModel: WeatherModel = WeatherModel.ICON_D2,
    val searchQuery: String = "",
    val isSearchingCities: Boolean = false,
    val searchResults: List<GeocodingResultItem> = emptyList(),
    val statusBannerMessage: String? = null
)

fun defaultParisGpsFallback(): ActiveLocation = ActiveLocation(
    id = null,
    name = "Position actuelle (Paris)",
    adminRegion = "Île-de-France · Repli GPS",
    latitude = 48.8566,
    longitude = 2.3522,
    type = LocationType.HOME,
    isCurrentGps = true,
    isSaved = false,
    forcedModelApiId = null
)

class WeatherViewModel(
    private val repository: WeatherRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(WeatherAppUiState())
    val uiState: StateFlow<WeatherAppUiState> = _uiState.asStateFlow()

    val savedLocations: StateFlow<List<SavedLocationEntity>> = repository.savedLocationsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val runningSettings: StateFlow<RunningScoreSettingsEntity> = repository.runningSettingsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), RunningScoreSettingsEntity())

    private var searchJob: Job? = null
    private var loadJob: Job? = null

    init {
        viewModelScope.launch {
            repository.ensureDefaultDataSeeded()
            refreshActiveLocationWeather(forceReliabilityRecalc = false)
            refreshBackgroundSavedLocationSummaries()
        }
    }

    fun navigateTo(screen: AppScreen) {
        _uiState.update { it.copy(currentScreen = screen) }
    }

    /**
     * Appelé lorsque l'utilisateur accorde ou refuse la permission GPS au démarrage.
     */
    @SuppressLint("MissingPermission")
    fun resolveGpsLocation(context: Context, permissionGranted: Boolean) {
        if (!permissionGranted) {
            _uiState.update {
                it.copy(
                    activeLocation = defaultParisGpsFallback(),
                    statusBannerMessage = "Permission GPS refusée : affichage sur Paris par défaut."
                )
            }
            refreshActiveLocationWeather(forceReliabilityRecalc = false)
            return
        }

        val fusedClient = runCatching { LocationServices.getFusedLocationProviderClient(context) }.getOrNull()
        if (fusedClient == null) {
            refreshActiveLocationWeather(forceReliabilityRecalc = false)
            return
        }

        val tokenSource = CancellationTokenSource()
        fusedClient.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, tokenSource.token)
            .addOnSuccessListener { loc ->
                if (loc != null) {
                    val gpsActive = ActiveLocation(
                        id = null,
                        name = "Ma Position GPS",
                        adminRegion = String.format(java.util.Locale.US, "%.3f°N, %.3f°E", loc.latitude, loc.longitude),
                        latitude = loc.latitude,
                        longitude = loc.longitude,
                        type = LocationType.HOME,
                        isCurrentGps = true,
                        isSaved = false,
                        forcedModelApiId = _uiState.value.activeLocation.forcedModelApiId
                    )
                    _uiState.update { it.copy(activeLocation = gpsActive, statusBannerMessage = null) }
                    refreshActiveLocationWeather(forceReliabilityRecalc = false)
                }
            }
    }

    fun refreshActiveLocationWeather(forceReliabilityRecalc: Boolean = false) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val loc = _uiState.value.activeLocation
            _uiState.update {
                it.copy(
                    isLoadingWeather = true,
                    isLoadingReliability = true,
                    weatherError = null
                )
            }

            val reliability = repository.getOrComputeReliabilityReport(
                location = loc,
                forceRefresh = forceReliabilityRecalc
            )

            // Sélectionner par défaut pour le graphe 30j le meilleur modèle J+1 s'il existe
            val bestJ1Model = reliability.metrics
                .filter { it.leadDays == 1 }
                .minByOrNull { it.tempMaeC }?.model
                ?: WeatherModel.ICON_D2

            _uiState.update {
                it.copy(
                    isLoadingReliability = false,
                    reliabilityReport = reliability,
                    selectedReliabilityModel = bestJ1Model
                )
            }

            runCatching {
                repository.loadWeatherDashboard(location = loc, reliabilityReport = reliability)
            }.onSuccess { dash ->
                _uiState.update {
                    it.copy(
                        isLoadingWeather = false,
                        dashboard = dash,
                        weatherError = null
                    )
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isLoadingWeather = false,
                        weatherError = err.message ?: "Erreur lors du chargement des prévisions Open-Meteo"
                    )
                }
            }
        }
    }

    private fun refreshBackgroundSavedLocationSummaries() {
        viewModelScope.launch {
            val all = savedLocations.value
            for (entity in all) {
                runCatching {
                    repository.loadWeatherDashboard(entity.toActiveLocation())
                }
            }
        }
    }

    /**
     * Permet de forcer un modèle manuellement ou de revenir au choix automatique (model == null).
     * Le forçage manuel prime toujours sur le choix automatique.
     */
    fun selectModelOverride(model: WeatherModel?) {
        val currentLoc = _uiState.value.activeLocation
        val updatedLoc = currentLoc.copy(forcedModelApiId = model?.apiId)
        _uiState.update { it.copy(activeLocation = updatedLoc) }

        viewModelScope.launch {
            if (currentLoc.id != null) {
                repository.setForcedModelForSavedLocation(currentLoc.id, model)
            }
            refreshActiveLocationWeather(forceReliabilityRecalc = false)
        }
    }

    /**
     * Sélectionne un lieu enregistré et bascule sur l'écran principal.
     */
    fun selectSavedLocation(entity: SavedLocationEntity) {
        _uiState.update {
            it.copy(
                activeLocation = entity.toActiveLocation(),
                currentScreen = AppScreen.DASHBOARD
            )
        }
        refreshActiveLocationWeather(forceReliabilityRecalc = false)
    }

    /**
     * Consulte une ville issue du Geocoding SANS l'enregistrer en base.
     */
    fun previewSearchedCityWithoutSaving(item: GeocodingResultItem) {
        val tempLocation = ActiveLocation(
            id = null,
            name = item.name,
            adminRegion = item.subtitle.ifBlank { "Consultation libre" },
            latitude = item.latitude,
            longitude = item.longitude,
            type = LocationType.OTHER,
            isCurrentGps = false,
            isSaved = false,
            forcedModelApiId = null
        )
        _uiState.update {
            it.copy(
                activeLocation = tempLocation,
                currentScreen = AppScreen.DASHBOARD,
                searchQuery = "",
                searchResults = emptyList()
            )
        }
        refreshActiveLocationWeather(forceReliabilityRecalc = false)
    }

    fun updateSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        searchJob?.cancel()
        if (query.trim().length < 2) {
            _uiState.update { it.copy(searchResults = emptyList(), isSearchingCities = false) }
            return
        }
        searchJob = viewModelScope.launch {
            _uiState.update { it.copy(isSearchingCities = true) }
            delay(280)
            val results = repository.searchCities(query)
            _uiState.update { it.copy(searchResults = results, isSearchingCities = false) }
        }
    }

    fun saveLocation(
        existingId: Long = 0L,
        name: String,
        adminRegion: String,
        latitude: Double,
        longitude: Double,
        type: LocationType,
        commuteMorningStart: Int,
        commuteMorningEnd: Int,
        commuteEveningStart: Int,
        commuteEveningEnd: Int,
        selectImmediately: Boolean = true
    ) {
        viewModelScope.launch {
            val entity = SavedLocationEntity(
                id = existingId,
                name = name.trim().ifBlank { "Lieu" },
                adminRegion = adminRegion.trim(),
                latitude = latitude,
                longitude = longitude,
                locationType = type.name,
                commuteMorningStartHour = commuteMorningStart.coerceIn(0, 23),
                commuteMorningEndHour = commuteMorningEnd.coerceIn(0, 23),
                commuteEveningStartHour = commuteEveningStart.coerceIn(0, 23),
                commuteEveningEndHour = commuteEveningEnd.coerceIn(0, 23),
                forcedModelApiId = _uiState.value.activeLocation.forcedModelApiId
            )
            val savedId = repository.saveOrUpdateLocation(entity)
            val updatedEntity = entity.copy(id = savedId)
            if (selectImmediately) {
                _uiState.update {
                    it.copy(
                        activeLocation = updatedEntity.toActiveLocation(),
                        searchQuery = "",
                        searchResults = emptyList()
                    )
                }
                refreshActiveLocationWeather(forceReliabilityRecalc = false)
            } else {
                refreshBackgroundSavedLocationSummaries()
            }
        }
    }

    fun deleteSavedLocation(id: Long) {
        viewModelScope.launch {
            repository.deleteLocation(id)
            if (_uiState.value.activeLocation.id == id) {
                _uiState.update { it.copy(activeLocation = defaultParisGpsFallback()) }
                refreshActiveLocationWeather(forceReliabilityRecalc = false)
            }
        }
    }

    fun updateRunningSettings(newSettings: RunningScoreSettingsEntity) {
        viewModelScope.launch {
            repository.saveRunningSettings(newSettings)
            refreshActiveLocationWeather(forceReliabilityRecalc = false)
        }
    }

    fun selectReliabilityLeadDay(leadDay: Int) {
        _uiState.update { state ->
            val availableModelsForLead = state.reliabilityReport?.metrics
                ?.filter { it.leadDays == leadDay }
                ?.map { it.model }
                .orEmpty()
            val nextModel = if (state.selectedReliabilityModel in availableModelsForLead) {
                state.selectedReliabilityModel
            } else {
                availableModelsForLead.firstOrNull() ?: WeatherModel.ECMWF_IFS
            }
            state.copy(
                selectedReliabilityLeadDays = leadDay,
                selectedReliabilityModel = nextModel
            )
        }
    }

    fun selectReliabilityModel(model: WeatherModel) {
        _uiState.update { it.copy(selectedReliabilityModel = model) }
    }

    fun dismissBannerMessage() {
        _uiState.update { it.copy(statusBannerMessage = null) }
    }

    companion object {
        fun provideFactory(context: Context): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    val db = AppDatabase.getInstance(context)
                    val client = OpenMeteoClient()
                    // Le « réel » = la station IRM la plus proche (≤ 25 km, sans clé), sinon la réanalyse
                    val groundTruth = StationThenReanalysisSource(
                        station = IrmStationObservationSource(client.httpClient),
                        reanalysis = OpenMeteoHistoricalReanalysisSource(client.httpClient)
                    )
                    val repo = WeatherRepository(
                        weatherDao = db.weatherDao(),
                        openMeteoClient = client,
                        groundTruthSource = groundTruth
                    )
                    return WeatherViewModel(repo) as T
                }
            }
        }
    }
}
