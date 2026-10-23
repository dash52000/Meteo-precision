package com.example.data

import com.example.data.local.ReliabilityCacheEntity
import com.example.data.local.RunningScoreSettingsEntity
import com.example.data.local.SavedLocationEntity
import com.example.data.local.WeatherDao
import com.example.data.local.WeatherForecastCacheEntity
import com.example.data.remote.GeocodingResultItem
import com.example.data.remote.GroundTruthWeatherSource
import com.example.data.remote.OpenMeteoClient
import com.example.domain.ForecastParserEngine
import com.example.domain.ReliabilityAndAutoModelEngine
import com.example.model.ActiveLocation
import com.example.model.CompleteWeatherDashboard
import com.example.model.LocationReliabilityReport
import com.example.model.LocationType
import com.example.model.WeatherModel
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.Locale

class WeatherRepository(
    private val weatherDao: WeatherDao,
    private val openMeteoClient: OpenMeteoClient,
    private val groundTruthSource: GroundTruthWeatherSource
) {

    val savedLocationsFlow: Flow<List<SavedLocationEntity>> = weatherDao.observeSavedLocations()

    val runningSettingsFlow: Flow<RunningScoreSettingsEntity> =
        weatherDao.observeRunningSettings().map { it ?: RunningScoreSettingsEntity() }

    suspend fun ensureDefaultDataSeeded() {
        val currentSettings = weatherDao.getRunningSettingsOnce()
        if (currentSettings == null) {
            weatherDao.saveRunningSettings(RunningScoreSettingsEntity())
        }
        val locations = weatherDao.getSavedLocationsOnce()
        if (locations.isEmpty()) {
            val defaults = listOf(
                SavedLocationEntity(
                    name = "Maison (Paris)",
                    adminRegion = "Île-de-France, France",
                    latitude = 48.8566,
                    longitude = 2.3522,
                    locationType = LocationType.HOME.name,
                    sortOrder = 0
                ),
                SavedLocationEntity(
                    name = "Boulot (La Défense)",
                    adminRegion = "Hauts-de-Seine, France",
                    latitude = 48.8924,
                    longitude = 2.2369,
                    locationType = LocationType.WORK.name,
                    commuteMorningStartHour = 7,
                    commuteMorningEndHour = 9,
                    commuteEveningStartHour = 17,
                    commuteEveningEndHour = 19,
                    sortOrder = 1
                ),
                SavedLocationEntity(
                    name = "Forêt de Meudon",
                    adminRegion = "Hauts-de-Seine, France",
                    latitude = 48.8032,
                    longitude = 2.2155,
                    locationType = LocationType.TRAIL.name,
                    sortOrder = 2
                ),
                SavedLocationEntity(
                    name = "Vallée de Chevreuse",
                    adminRegion = "Yvelines, France",
                    latitude = 48.7073,
                    longitude = 2.0385,
                    locationType = LocationType.BIKE.name,
                    sortOrder = 3
                )
            )
            defaults.forEach { weatherDao.insertLocation(it) }
        }
    }

    suspend fun getRunningSettingsOnce(): RunningScoreSettingsEntity {
        return weatherDao.getRunningSettingsOnce() ?: RunningScoreSettingsEntity().also {
            weatherDao.saveRunningSettings(it)
        }
    }

    suspend fun saveRunningSettings(settings: RunningScoreSettingsEntity) {
        weatherDao.saveRunningSettings(settings)
    }

    suspend fun saveOrUpdateLocation(entity: SavedLocationEntity): Long {
        return if (entity.id == 0L) {
            weatherDao.insertLocation(entity)
        } else {
            weatherDao.updateLocation(entity)
            entity.id
        }
    }

    suspend fun deleteLocation(id: Long) {
        weatherDao.deleteLocationById(id)
    }

    suspend fun setForcedModelForSavedLocation(locationId: Long, model: WeatherModel?) {
        weatherDao.updateForcedModel(locationId, model?.apiId)
    }

    suspend fun searchCities(query: String): List<GeocodingResultItem> {
        return runCatching {
            openMeteoClient.searchCities(query)
        }.getOrDefault(emptyList())
    }

    /**
     * Récupère ou calcule le rapport de fiabilité sur 30 jours glissants.
     * Mis en cache dans Room et recalculé au maximum une fois par jour et par lieu.
     */
    suspend fun getOrComputeReliabilityReport(
        location: ActiveLocation,
        forceRefresh: Boolean = false
    ): LocationReliabilityReport {
        val today = LocalDate.now()
        val todayEpoch = today.toEpochDay()
        val coordKey = location.coordKey

        if (!forceRefresh) {
            val cached = weatherDao.getReliabilityCache(coordKey)
            if (cached != null && cached.calculatedEpochDay == todayEpoch) {
                val parsed = ReliabilityAndAutoModelEngine.deserializeReportFromJson(cached.reportJson)
                if (parsed != null && parsed.metrics.isNotEmpty()) {
                    return parsed.copy(locationName = location.name)
                }
            }
        }

        val computed = runCatching {
            ReliabilityAndAutoModelEngine.computeReliabilityReport(
                locationKey = coordKey,
                locationName = location.name,
                latitude = location.latitude,
                longitude = location.longitude,
                today = today,
                openMeteoClient = openMeteoClient,
                groundTruthSource = groundTruthSource
            )
        }.getOrElse {
            // Si hors-ligne, tenter de lire le dernier cache même plus ancien
            val stale = weatherDao.getReliabilityCache(coordKey)
            val parsedStale = stale?.let { ReliabilityAndAutoModelEngine.deserializeReportFromJson(it.reportJson) }
            parsedStale ?: ReliabilityAndAutoModelEngine.createFallbackReport(
                locationKey = coordKey,
                locationName = location.name,
                today = today,
                sourceName = groundTruthSource.sourceDisplayName
            )
        }

        if (computed.metrics.isNotEmpty()) {
            val serialized = ReliabilityAndAutoModelEngine.serializeReportToJson(computed)
            weatherDao.saveReliabilityCache(
                ReliabilityCacheEntity(
                    coordKey = coordKey,
                    locationName = location.name,
                    calculatedEpochDay = todayEpoch,
                    reportJson = serialized
                )
            )
        }
        return computed
    }

    /**
     * Charge le tableau de bord complet pour [location], en respectant le forçage manuel
     * ou le choix automatique du meilleur modèle (0-48h vs >48h, température vs pluie).
     */
    suspend fun loadWeatherDashboard(
        location: ActiveLocation,
        reliabilityReport: LocationReliabilityReport? = null
    ): CompleteWeatherDashboard = coroutineScope {
        val settings = getRunningSettingsOnce()
        val report = reliabilityReport ?: getOrComputeReliabilityReport(location, forceRefresh = false)
        val autoRec = report.autoRecommendation
        val manualModel = WeatherModel.fromApiId(location.forcedModelApiId)

        val modelsToFetch = if (manualModel != null) {
            if (manualModel == WeatherModel.BEST_MATCH) {
                listOf(WeatherModel.BEST_MATCH)
            } else {
                // Ajouter ECMWF_IFS en secours pour couvrir les jours 3 à 7 si un modèle régional court terme est forcé
                listOf(manualModel, WeatherModel.ECMWF_IFS).distinct()
            }
        } else {
            listOf(
                autoRec.tempModel0to48h,
                autoRec.precipModel0to48h,
                autoRec.tempModelOver48h,
                autoRec.precipModelOver48h
            ).distinct()
        }

        val cacheKey = "${location.coordKey}_${modelsToFetch.joinToString("-") { it.apiId }}"

        val forecastDeferred = async {
            runCatching {
                openMeteoClient.fetchForecastJson(
                    latitude = location.latitude,
                    longitude = location.longitude,
                    models = modelsToFetch
                )
            }.getOrNull()
        }

        val airQualityDeferred = async {
            openMeteoClient.fetchAirQualityJson(
                latitude = location.latitude,
                longitude = location.longitude
            )
        }

        val freshForecastJson = forecastDeferred.await()
        val freshAqJson = airQualityDeferred.await()

        val effectiveForecastJson: String
        val effectiveAqJson: String?

        if (freshForecastJson != null) {
            effectiveForecastJson = freshForecastJson
            effectiveAqJson = freshAqJson
            weatherDao.saveForecastCache(
                WeatherForecastCacheEntity(
                    cacheKey = cacheKey,
                    updatedAtMillis = System.currentTimeMillis(),
                    rawForecastJson = freshForecastJson,
                    rawAirQualityJson = freshAqJson
                )
            )
        } else {
            val cached = weatherDao.getForecastCache(cacheKey)
            if (cached != null) {
                effectiveForecastJson = cached.rawForecastJson
                effectiveAqJson = cached.rawAirQualityJson
            } else {
                throw IllegalStateException("Impossible de joindre Open-Meteo et aucun cache local disponible pour ${location.name}.")
            }
        }

        val airQualitySummary = openMeteoClient.parseAirQuality(effectiveAqJson)

        val dashboard = ForecastParserEngine.buildDashboardFromJson(
            forecastJsonString = effectiveForecastJson,
            airQuality = airQualitySummary,
            location = location,
            settings = settings,
            autoRecommendation = autoRec,
            manualModel = manualModel,
            nowDateTime = LocalDateTime.now()
        )

        // Si le lieu est enregistré en base, mettre à jour son résumé pour l'écran Liste des lieux
        if (location.id != null) {
            val nowHour = LocalDateTime.now().hour
            val currentPt = dashboard.hourlyToday24h.find { it.hourOfDay == nowHour }
                ?: dashboard.hourlyToday24h.firstOrNull()
            val todayDaily = dashboard.daily7Days.firstOrNull()
            val specialBadge = when (location.type) {
                LocationType.WORK -> {
                    val w = dashboard.workCommuteStatus
                    val ice = w?.morningReport?.hasBlackIceRisk == true || w?.eveningReport?.hasBlackIceRisk == true
                    val fog = w?.morningReport?.hasFogRisk == true || w?.eveningReport?.hasFogRisk == true
                    when {
                        ice -> "Alerte Verglas trajet"
                        fog -> "Brouillard trajet"
                        else -> "Trajets RAS (${location.commuteMorningStartHour}h/${location.commuteEveningStartHour}h)"
                    }
                }
                LocationType.TRAIL -> {
                    val t = dashboard.trailConditionStatus
                    if (t != null) {
                        String.format(Locale.FRANCE, "%s · %.1f mm/48h", t.mudState.labelFr, t.rainLast48hMm)
                    } else null
                }
                LocationType.BIKE -> {
                    val b = dashboard.bikeWindStatus
                    if (b != null) {
                        String.format(Locale.FRANCE, "Vent %s %.0f km/h (raf. %.0f)", b.windFromCardinal, b.windSpeedKmh, b.gustsKmh)
                    } else null
                }
                else -> {
                    val bestSlot = dashboard.top3RunningSlots.firstOrNull()
                    bestSlot?.let { "Course : top à ${it.timeLabel} (${it.overallLevel.labelFr})" }
                }
            }

            val modelBadge = manualModel?.shortBadge
                ?: "Auto (${autoRec.tempModel0to48h.shortBadge}/${autoRec.precipModel0to48h.shortBadge})"

            weatherDao.updateLocationSummaryCache(
                id = location.id,
                currentTempC = currentPt?.temperatureC,
                minTempC = todayDaily?.tempMinC,
                maxTempC = todayDaily?.tempMaxC,
                rainMm = todayDaily?.precipitationSumMm,
                windKmh = currentPt?.windGustsKmh,
                specialBadge = specialBadge,
                modelBadge = modelBadge
            )
        }

        dashboard
    }
}
