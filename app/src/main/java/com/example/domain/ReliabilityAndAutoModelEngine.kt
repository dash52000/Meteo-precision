package com.example.domain

import com.example.data.remote.GroundTruthDailyObservation
import com.example.data.remote.GroundTruthWeatherSource
import com.example.data.remote.OpenMeteoClient
import com.example.data.remote.PreviousRunDailyForecast
import com.example.model.AutoModelRecommendation
import com.example.model.ForecastVsRealDayPoint
import com.example.model.LocationReliabilityReport
import com.example.model.ModelLeadReliability
import com.example.model.WeatherModel
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.util.Locale
import kotlin.math.abs

object ReliabilityAndAutoModelEngine {

    private const val RAIN_DETECTION_THRESHOLD_MM = 0.2

    /** Version de la méthode de calcul : un rapport en cache d'une autre version est recalculé. */
    private const val METHOD_VERSION = 3

    /**
     * Calcule la fiabilité des prévisions sur une fenêtre glissante de 30 jours en comparant
     * Previous Runs API aux observations réelles (GroundTruthWeatherSource).
     * Arrête automatiquement la fenêtre au dernier jour où la réanalyse existe.
     */
    suspend fun computeReliabilityReport(
        locationKey: String,
        locationName: String,
        latitude: Double,
        longitude: Double,
        today: LocalDate,
        openMeteoClient: OpenMeteoClient,
        groundTruthSource: GroundTruthWeatherSource
    ): LocationReliabilityReport = coroutineScope {
        // La réanalyse ERA5/IFS a généralement 2 à 5 jours de délai :
        // on interroge les 36 derniers jours puis on conserve les 30 derniers jours réellement disponibles.
        val queryEnd = today.minusDays(2)
        val queryStart = queryEnd.minusDays(35)

        val groundTruth = groundTruthSource.fetchDailyObservationsWithSource(
            latitude = latitude,
            longitude = longitude,
            startDate = queryStart,
            endDate = queryEnd
        )
        val actualAll = groundTruth.observations

        val sortedValidDates = actualAll.keys.sorted().takeLast(30)
        if (sortedValidDates.isEmpty()) {
            return@coroutineScope createFallbackReport(
                locationKey = locationKey,
                locationName = locationName,
                today = today,
                sourceName = groundTruth.sourceDisplayName
            )
        }

        val windowStart = LocalDate.parse(sortedValidDates.first())
        val windowEnd = LocalDate.parse(sortedValidDates.last())
        val actualWindow = sortedValidDates.associateWith { actualAll.getValue(it) }

        // Interroger Previous Runs API en parallèle pour les 5 modèles comparés
        val modelsToCompare = WeatherModel.comparableModels
        val previousRunsPerModel = modelsToCompare.map { model ->
            async {
                val runs = runCatching {
                    openMeteoClient.fetchPreviousRunsForModel(
                        latitude = latitude,
                        longitude = longitude,
                        model = model,
                        startDate = windowStart,
                        endDate = windowEnd
                    )
                }.getOrDefault(emptyMap())
                model to runs
            }
        }.awaitAll()

        val allMetrics = mutableListOf<ModelLeadReliability>()
        for ((model, runsByLead) in previousRunsPerModel) {
            for ((leadDay, forecastByDate) in runsByLead) {
                val metric = evaluateModelAtLead(
                    model = model,
                    leadDays = leadDay,
                    actualByDate = actualWindow,
                    forecastByDate = forecastByDate
                )
                if (metric != null) {
                    allMetrics.add(metric)
                }
            }
        }

        val autoRec = selectBestModelsFromMetrics(allMetrics)
        val verdict = buildVerdictSentence(locationName, allMetrics, autoRec)

        LocationReliabilityReport(
            locationKey = locationKey,
            locationName = locationName,
            calculatedEpochDay = today.toEpochDay(),
            windowStartIso = windowStart.toString(),
            windowEndIso = windowEnd.toString(),
            groundTruthSourceName = groundTruth.sourceDisplayName,
            metrics = allMetrics,
            verdictSentence = verdict,
            autoRecommendation = autoRec
        )
    }

