package com.example.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.time.LocalDate

/**
 * Donnée journalière consolidée représentant le « réel » observé pour une date donnée.
 */
data class GroundTruthDailyObservation(
    val dateIso: String,
    val tempMeanC: Double,
    val tempMinC: Double,
    val tempMaxC: Double,
    val precipitationSumMm: Double
)

/**
 * Module remplaçable pour la source du « réel » servant de référence aux calculs de fiabilité.
 * Permet de brancher ultérieurement des observations de stations météo physiques (Météo-France,
 * Infoclimat, Netatmo, etc.) sans modifier le moteur de fiabilité ni l'UI.
 */
interface GroundTruthWeatherSource {
    val sourceDisplayName: String

    /**
     * Récupère les observations journalières réelles entre [startDate] et [endDate] inclus.
     * Si la source a quelques jours de retard (ex. réanalyse ERA5/IFS), seules les journées
     * réellement disponibles (non nulles) sont retournées afin d'arrêter proprement la fenêtre glissante.
     */
    suspend fun fetchDailyObservations(
        latitude: Double,
        longitude: Double,
        startDate: LocalDate,
        endDate: LocalDate
    ): Map<String, GroundTruthDailyObservation>
}

/**
 * Implémentation par défaut basée sur l'API Historical Weather (Réanalyse ERA5 / ECMWF IFS)
 * d'Open-Meteo (`https://archive-api.open-meteo.com/v1/archive`).
 */
class OpenMeteoHistoricalReanalysisSource(
    private val httpClient: OkHttpClient
) : GroundTruthWeatherSource {

    override val sourceDisplayName: String = "Open-Meteo Historical Weather API (Réanalyse)"

    override suspend fun fetchDailyObservations(
        latitude: Double,
        longitude: Double,
        startDate: LocalDate,
        endDate: LocalDate
    ): Map<String, GroundTruthDailyObservation> = withContext(Dispatchers.IO) {
        val url = "https://archive-api.open-meteo.com/v1/archive".toHttpUrl().newBuilder()
            .addQueryParameter("latitude", latitude.toString())
            .addQueryParameter("longitude", longitude.toString())
            .addQueryParameter("start_date", startDate.toString())
            .addQueryParameter("end_date", endDate.toString())
            .addQueryParameter(
                "daily",
                "temperature_2m_mean,temperature_2m_max,temperature_2m_min,precipitation_sum"
            )
            .addQueryParameter("timezone", "auto")
            .build()

        val request = Request.Builder().url(url).get().build()
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("Erreur Historical Weather API (${response.code})")
            }
            val bodyStr = response.body?.string() ?: return@withContext emptyMap()
            parseHistoricalDailyJson(bodyStr)
        }
    }

    internal fun parseHistoricalDailyJson(jsonString: String): Map<String, GroundTruthDailyObservation> {
        val root = JSONObject(jsonString)
        val daily = root.optJSONObject("daily") ?: return emptyMap()
        val times = daily.optJSONArray("time") ?: return emptyMap()
        val tempMeans = daily.optJSONArray("temperature_2m_mean")
        val tempMaxs = daily.optJSONArray("temperature_2m_max")
        val tempMins = daily.optJSONArray("temperature_2m_min")
        val precipSums = daily.optJSONArray("precipitation_sum")

        val result = LinkedHashMap<String, GroundTruthDailyObservation>()
        for (i in 0 until times.length()) {
            val dateIso = times.optString(i)
            if (dateIso.isNullOrBlank()) continue

            // La réanalyse arrive avec quelques jours de retard : on ignore les jours où la donnée est null/NaN
            if (
                tempMaxs == null || tempMins == null || precipSums == null ||
                tempMaxs.isNull(i) || tempMins.isNull(i) || precipSums.isNull(i)
            ) {
                continue
            }

            val maxC = tempMaxs.optDouble(i, Double.NaN)
            val minC = tempMins.optDouble(i, Double.NaN)
            val precipMm = precipSums.optDouble(i, Double.NaN)
            if (maxC.isNaN() || minC.isNaN() || precipMm.isNaN()) continue

            val meanC = if (tempMeans != null && !tempMeans.isNull(i)) {
                tempMeans.optDouble(i, (maxC + minC) / 2.0)
            } else {
                (maxC + minC) / 2.0
            }

            result[dateIso] = GroundTruthDailyObservation(
                dateIso = dateIso,
                tempMeanC = meanC,
                tempMinC = minC,
                tempMaxC = maxC,
                precipitationSumMm = precipMm.coerceAtLeast(0.0)
            )
        }
        return result
    }
}
