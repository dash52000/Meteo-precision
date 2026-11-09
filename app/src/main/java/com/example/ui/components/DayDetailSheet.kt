package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.domain.DayDetailEngine
import com.example.domain.RunningScoreEngine
import com.example.model.ConfidenceLevel
import com.example.model.DailyWeatherPoint
import com.example.model.DayConfidence
import com.example.model.DayDetail
import com.example.model.DaySlot
import com.example.model.ForecastStep
import com.example.model.HourlyWeatherPoint
import com.example.ui.theme.AmberTemp
import com.example.ui.theme.CyanPrecip
import com.example.ui.theme.GoldApparentTemp
import com.example.ui.theme.MagentaGusts
import com.example.ui.theme.ScoreGreen
import com.example.ui.theme.ScoreOrange
import com.example.ui.theme.ScoreOrangeBg
import com.example.ui.theme.ScoreRed
import com.example.ui.theme.SlateBorder
import com.example.ui.theme.SlateSurface
import com.example.ui.theme.SlateSurfaceBright
import com.example.ui.theme.SlateSurfaceElevated
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.VioletWind
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.Locale

/** Couleur du point de confiance (accord ECMWF / GFS). */
fun confidenceColor(level: ConfidenceLevel): Color = when (level) {
    ConfidenceLevel.GOOD -> ScoreGreen
    ConfidenceLevel.MEDIUM -> ScoreOrange
    ConfidenceLevel.LOW -> ScoreRed
}

/** Pastille de confiance ; espace vide si l'indice est indisponible (aucune valeur inventée). */
@Composable
fun ConfidenceDot(confidence: DayConfidence?, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(8.dp)
            .clip(CircleShape)
            .background(confidence?.let { confidenceColor(it.level) } ?: Color.Transparent)
    )
}

/**
 * Flèche du vent : la direction météo indique d'où il vient, la flèche montre où il souffle (deg + 180°),
 * comme dans l'encart Vélo.
 */
@Composable
fun WindDirectionArrow(directionDeg: Int?, modifier: Modifier = Modifier, color: Color = VioletWind) {
    if (directionDeg == null) {
        Text(text = "—", style = MaterialTheme.typography.labelSmall, color = TextMuted, modifier = modifier)
        return
    }
    Canvas(modifier = modifier) {
        val flowDeg = (directionDeg + 180f) % 360f
        rotate(degrees = flowDeg) {
            val cx = size.width / 2f
            drawLine(
                color = color,
                start = Offset(cx, size.height * 0.88f),
                end = Offset(cx, size.height * 0.25f),
                strokeWidth = 1.6.dp.toPx()
            )
            val head = Path().apply {
                moveTo(cx, size.height * 0.08f)
                lineTo(cx - size.width * 0.28f, size.height * 0.40f)
                lineTo(cx + size.width * 0.28f, size.height * 0.40f)
                close()
            }
            drawPath(head, color = color)
        }
    }
}

private fun orDash(value: Double?, pattern: String): String =
    value?.let { String.format(Locale.FRANCE, pattern, it) } ?: "—"