    internal fun evaluateModelAtLead(
        model: WeatherModel,
        leadDays: Int,
        actualByDate: Map<String, GroundTruthDailyObservation>,
        forecastByDate: Map<String, PreviousRunDailyForecast>
    ): ModelLeadReliability? {
        val points = mutableListOf<ForecastVsRealDayPoint>()
        var tempAbsErrorSum = 0.0
        var precipAbsErrorSum = 0.0
        var rainBinaryCorrectCount = 0

        for ((dateIso, actual) in actualByDate) {
            val forecast = forecastByDate[dateIso] ?: continue
            val shortLabel = if (dateIso.length >= 10) {
                "${dateIso.substring(8, 10)}/${dateIso.substring(5, 7)}"
            } else {
                dateIso
            }

            // Erreur absolue moyenne sur la température journalière (moyenne des erreurs sur Tmean, Tmin, Tmax)
            val dayTempErr = (
                abs(forecast.tempMeanC - actual.tempMeanC) +
                    abs(forecast.tempMaxC - actual.tempMaxC) +
                    abs(forecast.tempMinC - actual.tempMinC)
                ) / 3.0
            tempAbsErrorSum += dayTempErr

            // Erreur sur le cumul journalier de pluie
            val dayPrecipErr = abs(forecast.precipSumMm - actual.precipitationSumMm)
            precipAbsErrorSum += dayPrecipErr

            // Pluie / pas de pluie bien prévu (seuil 0,2 mm/jour)
            val forecastRain = forecast.precipSumMm >= RAIN_DETECTION_THRESHOLD_MM
            val actualRain = actual.precipitationSumMm >= RAIN_DETECTION_THRESHOLD_MM
            if (forecastRain == actualRain) {
                rainBinaryCorrectCount++
            }

            points.add(
                ForecastVsRealDayPoint(
                    dateIso = dateIso,
                    shortDateLabel = shortLabel,
                    forecastTempMeanC = forecast.tempMeanC,
                    actualTempMeanC = actual.tempMeanC,
                    forecastTempMinC = forecast.tempMinC,
                    actualTempMinC = actual.tempMinC,
                    forecastTempMaxC = forecast.tempMaxC,
                    actualTempMaxC = actual.tempMaxC,
                    forecastPrecipMm = forecast.precipSumMm,
                    actualPrecipMm = actual.precipitationSumMm
                )
            )
        }

        // Exiger au moins 5 jours communs pour que la métrique soit représentative
        if (points.size < 5) return null
        val n = points.size.toDouble()
        return ModelLeadReliability(
            model = model,
            leadDays = leadDays,
            tempMaeC = tempAbsErrorSum / n,
            precipDailyMaeMm = precipAbsErrorSum / n,
            rainNoRainAccuracyPercent = (rainBinaryCorrectCount / n) * 100.0,
            sampleDaysCount = points.size,
            dailyComparison = points
        )
    }

    /**
     * Choix automatique du meilleur modèle à partir des scores de fiabilité :
     * - séparément pour la température et pour les précipitations,
     * - séparément pour 0–48 h (modèles régionaux inclus, échéances J+1 et J+2)
     *   et au-delà de 48 h (modèles globaux, échéances J+3 à J+5).
     */
    fun selectBestModelsFromMetrics(metrics: List<ModelLeadReliability>): AutoModelRecommendation {
        val shortTermPool = metrics.filter { it.leadDays in 1..2 }
        val mediumTermPool = metrics.filter { it.leadDays in 3..5 && it.model.isGlobal }

        // 0-48h Température : minimiser tempMaeC
        val bestTempShort = shortTermPool
            .groupBy { it.model }
            .mapValues { (_, list) -> list.map { it.tempMaeC }.average() }
            .minByOrNull { it.value }?.key
            ?: WeatherModel.AROME

        // 0-48h Précipitations : score combinant l'erreur de cumul et le taux de détection pluie/sec
        val bestPrecipShort = shortTermPool
            .groupBy { it.model }
            .mapValues { (_, list) ->
                val mae = list.map { it.precipDailyMaeMm }.average()
                val acc = list.map { it.rainNoRainAccuracyPercent }.average()
                mae - (acc * 0.03) // Plus petit est meilleur
            }
            .minByOrNull { it.value }?.key
            ?: WeatherModel.ICON_D2

        // > 48h Température (modèles globaux)
        val bestTempMedium = mediumTermPool
            .groupBy { it.model }
            .mapValues { (_, list) -> list.map { it.tempMaeC }.average() }
            .minByOrNull { it.value }?.key
            ?: WeatherModel.ECMWF_IFS

        // > 48h Précipitations (modèles globaux)
        val bestPrecipMedium = mediumTermPool
            .groupBy { it.model }
            .mapValues { (_, list) ->
                val mae = list.map { it.precipDailyMaeMm }.average()
                val acc = list.map { it.rainNoRainAccuracyPercent }.average()
                mae - (acc * 0.03)
            }
            .minByOrNull { it.value }?.key
            ?: WeatherModel.ECMWF_IFS

        val summary = "0-48h : T° ${bestTempShort.shortBadge} · Pluie ${bestPrecipShort.shortBadge} | >48h : T° ${bestTempMedium.shortBadge} · Pluie ${bestPrecipMedium.shortBadge}"
        return AutoModelRecommendation(
            tempModel0to48h = bestTempShort,
            precipModel0to48h = bestPrecipShort,
            tempModelOver48h = bestTempMedium,
            precipModelOver48h = bestPrecipMedium,
            summaryText = summary
        )
    }

