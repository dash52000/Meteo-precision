package com.example.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddLocationAlt
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.DirectionsBike
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.Grass
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Terrain
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.AirQualitySummary
import com.example.model.BikeWindStatus
import com.example.model.CompleteWeatherDashboard
import com.example.model.DailyWeatherPoint
import com.example.model.HourlyRunningScore
import com.example.model.LocationType
import com.example.model.MudState
import com.example.model.ScoreLevel
import com.example.model.TrailConditionStatus
import com.example.model.WeatherModel
import com.example.model.WorkCommuteStatus
import com.example.ui.components.Hourly24hMultiCurveChart
import com.example.ui.components.Precipitation3HoursChart
import com.example.ui.components.WeatherConditionIcon
import com.example.ui.components.wmoCodeToLabelFr
import com.example.ui.theme.AmberTemp
import com.example.ui.theme.CyanPrecip
import com.example.ui.theme.GoldApparentTemp
import com.example.ui.theme.MagentaGusts
import com.example.ui.theme.ScoreGreen
import com.example.ui.theme.ScoreGreenBg
import com.example.ui.theme.ScoreOrange
import com.example.ui.theme.ScoreOrangeBg
import com.example.ui.theme.ScoreRed
import com.example.ui.theme.ScoreRedBg
import com.example.ui.theme.SlateBackground
import com.example.ui.theme.SlateBorder
import com.example.ui.theme.SlateSurface
import com.example.ui.theme.SlateSurfaceBright
import com.example.ui.theme.SlateSurfaceElevated
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.VioletWind
import java.util.Locale

