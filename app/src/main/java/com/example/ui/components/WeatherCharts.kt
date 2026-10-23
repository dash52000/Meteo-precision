package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.model.ForecastVsRealDayPoint
import com.example.model.HourlyWeatherPoint
import com.example.model.Minutely15Point
import com.example.ui.theme.AmberTemp
import com.example.ui.theme.CyanPrecip
import com.example.ui.theme.DeepBluePrecip
import com.example.ui.theme.GoldApparentTemp
import com.example.ui.theme.MagentaGusts
import com.example.ui.theme.NightShadeOverlay
import com.example.ui.theme.ScoreGreen
import com.example.ui.theme.SlateBorder
import com.example.ui.theme.SlateSurfaceElevated
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.VioletWind
import java.util.Locale
import kotlin.math.roundToInt

/**
 * 1. Graphe des précipitations au quart d'heure sur les 3 prochaines heures.
 * Calibré pour la largeur d'écran du Galaxy S25 (360dp-411dp).
 */
@Composable
fun Precipitation3HoursChart(
    points: List<Minutely15Point>,
    modifier: Modifier = Modifier
) {
    val total3hMm = remember(points) { points.sumOf { it.precipitationMm } }
    val maxMm = remember(points) { (points.maxOfOrNull { it.precipitationMm } ?: 1.0).coerceAtLeast(1.0) }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (total3hMm < 0.05) {
                    "0,0 mm prévus sur 3 h"
                } else {
                    String.format(Locale.FRANCE, "Cumul 3 h : %.1f mm", total3hMm)
                },
                style = MaterialTheme.typography.labelLarge,
                color = if (total3hMm < 0.05) ScoreGreen else CyanPrecip,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = String.format(Locale.FRANCE, "Max %.1f mm/15m", maxMm),
                style = MaterialTheme.typography.labelSmall,
                color = TextMuted,
                maxLines = 1
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(98.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(SlateSurfaceElevated)
                .border(1.dp, SlateBorder, RoundedCornerShape(10.dp))
                .padding(horizontal = 8.dp, vertical = 8.dp)
                .testTag("chart_precip_3h")
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height - 16.dp.toPx()
                val count = points.size.coerceAtLeast(1)
                val slotW = w / count

                for (frac in listOf(0.25f, 0.5f, 0.75f, 1f)) {
                    val y = h * (1f - frac)
                    drawLine(
                        color = SlateBorder.copy(alpha = 0.5f),
                        start = Offset(0f, y),
                        end = Offset(w, y),
                        strokeWidth = 1f
                    )
                }

                points.forEachIndexed { idx, pt ->
                    val ratio = (pt.precipitationMm / maxMm).toFloat().coerceIn(0f, 1f)
                    val barH = (ratio * (h - 6f)).coerceAtLeast(if (pt.precipitationMm > 0.0) 4f else 2f)
                    val barW = (slotW * 0.62f).coerceAtLeast(4f)
                    val left = idx * slotW + (slotW - barW) / 2f
                    val top = h - barH

                    drawRoundRect(
                        color = if (pt.precipitationMm > 0.01) CyanPrecip else SlateBorder,
                        topLeft = Offset(left, top),
                        size = Size(barW, barH),
                        cornerRadius = CornerRadius(4f, 4f)
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                val indices = listOf(0, points.size / 3, (points.size * 2) / 3, points.lastIndex)
                    .filter { it in points.indices }
                    .distinct()
                indices.forEach { i ->
                    Text(
                        text = points[i].timeLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary
                    )
                }
            }
        }
    }
}

