package com.example.model

enum class WeatherModel(
    val apiId: String,
    val displayName: String,
    val shortBadge: String,
    val agency: String,
    val isRegional: Boolean,
    val isGlobal: Boolean,
    val maxHorizonHours: Int,
    val maxLeadDaysForReliability: Int
) {
    BEST_MATCH(
        apiId = "best_match",
        displayName = "Best Match (Open-Meteo)",
        shortBadge = "Best Match",
        agency = "Open-Meteo",
        isRegional = true,
        isGlobal = true,
        maxHorizonHours = 168,
        maxLeadDaysForReliability = 3
    ),
    AROME(
        apiId = "meteofrance_arome_france",
        displayName = "AROME (Météo-France)",
        shortBadge = "AROME",
        agency = "Météo-France",
        isRegional = true,
        isGlobal = false,
        maxHorizonHours = 48,
        maxLeadDaysForReliability = 2
    ),
    ICON_D2(
        apiId = "icon_d2",
        displayName = "ICON-D2 (DWD)",
        shortBadge = "ICON-D2",
        agency = "DWD (2 km)",
        isRegional = true,
        isGlobal = false,
        maxHorizonHours = 48,
        maxLeadDaysForReliability = 2
    ),
    ICON_EU(
        apiId = "icon_eu",
        displayName = "ICON-EU (DWD)",
        shortBadge = "ICON-EU",
        agency = "DWD Europe (7 km)",
        isRegional = true,
        isGlobal = true,
        maxHorizonHours = 120,
        maxLeadDaysForReliability = 5
    ),
    ECMWF_IFS(
        apiId = "ecmwf_ifs025",
        displayName = "ECMWF IFS",
        shortBadge = "ECMWF IFS",
        agency = "CEPMMT (9 km)",
        isRegional = false,
        isGlobal = true,
        maxHorizonHours = 168,
        maxLeadDaysForReliability = 7
    ),
    GFS(
        apiId = "gfs_seamless",
        displayName = "GFS Seamless (NOAA)",
        shortBadge = "GFS NOAA",
        agency = "NOAA Global",
        isRegional = false,
        isGlobal = true,
        maxHorizonHours = 168,
        maxLeadDaysForReliability = 7
    );

    companion object {
        val comparableModels: List<WeatherModel> = listOf(AROME, ICON_D2, ICON_EU, ECMWF_IFS, GFS)
        val shortTermCandidates: List<WeatherModel> = listOf(AROME, ICON_D2, ICON_EU, ECMWF_IFS, GFS)
        val mediumTermGlobalCandidates: List<WeatherModel> = listOf(ECMWF_IFS, ICON_EU, GFS)

        fun fromApiId(id: String?): WeatherModel? = entries.find { it.apiId == id }
    }
}

enum class LocationType(
    val labelFr: String,
    val descriptionFr: String
) {
    HOME("Maison", "Affichage standard complet"),
    WORK("Boulot", "Heures trajet aller/retour, brouillard & risque verglas"),
    TRAIL("Spot de trail", "Cumul pluie 48h & état du terrain (sec / gras / boue)"),
    BIKE("Vélo", "Force, direction du vent (flèche) & rafales pour la boucle"),
    OTHER("Autre", "Affichage standard")
}

enum class MudState(val labelFr: String, val detailFr: String) {
    DRY("Terrain Sec", "Accroche optimale"),
    DAMP("Terrain Gras", "Humide par endroits"),
    MUDDY("Terrain Boueux", "Boue marquée, crampons conseillés")
}

enum class ScoreLevel(val labelFr: String, val severityRank: Int) {
    GREEN("Vert", 0),
    ORANGE("Orange", 1),
    RED("Rouge", 2)
}

data class Minutely15Point(
    val timeIso: String,
    val timeLabel: String,
    val precipitationMm: Double
)

data class HourlyWeatherPoint(
    val timeIso: String,
    val dateIso: String,
    val hourOfDay: Int,
    val hourLabel: String,
    val temperatureC: Double,
    val apparentTemperatureC: Double,
    val precipitationMm: Double,
    val windSpeedKmh: Double,
    // null = donnée non fournie par les modèles interrogés (jamais de valeur inventée)
    val windGustsKmh: Double?,
    val windDirectionDeg: Int?,
    val visibilityMeters: Double?,
    val relativeHumidityPercent: Int?,
    val weatherCode: Int?,
    val isDay: Boolean,
    val tempModelUsed: WeatherModel,
    val precipModelUsed: WeatherModel
)

data class DailyWeatherPoint(
    val dateIso: String,
    val dayNameShort: String,
    val dayFullLabel: String,
    val dayOfWeekIso: Int, // 1 = Lundi .. 7 = Dimanche
    val isWeekend: Boolean,
    val tempMinC: Double,
    val tempMaxC: Double,
    val precipitationSumMm: Double,
    // null = donnée non fournie par les modèles interrogés
    val windSpeedMaxKmh: Double?,
    val windGustsMaxKmh: Double?,
    val dominantWindDirectionDeg: Int?,
    val sunriseIso: String,
    val sunsetIso: String,
    val sunriseLabel: String,
    val sunsetLabel: String,
    val daylightDurationHoursLabel: String,
    val uvIndexMax: Double?,
    val weatherCode: Int?,
    val tempModelUsed: WeatherModel,
    val precipModelUsed: WeatherModel
)