@Composable
fun DashboardScreen(
    dashboard: CompleteWeatherDashboard?,
    isLoading: Boolean,
    errorMessage: String?,
    statusBanner: String?,
    onRefresh: () -> Unit,
    onSelectModelOverride: (WeatherModel?) -> Unit,
    onOpenLocations: () -> Unit,
    onOpenReliability: () -> Unit,
    onQuickSaveCurrentCity: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showModelPickerDialog by remember { mutableStateOf(false) }
    var inspectedRunningScore by remember { mutableStateOf<HourlyRunningScore?>(null) }

    if (showModelPickerDialog && dashboard != null) {
        ModelOverrideDialog(
            dashboard = dashboard,
            onDismiss = { showModelPickerDialog = false },
            onSelectModel = { model ->
                showModelPickerDialog = false
                onSelectModelOverride(model)
            }
        )
    }

    if (inspectedRunningScore != null) {
        val score = inspectedRunningScore!!
        AlertDialog(
            onDismissRequest = { inspectedRunningScore = null },
            title = {
                Text(
                    text = "Détail Score Course · ${score.timeLabel}",
                    style = MaterialTheme.typography.titleLarge
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Statut global : ${score.overallLevel.labelFr.uppercase()} (le pire critère l'emporte)",
                        style = MaterialTheme.typography.labelLarge,
                        color = score.overallLevel.toColor()
                    )
                    Text(
                        text = String.format(
                            Locale.FRANCE,
                            "• Précipitations : %.1f mm/h (%s)\n• Ressenti : %.1f °C / Réel %.1f °C (%s)\n• Rafales : %.0f km/h (%s)\n• Lumière : %s",
                            score.precipMm,
                            score.precipLevel.labelFr,
                            score.apparentTempC,
                            score.temperatureC,
                            score.apparentTempLevel.labelFr,
                            score.gustsKmh,
                            score.gustsLevel.labelFr,
                            score.lightLevel.labelFr
                        ),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "Facteurs : ${score.limitingReasons.joinToString(", ")}",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { inspectedRunningScore = null }) {
                    Text("Fermer")
                }
            }
        )
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(SlateBackground)
            .testTag("dashboard_lazy_column"),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (!statusBanner.isNullOrBlank()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(SlateSurfaceBright)
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = statusBanner,
                        style = MaterialTheme.typography.bodySmall,
                        color = GoldApparentTemp
                    )
                }
            }
        }

        if (dashboard == null && isLoading) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(320.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = CyanPrecip)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Chargement des flux Open-Meteo…",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                    }
                }
            }
        } else if (dashboard == null && errorMessage != null) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = ScoreRedBg),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = errorMessage,
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(onClick = onRefresh, modifier = Modifier.testTag("retry_weather_button")) {
                            Text(stringResource(R.string.retry_action))
                        }
                    }
                }
            }
        } else if (dashboard != null) {
            // En-tête de télémétrie + Sélecteur de modèle
            item {
                TelemetryHeaderCard(
                    dashboard = dashboard,
                    onOpenLocations = onOpenLocations,
                    onOpenModelPicker = { showModelPickerDialog = true },
                    onRefresh = onRefresh,
                    onQuickSaveCity = onQuickSaveCurrentCity
                )
            }

            // Bandeau : Indice UV, Qualité de l'air, Pollens
            item {
                UvAirPollenBanner(
                    airQuality = dashboard.airQuality,
                    todayUvMax = dashboard.daily7Days.firstOrNull()?.uvIndexMax ?: 0.0
                )
            }

            // Encart spécifique au type de lieu (Boulot, Spot de trail, Vélo)
            if (dashboard.workCommuteStatus != null) {
                item {
                    WorkCommuteTelemetryCard(status = dashboard.workCommuteStatus)
                }
            }
            if (dashboard.trailConditionStatus != null) {
                item {
                    TrailMudTelemetryCard(status = dashboard.trailConditionStatus)
                }
            }
            if (dashboard.bikeWindStatus != null) {
                item {
                    BikeWindTelemetryCard(status = dashboard.bikeWindStatus)
                }
            }

            // 1. Les 3 prochaines heures : graphe des précipitations au quart d'heure
            item {
                SectionContainerCard(
                    title = "1 · Les 3 prochaines heures (Pluie 15 min)",
                    modelTag = dashboard.minutely15ModelUsed.shortBadge,
                    onModelTagClick = { showModelPickerDialog = true }
                ) {
                    Precipitation3HoursChart(points = dashboard.minutely15Next3h)
                }
            }

            // 2. La journée heure par heure : courbes superposées + nuits grisées
            item {
                val sampleHour = dashboard.hourlyToday24h.firstOrNull()
                val shortHorizonBadge = if (dashboard.isAutoModelActive && sampleHour != null) {
                    "T° ${sampleHour.tempModelUsed.shortBadge} · Pluie ${sampleHour.precipModelUsed.shortBadge}"
                } else {
                    dashboard.manualModel?.shortBadge ?: "Auto"
                }
                SectionContainerCard(
                    title = "2 · Journée heure par heure (24 h)",
                    modelTag = shortHorizonBadge,
                    onModelTagClick = { showModelPickerDialog = true }
                ) {
                    Hourly24hMultiCurveChart(
                        hours = dashboard.hourlyToday24h,
                        sunriseFraction = dashboard.todaySunriseHourFraction,
                        sunsetFraction = dashboard.todaySunsetHourFraction,
                        sunriseLabel = dashboard.todaySunriseLabel,
                        sunsetLabel = dashboard.todaySunsetLabel,
                        daylightDurationLabel = dashboard.todayDaylightDurationLabel
                    )
                }
            }

            // 3. Bande « Score course » (6 h – 21 h) + Top 3 créneaux du jour
            item {
                SectionContainerCard(
                    title = "3 · Score Course (6 h – 21 h) & Top 3 Créneaux",
                    modelTag = "Règles Fixes",
                    onModelTagClick = { showModelPickerDialog = true }
                ) {
                    RunningScoreSection(
                        scores = dashboard.runningScores6to21h,
                        top3Slots = dashboard.top3RunningSlots,
                        onInspectHour = { inspectedRunningScore = it }
                    )
                }
            }

            // 4. Bloc « Week-end » mis en avant (Samedi et Dimanche)
            if (dashboard.weekendDays.isNotEmpty()) {
                item {
                    val wkSample = dashboard.weekendDays.first()
                    val wkModelTag = if (dashboard.isAutoModelActive) {
                        "T° ${wkSample.tempModelUsed.shortBadge} · Pluie ${wkSample.precipModelUsed.shortBadge}"
                    } else {
                        dashboard.manualModel?.shortBadge ?: wkSample.tempModelUsed.shortBadge
                    }
                    SectionContainerCard(
                        title = "4 · Focus Week-end (Samedi & Dimanche)",
                        modelTag = wkModelTag,
                        onModelTagClick = { showModelPickerDialog = true }
                    ) {
                        WeekendHighlightBlock(weekendDays = dashboard.weekendDays)
                    }
                }
            }

            // 5. Les 7 jours en barres min/max avec le cumul de pluie
            item {
                val dSample = dashboard.daily7Days.lastOrNull()
                val medModelTag = if (dashboard.isAutoModelActive && dSample != null) {
                    "0-48h ${dashboard.autoRecommendation.tempModel0to48h.shortBadge} | >48h ${dSample.tempModelUsed.shortBadge}"
                } else {
                    dashboard.manualModel?.shortBadge ?: "Auto"
                }
                SectionContainerCard(
                    title = "5 · Horizon 7 Jours (Barres Min/Max & Cumul Pluie)",
                    modelTag = medModelTag,
                    onModelTagClick = { showModelPickerDialog = true }
                ) {
                    Daily7DaysBarsSection(days = dashboard.daily7Days)
                }
            }

            // Pied de page : attribution Open-Meteo + raccourci audit fiabilité
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    OutlinedButton(
                        onClick = onOpenReliability,
                        modifier = Modifier.testTag("open_reliability_from_dashboard")
                    ) {
                        Text("Consulter l'audit de fiabilité 30 jours pour ${dashboard.location.name}")
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.open_meteo_attribution),
                        style = MaterialTheme.typography.labelMedium,
                        color = TextMuted,
                        modifier = Modifier.testTag("open_meteo_attribution_text")
                    )
                }
            }
        }
    }
}

