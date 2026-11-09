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
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.DayDetailEngine
import com.example.model.DaySlot
import com.example.model.ForecastStep
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
import kotlin.math.abs
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
 *
 * [slots] : lignes de lecture du curseur et barres de pluie. null = pas horaire (une par heure).
 * En tranches de 3 h / 6 h, le curseur sélectionne une tranche entière (min–max, cumul) :
 * aucune valeur horaire interpolée n'est présentée comme précise.
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalTextApi::class)
@Composable
fun Hourly24hMultiCurveChart(
    hours: List<HourlyWeatherPoint>,
    sunriseFraction: Float?,
    sunsetFraction: Float?,
    sunriseLabel: String,
    sunsetLabel: String,
    daylightDurationLabel: String,
    modifier: Modifier = Modifier,
    slots: List<DaySlot>? = null
) {
    if (hours.isEmpty()) return
    val chartSlots = remember(hours, slots) {
        slots?.takeIf { it.isNotEmpty() } ?: DayDetailEngine.buildSlots(hours, ForecastStep.HOURLY)
    }
    if (chartSlots.isEmpty()) return
    val isHourly = remember(chartSlots) { chartSlots.all { it.endHourExclusive - it.startHour <= 1 } }
    val slotHours = chartSlots.first().let { it.endHourExclusive - it.startHour }

    var selectedIdx by remember(chartSlots) {
        mutableIntStateOf((chartSlots.size / 2).coerceIn(0, chartSlots.lastIndex))
    }
    val selectedSlot = chartSlots[selectedIdx.coerceIn(0, chartSlots.lastIndex)]

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
    val maxPrecip = remember(chartSlots) {
        (chartSlots.maxOf { it.precipitationMm } * 1.3).coerceAtLeast(2.0)
    }
    val textMeasurer = rememberTextMeasurer()

    // Choisit la ligne (heure ou tranche) la plus proche de l'heure touchée
    fun slotIndexAt(xFraction: Float): Int {
        val hourAt = xFraction.coerceIn(0f, 1f) * 23f
        return chartSlots.indices.minByOrNull { abs(chartSlots[it].centerHour - hourAt) } ?: 0
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
            LegendDot(color = CyanPrecip, label = if (isHourly) "Pluie mm" else "Pluie mm / ${slotHours} h")
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
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    WeatherConditionIcon(
                        weatherCode = selectedSlot.weatherCode,
                        isDay = selectedSlot.isDay,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "${slotTitle(selectedSlot)} · ${wmoCodeToLabelFr(selectedSlot.weatherCode, selectedSlot.isDay)}",
                        style = MaterialTheme.typography.labelLarge,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                // Chaque valeur dans la couleur de sa courbe
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "T° ${tempRangeLabel(selectedSlot.tempMinC, selectedSlot.tempMaxC, isHourly)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = AmberTemp
                    )
                    Text(
                        text = " · ",
                        style = MaterialTheme.typography.labelMedium,
                        color = TextMuted
                    )
                    Text(
                        text = "Ressenti ${tempRangeLabel(selectedSlot.apparentMinC, selectedSlot.apparentMaxC, isHourly)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = GoldApparentTemp
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isHourly) {
                        String.format(Locale.FRANCE, "Pluie : %.1f mm/h", selectedSlot.precipitationMm)
                    } else {
                        String.format(Locale.FRANCE, "Pluie : %.1f mm / %d h", selectedSlot.precipitationMm, slotHours)
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = CyanPrecip
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = String.format(
                            Locale.FRANCE,
                            if (isHourly) "Vent %.0f km/h" else "Vent max %.0f km/h",
                            selectedSlot.windSpeedKmh
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = VioletWind
                    )
                    Text(
                        text = " · ",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted
                    )
                    Text(
                        text = "Raf. ${selectedSlot.windGustsKmh?.let { String.format(Locale.FRANCE, "%.0f", it) } ?: "—"} km/h",
                        style = MaterialTheme.typography.labelSmall,
                        color = MagentaGusts
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Frise d'aperçu rapide des icônes météo : toutes les 4 heures, ou une par tranche
        val frieze: List<Triple<Int?, Boolean, String>> = if (isHourly) {
            listOf(0, 4, 8, 12, 16, 20, 23).mapNotNull { h ->
                chartSlots.firstOrNull { it.startHour == h }?.let { Triple(it.weatherCode, it.isDay, it.label) }
            }
        } else {
            chartSlots.map { Triple(it.weatherCode, it.isDay, if (slotHours >= 6) it.label else "${it.startHour}h") }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            frieze.forEach { (code, isDay, label) ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    WeatherConditionIcon(
                        weatherCode = code,
                        isDay = isDay,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted,
                        maxLines = 1
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
                .pointerInput(chartSlots) {
                    detectTapGestures { offset ->
                        selectedIdx = slotIndexAt(offset.x / size.width.coerceAtLeast(1))
                    }
                }
                .pointerInput(chartSlots) {
                    detectHorizontalDragGestures { change, _ ->
                        change.consume()
                        selectedIdx = slotIndexAt(change.position.x / size.width.coerceAtLeast(1))
                    }
                }
                .padding(horizontal = 8.dp, vertical = 8.dp)
                .testTag("chart_hourly_24h")
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val chartH = size.height - 18.dp.toPx()

                // Position horizontale selon l'heure de la journée (0 h à gauche, 23 h à droite)
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

                // Tranche sélectionnée : bande en surbrillance sur toute sa durée
                if (!isHourly) {
                    val bandLeft = xForHour(selectedSlot.startHour - 0.5f)
                    val bandRight = xForHour(selectedSlot.endHourExclusive - 0.5f)
                    drawRect(
                        color = TextPrimary.copy(alpha = 0.08f),
                        topLeft = Offset(bandLeft, 0f),
                        size = Size((bandRight - bandLeft).coerceAtLeast(1f), chartH)
                    )
                }

                // Lignes de repère + échelles : °C à gauche (températures), km/h à droite (vent)
                val axisStyle = TextStyle(fontSize = 9.sp)
                for (frac in listOf(0.2f, 0.5f, 0.8f)) {
                    val y = chartH * frac
                    drawLine(
                        color = SlateBorder.copy(alpha = 0.55f),
                        start = Offset(0f, y),
                        end = Offset(w, y),
                        strokeWidth = 1f
                    )
                    val norm = ((1f - frac) - 0.08f) / 0.84f
                    val tempAtLine = minTemp + norm * (maxTemp - minTemp)
                    val windAtLine = norm * maxWind
                    val tempLabel = textMeasurer.measure(
                        String.format(Locale.FRANCE, "%.0f°", tempAtLine),
                        axisStyle.copy(color = AmberTemp)
                    )
                    drawText(tempLabel, topLeft = Offset(2f, y - tempLabel.size.height))
                    val windLabel = textMeasurer.measure(
                        String.format(Locale.FRANCE, "%.0f km/h", windAtLine),
                        axisStyle.copy(color = VioletWind)
                    )
                    drawText(windLabel, topLeft = Offset(w - windLabel.size.width - 2f, y - windLabel.size.height))
                }

                // Barres de pluie : une par heure, ou une large barre par tranche (cumul de la tranche)
                val hourW = w / 24f
                chartSlots.forEach { slot ->
                    if (slot.precipitationMm > 0.0) {
                        val pRatio = (slot.precipitationMm / maxPrecip).toFloat().coerceIn(0f, 1f)
                        val barH = (pRatio * chartH * 0.45f).coerceAtLeast(4f)
                        val left: Float
                        val barW: Float
                        if (isHourly) {
                            barW = (hourW * 0.55f).coerceAtLeast(4f)
                            left = xForHour(slot.startHour.toFloat()) - barW / 2f
                        } else {
                            val inset = 2.dp.toPx()
                            val l = xForHour(slot.startHour - 0.5f) + inset
                            val r = xForHour(slot.endHourExclusive - 0.5f) - inset
                            left = l
                            barW = (r - l).coerceAtLeast(4f)
                        }
                        drawRoundRect(
                            color = DeepBluePrecip.copy(alpha = 0.75f),
                            topLeft = Offset(left, chartH - barH),
                            size = Size(barW, barH),
                            cornerRadius = CornerRadius(3f, 3f)
                        )
                    }
                }

                // Une valeur absente (null) coupe la courbe au lieu d'être inventée
                fun buildSeriesPath(selector: (HourlyWeatherPoint) -> Double?, minVal: Double, maxVal: Double): Path {
                    val path = Path()
                    var penDown = false
                    var previousHour = -2
                    hours.forEach { pt ->
                        val value = selector(pt)
                        // Heure manquante dans les données : on lève le crayon
                        if (value == null || pt.hourOfDay != previousHour + 1) penDown = false
                        previousHour = pt.hourOfDay
                        if (value != null) {
                            val x = xForHour(pt.hourOfDay.toFloat())
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

                // Pas horaire : curseur + un point sur chaque courbe à l'heure choisie
                if (isHourly) {
                    val selX = xForHour(selectedSlot.startHour.toFloat())
                    drawLine(
                        color = TextPrimary.copy(alpha = 0.7f),
                        start = Offset(selX, 0f),
                        end = Offset(selX, chartH),
                        strokeWidth = 1.5.dp.toPx()
                    )
                    fun yFor(value: Double, minVal: Double, maxVal: Double): Float {
                        val norm = ((value - minVal) / (maxVal - minVal)).toFloat().coerceIn(0f, 1f)
                        return chartH * (1f - (0.08f + norm * 0.84f))
                    }
                    fun marker(color: Color, y: Float, radiusDp: Float) {
                        drawCircle(color = SlateSurfaceElevated, radius = (radiusDp + 1.5f).dp.toPx(), center = Offset(selX, y))
                        drawCircle(color = color, radius = radiusDp.dp.toPx(), center = Offset(selX, y))
                    }
                    marker(VioletWind, yFor(selectedSlot.windSpeedKmh, 0.0, maxWind), 4f)
                    selectedSlot.windGustsKmh?.let { marker(MagentaGusts, yFor(it, 0.0, maxWind), 4f) }
                    marker(GoldApparentTemp, yFor(selectedSlot.apparentMaxC, minTemp, maxTemp), 4.5f)
                    marker(AmberTemp, yFor(selectedSlot.tempMaxC, minTemp, maxTemp), 5f)
                }
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

/** Titre d'une ligne : « 14h00 », « 12h–15h » ou « Matin 6–12h ». */
fun slotTitle(slot: DaySlot): String = when {
    slot.endHourExclusive - slot.startHour <= 1 -> "${slot.startHour}h00"
    slot.label.firstOrNull()?.isDigit() == true -> slot.label
    else -> "${slot.label} ${slot.startHour}–${slot.endHourExclusive}h"
}

/** « 12,3° » en pas horaire ; « 10–13° » (ou « 12° » si min = max à l'arrondi) pour une tranche. */
fun tempRangeLabel(minC: Double, maxC: Double, precise: Boolean): String {
    if (precise) return String.format(Locale.FRANCE, "%.1f°", maxC)
    val lo = minC.roundToInt()
    val hi = maxC.roundToInt()
    return if (lo == hi) "$lo°" else "$lo–$hi°"
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
