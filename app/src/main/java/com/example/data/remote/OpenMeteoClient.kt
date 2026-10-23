package com.example.data.remote

import com.example.model.AirQualitySummary
import com.example.model.WeatherModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.util.concurrent.TimeUnit

data class GeocodingResultItem(
    val id: Long,
    val name: String,
    val admin1: String,
    val country: String,
    val latitude: Double,
    val longitude: Double,
    val elevationMeters: Double?
) {
    val subtitle: String
        get() = listOfNotNull(
            admin1.takeIf { it.isNotBlank() },
            country.takeIf { it.isNotBlank() }
        ).joinToString(", ")
}

data class PreviousRunDailyForecast(
    val dateIso: String,
    val model: WeatherModel,
    val leadDays: Int,
    val tempMeanC: Double,
    val tempMinC: Double,
    val tempMaxC: Double,
    val precipSumMm: Double
)

class OpenMeteoClient(
    val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()
) {

    /**
     * Appelle l'API Forecast d'Open-Meteo avec les données 15 minutes, horaires (incluant past_days=2 pour
     * le cumul de pluie 48h des spots de trail) et journalières (sur 8 jours).
     * Peut interroger un modèle unique ou plusieurs modèles simultanément (pour le mode Auto hybride).
     */
    suspend fun fetchForecastJson(
        latitude: Double,
        longitude: Double,
        models: List<WeatherModel>
    ): String = withContext(Dispatchers.IO) {
        val modelParam = models.map { it.apiId }.distinct().joinToString(",")
        val urlBuilder = "https://api.open-meteo.com/v1/forecast".toHttpUrl().newBuilder()
            .addQueryParameter("latitude", latitude.toString())
            .addQueryParameter("longitude", longitude.toString())
            .addQueryParameter("minutely_15", "precipitation")
            .addQueryParameter(
                "hourly",
                "temperature_2m,apparent_temperature,precipitation,wind_speed_10m,wind_gusts_10m,wind_direction_10m,visibility,relative_humidity_2m,weather_code,is_day"
            )
            .addQueryParameter(
                "daily",
                "weather_code,temperature_2m_max,temperature_2m_min,sunrise,sunset,daylight_duration,uv_index_max,precipitation_sum,wind_speed_10m_max,wind_gusts_10m_max,wind_direction_10m_dominant"
            )
            .addQueryParameter("past_days", "2")
            .addQueryParameter("forecast_days", "8")
            .addQueryParameter("timezone", "auto")

        if (modelParam.isNotBlank() && modelParam != WeatherModel.BEST_MATCH.apiId) {
            urlBuilder.addQueryParameter("models", modelParam)
        }

        val request = Request.Builder().url(urlBuilder.build()).get().build()
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("Erreur Open-Meteo Forecast API (${response.code})")
            }
            response.body?.string() ?: throw IllegalStateException("Réponse Forecast API vide")
        }
    }

    /**
     * Appelle l'API Air Quality d'Open-Meteo (IQA européen, PM2.5, PM10, UV actuel et 6 pollens).
     */
    suspend fun fetchAirQualityJson(
        latitude: Double,
        longitude: Double
    ): String? = withContext(Dispatchers.IO) {
        runCatching {
            val url = "https://air-quality-api.open-meteo.com/v1/air-quality".toHttpUrl().newBuilder()
                .addQueryParameter("latitude", latitude.toString())
                .addQueryParameter("longitude", longitude.toString())
                .addQueryParameter(
                    "current",
                    "european_aqi,pm10,pm2_5,uv_index,alder_pollen,birch_pollen,grass_pollen,mugwort_pollen,olive_pollen,ragweed_pollen"
                )
                .addQueryParameter("timezone", "auto")
                .build()
            val request = Request.Builder().url(url).get().build()
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@runCatching null
                response.body?.string()
            }
        }.getOrNull()
    }

    fun parseAirQuality(jsonString: String?): AirQualitySummary {
        if (jsonString.isNullOrBlank()) {
            return AirQualitySummary(
                europeanAqi = null,
                aqiLabelFr = "Indisponible",
                uvIndexCurrent = null,
                pm25 = null,
                pm10 = null,
                alderPollen = null,
                birchPollen = null,
                grassPollen = null,
                mugwortPollen = null,
                olivePollen = null,
                ragweedPollen = null
            )
        }
        return runCatching {
            val root = JSONObject(jsonString)
            val current = root.optJSONObject("current")
            val aqi = current?.takeIf { !it.isNull("european_aqi") }?.optInt("european_aqi")
            val aqiLabel = when {
                aqi == null -> "N/D"
                aqi <= 20 -> "Bon ($aqi)"
                aqi <= 40 -> "Correct ($aqi)"
                aqi <= 60 -> "Moyen ($aqi)"
                aqi <= 80 -> "Dégradé ($aqi)"
                aqi <= 100 -> "Mauvais ($aqi)"
                else -> "Très mauvais ($aqi)"
            }
            fun optDoubleNullable(key: String): Double? {
                if (current == null || current.isNull(key)) return null
                val d = current.optDouble(key, Double.NaN)
                return if (d.isNaN()) null else d
            }
            AirQualitySummary(
                europeanAqi = aqi,
                aqiLabelFr = aqiLabel,
                uvIndexCurrent = optDoubleNullable("uv_index"),
                pm25 = optDoubleNullable("pm2_5"),
                pm10 = optDoubleNullable("pm10"),
                alderPollen = optDoubleNullable("alder_pollen"),
                birchPollen = optDoubleNullable("birch_pollen"),
                grassPollen = optDoubleNullable("grass_pollen"),
                mugwortPollen = optDoubleNullable("mugwort_pollen"),
                olivePollen = optDoubleNullable("olive_pollen"),
                ragweedPollen = optDoubleNullable("ragweed_pollen")
            )
        }.getOrElse {
            AirQualitySummary(null, "N/D", null, null, null, null, null, null, null, null, null)
        }
    }

    /**
     * Appelle l'API Geocoding d'Open-Meteo pour la recherche libre de ville en français.
     */
    suspend fun searchCities(query: String): List<GeocodingResultItem> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.length < 2) return@withContext emptyList()

        val url = "https://geocoding-api.open-meteo.com/v1/search".toHttpUrl().newBuilder()
            .addQueryParameter("name", trimmed)
            .addQueryParameter("count", "12")
            .addQueryParameter("language", "fr")
            .addQueryParameter("format", "json")
            .build()

        val request = Request.Builder().url(url).get().build()
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@withContext emptyList()
            val body = response.body?.string() ?: return@withContext emptyList()
            val root = JSONObject(body)
            val results = root.optJSONArray("results") ?: return@withContext emptyList()
            val list = ArrayList<GeocodingResultItem>(results.length())
            for (i in 0 until results.length()) {
                val item = results.optJSONObject(i) ?: continue
                list.add(
                    GeocodingResultItem(
                        id = item.optLong("id", i.toLong()),
                        name = item.optString("name", "Ville"),
                        admin1 = item.optString("admin1", ""),
                        country = item.optString("country", ""),
                        latitude = item.optDouble("latitude", 0.0),
                        longitude = item.optDouble("longitude", 0.0),
                        elevationMeters = if (item.isNull("elevation")) null else item.optDouble("elevation")
                    )
                )
            }
            list
        }
    }

    /**
     * Appelle l'API Previous Runs d'Open-Meteo (`https://previous-runs-api.open-meteo.com/v1/forecast`)
     * pour un modèle donné sur la fenêtre [startDate]..[endDate] et agrège par jour les prévisions
     * émises à J-1 .. J-maxLeadDays.
     */
    suspend fun fetchPreviousRunsForModel(
        latitude: Double,
        longitude: Double,
        model: WeatherModel,
        startDate: LocalDate,
        endDate: LocalDate
    ): Map<Int, Map<String, PreviousRunDailyForecast>> = withContext(Dispatchers.IO) {
        val maxLead = model.maxLeadDaysForReliability
        val tempVars = (1..maxLead).map { "temperature_2m_previous_day$it" }
        val precipVars = (1..maxLead).map { "precipitation_previous_day$it" }
        val hourlyParams = (tempVars + precipVars).joinToString(",")

        val url = "https://previous-runs-api.open-meteo.com/v1/forecast".toHttpUrl().newBuilder()
            .addQueryParameter("latitude", latitude.toString())
            .addQueryParameter("longitude", longitude.toString())
            .addQueryParameter("start_date", startDate.toString())
            .addQueryParameter("end_date", endDate.toString())
            .addQueryParameter("hourly", hourlyParams)
            .addQueryParameter("models", model.apiId)
            .addQueryParameter("timezone", "auto")
            .build()

        val request = Request.Builder().url(url).get().build()
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                return@withContext emptyMap()
            }
            val body = response.body?.string() ?: return@withContext emptyMap()
            parsePreviousRunsHourlyToDaily(body, model, maxLead)
        }
    }

    internal fun parsePreviousRunsHourlyToDaily(
        jsonString: String,
        model: WeatherModel,
        maxLead: Int
    ): Map<Int, Map<String, PreviousRunDailyForecast>> {
        val root = JSONObject(jsonString)
        val hourly = root.optJSONObject("hourly") ?: return emptyMap()
        val times = hourly.optJSONArray("time") ?: return emptyMap()

        fun findArray(baseKey: String): JSONArray? {
            hourly.optJSONArray(baseKey)?.let { return it }
            hourly.optJSONArray("${baseKey}_${model.apiId}")?.let { return it }
            val keys = hourly.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                if (k.startsWith(baseKey)) {
                    return hourly.optJSONArray(k)
                }
            }
            return null
        }

        val resultByLead = LinkedHashMap<Int, Map<String, PreviousRunDailyForecast>>()

        for (lead in 1..maxLead) {
            val tempArr = findArray("temperature_2m_previous_day$lead") ?: continue
            val precipArr = findArray("precipitation_previous_day$lead") ?: continue

            val tempByDay = LinkedHashMap<String, MutableList<Double>>()
            val precipByDay = LinkedHashMap<String, MutableList<Double>>()

            for (i in 0 until times.length()) {
                val timeIso = times.optString(i)
                if (timeIso.length < 10) continue
                val dateIso = timeIso.substring(0, 10)
                if (tempArr.isNull(i) || precipArr.isNull(i)) continue
                val t = tempArr.optDouble(i, Double.NaN)
                val p = precipArr.optDouble(i, Double.NaN)
                if (t.isNaN() || p.isNaN()) continue

                tempByDay.getOrPut(dateIso) { ArrayList(24) }.add(t)
                precipByDay.getOrPut(dateIso) { ArrayList(24) }.add(p.coerceAtLeast(0.0))
            }

            val dailyMap = LinkedHashMap<String, PreviousRunDailyForecast>()
            for ((dateIso, temps) in tempByDay) {
                val precips = precipByDay[dateIso] ?: continue
                // Exiger au moins 18 heures valides dans la journée pour une agrégation journalière fiable
                if (temps.size >= 18 && precips.size >= 18) {
                    dailyMap[dateIso] = PreviousRunDailyForecast(
                        dateIso = dateIso,
                        model = model,
                        leadDays = lead,
                        tempMeanC = temps.average(),
                        tempMinC = temps.minOrNull() ?: 0.0,
                        tempMaxC = temps.maxOrNull() ?: 0.0,
                        precipSumMm = precips.sum()
                    )
                }
            }
            if (dailyMap.isNotEmpty()) {
                resultByLead[lead] = dailyMap
            }
        }
        return resultByLead
    }
}