/**
 * 2. Graphe de la journée heure par heure (24 h) calibré pour écran S25 :
 * Courbes superposées de température, ressenti, précipitations, vent et rafales.
 * Nuits grisées selon le lever et le coucher du soleil + durée du jour.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun Hourly24hMultiCurveChart(
    hours: List<HourlyWeatherPoint>,
    sunriseFraction: Float?,
    sunsetFraction: Float?,
    sunriseLabel: String,
    sunsetLabel: String,
    daylightDurationLabel: String,
    modifier: Modifier = Modifier
) {
    if (hours.isEmpty()) return
    var selectedIdx by remember(hours) {
        mutableIntStateOf((hours.size / 2).coerceIn(0, hours.lastIndex))
    }
    val selectedPoint = hours[selectedIdx.coerceIn(0, hours.lastIndex)]

    val minTemp = remember(hours) {
        (hours.minOf { minOf(it.temperatureC, it.apparentTemperatureC) } - 2.0)
    }
    val maxTemp = remember(hours) {
        (hours.maxOf { maxOf(it.temperatureC, it.apparentTemperatureC) } + 2.0)
            .coerceAtLeast(minTemp + 6.0)
    }
    val maxWind = remember(hours) {
        (hours.maxOf { maxOf(it.windSpeedKmh, it.windGustsKmh ?: it.windSpeedKmh) } * 1.15).coerceAtLeast(30.0)
    }
    val maxPrecip = remember(hours) {
        (hours.maxOf { it.precipitationMm } * 1.3).coerceAtLeast(2.0)
    }

    Column(modifier = modifier.fillMaxWidth()) {
        // Bandeau Lever / Coucher du soleil & Durée du jour optimisé 360dp
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(SlateSurfaceElevated)
                .padding(horizontal = 8.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "☀ $sunriseLabel → $sunsetLabel",
                style = MaterialTheme.typography.labelMedium,
                color = GoldApparentTemp,
                maxLines = 1
            )
            Text(
                text = "Jour : $daylightDurationLabel",
                style = MaterialTheme.typography.labelMedium,
                color = TextPrimary,
                maxLines = 1
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Légende FlowRow sans coupure sur l'écran 6.2" du S25
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            LegendDot(color = AmberTemp, label = "Temp °C")
            LegendDot(color = GoldApparentTemp, label = "Ressenti (pointillé)")
            LegendDot(color = CyanPrecip, label = "Pluie mm")
            LegendDot(color = VioletWind, label = "Vent km/h")
            LegendDot(color = MagentaGusts, label = "Rafales")
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Encart de lecture précise sur 2 lignes tabulaires avec icône météo pour lisibilité sur S25
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(SlateSurfaceElevated.copy(alpha = 0.9f))
                .border(1.dp, SlateBorder, RoundedCornerShape(8.dp))
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    WeatherConditionIcon(
                        weatherCode = selectedPoint.weatherCode,
                        isDay = selectedPoint.isDay,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "${selectedPoint.hourLabel}00 · ${wmoCodeToLabelFr(selectedPoint.weatherCode, selectedPoint.isDay)}",
                        style = MaterialTheme.typography.labelLarge,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    text = String.format(
                        Locale.FRANCE,
                        "T° %.1f° (Ress. %.1f°)",
                        selectedPoint.temperatureC,
                        selectedPoint.apparentTemperatureC
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = AmberTemp
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = String.format(Locale.FRANCE, "Pluie : %.1f mm/h", selectedPoint.precipitationMm),
                    style = MaterialTheme.typography.labelSmall,
                    color = CyanPrecip
                )
                Text(
                    text = String.format(
                        Locale.FRANCE,
                        "Vent %.0f km/h · Raf. %s km/h",
                        selectedPoint.windSpeedKmh,
                        selectedPoint.windGustsKmh?.let { String.format(Locale.FRANCE, "%.0f", it) } ?: "—"
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MagentaGusts
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Frise d'aperçu rapide des icônes météo de la journée (toutes les 4 heures)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            listOf(0, 4, 8, 12, 16, 20, 23).mapNotNull { hIdx -> hours.getOrNull(hIdx) }.forEach { pt ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    WeatherConditionIcon(
                        weatherCode = pt.weatherCode,
                        isDay = pt.isDay,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = pt.hourLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(195.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(SlateSurfaceElevated)
                .border(1.dp, SlateBorder, RoundedCornerShape(10.dp))
                .pointerInput(hours.size) {
                    detectTapGestures { offset ->
                        val frac = (offset.x / size.width.coerceAtLeast(1)).coerceIn(0f, 1f)
                        selectedIdx = (frac * (hours.size - 1)).roundToInt().coerceIn(0, hours.lastIndex)
                    }
                }
                .pointerInput(hours.size) {
                    detectHorizontalDragGestures { change, _ ->
                        change.consume()
                        val frac = (change.position.x / size.width.coerceAtLeast(1)).coerceIn(0f, 1f)
                        selectedIdx = (frac * (hours.size - 1)).roundToInt().coerceIn(0, hours.lastIndex)
                    }
                }
                .padding(horizontal = 8.dp, vertical = 8.dp)
                .testTag("chart_hourly_24h")
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val chartH = size.height - 18.dp.toPx()
                val n = (hours.size - 1).coerceAtLeast(1)

                fun xForHour(hFraction: Float): Float =
                    ((hFraction / 23f) * w).coerceIn(0f, w)

                // Lever / coucher inconnus : pas d'ombrage de nuit inventé
                val sunriseX = sunriseFraction?.let { xForHour(it) } ?: 0f
                val sunsetX = sunsetFraction?.let { xForHour(it) } ?: w
                if (sunriseX > 0f) {
                    drawRect(
                        color = NightShadeOverlay,
                        topLeft = Offset(0f, 0f),
                        size = Size(sunriseX, chartH)
                    )
                }
                if (sunsetX < w) {
                    drawRect(
                        color = NightShadeOverlay,
                        topLeft = Offset(sunsetX, 0f),
                        size = Size(w - sunsetX, chartH)
                    )
                }

                for (frac in listOf(0.2f, 0.5f, 0.8f)) {
                    val y = chartH * frac
                    drawLine(
                        color = SlateBorder.copy(alpha = 0.55f),
                        start = Offset(0f, y),
                        end = Offset(w, y),
                        strokeWidth = 1f
                    )
                }

                val slotW = w / hours.size.coerceAtLeast(1)
                hours.forEachIndexed { idx, pt ->
                    if (pt.precipitationMm > 0.0) {
                        val pRatio = (pt.precipitationMm / maxPrecip).toFloat().coerceIn(0f, 1f)
                        val barH = (pRatio * chartH * 0.45f).coerceAtLeast(4f)
                        val xCenter = (idx.toFloat() / n) * w
                        val barW = (slotW * 0.55f).coerceAtLeast(4f)
                        drawRoundRect(
                            color = DeepBluePrecip.copy(alpha = 0.75f),
                            topLeft = Offset(xCenter - barW / 2f, chartH - barH),
                            size = Size(barW, barH),
                            cornerRadius = CornerRadius(3f, 3f)
                        )
                    }
                }

                // Une valeur absente (null) coupe la courbe au lieu d'être inventée
                fun buildSeriesPath(selector: (HourlyWeatherPoint) -> Double?, minVal: Double, maxVal: Double): Path {
                    val path = Path()
                    var penDown = false
                    hours.forEachIndexed { idx, pt ->
                        val value = selector(pt)
                        if (value == null) {
                            penDown = false
                        } else {
                            val x = (idx.toFloat() / n) * w
                            val norm = ((value - minVal) / (maxVal - minVal)).toFloat().coerceIn(0f, 1f)
                            val y = chartH * (1f - (0.08f + norm * 0.84f))
                            if (penDown) path.lineTo(x, y) else path.moveTo(x, y)
                            penDown = true
                        }
                    }
                    return path
                }

                val windPath = buildSeriesPath({ it.windSpeedKmh }, 0.0, maxWind)
                val gustsPath = buildSeriesPath({ it.windGustsKmh }, 0.0, maxWind)
                drawPath(
                    path = windPath,
                    color = VioletWind.copy(alpha = 0.85f),
                    style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
                )
                drawPath(
                    path = gustsPath,
                    color = MagentaGusts.copy(alpha = 0.9f),
                    style = Stroke(
                        width = 2.dp.toPx(),
                        cap = StrokeCap.Round,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f), 0f)
                    )
                )

                val apparentPath = buildSeriesPath({ it.apparentTemperatureC }, minTemp, maxTemp)
                val tempPath = buildSeriesPath({ it.temperatureC }, minTemp, maxTemp)
                drawPath(
                    path = apparentPath,
                    color = GoldApparentTemp,
                    style = Stroke(
                        width = 2.2.dp.toPx(),
                        cap = StrokeCap.Round,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 6f), 0f)
                    )
                )
                drawPath(
                    path = tempPath,
                    color = AmberTemp,
                    style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                )

                val selX = (selectedIdx.toFloat() / n) * w
                drawLine(
                    color = TextPrimary.copy(alpha = 0.7f),
                    start = Offset(selX, 0f),
                    end = Offset(selX, chartH),
                    strokeWidth = 1.5.dp.toPx()
                )
                val selTempNorm = ((selectedPoint.temperatureC - minTemp) / (maxTemp - minTemp)).toFloat().coerceIn(0f, 1f)
                val selTempY = chartH * (1f - (0.08f + selTempNorm * 0.84f))
                drawCircle(
                    color = AmberTemp,
                    radius = 5.dp.toPx(),
                    center = Offset(selX, selTempY)
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                listOf("00h", "06h", "12h", "18h", "23h").forEach { label ->
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary
                    )
                }
            }
        }
    }
}

/**
 * Graphe « Prévu vs Réel » sur 30 jours glissants pour l'écran Fiabilité.
 */