    /**
     * Phrase de verdict en français, cohérente avec le choix automatique : elle nomme les modèles
     * réellement retenus pour 0–48 h et donne leurs erreurs mesurées à J+1.
     * Exemple : « À Saulnes, le mode auto retient ICON-D2 pour la température (erreur moy. 0,9 °C à J+1)
     * et ECMWF IFS pour la pluie (97 % pluie/sec juste, ±0,8 mm/j à J+1). »
     */
    fun buildVerdictSentence(
        locationName: String,
        metrics: List<ModelLeadReliability>,
        autoRecommendation: AutoModelRecommendation
    ): String {
        if (metrics.isEmpty()) {
            return "À $locationName, les données historiques en cours de consolidation utilisent ECMWF IFS et AROME par défaut."
        }
        val tempModel = autoRecommendation.tempModel0to48h
        val rainModel = autoRecommendation.precipModel0to48h
        val tempJ1 = metrics.firstOrNull { it.model == tempModel && it.leadDays == 1 }
        val rainJ1 = metrics.firstOrNull { it.model == rainModel && it.leadDays == 1 }

        val tempPart = if (tempJ1 != null) {
            String.format(Locale.FRANCE, "%s pour la température (erreur moy. %.1f °C à J+1)", tempModel.shortBadge, tempJ1.tempMaeC)
        } else {
            "${tempModel.shortBadge} pour la température"
        }
        val rainPart = if (rainJ1 != null) {
            String.format(
                Locale.FRANCE,
                "%s pour la pluie (%.0f %% pluie/sec juste, ±%.1f mm/j à J+1)",
                rainModel.shortBadge,
                rainJ1.rainNoRainAccuracyPercent,
                rainJ1.precipDailyMaeMm
            )
        } else {
            "${rainModel.shortBadge} pour la pluie"
        }
        return "À $locationName, le mode auto retient $tempPart et $rainPart."
    }

    fun createFallbackReport(
        locationKey: String,
        locationName: String,
        today: LocalDate,
        sourceName: String
    ): LocationReliabilityReport {
        val defaultRec = AutoModelRecommendation(
            tempModel0to48h = WeatherModel.ICON_D2,
            precipModel0to48h = WeatherModel.AROME,
            tempModelOver48h = WeatherModel.ECMWF_IFS,
            precipModelOver48h = WeatherModel.ECMWF_IFS,
            summaryText = "0-48h : T° ICON-D2 · Pluie AROME | >48h : ECMWF IFS"
        )
        return LocationReliabilityReport(
            locationKey = locationKey,
            locationName = locationName,
            calculatedEpochDay = today.toEpochDay(),
            windowStartIso = today.minusDays(32).toString(),
            windowEndIso = today.minusDays(2).toString(),
            groundTruthSourceName = sourceName,
            metrics = emptyList(),
            verdictSentence = "À $locationName, sélection par défaut active (ICON-D2 / AROME à court terme, ECMWF IFS au-delà de 48h).",
            autoRecommendation = defaultRec
        )
    }

    fun serializeReportToJson(report: LocationReliabilityReport): String {
        val root = JSONObject()
        root.put("methodVersion", METHOD_VERSION)
        root.put("locationKey", report.locationKey)
        root.put("locationName", report.locationName)
        root.put("calculatedEpochDay", report.calculatedEpochDay)
        root.put("windowStartIso", report.windowStartIso)
        root.put("windowEndIso", report.windowEndIso)
        root.put("groundTruthSourceName", report.groundTruthSourceName)
        root.put("verdictSentence", report.verdictSentence)

        val autoObj = JSONObject().apply {
            put("temp0_48", report.autoRecommendation.tempModel0to48h.apiId)
            put("precip0_48", report.autoRecommendation.precipModel0to48h.apiId)
            put("tempOver48", report.autoRecommendation.tempModelOver48h.apiId)
            put("precipOver48", report.autoRecommendation.precipModelOver48h.apiId)
            put("summaryText", report.autoRecommendation.summaryText)
        }
        root.put("autoRecommendation", autoObj)

        val metricsArr = JSONArray()
        for (m in report.metrics) {
            val mObj = JSONObject()
            mObj.put("modelApiId", m.model.apiId)
            mObj.put("leadDays", m.leadDays)
            mObj.put("tempMaeC", m.tempMaeC)
            mObj.put("precipDailyMaeMm", m.precipDailyMaeMm)
            mObj.put("rainNoRainAccuracyPercent", m.rainNoRainAccuracyPercent)
            mObj.put("sampleDaysCount", m.sampleDaysCount)

            val daysArr = JSONArray()
            for (d in m.dailyComparison) {
                val dObj = JSONObject()
                dObj.put("dateIso", d.dateIso)
                dObj.put("shortDateLabel", d.shortDateLabel)
                dObj.put("fTempMean", d.forecastTempMeanC)
                dObj.put("aTempMean", d.actualTempMeanC)
                dObj.put("fTempMin", d.forecastTempMinC)
                dObj.put("aTempMin", d.actualTempMinC)
                dObj.put("fTempMax", d.forecastTempMaxC)
                dObj.put("aTempMax", d.actualTempMaxC)
                dObj.put("fPrecip", d.forecastPrecipMm)
                dObj.put("aPrecip", d.actualPrecipMm)
                daysArr.put(dObj)
            }
            mObj.put("dailyComparison", daysArr)
            metricsArr.put(mObj)
        }
        root.put("metrics", metricsArr)
        return root.toString()
    }

