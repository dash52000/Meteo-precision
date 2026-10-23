package com.example.domain

import com.example.data.local.RunningScoreSettingsEntity
import com.example.model.ActiveLocation
import com.example.model.BikeWindStatus
import com.example.model.CommuteSliceReport
import com.example.model.HourlyRunningScore
import com.example.model.HourlyWeatherPoint
import com.example.model.LocationType
import com.example.model.MudState
import com.example.model.ScoreLevel
import com.example.model.TrailConditionStatus
import com.example.model.WorkCommuteStatus
import kotlin.math.abs

object RunningScoreEngine {

    /**
     * Calcule le score course pour chaque heure entre 6 h et 21 h inclus.
     * Règle : le pire critère l'emporte (Rouge > Orange > Vert).
     */
    fun computeHourlyScores(
        todayHours: List<HourlyWeatherPoint>,
        settings: RunningScoreSettingsEntity,
        locationType: LocationType,
        past48hRainMm: Double,
        isWeekendDay: Boolean
    ): List<HourlyRunningScore> {
        val targetHours = todayHours.filter { it.hourOfDay in 6..21 }.sortedBy { it.hourOfDay }
        val nightLevelConfig = runCatching {
            ScoreLevel.valueOf(settings.nightPenaltyLevel)
        }.getOrDefault(ScoreLevel.ORANGE)

        val trailMudLevel: ScoreLevel? = if (locationType == LocationType.TRAIL) {
            when {
                past48hRainMm > settings.trailRain48hRedMm -> ScoreLevel.RED
                past48hRainMm > settings.trailRain48hOrangeMm -> ScoreLevel.ORANGE
                else -> ScoreLevel.GREEN
            }
        } else {
            null
        }

        return targetHours.map { pt ->
            val reasons = mutableListOf<String>()

            // 1. Précipitations
            val precipLevel = when {
                pt.precipitationMm > settings.precipOrangeMaxMm -> {
                    reasons.add(String.format("Pluie forte (%.1f mm/h)", pt.precipitationMm))
                    ScoreLevel.RED
                }
                pt.precipitationMm >= settings.precipGreenMaxMm -> {
                    reasons.add(String.format("Pluie faible (%.1f mm/h)", pt.precipitationMm))
                    ScoreLevel.ORANGE
                }
                else -> ScoreLevel.GREEN
            }

            // 2. Ressenti thermique
            val apparentLevel = when {
                pt.apparentTemperatureC < settings.feelsLikeOrangeMinC -> {
                    reasons.add(String.format("Ressenti glacial (%.0f °C)", pt.apparentTemperatureC))
                    ScoreLevel.RED
                }
                pt.apparentTemperatureC > settings.feelsLikeOrangeMaxC -> {
                    reasons.add(String.format("Ressenti très chaud (%.0f °C)", pt.apparentTemperatureC))
                    ScoreLevel.RED
                }
                pt.apparentTemperatureC < settings.feelsLikeGreenMinC -> {
                    reasons.add(String.format("Ressenti frais (%.0f °C)", pt.apparentTemperatureC))
                    ScoreLevel.ORANGE
                }
                pt.apparentTemperatureC > settings.feelsLikeGreenMaxC -> {
                    reasons.add(String.format("Ressenti doux/chaud (%.0f °C)", pt.apparentTemperatureC))
                    ScoreLevel.ORANGE
                }
                else -> ScoreLevel.GREEN
            }

            // 3. Rafales
            val gustsLevel = when {
                pt.windGustsKmh > settings.gustsOrangeMaxKmh -> {
                    reasons.add(String.format("Rafales violentes (%.0f km/h)", pt.windGustsKmh))
                    ScoreLevel.RED
                }
                pt.windGustsKmh >= settings.gustsGreenMaxKmh -> {
                    reasons.add(String.format("Rafales sensibles (%.0f km/h)", pt.windGustsKmh))
                    ScoreLevel.ORANGE
                }
                else -> ScoreLevel.GREEN
            }

            // 4. Lumière (nuit)
            val lightLevel = if (!pt.isDay) {
                if (nightLevelConfig != ScoreLevel.GREEN) {
                    reasons.add("Obscurité / Nuit (frontale requise)")
                }
                nightLevelConfig
            } else {
                ScoreLevel.GREEN
            }

            // 5. Orage (codes WMO 95, 96, 99)
            val isStorm = pt.weatherCode in setOf(95, 96, 99)
            val stormLevel = if (isStorm) {
                reasons.add("Risque d'orage")
                ScoreLevel.RED
            } else {
                ScoreLevel.GREEN
            }

            // 6. Spot de trail : cumul 48h
            if (trailMudLevel == ScoreLevel.RED) {
                reasons.add(String.format("Boue trail (%.1f mm / 48h)", past48hRainMm))
            } else if (trailMudLevel == ScoreLevel.ORANGE) {
                reasons.add(String.format("Terrain gras (%.1f mm / 48h)", past48hRainMm))
            }

            val allCriteria = listOfNotNull(
                precipLevel,
                apparentLevel,
                gustsLevel,
                lightLevel,
                stormLevel,
                trailMudLevel
            )
            val worstLevel = allCriteria.maxByOrNull { it.severityRank } ?: ScoreLevel.GREEN

            val isRealistic = isHourInRealisticWindow(pt.hourOfDay, isWeekendDay, settings)

            // Score continu interne (100 = parfait, pour départager les 3 meilleurs créneaux)
            val idealFeelsLike = (settings.feelsLikeGreenMinC + settings.feelsLikeGreenMaxC) / 2.0
            val tempDeviationPenalty = abs(pt.apparentTemperatureC - idealFeelsLike) * 1.4
            val precipPenalty = pt.precipitationMm * 35.0
            val windPenalty = (pt.windGustsKmh / 10.0) * 2.5
            val nightPenalty = if (!pt.isDay) 12.0 else 0.0
            val severityBasePenalty = worstLevel.severityRank * 100.0
            val rankScore = 1000.0 - severityBasePenalty - tempDeviationPenalty - precipPenalty - windPenalty - nightPenalty

            if (reasons.isEmpty()) {
                reasons.add("Conditions idéales")
            }

            HourlyRunningScore(
                hour = pt.hourOfDay,
                timeLabel = "${pt.hourOfDay}h00",
                overallLevel = worstLevel,
                precipLevel = precipLevel,
                apparentTempLevel = apparentLevel,
                gustsLevel = gustsLevel,
                lightLevel = lightLevel,
                stormLevel = stormLevel,
                trailMudLevel = trailMudLevel,
                limitingReasons = reasons,
                qualityRankScore = rankScore,
                temperatureC = pt.temperatureC,
                apparentTempC = pt.apparentTemperatureC,
                precipMm = pt.precipitationMm,
                gustsKmh = pt.windGustsKmh,
                weatherCode = pt.weatherCode,
                isDay = pt.isDay,
                isRealisticSlot = isRealistic
            )
        }
    }