@Composable
private fun TelemetryHeaderCard(
    dashboard: CompleteWeatherDashboard,
    onOpenLocations: () -> Unit,
    onOpenModelPicker: () -> Unit,
    onRefresh: () -> Unit,
    onQuickSaveCity: () -> Unit
) {
    val currentHourPt = dashboard.hourlyToday24h.getOrNull(
        java.time.LocalTime.now().hour.coerceIn(0, (dashboard.hourlyToday24h.size - 1).coerceAtLeast(0))
    ) ?: dashboard.hourlyToday24h.firstOrNull()
    val todayDaily = dashboard.daily7Days.firstOrNull()

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = SlateSurface),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, SlateBorder, RoundedCornerShape(14.dp))
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Image(
                painter = painterResource(id = R.drawable.img_telemetry_header_1792668266222),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(175.dp),
                alpha = 0.28f
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(175.dp)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(SlateBackground.copy(alpha = 0.3f), SlateSurface)
                        )
                    )
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(onClick = onOpenLocations)
                            .padding(vertical = 4.dp, horizontal = 4.dp)
                            .testTag("header_location_selector")
                    ) {
                        Icon(
                            imageVector = when {
                                dashboard.location.isCurrentGps -> Icons.Default.MyLocation
                                dashboard.location.type == LocationType.HOME -> Icons.Default.Home
                                dashboard.location.type == LocationType.WORK -> Icons.Default.Work
                                dashboard.location.type == LocationType.TRAIL -> Icons.Default.Terrain
                                dashboard.location.type == LocationType.BIKE -> Icons.Default.DirectionsBike
                                else -> Icons.Default.Place
                            },
                            contentDescription = "Type de lieu",
                            tint = CyanPrecip,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = dashboard.location.name,
                                    style = MaterialTheme.typography.headlineMedium,
                                    color = TextPrimary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(SlateSurfaceBright)
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = if (dashboard.location.isCurrentGps) "GPS" else dashboard.location.type.labelFr,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = CyanPrecip
                                    )
                                }
                            }
                            Text(
                                text = "${dashboard.location.adminRegion} · Maj ${dashboard.fetchedAtLabel}",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (!dashboard.location.isSaved && !dashboard.location.isCurrentGps) {
                            IconButton(
                                onClick = onQuickSaveCity,
                                modifier = Modifier.testTag("quick_save_city_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AddLocationAlt,
                                    contentDescription = "Enregistrer cette ville",
                                    tint = ScoreGreen
                                )
                            }
                        }
                        IconButton(
                            onClick = onRefresh,
                            modifier = Modifier.testTag("refresh_weather_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Actualiser la météo",
                                tint = TextPrimary
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Chiffres clés instantanés + Icône météo actuelle (Soleil, Soleil nuageux, Brouillard, Pluie...)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        WeatherConditionIcon(
                            weatherCode = currentHourPt?.weatherCode ?: 1,
                            isDay = currentHourPt?.isDay ?: true,
                            modifier = Modifier.size(40.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = String.format(Locale.FRANCE, "%.1f°C", currentHourPt?.temperatureC ?: 15.0),
                            style = MaterialTheme.typography.displayMedium,
                            color = AmberTemp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = wmoCodeToLabelFr(
                                    currentHourPt?.weatherCode ?: 1,
                                    currentHourPt?.isDay ?: true
                                ),
                                style = MaterialTheme.typography.labelLarge,
                                color = TextPrimary,
                                maxLines = 1
                            )
                            Text(
                                text = String.format(
                                    Locale.FRANCE,
                                    "Ress. %.1f° · %.0f°/%.0f°",
                                    currentHourPt?.apparentTemperatureC ?: 15.0,
                                    todayDaily?.tempMinC ?: 8.0,
                                    todayDaily?.tempMaxC ?: 18.0
                                ),
                                style = MaterialTheme.typography.labelSmall,
                                color = GoldApparentTemp,
                                maxLines = 1
                            )
                        }
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = String.format(
                                Locale.FRANCE,
                                "Pluie %.1f mm",
                                todayDaily?.precipitationSumMm ?: 0.0
                            ),
                            style = MaterialTheme.typography.labelLarge,
                            color = CyanPrecip
                        )
                        Text(
                            text = String.format(
                                Locale.FRANCE,
                                "Vent %.0f (raf. %.0f)",
                                currentHourPt?.windSpeedKmh ?: 0.0,
                                currentHourPt?.windGustsKmh ?: 0.0
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = VioletWind
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Barre de contrôle du modèle affiché (Auto vs Forçage manuel)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(SlateSurfaceElevated)
                        .border(1.dp, SlateBorder, RoundedCornerShape(8.dp))
                        .clickable(onClick = onOpenModelPicker)
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                        .testTag("model_selector_bar"),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = "Choix du modèle",
                            tint = CyanPrecip,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (dashboard.isAutoModelActive) {
                                "Modèle Auto · ${dashboard.autoRecommendation.summaryText}"
                            } else {
                                "Modèle Forcé · ${dashboard.manualModel?.displayName ?: ""}"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = TextPrimary
                        )
                    }
                    Text(
                        text = "Changer",
                        style = MaterialTheme.typography.labelMedium,
                        color = CyanPrecip
                    )
                }
            }
        }
    }
}