/**
 * Panneau du bas : détail d'une journée (graphe + tableau) avec température, ressenti, pluie,
 * vent, rafales et direction, au pas réellement disponible (1 h, 3 h ou tranches de 6 h).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DayDetailBottomSheet(
    detail: DayDetail,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = SlateSurface,
        contentColor = TextPrimary
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp)
                .padding(bottom = 28.dp)
                .testTag("day_detail_sheet"),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            DayDetailHeader(detail)
            StepAndModelsCard(detail)
            ConfidenceCard(detail.day)
            if (detail.day.leadDays >= 7) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(ScoreOrangeBg)
                        .border(1.dp, ScoreOrange.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = "Au-delà de 7 jours, l'audit de fiabilité ne mesure plus les modèles : à lire comme une tendance, pas comme une prévision précise.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextPrimary
                    )
                }
            }
            if (detail.hours.isEmpty()) {
                Text(
                    text = "Aucune donnée heure par heure disponible pour ce jour.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
            } else {
                Hourly24hMultiCurveChart(
                    hours = detail.hours,
                    sunriseFraction = detail.sunriseHourFraction,
                    sunsetFraction = detail.sunsetHourFraction,
                    sunriseLabel = detail.day.sunriseLabel,
                    sunsetLabel = detail.day.sunsetLabel,
                    daylightDurationLabel = detail.day.daylightDurationHoursLabel,
                    slots = detail.slots
                )
                DaySlotsTable(detail)
            }
        }
    }
}

@Composable
private fun DayDetailHeader(detail: DayDetail) {
    val day = detail.day
    val leadLabel = if (day.leadDays == 0) "Aujourd'hui" else "J+${day.leadDays}"
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        WeatherConditionIcon(
            weatherCode = day.weatherCode,
            isDay = true,
            modifier = Modifier.size(36.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = day.dayFullLabel,
                style = MaterialTheme.typography.titleLarge,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "$leadLabel · ${wmoCodeToLabelFr(day.weatherCode, true)}",
                style = MaterialTheme.typography.labelMedium,
                color = GoldApparentTemp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(SlateSurfaceElevated)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = String.format(Locale.FRANCE, "%.0f° → %.0f°", day.tempMinC, day.tempMaxC),
            style = MaterialTheme.typography.titleMedium,
            color = AmberTemp
        )
        Text(
            text = String.format(Locale.FRANCE, "Pluie %.1f mm", day.precipitationSumMm),
            style = MaterialTheme.typography.labelLarge,
            color = CyanPrecip
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Vent ${orDash(day.windSpeedMaxKmh, "%.0f")} · Raf. ${orDash(day.windGustsMaxKmh, "%.0f")}",
                style = MaterialTheme.typography.labelMedium,
                color = MagentaGusts
            )
            Spacer(modifier = Modifier.width(4.dp))
            WindDirectionArrow(day.dominantWindDirectionDeg, Modifier.size(16.dp))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StepAndModelsCard(detail: DayDetail) {
    val tempModels = detail.hours.map { it.tempModelUsed.shortBadge }.distinct().joinToString(" + ")
    val precipModels = detail.hours.map { it.precipModelUsed.shortBadge }.distinct().joinToString(" + ")
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(SlateSurfaceElevated)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Chip(text = detail.step.labelFr, color = CyanPrecip)
            if (tempModels.isNotBlank()) Chip(text = "T° $tempModels", color = AmberTemp)
            if (precipModels.isNotBlank()) Chip(text = "Pluie $precipModels", color = CyanPrecip)
        }
        Text(
            text = detail.step.detailFr,
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary
        )
    }
}

@Composable
private fun Chip(text: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(SlateSurfaceBright)
            .border(1.dp, color.copy(alpha = 0.55f), RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(text = text, style = MaterialTheme.typography.labelSmall, color = color, maxLines = 1)
    }
}

@Composable
private fun ConfidenceCard(day: DailyWeatherPoint) {
    val c = day.confidence
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(SlateSurfaceElevated)
            .border(1.dp, (c?.let { confidenceColor(it.level) } ?: SlateBorder).copy(alpha = 0.5f), RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp)
            .testTag("day_confidence_card"),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ConfidenceDot(c)
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "Confiance (accord ECMWF / GFS) : ${c?.level?.labelFr ?: "indisponible"}",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = c?.let { confidenceColor(it.level) } ?: TextSecondary
            )
        }
        if (c != null) {
            Text(
                text = String.format(
                    Locale.FRANCE,
                    "ECMWF %.0f° → %.0f° · %.1f mm   |   GFS %.0f° → %.0f° · %.1f mm",
                    c.ecmwfTempMinC, c.ecmwfTempMaxC, c.ecmwfPrecipMm,
                    c.gfsTempMinC, c.gfsTempMaxC, c.gfsPrecipMm
                ),
                style = MaterialTheme.typography.labelSmall,
                color = TextPrimary
            )
            Text(
                text = String.format(
                    Locale.FRANCE,
                    "Écart T° jusqu'à %.1f °C · écart pluie %.1f mm. Accord entre modèles, pas une fiabilité mesurée.",
                    c.maxTempGapC, c.precipGapMm
                ),
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary
            )
        } else {
            Text(
                text = "ECMWF ou GFS ne couvre pas ce jour : pas d'indice calculé.",
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary
            )
        }
    }
}

/** Tableau : une ligne par heure ou par tranche, avec T°, ressenti, pluie, vent, rafales et direction. */
@Composable
private fun DaySlotsTable(detail: DayDetail) {
    val precise = detail.step == ForecastStep.HOURLY
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .border(1.dp, SlateBorder, RoundedCornerShape(10.dp))
            .testTag("day_detail_table")
    ) {
        SlotRowLayout(
            background = SlateSurfaceBright,
            label = { Text(if (precise) "Heure" else "Tranche", style = MaterialTheme.typography.labelSmall, color = TextSecondary) },
            icon = { },
            temp = { HeaderCell("T°", AmberTemp) },
            apparent = { HeaderCell("Ress.", GoldApparentTemp) },
            precip = { HeaderCell("mm", CyanPrecip) },
            wind = { HeaderCell("Vent", VioletWind) },
            gusts = { HeaderCell("Raf.", MagentaGusts) },
            direction = { HeaderCell("Dir.", TextSecondary) }
        )
        detail.slots.forEachIndexed { idx, slot ->
            SlotRow(slot = slot, zebra = idx % 2 == 1)
        }
    }
    Text(
        text = if (precise) {
            "Vent et rafales en km/h · pluie en mm sur l'heure · la flèche montre où souffle le vent."
        } else {
            "Valeurs min–max sur la tranche · vent et rafales max en km/h · pluie cumulée sur la tranche · la flèche montre où souffle le vent (direction dominante)."
        },
        style = MaterialTheme.typography.labelSmall,
        color = TextMuted
    )
}

