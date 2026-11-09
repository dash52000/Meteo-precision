package com.example.domain

import com.example.model.ConfidenceLevel
import com.example.model.DailyWeatherPoint
import com.example.model.DayConfidence
import com.example.model.DayDetail
import com.example.model.DaySlot
import com.example.model.ForecastStep
import com.example.model.HourlyWeatherPoint
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Détail d'une journée (panneau du bas) : choix du pas de temps honnête selon la résolution native
 * des modèles, regroupement en tranches 3 h / 6 h, et indice de confiance ECMWF vs GFS.
 */
object DayDetailEngine {

    /**
     * Âge typique du dernier run disponible chez Open-Meteo : l'échéance réelle du modèle dépasse
     * l'écart avec maintenant d'environ ce nombre d'heures. On en tient compte pour ne pas annoncer
     * un pas horaire là où le modèle est déjà passé au pas de 3 h.
     */
    const val RUN_AGE_MARGIN_HOURS = 6L

    // Seuils de l'indice de confiance (accord ECMWF IFS / GFS sur la journée)
    const val GOOD_MAX_TEMP_GAP_C = 2.0
    const val LOW_MIN_TEMP_GAP_C = 4.0
    const val RAIN_DAY_THRESHOLD_MM = 1.0
    const val LOW_MIN_PRECIP_GAP_MM = 5.0

    // Seuil à partir duquel une tranche est considérée pluvieuse pour l'icône
    private const val WET_SLOT_THRESHOLD_MM = 0.2

    /** Les 4 tranches de 6 h (heures locales). */
    val SIX_HOUR_TRANCHES: List<Triple<Int, Int, String>> = listOf(
        Triple(0, 6, "Nuit"),
        Triple(6, 12, "Matin"),
        Triple(12, 18, "Après-midi"),
        Triple(18, 24, "Soirée")
    )

    /**
     * Indice de confiance d'une journée à partir des min/max et cumuls de pluie d'ECMWF et de GFS.
     * - Bonne : écart Tmin/Tmax ≤ 2 °C, d'accord sur pluie / pas pluie (seuil 1 mm) et écart de cumul < 5 mm.
     * - Faible : écart > 4 °C, ou désaccord sur la pluie avec ≥ 5 mm d'écart.
     * - Moyenne : le reste.
     * Renvoie null si l'un des deux modèles ne fournit pas la journée (aucune valeur inventée).
     */
    fun computeConfidence(
        ecmwfTempMinC: Double?,
        ecmwfTempMaxC: Double?,
        ecmwfPrecipMm: Double?,
        gfsTempMinC: Double?,
        gfsTempMaxC: Double?,
        gfsPrecipMm: Double?
    ): DayConfidence? {
        if (ecmwfTempMinC == null || ecmwfTempMaxC == null || ecmwfPrecipMm == null ||
            gfsTempMinC == null || gfsTempMaxC == null || gfsPrecipMm == null
        ) {
            return null
        }
        val ecmwfP = ecmwfPrecipMm.coerceAtLeast(0.0)
        val gfsP = gfsPrecipMm.coerceAtLeast(0.0)
        val tempGap = maxOf(abs(ecmwfTempMinC - gfsTempMinC), abs(ecmwfTempMaxC - gfsTempMaxC))
        val precipGap = abs(ecmwfP - gfsP)
        val rainAgreement = (ecmwfP >= RAIN_DAY_THRESHOLD_MM) == (gfsP >= RAIN_DAY_THRESHOLD_MM)

        val level = when {
            tempGap > LOW_MIN_TEMP_GAP_C -> ConfidenceLevel.LOW
            !rainAgreement && precipGap >= LOW_MIN_PRECIP_GAP_MM -> ConfidenceLevel.LOW
            tempGap <= GOOD_MAX_TEMP_GAP_C && rainAgreement && precipGap < LOW_MIN_PRECIP_GAP_MM -> ConfidenceLevel.GOOD
            else -> ConfidenceLevel.MEDIUM
        }
        return DayConfidence(
            level = level,
            ecmwfTempMinC = ecmwfTempMinC,
            ecmwfTempMaxC = ecmwfTempMaxC,
            gfsTempMinC = gfsTempMinC,
            gfsTempMaxC = gfsTempMaxC,
            ecmwfPrecipMm = ecmwfP,
            gfsPrecipMm = gfsP
        )
    }

    /**
     * Pas de temps de la journée : le plus grossier des pas natifs des modèles réellement utilisés
     * (température et pluie) sur chacune de ses heures, pour ne pas changer de pas en cours de journée.
     */
    fun stepForDay(hours: List<HourlyWeatherPoint>, now: LocalDateTime): ForecastStep {
        if (hours.isEmpty()) return ForecastStep.HOURLY
        var coarsest = 1
        for (pt in hours) {
            val time = runCatching { LocalDateTime.parse(pt.timeIso) }.getOrNull() ?: continue
            val leadHours = (Duration.between(now, time).toHours() + RUN_AGE_MARGIN_HOURS)
                .coerceAtLeast(0L)
                .coerceAtMost(Int.MAX_VALUE.toLong())
                .toInt()
            val step = maxOf(
                pt.tempModelUsed.nativeStepHoursAt(leadHours),
                pt.precipModelUsed.nativeStepHoursAt(leadHours)
            )
            if (step > coarsest) coarsest = step
        }
        return ForecastStep.fromHours(coarsest)
    }

