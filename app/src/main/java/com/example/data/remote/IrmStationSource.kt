package com.example.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.TreeMap
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/** Début du nom affiché quand la référence est une vraie station de l'IRM. */
const val IRM_SOURCE_PREFIX = "Station IRM"

data class IrmStation(
    val code: Long,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val altitudeMeters: Double?
)

/**
 * Observations réelles des stations automatiques de l'IRM (Institut royal météorologique de Belgique).
 *
 * Service WFS ouvert, sans clé ni inscription (licence CC BY 4.0) :
 * https://opendata.meteo.be/service/aws/wfs — couches `aws:aws_station` et `aws:aws_1hour`.
 *
 * On lit les données horaires et on les agrège nous-mêmes en journées locales (minuit à minuit),
 * comme le sont les prévisions comparées : même découpage, mêmes règles (au moins 18 h de température).
 */
class IrmStationObservationSource(
    private val httpClient: OkHttpClient,
    private val maxDistanceKm: Double = 25.0,
    private val zoneId: ZoneId = ZoneId.systemDefault()
) {

    @Volatile
    private var cachedStations: List<IrmStation>? = null

    /**
     * Observations journalières de la station IRM la plus proche, ou null si aucune station active
     * n'est à moins de [maxDistanceKm]. Lève une exception si le service ne répond pas.
     */
    suspend fun fetchNearestStationObservations(
        latitude: Double,
        longitude: Double,
        startDate: LocalDate,
        endDate: LocalDate
    ): GroundTruthResult? = withContext(Dispatchers.IO) {
        val stations = cachedStations ?: fetchStations().also { cachedStations = it }
        val nearest = stations
            .map { it to distanceKm(latitude, longitude, it.latitude, it.longitude) }
            .minByOrNull { it.second }
            ?: return@withContext null
        val (station, distance) = nearest
        if (distance > maxDistanceKm) return@withContext null

        val startUtc = startDate.atStartOfDay(zoneId).toInstant()
        val endUtc = endDate.plusDays(1).atStartOfDay(zoneId).toInstant()
        val json = fetchHourlyJson(station.code, startUtc, endUtc)
        val observations = parseIrmHourlyToDaily(json, zoneId)
            .filterKeys { iso ->
                val d = LocalDate.parse(iso)
                !d.isBefore(startDate) && !d.isAfter(endDate)
            }

        GroundTruthResult(
            sourceDisplayName = stationLabel(station, distance),
            observations = observations
        )
    }

    private fun fetchStations(): List<IrmStation> {
        val url = BASE_URL.toHttpUrl().newBuilder()
            .addQueryParameter("service", "WFS")
            .addQueryParameter("version", "2.0.0")
            .addQueryParameter("request", "GetFeature")
            .addQueryParameter("typeNames", "aws:aws_station")
            .addQueryParameter("outputFormat", "application/json")
            .build()
        return parseIrmStations(get(url.toString()))
    }

    private fun fetchHourlyJson(stationCode: Long, startUtc: Instant, endUtc: Instant): String {
        val iso = DateTimeFormatter.ISO_INSTANT
        val filter = "code = $stationCode AND timestamp >= ${iso.format(startUtc)} AND timestamp <= ${iso.format(endUtc)}"
        val url = BASE_URL.toHttpUrl().newBuilder()
            .addQueryParameter("service", "WFS")
            .addQueryParameter("version", "2.0.0")
            .addQueryParameter("request", "GetFeature")
            .addQueryParameter("typeNames", "aws:aws_1hour")
            .addQueryParameter("outputFormat", "application/json")
            .addQueryParameter("count", "5000")
            .addQueryParameter("cql_filter", filter)
            .build()
        return get(url.toString())
    }

    private fun get(url: String): String {
        val request = Request.Builder().url(url).get().build()
        return httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("Erreur service IRM (${response.code})")
            }
            response.body?.string() ?: throw IllegalStateException("Réponse IRM vide")
        }
    }

    companion object {
        private const val BASE_URL = "https://opendata.meteo.be/service/aws/wfs"

        // Bornes physiques : au-delà, la valeur est une erreur de capteur et elle est ignorée
        private const val MIN_TEMP_C = -50.0
        private const val MAX_TEMP_C = 50.0
        private const val MAX_HOURLY_PRECIP_MM = 300.0

        // Une journée n'est retenue que si elle est assez complète
        private const val MIN_TEMP_HOURS = 18
        private const val MIN_PRECIP_HOURS = 22

        /** Liste des stations actives (sans date de fin) avec leurs coordonnées. */
        internal fun parseIrmStations(jsonString: String): List<IrmStation> {
            val features = JSONObject(jsonString).optJSONArray("features") ?: return emptyList()
            val stations = ArrayList<IrmStation>(features.length())
            for (i in 0 until features.length()) {
                val feature = features.optJSONObject(i) ?: continue
                val props = feature.optJSONObject("properties") ?: continue
                val coords = feature.optJSONObject("geometry")?.optJSONArray("coordinates") ?: continue
                if (coords.length() < 2) continue
                val dateEnd = if (props.isNull("date_end")) "" else props.optString("date_end", "")
                if (dateEnd.isNotBlank()) continue // station fermée
                val code = props.optLong("code", -1L)
                if (code < 0) continue
                // GeoJSON : [longitude, latitude]
                val lon = coords.optDouble(0, Double.NaN)
                val lat = coords.optDouble(1, Double.NaN)
                if (lat.isNaN() || lon.isNaN()) continue
                val altitude = if (props.isNull("altitude")) null else props.optDouble("altitude", Double.NaN).takeIf { !it.isNaN() }
                stations.add(
                    IrmStation(
                        code = code,
                        name = prettyStationName(props.optString("name", "Station $code")),
                        latitude = lat,
                        longitude = lon,
                        altitudeMeters = altitude
                    )
                )
            }
            return stations
        }

        /**
         * Agrège les mesures horaires en journées locales.
         * L'horodatage IRM est en UTC et marque la fin de l'heure mesurée : l'heure 23 h–00 h
         * (horodatée 00:00) appartient donc à la veille.
         */
        internal fun parseIrmHourlyToDaily(
            jsonString: String,
            zoneId: ZoneId
        ): Map<String, GroundTruthDailyObservation> {
            val features = JSONObject(jsonString).optJSONArray("features") ?: return emptyMap()
            val tempsByDay = HashMap<LocalDate, MutableList<Double>>()
            val precipByDay = HashMap<LocalDate, MutableList<Double>>()

            for (i in 0 until features.length()) {
                val props = features.optJSONObject(i)?.optJSONObject("properties") ?: continue
                val instant = runCatching { Instant.parse(props.optString("timestamp", "")) }.getOrNull() ?: continue
                val day = instant.minusSeconds(60).atZone(zoneId).toLocalDate()

                readBounded(props, "temp_dry_shelter_avg", MIN_TEMP_C, MAX_TEMP_C)?.let {
                    tempsByDay.getOrPut(day) { ArrayList(24) }.add(it)
                }
                readBounded(props, "precip_quantity", 0.0, MAX_HOURLY_PRECIP_MM)?.let {
                    precipByDay.getOrPut(day) { ArrayList(24) }.add(it)
                }
            }

            val result = TreeMap<String, GroundTruthDailyObservation>()
            for ((day, temps) in tempsByDay) {
                val precips = precipByDay[day] ?: continue
                if (temps.size < MIN_TEMP_HOURS || precips.size < MIN_PRECIP_HOURS) continue
                val dateIso = day.toString()
                result[dateIso] = GroundTruthDailyObservation(
                    dateIso = dateIso,
                    tempMeanC = temps.average(),
                    tempMinC = temps.min(),
                    tempMaxC = temps.max(),
                    precipitationSumMm = precips.sum()
                )
            }
            return result
        }

        private fun readBounded(props: JSONObject, key: String, min: Double, max: Double): Double? {
            if (!props.has(key) || props.isNull(key)) return null
            val v = props.optDouble(key, Double.NaN)
            return if (v.isNaN() || v < min || v > max) null else v
        }

        /** « SINT-KATELIJNE-WAVER » → « Sint-Katelijne-Waver ». */
        internal fun prettyStationName(raw: String): String =
            raw.trim().lowercase(Locale.FRANCE)
                .split(" ")
                .joinToString(" ") { word ->
                    word.split("-").joinToString("-") { part ->
                        part.replaceFirstChar { c -> c.titlecase(Locale.FRANCE) }
                    }
                }

        internal fun stationLabel(station: IrmStation, distanceKm: Double): String {
            val altitude = station.altitudeMeters?.let { " · alt. ${it.roundToInt()} m" } ?: ""
            return "$IRM_SOURCE_PREFIX ${station.name} · à ${distanceKm.roundToInt()} km$altitude"
        }

        /** Distance orthodromique (haversine) en kilomètres. */
        internal fun distanceKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
            val r = 6371.0
            val dLat = Math.toRadians(lat2 - lat1)
            val dLon = Math.toRadians(lon2 - lon1)
            val a = sin(dLat / 2).pow(2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
            return 2 * r * asin(sqrt(a))
        }
    }
}

