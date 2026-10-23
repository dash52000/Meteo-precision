package com.example.domain

import com.example.data.local.RunningScoreSettingsEntity
import com.example.model.ActiveLocation
import com.example.model.AirQualitySummary
import com.example.model.AutoModelRecommendation
import com.example.model.CompleteWeatherDashboard
import com.example.model.DailyWeatherPoint
import com.example.model.HourlyWeatherPoint
import com.example.model.LocationType
import com.example.model.Minutely15Point
import com.example.model.WeatherModel
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object ForecastParserEngine {

    private val frenchDayShort = mapOf(
        1 to "Lun",
        2 to "Mar",
        3 to "Mer",
        4 to "Jeu",
        5 to "Ven",
        6 to "Sam",
        7 to "Dim"
    )

    private val frenchDayFull = mapOf(
        1 to "Lundi",
        2 to "Mardi",
        3 to "Mercredi",
        4 to "Jeudi",
        5 to "Vendredi",
        6 to "Samedi",
        7 to "Dimanche"
    )

    /**
     * Transforme la réponse JSON de l'API Forecast d'Open-Meteo en [CompleteWeatherDashboard].
     * Gère à la fois le mode manuel (un seul modèle forcé) et le mode automatique hybride
     * (assemblage par variable Température vs Pluie et par horizon 0-48h vs >48h).
     */
    fun buildDashboardFromJson(
        forecastJsonString: String,
        airQuality: AirQualitySummary,
        location: ActiveLocation,
        settings: RunningScoreSettingsEntity,
        autoRecommendation: AutoModelRecommendation,
        manualModel: WeatherModel?,
        nowDateTime: LocalDateTime = LocalDateTime.now(),
        dataFetchedAtMillis: Long? = null,
        fromOfflineCache: Boolean = false
    ): CompleteWeatherDashboard {
        val root = JSONObject(forecastJsonString)
        val isAuto = manualModel == null

        // Helper pour lire une valeur dans un objet JSON (hourly, daily, minutely_15) avec suffixe de modèle.
        // Renvoie null si aucun modèle interrogé ne fournit la donnée : jamais de valeur inventée.
        fun resolveOptional(
            section: JSONObject?,
            baseVar: String,
            index: Int,
            preferredModel: WeatherModel,
            fallbackModels: List<WeatherModel>
        ): Pair<Double?, WeatherModel> {
            if (section == null) return null to preferredModel
            val candidates = listOf(preferredModel) + fallbackModels
            for (m in candidates.distinct()) {
                val suffixedArr = section.optJSONArray("${baseVar}_${m.apiId}")
                if (suffixedArr != null && index < suffixedArr.length() && !suffixedArr.isNull(index)) {
                    val v = suffixedArr.optDouble(index, Double.NaN)
                    if (!v.isNaN()) return v to m
                }
            }
            val plainArr = section.optJSONArray(baseVar)
            if (plainArr != null && index < plainArr.length() && !plainArr.isNull(index)) {
                val v = plainArr.optDouble(index, Double.NaN)
                if (!v.isNaN()) return v to preferredModel
            }
            return null to preferredModel
        }

        fun resolveString(
            section: JSONObject?,
            baseVar: String,
            index: Int,
            preferredModel: WeatherModel,
            fallbackModels: List<WeatherModel>
        ): String {
            if (section == null) return ""
            val candidates = listOf(preferredModel) + fallbackModels
            for (m in candidates.distinct()) {
                val suffixedArr = section.optJSONArray("${baseVar}_${m.apiId}")
                if (suffixedArr != null && index < suffixedArr.length() && !suffixedArr.isNull(index)) {
                    val s = suffixedArr.optString(index, "")
                    if (s.isNotBlank()) return s
                }
            }
            val plainArr = section.optJSONArray(baseVar)
            if (plainArr != null && index < plainArr.length() && !plainArr.isNull(index)) {
                return plainArr.optString(index, "")
            }
            return ""
        }

        val fallbackChain = listOf(
            WeatherModel.ECMWF_IFS,
            WeatherModel.ICON_EU,
            WeatherModel.GFS,
            WeatherModel.ICON_D2,
            WeatherModel.AROME,
            WeatherModel.BEST_MATCH
        )

        // 1. Précipitations au quart d'heure sur les 3 prochaines heures (12 créneaux de 15 min)
        val minutelySection = root.optJSONObject("minutely_15")
        val minutelyTimes = minutelySection?.optJSONArray("time") ?: JSONArray()
        val preferredShortPrecipModel = manualModel ?: autoRecommendation.precipModel0to48h
        val minutelyPoints = mutableListOf<Minutely15Point>()
        var minutelyModelActual = preferredShortPrecipModel

        val nowIsoPrefix = nowDateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm"))
        // -1 = l'instant présent n'est pas couvert (données trop anciennes) : aucun quart d'heure affiché
        var startMinutelyIdx = -1
        for (i in 0 until minutelyTimes.length()) {
            val t = minutelyTimes.optString(i, "")
            if (t >= nowIsoPrefix) {
                startMinutelyIdx = (i - 1).coerceAtLeast(0)
                break
            }
        }

        val minutelyRange = if (startMinutelyIdx < 0) {
            IntRange.EMPTY
        } else {
            startMinutelyIdx until (startMinutelyIdx + 12).coerceAtMost(minutelyTimes.length())
        }
        for (i in minutelyRange) {
            val tIso = minutelyTimes.optString(i, "")
            val label = if (tIso.length >= 16) tIso.substring(11, 16) else tIso
            val (mmOpt, usedM) = resolveOptional(
                minutelySection,
                "precipitation",
                i,
                preferredShortPrecipModel,
                fallbackChain
            )
            val mm = mmOpt ?: continue
            if (minutelyPoints.isEmpty()) minutelyModelActual = usedM
            minutelyPoints.add(
                Minutely15Point(
                    timeIso = tIso,
                    timeLabel = label,
                    precipitationMm = mm.coerceAtLeast(0.0)
                )
            )
        }

        // 2. Données horaires (past_days=2 + forecast_days=8)
        val hourlySection = root.optJSONObject("hourly")
        val hourlyTimes = hourlySection?.optJSONArray("time") ?: JSONArray()
        val todayDateIso = nowDateTime.toLocalDate().toString()

        // Trouver l'index correspondant à aujourd'hui 00:00
        var todayStartHourIdx = -1
        for (i in 0 until hourlyTimes.length()) {
            val tIso = hourlyTimes.optString(i, "")
            if (tIso.startsWith(todayDateIso)) {
                todayStartHourIdx = i
                break
            }
        }
        if (todayStartHourIdx < 0) {
            // Données (souvent un vieux cache hors-ligne) qui ne couvrent plus aujourd'hui :
            // on refuse de les afficher comme si elles étaient du jour.
            throw IllegalStateException(
                "Les prévisions disponibles hors-ligne sont trop anciennes et ne couvrent plus aujourd'hui. Connecte-toi pour actualiser."
            )
        }

        val currentHourIdx = (todayStartHourIdx + nowDateTime.hour).coerceIn(0, (hourlyTimes.length() - 1).coerceAtLeast(0))

        // Calcul du cumul de pluie des 48 dernières heures (pour le Spot de trail)
        var past48hRainSum = 0.0
        val start48hIdx = (currentHourIdx - 48).coerceAtLeast(0)
        for (i in start48hIdx until currentHourIdx) {
            val (pMm, _) = resolveOptional(
                hourlySection,
                "precipitation",
                i,
                preferredShortPrecipModel,
                fallbackChain
            )
            past48hRainSum += (pMm ?: 0.0).coerceAtLeast(0.0)
        }

        // Extraire les 24 heures de la journée actuelle
        val todayHourlyPoints = mutableListOf<HourlyWeatherPoint>()
        for (offset in 0 until 24) {
            val idx = todayStartHourIdx + offset
            if (idx >= hourlyTimes.length()) break
            val tIso = hourlyTimes.optString(idx, "")
            val datePart = if (tIso.length >= 10) tIso.substring(0, 10) else todayDateIso
            val hourVal = if (tIso.length >= 13) tIso.substring(11, 13).toIntOrNull() ?: offset else offset

            val hoursAheadFromNow = (idx - currentHourIdx).coerceAtLeast(0)
            val targetTempModel = manualModel ?: if (hoursAheadFromNow <= 48) {
                autoRecommendation.tempModel0to48h
            } else {
                autoRecommendation.tempModelOver48h
            }
            val targetPrecipModel = manualModel ?: if (hoursAheadFromNow <= 48) {
                autoRecommendation.precipModel0to48h
            } else {
                autoRecommendation.precipModelOver48h
            }

            val (tempOpt, actualTempM) = resolveOptional(hourlySection, "temperature_2m", idx, targetTempModel, fallbackChain)
            val (precipOpt, actualPrecipM) = resolveOptional(hourlySection, "precipitation", idx, targetPrecipModel, fallbackChain)
            val windOpt = resolveOptional(hourlySection, "wind_speed_10m", idx, actualTempM, fallbackChain).first
            // Heure sans température, pluie ou vent dans aucun modèle : on ne l'affiche pas plutôt que d'inventer
            if (tempOpt == null || precipOpt == null || windOpt == null) continue
            val tempC: Double = tempOpt
            val precipMm: Double = precipOpt
            val windKmh: Double = windOpt
            // Ressenti absent : on affiche la température réelle (donnée du modèle, pas une valeur inventée)
            val appTempC = resolveOptional(hourlySection, "apparent_temperature", idx, actualTempM, fallbackChain).first ?: tempC
            val gustsKmh = resolveOptional(hourlySection, "wind_gusts_10m", idx, actualTempM, fallbackChain).first
            val windDir = resolveOptional(hourlySection, "wind_direction_10m", idx, actualTempM, fallbackChain).first
            val visM = resolveOptional(hourlySection, "visibility", idx, actualTempM, fallbackChain).first
            val rh = resolveOptional(hourlySection, "relative_humidity_2m", idx, actualTempM, fallbackChain).first
            val wCode = resolveOptional(hourlySection, "weather_code", idx, actualPrecipM, fallbackChain).first
            // Jour/nuit : donnée astronomique toujours fournie par Open-Meteo ; repli sur l'heure uniquement pour l'icône
            val isDayVal = resolveOptional(hourlySection, "is_day", idx, actualTempM, fallbackChain).first
                ?: if (hourVal in 7..20) 1.0 else 0.0

            todayHourlyPoints.add(
                HourlyWeatherPoint(
                    timeIso = tIso,
                    dateIso = datePart,
                    hourOfDay = hourVal,
                    hourLabel = "${hourVal}h",
                    temperatureC = tempC,
                    apparentTemperatureC = appTempC,
                    precipitationMm = precipMm.coerceAtLeast(0.0),
                    windSpeedKmh = windKmh.coerceAtLeast(0.0),
                    windGustsKmh = gustsKmh?.coerceAtLeast(windKmh),
                    windDirectionDeg = windDir?.toInt(),
                    visibilityMeters = visM,
                    relativeHumidityPercent = rh?.toInt()?.coerceIn(0, 100),
                    weatherCode = wCode?.toInt(),
                    isDay = isDayVal >= 0.5,
                    tempModelUsed = actualTempM,
                    precipModelUsed = actualPrecipM
                )
            )
        }

        // Si minutely_15 est vide (certains modèles globaux hors Europe), synthétiser les 12 quarts d'heure à partir des 3 prochaines heures
        if (minutelyPoints.isEmpty() && todayHourlyPoints.isNotEmpty()) {
            val baseHour = nowDateTime.hour
            for (q in 0 until 12) {
                val totalMin = baseHour * 60 + (nowDateTime.minute / 15) * 15 + q * 15
                val h = (totalMin / 60) % 24
                val m = totalMin % 60
                val matchingHour = todayHourlyPoints.find { it.hourOfDay == h } ?: continue
                minutelyPoints.add(
                    Minutely15Point(
                        timeIso = String.format(Locale.US, "%sT%02d:%02d", todayDateIso, h, m),
                        timeLabel = String.format(Locale.US, "%02d:%02d", h, m),
                        precipitationMm = (matchingHour.precipitationMm / 4.0).coerceAtLeast(0.0)
                    )
                )
            }
        }

        // 3. Données journalières (7 jours à partir d'aujourd'hui)
        val dailySection = root.optJSONObject("daily")
        val dailyTimes = dailySection?.optJSONArray("time") ?: JSONArray()
        var todayDailyIdx = -1
        for (i in 0 until dailyTimes.length()) {
            if (dailyTimes.optString(i, "") == todayDateIso) {
                todayDailyIdx = i
                break
            }
        }

        val dailyPoints = mutableListOf<DailyWeatherPoint>()
        for (dayOffset in 0 until (if (todayDailyIdx < 0) 0 else 8)) {
            val idx = todayDailyIdx + dayOffset
            if (idx >= dailyTimes.length()) break
            val dIso = dailyTimes.optString(idx, "")
            val parsedDate = runCatching { LocalDate.parse(dIso) }.getOrDefault(nowDateTime.toLocalDate().plusDays(dayOffset.toLong()))
            val dow = parsedDate.dayOfWeek.value // 1..7
            val isWeekend = dow == 6 || dow == 7

            val targetTempModel = manualModel ?: if (dayOffset <= 1) {
                autoRecommendation.tempModel0to48h
            } else {
                autoRecommendation.tempModelOver48h
            }
            val targetPrecipModel = manualModel ?: if (dayOffset <= 1) {
                autoRecommendation.precipModel0to48h
            } else {
                autoRecommendation.precipModelOver48h
            }

            val (tMinOpt, actualTempM) = resolveOptional(dailySection, "temperature_2m_min", idx, targetTempModel, fallbackChain)
            val tMaxOpt = resolveOptional(dailySection, "temperature_2m_max", idx, actualTempM, fallbackChain).first
            val (pSumOpt, actualPrecipM) = resolveOptional(dailySection, "precipitation_sum", idx, targetPrecipModel, fallbackChain)
            // Jour sans min/max ou sans cumul de pluie dans aucun modèle : on ne l'affiche pas
            if (tMinOpt == null || tMaxOpt == null || pSumOpt == null) continue
            val tMin: Double = tMinOpt
            val tMax: Double = tMaxOpt
            val pSum: Double = pSumOpt
            val wMax = resolveOptional(dailySection, "wind_speed_10m_max", idx, actualTempM, fallbackChain).first
            val gMax = resolveOptional(dailySection, "wind_gusts_10m_max", idx, actualTempM, fallbackChain).first
            val wDir = resolveOptional(dailySection, "wind_direction_10m_dominant", idx, actualTempM, fallbackChain).first
            val uvMax = resolveOptional(dailySection, "uv_index_max", idx, actualTempM, fallbackChain).first
            val daylightSec = resolveOptional(dailySection, "daylight_duration", idx, actualTempM, fallbackChain).first
            val wCode = resolveOptional(dailySection, "weather_code", idx, actualPrecipM, fallbackChain).first

            val sunriseIso = resolveString(dailySection, "sunrise", idx, actualTempM, fallbackChain)
            val sunsetIso = resolveString(dailySection, "sunset", idx, actualTempM, fallbackChain)
            val sunriseLabel = if (sunriseIso.length >= 16) sunriseIso.substring(11, 16) else "—"
            val sunsetLabel = if (sunsetIso.length >= 16) sunsetIso.substring(11, 16) else "—"

            val daylightLabel = if (daylightSec != null) {
                val totalMinutes = (daylightSec / 60.0).toInt()
                String.format(Locale.FRANCE, "%dh%02d", totalMinutes / 60, totalMinutes % 60)
            } else {
                "—"
            }

            val shortName = if (dayOffset == 0) "Auj." else frenchDayShort[dow] ?: "Jour"
            val fullLabel = if (dayOffset == 0) {
                "Aujourd'hui (${parsedDate.dayOfMonth}/${parsedDate.monthValue})"
            } else {
                "${frenchDayFull[dow]} ${parsedDate.dayOfMonth}/${parsedDate.monthValue}"
            }

            dailyPoints.add(
                DailyWeatherPoint(
                    dateIso = dIso,
                    dayNameShort = shortName,
                    dayFullLabel = fullLabel,
                    dayOfWeekIso = dow,
                    isWeekend = isWeekend,
                    tempMinC = tMin,
                    tempMaxC = tMax,
                    precipitationSumMm = pSum.coerceAtLeast(0.0),
                    windSpeedMaxKmh = wMax?.coerceAtLeast(0.0),
                    windGustsMaxKmh = if (gMax != null && wMax != null) gMax.coerceAtLeast(wMax) else gMax,
                    dominantWindDirectionDeg = wDir?.toInt(),
                    sunriseIso = sunriseIso,
                    sunsetIso = sunsetIso,
                    sunriseLabel = sunriseLabel,
                    sunsetLabel = sunsetLabel,
                    daylightDurationHoursLabel = daylightLabel,
                    uvIndexMax = uvMax?.coerceAtLeast(0.0),
                    weatherCode = wCode?.toInt(),
                    tempModelUsed = actualTempM,
                    precipModelUsed = actualPrecipM
                )
            )
        }

        val todayDaily = dailyPoints.firstOrNull { it.dateIso == todayDateIso }
        val sunriseFraction = todayDaily?.sunriseLabel?.let { parseTimeFractionOrNull(it) }
        val sunsetFraction = todayDaily?.sunsetLabel?.let { parseTimeFractionOrNull(it) }
        val isTodayWeekend = nowDateTime.dayOfWeek.value in setOf(6, 7)

        // Score course 6h - 21h
        val runningScores = RunningScoreEngine.computeHourlyScores(
            todayHours = todayHourlyPoints,
            settings = settings,
            locationType = location.type,
            past48hRainMm = past48hRainSum,
            isWeekendDay = isTodayWeekend
        )
        val top3Slots = RunningScoreEngine.selectTop3Slots(runningScores)

        // Indicateurs métiers selon le type de lieu
        val workStatus = if (location.type == LocationType.WORK) {
            RunningScoreEngine.computeWorkCommuteStatus(todayHourlyPoints, location)
        } else {
            null
        }
        val trailStatus = if (location.type == LocationType.TRAIL) {
            RunningScoreEngine.computeTrailConditionStatus(past48hRainSum, settings)
        } else {
            null
        }
        val currentHourPoint = todayHourlyPoints.find { it.hourOfDay == nowDateTime.hour }
        val bikeStatus = if (location.type == LocationType.BIKE && currentHourPoint != null) {
            RunningScoreEngine.computeBikeWindStatus(currentHourPoint)
        } else {
            null
        }

        val weekendDays = dailyPoints.filter { it.isWeekend }.take(2)
        val sevenDays = dailyPoints.take(7)

        return CompleteWeatherDashboard(
            location = location,
            fetchedAtLabel = buildFetchedAtLabel(nowDateTime, dataFetchedAtMillis, fromOfflineCache),
            isAutoModelActive = isAuto,
            manualModel = manualModel,
            autoRecommendation = autoRecommendation,
            minutely15Next3h = minutelyPoints,
            minutely15ModelUsed = minutelyModelActual,
            hourlyToday24h = todayHourlyPoints,
            todaySunriseLabel = todayDaily?.sunriseLabel ?: "—",
            todaySunsetLabel = todayDaily?.sunsetLabel ?: "—",
            todayDaylightDurationLabel = todayDaily?.daylightDurationHoursLabel ?: "—",
            todaySunriseHourFraction = sunriseFraction,
            todaySunsetHourFraction = sunsetFraction,
            runningScores6to21h = runningScores,
            top3RunningSlots = top3Slots,
            weekendDays = weekendDays,
            daily7Days = sevenDays,
            airQuality = airQuality,
            workCommuteStatus = workStatus,
            trailConditionStatus = trailStatus,
            bikeWindStatus = bikeStatus
        )
    }

    private fun parseTimeFractionOrNull(hhMm: String): Float? {
        return runCatching {
            val t = LocalTime.parse(hhMm)
            t.hour + (t.minute / 60f)
        }.getOrNull()
    }

    /**
     * Heure réelle du téléchargement des prévisions (et non l'heure d'affichage),
     * avec la date si ce n'est pas aujourd'hui et la mention « hors-ligne » pour un cache.
     */
    internal fun buildFetchedAtLabel(
        nowDateTime: LocalDateTime,
        dataFetchedAtMillis: Long?,
        fromOfflineCache: Boolean
    ): String {
        val fetchedAt = dataFetchedAtMillis
            ?.let { LocalDateTime.ofInstant(Instant.ofEpochMilli(it), ZoneId.systemDefault()) }
            ?: nowDateTime
        val pattern = if (fetchedAt.toLocalDate() == nowDateTime.toLocalDate()) "HH:mm" else "dd/MM HH:mm"
        val base = fetchedAt.format(DateTimeFormatter.ofPattern(pattern))
        return if (fromOfflineCache) "$base · hors-ligne" else base
    }
}