data class AirQualitySummary(
    val europeanAqi: Int?,
    val aqiLabelFr: String,
    val uvIndexCurrent: Double?,
    val pm25: Double?,
    val pm10: Double?,
    val alderPollen: Double?,
    val birchPollen: Double?,
    val grassPollen: Double?,
    val mugwortPollen: Double?,
    val olivePollen: Double?,
    val ragweedPollen: Double?
) {
    val dominantPollenSummary: String
        get() {
            val pollens = listOfNotNull(
                grassPollen?.let { "Graminées" to it },
                birchPollen?.let { "Bouleau" to it },
                alderPollen?.let { "Aulne" to it },
                ragweedPollen?.let { "Ambroisie" to it },
                mugwortPollen?.let { "Armoise" to it },
                olivePollen?.let { "Olivier" to it }
            )
            if (pollens.isEmpty()) return "N/D"
            val maxEntry = pollens.maxByOrNull { it.second } ?: return "Faible"
            return when {
                maxEntry.second < 1.0 -> "Nul à très faible (0 gr/m³)"
                maxEntry.second < 20.0 -> "${maxEntry.first} modéré (${maxEntry.second.toInt()} gr/m³)"
                else -> "${maxEntry.first} élevé (${maxEntry.second.toInt()} gr/m³)"
            }
        }
}

data class CommuteSliceReport(
    val label: String,
    val windowText: String,
    val minTempC: Double?,
    val maxHumidityPercent: Int?,
    val totalPrecipMm: Double,
    val minVisibilityMeters: Double?,
    val hasFogRisk: Boolean,
    val hasBlackIceRisk: Boolean
)

data class WorkCommuteStatus(
    val morningReport: CommuteSliceReport,
    val eveningReport: CommuteSliceReport
)

data class TrailConditionStatus(
    val rainLast48hMm: Double,
    val mudState: MudState,
    val orangeThresholdMm: Double,
    val redThresholdMm: Double
)

data class BikeWindStatus(
    val windSpeedKmh: Double,
    val gustsKmh: Double?,
    val windDirectionDeg: Int,
    val windFromCardinal: String,
    val loopAdviceFr: String
)

data class HourlyRunningScore(
    val hour: Int,
    val timeLabel: String,
    val overallLevel: ScoreLevel,
    val precipLevel: ScoreLevel,
    val apparentTempLevel: ScoreLevel,
    val gustsLevel: ScoreLevel,
    val lightLevel: ScoreLevel,
    val stormLevel: ScoreLevel,
    val trailMudLevel: ScoreLevel?,
    val limitingReasons: List<String>,
    val qualityRankScore: Double,
    val temperatureC: Double,
    val apparentTempC: Double,
    val precipMm: Double,
    val gustsKmh: Double?,
    val weatherCode: Int? = null,
    val isDay: Boolean = true,
    val isRealisticSlot: Boolean
)

data class ForecastVsRealDayPoint(
    val dateIso: String,
    val shortDateLabel: String,
    val forecastTempMeanC: Double,
    val actualTempMeanC: Double,
    val forecastTempMinC: Double,
    val actualTempMinC: Double,
    val forecastTempMaxC: Double,
    val actualTempMaxC: Double,
    val forecastPrecipMm: Double,
    val actualPrecipMm: Double
)

data class ModelLeadReliability(
    val model: WeatherModel,
    val leadDays: Int, // 1..7
    val tempMaeC: Double,
    val precipDailyMaeMm: Double,
    val rainNoRainAccuracyPercent: Double, // Seuil 0,2 mm/j
    val sampleDaysCount: Int,
    val dailyComparison: List<ForecastVsRealDayPoint>
)

data class AutoModelRecommendation(
    val tempModel0to48h: WeatherModel,
    val precipModel0to48h: WeatherModel,
    val tempModelOver48h: WeatherModel,
    val precipModelOver48h: WeatherModel,
    val summaryText: String
)

data class LocationReliabilityReport(
    val locationKey: String,
    val locationName: String,
    val calculatedEpochDay: Long,
    val windowStartIso: String,
    val windowEndIso: String,
    val groundTruthSourceName: String,
    val metrics: List<ModelLeadReliability>,
    val verdictSentence: String,
    val autoRecommendation: AutoModelRecommendation
)

data class ActiveLocation(
    val id: Long?, // null si position GPS ou ville consultée sans enregistrement
    val name: String,
    val adminRegion: String,
    val latitude: Double,
    val longitude: Double,
    val type: LocationType,
    val isCurrentGps: Boolean = false,
    val isSaved: Boolean = false,
    val commuteMorningStartHour: Int = 7,
    val commuteMorningEndHour: Int = 9,
    val commuteEveningStartHour: Int = 17,
    val commuteEveningEndHour: Int = 19,
    val forcedModelApiId: String? = null // null = choix automatique
) {
    val coordKey: String
        get() = String.format(java.util.Locale.US, "%.2f_%.2f", latitude, longitude)
}

data class CompleteWeatherDashboard(
    val location: ActiveLocation,
    val fetchedAtLabel: String,
    val isAutoModelActive: Boolean,
    val manualModel: WeatherModel?,
    val autoRecommendation: AutoModelRecommendation,
    val minutely15Next3h: List<Minutely15Point>,
    val minutely15ModelUsed: WeatherModel,
    val hourlyToday24h: List<HourlyWeatherPoint>,
    val todaySunriseLabel: String,
    val todaySunsetLabel: String,
    val todayDaylightDurationLabel: String,
    val todaySunriseHourFraction: Float?,
    val todaySunsetHourFraction: Float?,
    val runningScores6to21h: List<HourlyRunningScore>,
    val top3RunningSlots: List<HourlyRunningScore>,
    val weekendDays: List<DailyWeatherPoint>,
    val daily7Days: List<DailyWeatherPoint>,
    val airQuality: AirQualitySummary,
    val workCommuteStatus: WorkCommuteStatus?,
    val trailConditionStatus: TrailConditionStatus?,
    val bikeWindStatus: BikeWindStatus?
)