/**
 * Référence du « réel » : la station IRM la plus proche si elle a assez de jours mesurés,
 * sinon la réanalyse Open-Meteo (affichée comme telle). Une panne de l'IRM ne casse rien :
 * on retombe simplement sur la réanalyse.
 */
class StationThenReanalysisSource(
    private val station: IrmStationObservationSource,
    private val reanalysis: GroundTruthWeatherSource,
    private val minStationDays: Int = 10
) : GroundTruthWeatherSource {

    override val sourceDisplayName: String
        get() = reanalysis.sourceDisplayName

    override suspend fun fetchDailyObservations(
        latitude: Double,
        longitude: Double,
        startDate: LocalDate,
        endDate: LocalDate
    ): Map<String, GroundTruthDailyObservation> =
        fetchDailyObservationsWithSource(latitude, longitude, startDate, endDate).observations

    override suspend fun fetchDailyObservationsWithSource(
        latitude: Double,
        longitude: Double,
        startDate: LocalDate,
        endDate: LocalDate
    ): GroundTruthResult {
        val fromStation = runCatching {
            station.fetchNearestStationObservations(latitude, longitude, startDate, endDate)
        }.getOrNull()
        if (fromStation != null && fromStation.observations.size >= minStationDays) {
            return fromStation
        }
        return GroundTruthResult(
            sourceDisplayName = reanalysis.sourceDisplayName,
            observations = reanalysis.fetchDailyObservations(latitude, longitude, startDate, endDate)
        )
    }
}