@Composable
fun ForecastVsReal30DayChart(
    points: List<ForecastVsRealDayPoint>,
    showPrecipitation: Boolean,
    modelBadge: String,
    leadDay: Int,
    modifier: Modifier = Modifier
) {
    if (points.isEmpty()) return
    var selectedIndex by remember(points) { mutableIntStateOf(points.lastIndex) }
    val selected = points[selectedIndex.coerceIn(0, points.lastIndex)]

    val minVal = remember(points, showPrecipitation) {
        if (showPrecipitation) 0.0
        else (points.minOf { minOf(it.forecastTempMeanC, it.actualTempMeanC) } - 2.0)
    }
    val maxVal = remember(points, showPrecipitation) {
        if (showPrecipitation) {
            (points.maxOf { maxOf(it.forecastPrecipMm, it.actualPrecipMm) } * 1.15).coerceAtLeast(5.0)
        } else {
            (points.maxOf { maxOf(it.forecastTempMeanC, it.actualTempMeanC) } + 2.0).coerceAtLeast(minVal + 5.0)
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(SlateSurfaceElevated)
                .padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = selected.shortDateLabel,
                style = MaterialTheme.typography.labelLarge,
                color = TextPrimary
            )
            if (showPrecipitation) {
                Text(
                    text = String.format(
                        Locale.FRANCE,
                        "Prévu J+%d : %.1f mm · Réel : %.1f mm",
                        leadDay,
                        selected.forecastPrecipMm,
                        selected.actualPrecipMm
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = CyanPrecip,
                    maxLines = 1
                )
            } else {
                Text(
                    text = String.format(
                        Locale.FRANCE,
                        "%s J+%d : %.1f °C · Réel : %.1f °C",
                        modelBadge,
                        leadDay,
                        selected.forecastTempMeanC,
                        selected.actualTempMeanC
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = AmberTemp,
                    maxLines = 1
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(175.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(SlateSurfaceElevated)
                .border(1.dp, SlateBorder, RoundedCornerShape(10.dp))
                .pointerInput(points.size) {
                    detectTapGestures { offset ->
                        val frac = (offset.x / size.width.coerceAtLeast(1)).coerceIn(0f, 1f)
                        selectedIndex = (frac * (points.size - 1)).roundToInt().coerceIn(0, points.lastIndex)
                    }
                }
                .pointerInput(points.size) {
                    detectHorizontalDragGestures { change, _ ->
                        change.consume()
                        val frac = (change.position.x / size.width.coerceAtLeast(1)).coerceIn(0f, 1f)
                        selectedIndex = (frac * (points.size - 1)).roundToInt().coerceIn(0, points.lastIndex)
                    }
                }
                .padding(8.dp)
                .testTag("chart_reliability_30d")
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height - 16.dp.toPx()
                val n = (points.size - 1).coerceAtLeast(1)

                for (frac in listOf(0.25f, 0.5f, 0.75f, 1f)) {
                    val y = h * (1f - frac)
                    drawLine(
                        color = SlateBorder.copy(alpha = 0.5f),
                        start = Offset(0f, y),
                        end = Offset(w, y),
                        strokeWidth = 1f
                    )
                }

                fun buildLine(selector: (ForecastVsRealDayPoint) -> Double): Path {
                    val path = Path()
                    points.forEachIndexed { idx, pt ->
                        val x = (idx.toFloat() / n) * w
                        val norm = ((selector(pt) - minVal) / (maxVal - minVal)).toFloat().coerceIn(0f, 1f)
                        val y = h * (1f - (0.06f + norm * 0.88f))
                        if (idx == 0) path.moveTo(x, y) else path.lineTo(x, y)
                    }
                    return path
                }

                val actualPath = buildLine {
                    if (showPrecipitation) it.actualPrecipMm else it.actualTempMeanC
                }
                val forecastPath = buildLine {
                    if (showPrecipitation) it.forecastPrecipMm else it.forecastTempMeanC
                }

                drawPath(
                    path = actualPath,
                    color = ScoreGreen,
                    style = Stroke(width = 2.6.dp.toPx(), cap = StrokeCap.Round)
                )

                drawPath(
                    path = forecastPath,
                    color = if (showPrecipitation) CyanPrecip else AmberTemp,
                    style = Stroke(
                        width = 2.4.dp.toPx(),
                        cap = StrokeCap.Round,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 6f), 0f)
                    )
                )

                val selX = (selectedIndex.toFloat() / n) * w
                drawLine(
                    color = TextPrimary.copy(alpha = 0.6f),
                    start = Offset(selX, 0f),
                    end = Offset(selX, h),
                    strokeWidth = 1.2.dp.toPx()
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = points.first().shortDateLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary
                )
                Text(
                    text = "Vert = Réel · Pointillé = Prévu",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted
                )
                Text(
                    text = points.last().shortDateLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary
                )
            }
        }
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondary
        )
    }
}