    fun deserializeReportFromJson(jsonString: String): LocationReliabilityReport? {
        return runCatching {
            val root = JSONObject(jsonString)
            // Rapport calculé avec une ancienne méthode (ex. réanalyse seule) : on le recalcule
            if (root.optInt("methodVersion", 1) != METHOD_VERSION) return null
            val autoObj = root.getJSONObject("autoRecommendation")
            val autoRec = AutoModelRecommendation(
                tempModel0to48h = WeatherModel.fromApiId(autoObj.optString("temp0_48")) ?: WeatherModel.ICON_D2,
                precipModel0to48h = WeatherModel.fromApiId(autoObj.optString("precip0_48")) ?: WeatherModel.AROME,
                tempModelOver48h = WeatherModel.fromApiId(autoObj.optString("tempOver48")) ?: WeatherModel.ECMWF_IFS,
                precipModelOver48h = WeatherModel.fromApiId(autoObj.optString("precipOver48")) ?: WeatherModel.ECMWF_IFS,
                summaryText = autoObj.optString("summaryText", "")
            )

            val metricsArr = root.optJSONArray("metrics") ?: JSONArray()
            val metricsList = ArrayList<ModelLeadReliability>(metricsArr.length())
            for (i in 0 until metricsArr.length()) {
                val mObj = metricsArr.getJSONObject(i)
                val model = WeatherModel.fromApiId(mObj.optString("modelApiId")) ?: continue
                val daysArr = mObj.optJSONArray("dailyComparison") ?: JSONArray()
                val daysList = ArrayList<ForecastVsRealDayPoint>(daysArr.length())
                for (j in 0 until daysArr.length()) {
                    val dObj = daysArr.getJSONObject(j)
                    daysList.add(
                        ForecastVsRealDayPoint(
                            dateIso = dObj.optString("dateIso"),
                            shortDateLabel = dObj.optString("shortDateLabel"),
                            forecastTempMeanC = dObj.optDouble("fTempMean", 0.0),
                            actualTempMeanC = dObj.optDouble("aTempMean", 0.0),
                            forecastTempMinC = dObj.optDouble("fTempMin", 0.0),
                            actualTempMinC = dObj.optDouble("aTempMin", 0.0),
                            forecastTempMaxC = dObj.optDouble("fTempMax", 0.0),
                            actualTempMaxC = dObj.optDouble("aTempMax", 0.0),
                            forecastPrecipMm = dObj.optDouble("fPrecip", 0.0),
                            actualPrecipMm = dObj.optDouble("aPrecip", 0.0)
                        )
                    )
                }
                metricsList.add(
                    ModelLeadReliability(
                        model = model,
                        leadDays = mObj.optInt("leadDays", 1),
                        tempMaeC = mObj.optDouble("tempMaeC", 0.0),
                        precipDailyMaeMm = mObj.optDouble("precipDailyMaeMm", 0.0),
                        rainNoRainAccuracyPercent = mObj.optDouble("rainNoRainAccuracyPercent", 0.0),
                        sampleDaysCount = mObj.optInt("sampleDaysCount", daysList.size),
                        dailyComparison = daysList
                    )
                )
            }

            LocationReliabilityReport(
                locationKey = root.optString("locationKey"),
                locationName = root.optString("locationName"),
                calculatedEpochDay = root.optLong("calculatedEpochDay"),
                windowStartIso = root.optString("windowStartIso"),
                windowEndIso = root.optString("windowEndIso"),
                groundTruthSourceName = root.optString("groundTruthSourceName"),
                metrics = metricsList,
                verdictSentence = root.optString("verdictSentence"),
                autoRecommendation = autoRec
            )
        }.getOrNull()
    }
}