@Composable
private fun UvAirPollenBanner(
    airQuality: AirQualitySummary,
    todayUvMax: Double
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(SlateSurface)
            .border(1.dp, SlateBorder, RoundedCornerShape(10.dp))
            .padding(horizontal = 8.dp, vertical = 8.dp)
            .testTag("banner_uv_air_pollen"),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Indice UV
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .weight(0.95f)
                .clip(RoundedCornerShape(6.dp))
                .background(SlateSurfaceElevated)
                .padding(horizontal = 6.dp, vertical = 5.dp)
        ) {
            Icon(
                imageVector = Icons.Default.WbSunny,
                contentDescription = "Indice UV",
                tint = GoldApparentTemp,
                modifier = Modifier.size(15.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Column {
                Text(text = "UV", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                val uvCurr = airQuality.uvIndexCurrent
                Text(
                    text = if (uvCurr != null) {
                        String.format(Locale.FRANCE, "%.1f (Max %.0f)", uvCurr, todayUvMax)
                    } else {
                        String.format(Locale.FRANCE, "Max %.1f", todayUvMax)
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = TextPrimary,
                    maxLines = 1
                )
            }
        }

        // Qualité de l'air
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .weight(1.05f)
                .clip(RoundedCornerShape(6.dp))
                .background(SlateSurfaceElevated)
                .padding(horizontal = 6.dp, vertical = 5.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Air,
                contentDescription = "Qualité de l'air",
                tint = CyanPrecip,
                modifier = Modifier.size(15.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Column {
                Text(text = "Air (IQA)", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                Text(
                    text = airQuality.aqiLabelFr,
                    style = MaterialTheme.typography.labelSmall,
                    color = TextPrimary,
                    maxLines = 1
                )
            }
        }

        // Pollens
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .weight(1.25f)
                .clip(RoundedCornerShape(6.dp))
                .background(SlateSurfaceElevated)
                .padding(horizontal = 6.dp, vertical = 5.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Grass,
                contentDescription = "Pollens",
                tint = ScoreGreen,
                modifier = Modifier.size(15.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Column {
                Text(text = "Pollens", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                Text(
                    text = airQuality.dominantPollenSummary,
                    style = MaterialTheme.typography.labelSmall,
                    color = TextPrimary,
                    maxLines = 1
                )
            }
        }
    }
}

/**
 * Encart spécifique Boulot ultra-compact : Aller & Retour sur 2 lignes fines (verglas, brouillard, visibilité, T° min).
 */
@Composable
private fun WorkCommuteTelemetryCard(status: WorkCommuteStatus) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(SlateSurface)
            .border(1.dp, SlateBorder, RoundedCornerShape(10.dp))
            .padding(horizontal = 8.dp, vertical = 6.dp)
            .testTag("work_commute_card"),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        listOf(status.morningReport, status.eveningReport).forEach { slice ->
            val hasAlert = slice.hasBlackIceRisk || slice.hasFogRisk
            val shortLabel = slice.label.removePrefix("Trajet ").trim()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (hasAlert) ScoreOrangeBg else SlateSurfaceElevated)
                    .padding(horizontal = 8.dp, vertical = 5.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Work,
                        contentDescription = null,
                        tint = CyanPrecip,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = "$shortLabel ${slice.windowText}",
                        style = MaterialTheme.typography.labelMedium,
                        color = TextPrimary,
                        maxLines = 1
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = String.format(
                            Locale.FRANCE,
                            "%.1f°C · HR %d%% · Vis %.0fkm",
                            slice.minTempC,
                            slice.maxHumidityPercent,
                            (slice.minVisibilityMeters / 1000.0).coerceAtMost(99.0)
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))

                Text(
                    text = when {
                        slice.hasBlackIceRisk && slice.hasFogRisk -> "⚠ Verglas + Brouillard"
                        slice.hasBlackIceRisk -> "⚠ Verglas"
                        slice.hasFogRisk -> "⚠ Brouillard"
                        else -> "✓ Verglas/Vis OK"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = when {
                        slice.hasBlackIceRisk -> ScoreRed
                        slice.hasFogRisk -> ScoreOrange
                        else -> ScoreGreen
                    },
                    maxLines = 1
                )
            }
        }
    }
}

/**
 * Encart spécifique Spot de trail : Cumul de pluie des 48 dernières heures et indicateur « terrain boueux » (sec / gras / boue).
 */
@Composable
private fun TrailMudTelemetryCard(status: TrailConditionStatus) {
    val (badgeColor, badgeBg) = when (status.mudState) {
        MudState.DRY -> ScoreGreen to ScoreGreenBg
        MudState.DAMP -> ScoreOrange to ScoreOrangeBg
        MudState.MUDDY -> ScoreRed to ScoreRedBg
    }

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = SlateSurface),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, SlateBorder, RoundedCornerShape(12.dp))
            .testTag("trail_mud_card")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Terrain, contentDescription = null, tint = badgeColor, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Spot de Trail · État du terrain",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = String.format(
                        Locale.FRANCE,
                        "Cumul pluie 48 dernières heures : %.1f mm (seuils %.0f / %.0f mm)",
                        status.rainLast48hMm,
                        status.orangeThresholdMm,
                        status.redThresholdMm
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = CyanPrecip
                )
                Text(
                    text = status.mudState.detailFr,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(badgeBg)
                    .border(1.dp, badgeColor, RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Text(
                    text = status.mudState.labelFr.uppercase(),
                    style = MaterialTheme.typography.labelLarge,
                    color = badgeColor
                )
            }
        }
    }
}