    /**
     * Sélectionne les 3 meilleurs créneaux du jour parmi les créneaux réalistes configurés
     * (avec repli sur tous les créneaux 6h-21h si moins de 3 heures sont configurées).
     */
    fun selectTop3Slots(scores: List<HourlyRunningScore>): List<HourlyRunningScore> {
        val realistic = scores.filter { it.isRealisticSlot }
        val pool = if (realistic.size >= 3) realistic else scores
        return pool.sortedWith(
            compareBy<HourlyRunningScore> { it.overallLevel.severityRank }
                .thenByDescending { it.qualityRankScore }
        ).take(3).sortedBy { it.hour }
    }

    fun isHourInRealisticWindow(
        hour: Int,
        isWeekend: Boolean,
        settings: RunningScoreSettingsEntity
    ): Boolean {
        return if (isWeekend) {
            hour in settings.weekendSlotStartHour..settings.weekendSlotEndHour
        } else {
            (hour in settings.weekdaySlot1StartHour..settings.weekdaySlot1EndHour) ||
                (hour in settings.weekdaySlot2StartHour..settings.weekdaySlot2EndHour)
        }
    }

    /**
     * Indicateurs spécifiques au lieu de type « Boulot » :
     * Heures de trajet aller et retour, visibilité / brouillard et risque de verglas
     * (température <= 1 °C avec humidité >= 80 % ou précipitations > 0 mm).
     */
    fun computeWorkCommuteStatus(
        todayHours: List<HourlyWeatherPoint>,
        location: ActiveLocation
    ): WorkCommuteStatus {
        fun buildSlice(label: String, startH: Int, endH: Int): CommuteSliceReport {
            val s = startH.coerceIn(0, 23)
            val e = endH.coerceIn(s, 23)
            val slice = todayHours.filter { it.hourOfDay in s..e }.ifEmpty { todayHours.take(2) }
            val minTemp = slice.minOfOrNull { it.temperatureC } ?: 10.0
            val maxHum = slice.maxOfOrNull { it.relativeHumidityPercent } ?: 50
            val totalPrecip = slice.sumOf { it.precipitationMm }
            val minVis = slice.minOfOrNull { it.visibilityMeters } ?: 10000.0
            val hasFogCode = slice.any { it.weatherCode == 45 || it.weatherCode == 48 }
            val fogRisk = minVis < 1000.0 || hasFogCode
            val blackIceRisk = slice.any {
                it.temperatureC <= 1.0 && (it.relativeHumidityPercent >= 80 || it.precipitationMm > 0.0)
            }
            return CommuteSliceReport(
                label = label,
                windowText = "${s}h–${e}h",
                minTempC = minTemp,
                maxHumidityPercent = maxHum,
                totalPrecipMm = totalPrecip,
                minVisibilityMeters = minVis,
                hasFogRisk = fogRisk,
                hasBlackIceRisk = blackIceRisk
            )
        }

        return WorkCommuteStatus(
            morningReport = buildSlice(
                "Trajet Aller",
                location.commuteMorningStartHour,
                location.commuteMorningEndHour
            ),
            eveningReport = buildSlice(
                "Trajet Retour",
                location.commuteEveningStartHour,
                location.commuteEveningEndHour
            )
        )
    }