    /** Lignes du détail : une par heure, par tranche de 3 h (0–3 h, 3–6 h…) ou par tranche de 6 h. */
    fun buildSlots(hours: List<HourlyWeatherPoint>, step: ForecastStep): List<DaySlot> {
        val sorted = hours.sortedBy { it.hourOfDay }
        return when (step) {
            ForecastStep.HOURLY -> sorted.mapNotNull { pt ->
                aggregate(listOf(pt), pt.hourOfDay, pt.hourOfDay + 1, "${pt.hourOfDay}h")
            }
            ForecastStep.THREE_HOURS -> (0 until 24 step 3).mapNotNull { start ->
                val end = start + 3
                aggregate(sorted.filter { it.hourOfDay in start until end }, start, end, "${start}h–${end}h")
            }
            ForecastStep.SIX_HOURS -> SIX_HOUR_TRANCHES.mapNotNull { (start, end, label) ->
                aggregate(sorted.filter { it.hourOfDay in start until end }, start, end, label)
            }
        }
    }

    /** Assemble le détail d'un jour à partir de son résumé journalier et de ses points horaires. */
    fun buildDayDetail(
        day: DailyWeatherPoint,
        hours: List<HourlyWeatherPoint>,
        now: LocalDateTime
    ): DayDetail {
        val step = stepForDay(hours, now)
        return DayDetail(
            day = day,
            step = step,
            hours = hours.sortedBy { it.hourOfDay },
            slots = buildSlots(hours, step),
            sunriseHourFraction = timeFractionOrNull(day.sunriseLabel),
            sunsetHourFraction = timeFractionOrNull(day.sunsetLabel)
        )
    }

    private fun aggregate(
        points: List<HourlyWeatherPoint>,
        startHour: Int,
        endHourExclusive: Int,
        label: String
    ): DaySlot? {
        if (points.isEmpty()) return null
        val precipSum = points.sumOf { it.precipitationMm.coerceAtLeast(0.0) }
        return DaySlot(
            startHour = startHour,
            endHourExclusive = endHourExclusive,
            label = label,
            tempMinC = points.minOf { it.temperatureC },
            tempMaxC = points.maxOf { it.temperatureC },
            apparentMinC = points.minOf { it.apparentTemperatureC },
            apparentMaxC = points.maxOf { it.apparentTemperatureC },
            precipitationMm = precipSum,
            windSpeedKmh = points.maxOf { it.windSpeedKmh },
            windGustsKmh = points.mapNotNull { it.windGustsKmh }.maxOrNull(),
            windDirectionDeg = dominantDirectionDeg(points),
            weatherCode = dominantWeatherCode(points, precipSum),
            isDay = points.count { it.isDay } * 2 >= points.size
        )
    }

    /** Direction dominante : moyenne vectorielle pondérée par la vitesse du vent (null si aucune direction). */
    internal fun dominantDirectionDeg(points: List<HourlyWeatherPoint>): Int? {
        val withDir = points.filter { it.windDirectionDeg != null }
        if (withDir.isEmpty()) return null
        val allCalm = withDir.all { it.windSpeedKmh <= 0.0 }
        var east = 0.0
        var north = 0.0
        for (pt in withDir) {
            val weight = if (allCalm) 1.0 else pt.windSpeedKmh.coerceAtLeast(0.0)
            val rad = Math.toRadians(pt.windDirectionDeg!!.toDouble())
            east += weight * sin(rad)
            north += weight * cos(rad)
        }
        if (abs(east) < 1e-9 && abs(north) < 1e-9) return withDir.first().windDirectionDeg
        val deg = Math.toDegrees(atan2(east, north))
        return ((deg.roundToInt() % 360) + 360) % 360
    }

    /**
     * Icône d'une tranche : la condition la plus marquée. Les codes de pluie/neige (≥ 50) ne comptent
     * que si la tranche cumule au moins 0,2 mm ; un orage (≥ 95) compte toujours.
     */
    internal fun dominantWeatherCode(points: List<HourlyWeatherPoint>, precipSumMm: Double): Int? {
        val codes = points.mapNotNull { it.weatherCode }
        if (codes.isEmpty()) return null
        if (precipSumMm >= WET_SLOT_THRESHOLD_MM || codes.any { it >= 95 }) return codes.max()
        return codes.filter { it < 50 }.maxOrNull() ?: codes.max()
    }

    private fun timeFractionOrNull(hhMm: String): Float? = runCatching {
        val t = LocalTime.parse(hhMm)
        t.hour + (t.minute / 60f)
    }.getOrNull()
}