/**
 * Encart spécifique Vélo : Force et direction du vent (flèche vectorielle), rafales, et sens conseillé de la boucle.
 */
@Composable
private fun BikeWindTelemetryCard(status: BikeWindStatus) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = SlateSurface),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, SlateBorder, RoundedCornerShape(12.dp))
            .testTag("bike_wind_card")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Boussole & Flèche vectorielle orientée selon la direction du vent
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(SlateSurfaceElevated)
                    .border(1.dp, VioletWind, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.size(44.dp)) {
                    // La direction météo indique d'où vient le vent ; la flèche montre le flux (deg + 180°)
                    val flowDeg = (status.windDirectionDeg + 180f) % 360f
                    rotate(degrees = flowDeg) {
                        val cx = size.width / 2f
                        val cy = size.height / 2f
                        drawLine(
                            color = VioletWind,
                            start = Offset(cx, size.height * 0.82f),
                            end = Offset(cx, size.height * 0.18f),
                            strokeWidth = 3.dp.toPx()
                        )
                        val head = Path().apply {
                            moveTo(cx, size.height * 0.12f)
                            lineTo(cx - 6.dp.toPx(), size.height * 0.34f)
                            lineTo(cx + 6.dp.toPx(), size.height * 0.34f)
                            close()
                        }
                        drawPath(head, color = CyanPrecip)
                    }
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.DirectionsBike, contentDescription = null, tint = VioletWind, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Spécial Vélo · Vent ${status.windFromCardinal} (${status.windDirectionDeg}°)",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = String.format(
                        Locale.FRANCE,
                        "Vent moyen : %.0f km/h · Rafales : %.0f km/h",
                        status.windSpeedKmh,
                        status.gustsKmh
                    ),
                    style = MaterialTheme.typography.labelLarge,
                    color = MagentaGusts
                )
                Text(
                    text = status.loopAdviceFr,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
        }
    }
}