    /**
     * Indicateur spécifique au lieu « Spot de trail » :
     * Cumul de pluie des 48 dernières heures et état du terrain (Sec / Gras / Boue).
     */
    fun computeTrailConditionStatus(
        past48hRainMm: Double,
        settings: RunningScoreSettingsEntity
    ): TrailConditionStatus {
        val state = when {
            past48hRainMm > settings.trailRain48hRedMm -> MudState.MUDDY
            past48hRainMm > settings.trailRain48hOrangeMm -> MudState.DAMP
            else -> MudState.DRY
        }
        return TrailConditionStatus(
            rainLast48hMm = past48hRainMm,
            mudState = state,
            orangeThresholdMm = settings.trailRain48hOrangeMm,
            redThresholdMm = settings.trailRain48hRedMm
        )
    }

    /**
     * Indicateur spécifique au lieu « Vélo » :
     * Force et direction du vent (flèche), rafales, et conseil pour choisir le sens de la boucle.
     */
    fun computeBikeWindStatus(currentHourPoint: HourlyWeatherPoint): BikeWindStatus {
        val deg = ((currentHourPoint.windDirectionDeg % 360) + 360) % 360
        val cardinal = degreeToCardinalFr(deg)
        val advice = "Partir cap $cardinal (face au vent à l'aller, vent de dos au retour)"
        return BikeWindStatus(
            windSpeedKmh = currentHourPoint.windSpeedKmh,
            gustsKmh = currentHourPoint.windGustsKmh,
            windDirectionDeg = deg,
            windFromCardinal = cardinal,
            loopAdviceFr = advice
        )
    }

    fun degreeToCardinalFr(deg: Int): String {
        val directions = listOf("N", "NE", "E", "SE", "S", "SO", "O", "NO")
        val idx = (((deg + 22.5) % 360) / 45.0).toInt().coerceIn(0, 7)
        return directions[idx]
    }
}