@Composable
private fun HeaderCell(text: String, color: Color) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = color,
        textAlign = TextAlign.End,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun SlotRow(slot: DaySlot, zebra: Boolean) {
    val isSixHours = slot.endHourExclusive - slot.startHour >= 6
    SlotRowLayout(
        background = if (zebra) SlateSurfaceElevated else SlateSurface,
        label = {
            Column {
                Text(
                    text = slot.label,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (slot.isDay) TextPrimary else TextSecondary,
                    maxLines = 1
                )
                if (isSixHours) {
                    Text(
                        text = "${slot.startHour}–${slot.endHourExclusive}h",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted,
                        maxLines = 1
                    )
                }
            }
        },
        icon = {
            WeatherConditionIcon(weatherCode = slot.weatherCode, isDay = slot.isDay, modifier = Modifier.size(18.dp))
        },
        temp = { ValueCell(tempRangeLabel(slot.tempMinC, slot.tempMaxC, false), AmberTemp) },
        apparent = { ValueCell(tempRangeLabel(slot.apparentMinC, slot.apparentMaxC, false), GoldApparentTemp) },
        precip = {
            ValueCell(
                String.format(Locale.FRANCE, "%.1f", slot.precipitationMm),
                if (slot.precipitationMm >= 0.1) CyanPrecip else TextMuted
            )
        },
        wind = { ValueCell(String.format(Locale.FRANCE, "%.0f", slot.windSpeedKmh), VioletWind) },
        gusts = { ValueCell(orDash(slot.windGustsKmh, "%.0f"), MagentaGusts) },
        direction = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                WindDirectionArrow(slot.windDirectionDeg, Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(2.dp))
                Text(
                    text = slot.windDirectionDeg?.let { RunningScoreEngine.degreeToCardinalFr(it) } ?: "",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary,
                    maxLines = 1
                )
            }
        }
    )
}

@Composable
private fun ValueCell(text: String, color: Color) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = color,
        textAlign = TextAlign.End,
        maxLines = 1,
        modifier = Modifier.fillMaxWidth()
    )
}

/** Grille commune à l'en-tête et aux lignes du tableau (largeurs relatives, tient sur 360 dp). */
@Composable
private fun SlotRowLayout(
    background: Color,
    label: @Composable () -> Unit,
    icon: @Composable () -> Unit,
    temp: @Composable () -> Unit,
    apparent: @Composable () -> Unit,
    precip: @Composable () -> Unit,
    wind: @Composable () -> Unit,
    gusts: @Composable () -> Unit,
    direction: @Composable () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(background)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.weight(1.45f)) { label() }
        Box(modifier = Modifier.width(20.dp), contentAlignment = Alignment.Center) { icon() }
        Box(modifier = Modifier.weight(1.05f)) { temp() }
        Box(modifier = Modifier.weight(1.05f)) { apparent() }
        Box(modifier = Modifier.weight(0.8f)) { precip() }
        Box(modifier = Modifier.weight(0.7f)) { wind() }
        Box(modifier = Modifier.weight(0.7f)) { gusts() }
        Box(modifier = Modifier.weight(0.95f)) { direction() }
    }
}

/**
 * Calendrier : choisir un jour parmi ceux couverts par les prévisions (aujourd'hui à J+15).
 * Le DatePicker travaille en millisecondes UTC à minuit.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ForecastDatePickerDialog(
    availableDatesIso: List<String>,
    onDismiss: () -> Unit,
    onDateSelected: (String) -> Unit
) {
    val dates = availableDatesIso.mapNotNull { runCatching { LocalDate.parse(it) }.getOrNull() }.sorted()
    // Le tableau de bord n'ouvre ce dialogue que s'il y a des jours disponibles
    if (dates.isEmpty()) return
    val available = dates.toSet()
    val first = dates.first()
    val last = dates.last()
    fun toUtcMillis(d: LocalDate): Long = d.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    fun fromUtcMillis(ms: Long): LocalDate = Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate()

    val state = rememberDatePickerState(
        initialSelectedDateMillis = null,
        initialDisplayedMonthMillis = toUtcMillis(first),
        yearRange = first.year..last.year,
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean = fromUtcMillis(utcTimeMillis) in available
            override fun isSelectableYear(year: Int): Boolean = year in first.year..last.year
        }
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                enabled = state.selectedDateMillis != null,
                onClick = {
                    state.selectedDateMillis?.let { onDateSelected(fromUtcMillis(it).toString()) }
                }
            ) {
                Text("Voir le détail")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Annuler")
            }
        }
    ) {
        DatePicker(
            state = state,
            showModeToggle = false,
            title = {
                Text(
                    text = String.format(
                        Locale.FRANCE,
                        "Choisir un jour (%d/%d → %d/%d)",
                        first.dayOfMonth, first.monthValue, last.dayOfMonth, last.monthValue
                    ),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(start = 24.dp, end = 12.dp, top = 16.dp)
                )
            },
            modifier = Modifier.testTag("forecast_date_picker")
        )
    }
}

/** Raccourci : détail d'un jour du tableau de bord, calculé à l'instant présent. */
fun buildDayDetailNow(day: DailyWeatherPoint, hourlyByDate: Map<String, List<HourlyWeatherPoint>>): DayDetail =
    DayDetailEngine.buildDayDetail(day, hourlyByDate[day.dateIso].orEmpty(), LocalDateTime.now())