/**
 * 3. Section Score Course (bande 6h - 21h avec icône météo + Top 3 créneaux ultra-compacts sur 1 seule ligne).
 */
@Composable
private fun RunningScoreSection(
    scores: List<HourlyRunningScore>,
    top3Slots: List<HourlyRunningScore>,
    onInspectHour: (HourlyRunningScore) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Bande horizontale 6h - 21h avec icône météo et pastille couleur
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .testTag("running_score_strip"),
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            scores.forEach { slot ->
                val color = slot.overallLevel.toColor()
                val bg = slot.overallLevel.toBgColor()
                Column(
                    modifier = Modifier
                        .width(46.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(bg)
                        .border(
                            width = if (slot.isRealisticSlot) 1.5.dp else 1.dp,
                            color = if (slot.isRealisticSlot) color else SlateBorder,
                            shape = RoundedCornerShape(8.dp)
                        )
                        .clickable { onInspectHour(slot) }
                        .padding(vertical = 6.dp, horizontal = 3.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "${slot.hour}h",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    WeatherConditionIcon(
                        weatherCode = slot.weatherCode,
                        isDay = slot.isDay,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Box(
                        modifier = Modifier
                            .size(9.dp)
                            .clip(CircleShape)
                            .background(color)
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = String.format(Locale.FRANCE, "%.0f°", slot.apparentTempC),
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary
                    )
                }
            }
        }

        // Liste ultra-compacte des 3 meilleurs créneaux du jour (1 seule ligne fine par créneau)
        Text(
            text = "Top 3 créneaux du jour :",
            style = MaterialTheme.typography.labelMedium,
            color = TextSecondary
        )

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            top3Slots.forEachIndexed { index, slot ->
                val color = slot.overallLevel.toColor()
                val reasonCompact = slot.limitingReasons.firstOrNull() ?: "Idéal"
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(SlateSurfaceElevated)
                        .border(1.dp, color.copy(alpha = 0.55f), RoundedCornerShape(6.dp))
                        .clickable { onInspectHour(slot) }
                        .padding(horizontal = 8.dp, vertical = 5.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(color)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "#${index + 1} ${slot.hour}h–${slot.hour + 1}h",
                            style = MaterialTheme.typography.labelLarge,
                            color = color,
                            maxLines = 1
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        WeatherConditionIcon(
                            weatherCode = slot.weatherCode,
                            isDay = slot.isDay,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = reasonCompact,
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    Text(
                        text = String.format(
                            Locale.FRANCE,
                            "%.0f°C · %.1fmm · %.0fkm/h",
                            slot.apparentTempC,
                            slot.precipMm,
                            slot.gustsKmh
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = TextPrimary,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

/**
 * 4. Bloc « Week-end » mis en avant (Samedi et Dimanche) avec icône météo.
 */
@Composable
private fun WeekendHighlightBlock(weekendDays: List<DailyWeatherPoint>) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("weekend_highlight_block"),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        weekendDays.forEach { day ->
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(SlateSurfaceElevated)
                    .border(1.dp, CyanPrecip.copy(alpha = 0.45f), RoundedCornerShape(10.dp))
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = day.dayFullLabel,
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    WeatherConditionIcon(
                        weatherCode = day.weatherCode,
                        isDay = true,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Text(
                    text = wmoCodeToLabelFr(day.weatherCode, true),
                    style = MaterialTheme.typography.labelSmall,
                    color = GoldApparentTemp,
                    maxLines = 1
                )
                Text(
                    text = String.format(Locale.FRANCE, "%.1f °C → %.1f °C", day.tempMinC, day.tempMaxC),
                    style = MaterialTheme.typography.labelLarge,
                    color = AmberTemp
                )
                Text(
                    text = String.format(Locale.FRANCE, "Pluie : %.1f mm · Raf %.0f", day.precipitationSumMm, day.windGustsMaxKmh),
                    style = MaterialTheme.typography.labelSmall,
                    color = CyanPrecip,
                    maxLines = 1
                )
            }
        }
    }
}

/**
 * 5. Les 7 jours en barres min/max avec icône météo et cumul de pluie.
 */
@Composable
private fun Daily7DaysBarsSection(days: List<DailyWeatherPoint>) {
    if (days.isEmpty()) return
    val weekMin = remember(days) { days.minOf { it.tempMinC } }
    val weekMax = remember(days) { days.maxOf { it.tempMaxC }.coerceAtLeast(weekMin + 4.0) }
    val span = (weekMax - weekMin).coerceAtLeast(1.0)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("daily_7d_bars"),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        days.forEach { day ->
            val startFrac = ((day.tempMinC - weekMin) / span).toFloat().coerceIn(0f, 1f)
            val endFrac = ((day.tempMaxC - weekMin) / span).toFloat().coerceIn(startFrac + 0.05f, 1f)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (day.isWeekend) SlateSurfaceBright.copy(alpha = 0.55f) else SlateSurfaceElevated)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.width(46.dp)) {
                    Text(
                        text = day.dayNameShort,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (day.isWeekend) GoldApparentTemp else TextPrimary,
                        maxLines = 1
                    )
                    Text(
                        text = day.tempModelUsed.shortBadge,
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Icône météo du jour (soleil, soleil nuageux, nuageux, brouillard, pluie, orage)
                WeatherConditionIcon(
                    weatherCode = day.weatherCode,
                    isDay = true,
                    modifier = Modifier.size(20.dp)
                )

                Spacer(modifier = Modifier.width(6.dp))

                // Cumul de pluie journalier
                Text(
                    text = String.format(Locale.FRANCE, "%.1fmm", day.precipitationSumMm),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (day.precipitationSumMm >= 0.2) CyanPrecip else TextMuted,
                    modifier = Modifier.width(44.dp),
                    maxLines = 1
                )

                // Température Min
                Text(
                    text = String.format(Locale.FRANCE, "%.0f°", day.tempMinC),
                    style = MaterialTheme.typography.labelMedium,
                    color = TextSecondary,
                    modifier = Modifier.width(26.dp),
                    maxLines = 1
                )

                // Barre flottante d'amplitude thermique min/max calibrée S25
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(9.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(SlateBackground)
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val leftX = startFrac * size.width
                        val rightX = endFrac * size.width
                        drawRoundRect(
                            brush = Brush.horizontalGradient(
                                colors = listOf(CyanPrecip, GoldApparentTemp, AmberTemp),
                                startX = leftX,
                                endX = rightX
                            ),
                            topLeft = Offset(leftX, 0f),
                            size = androidx.compose.ui.geometry.Size(
                                (rightX - leftX).coerceAtLeast(8f),
                                size.height
                            ),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(10f, 10f)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(6.dp))

                // Température Max
                Text(
                    text = String.format(Locale.FRANCE, "%.0f°", day.tempMaxC),
                    style = MaterialTheme.typography.labelLarge,
                    color = AmberTemp,
                    modifier = Modifier.width(26.dp),
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun SectionContainerCard(
    title: String,
    modelTag: String,
    onModelTagClick: () -> Unit,
    content: @Composable () -> Unit
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = SlateSurface),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, SlateBorder, RoundedCornerShape(12.dp))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                    modifier = Modifier.weight(1f)
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(SlateSurfaceBright)
                        .border(1.dp, CyanPrecip.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                        .clickable(onClick = onModelTagClick)
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = modelTag,
                        style = MaterialTheme.typography.labelSmall,
                        color = CyanPrecip
                    )
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            content()
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ModelOverrideDialog(
    dashboard: CompleteWeatherDashboard,
    onDismiss: () -> Unit,
    onSelectModel: (WeatherModel?) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Sélection du modèle météo",
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Par défaut, le mode Automatique choisit le meilleur modèle par variable (température / pluie) et par horizon (0–48 h / > 48 h) selon les scores de fiabilité sur 30 jours. Le forçage manuel prime sur le choix automatique.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )

                // Bouton Mode Automatique
                val isAuto = dashboard.isAutoModelActive
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isAuto) ScoreGreenBg else SlateSurfaceElevated)
                        .border(1.dp, if (isAuto) ScoreGreen else SlateBorder, RoundedCornerShape(8.dp))
                        .clickable { onSelectModel(null) }
                        .padding(10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Choix Automatique (Fiabilité 30j)",
                            style = MaterialTheme.typography.labelLarge,
                            color = if (isAuto) ScoreGreen else TextPrimary
                        )
                        Text(
                            text = dashboard.autoRecommendation.summaryText,
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary
                        )
                    }
                    if (isAuto) {
                        Text("ACTIF", style = MaterialTheme.typography.labelSmall, color = ScoreGreen)
                    }
                }

                // Liste des modèles pour forçage manuel
                WeatherModel.entries.forEach { model ->
                    val isSelected = !isAuto && dashboard.manualModel == model
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSelected) SlateSurfaceBright else SlateSurfaceElevated)
                            .border(1.dp, if (isSelected) CyanPrecip else SlateBorder, RoundedCornerShape(8.dp))
                            .clickable { onSelectModel(model) }
                            .padding(10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = model.displayName,
                                style = MaterialTheme.typography.labelLarge,
                                color = if (isSelected) CyanPrecip else TextPrimary
                            )
                            Text(
                                text = "${model.agency} · ID: ${model.apiId}",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary
                            )
                        }
                        if (isSelected) {
                            Text("FORCÉ", style = MaterialTheme.typography.labelSmall, color = CyanPrecip)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Fermer")
            }
        }
    )
}

fun ScoreLevel.toColor(): Color = when (this) {
    ScoreLevel.GREEN -> ScoreGreen
    ScoreLevel.ORANGE -> ScoreOrange
    ScoreLevel.RED -> ScoreRed
}

fun ScoreLevel.toBgColor(): Color = when (this) {
    ScoreLevel.GREEN -> ScoreGreenBg
    ScoreLevel.ORANGE -> ScoreOrangeBg
    ScoreLevel.RED -> ScoreRedBg
}
