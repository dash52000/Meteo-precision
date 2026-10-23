package com.example.ui.screens

import com.example.data.remote.IRM_SOURCE_PREFIX
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.model.ActiveLocation
import com.example.model.LocationReliabilityReport
import com.example.model.ModelLeadReliability
import com.example.model.WeatherModel
import com.example.ui.components.ForecastVsReal30DayChart
import com.example.ui.theme.AmberTemp
import com.example.ui.theme.CyanPrecip
import com.example.ui.theme.GoldApparentTemp
import com.example.ui.theme.ScoreGreen
import com.example.ui.theme.ScoreGreenBg
import com.example.ui.theme.SlateBackground
import com.example.ui.theme.SlateBorder
import com.example.ui.theme.SlateSurface
import com.example.ui.theme.SlateSurfaceBright
import com.example.ui.theme.SlateSurfaceElevated
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import java.util.Locale

@Composable
fun ReliabilityScreen(
    activeLocation: ActiveLocation,
    report: LocationReliabilityReport?,
    isLoading: Boolean,
    selectedLeadDays: Int,
    selectedModel: WeatherModel,
    onSelectLeadDays: (Int) -> Unit,
    onSelectModel: (WeatherModel) -> Unit,
    onForceRecalculate: () -> Unit,
    onSelectModelOverride: (WeatherModel?) -> Unit,
    onBackToDashboard: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBackToDashboard)

    var chartShowPrecip by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(SlateBackground)
            .testTag("reliability_screen_list"),
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // En-tête de l'écran Fiabilité
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    val measuredByStation = report?.groundTruthSourceName?.startsWith(IRM_SOURCE_PREFIX) == true
                    Text(
                        text = if (report == null || measuredByStation) {
                            "Fiabilité des Prévisions · ${activeLocation.name}"
                        } else {
                            "Écart à la réanalyse · ${activeLocation.name}"
                        },
                        style = MaterialTheme.typography.headlineMedium,
                        color = TextPrimary
                    )
                    if (report != null) {
                        Text(
                            text = "Fenêtre glissante consolidée : ${report.windowStartIso} → ${report.windowEndIso}",
                            style = MaterialTheme.typography.labelSmall,
                            color = CyanPrecip
                        )
                    }
                }
                IconButton(
                    onClick = onForceRecalculate,
                    modifier = Modifier.testTag("force_recalculate_reliability_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Recalculer la fiabilité",
                        tint = CyanPrecip
                    )
                }
            }
        }

        if (isLoading && report == null) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(260.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = CyanPrecip)
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Comparaison Previous Runs API vs Historical Weather API (30j)…",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                }
            }
        } else if (report != null) {
            // 1. Phrase de verdict mise en avant
            item {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = ScoreGreenBg),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, ScoreGreen, RoundedCornerShape(12.dp))
                        .testTag("reliability_verdict_card")
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = ScoreGreen,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Verdict de fiabilité locale (30 jours)",
                                style = MaterialTheme.typography.labelLarge,
                                color = ScoreGreen
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "« ${report.verdictSentence} »",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary,
                            modifier = Modifier.testTag("reliability_verdict_text")
                        )
                    }
                }
            }

            // 2. Synthèse du choix automatique du meilleur modèle (0-48h vs >48h, Temp vs Pluie)
            item {
                val auto = report.autoRecommendation
                val isAutoActive = activeLocation.forcedModelApiId == null
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = SlateSurface),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, SlateBorder, RoundedCornerShape(12.dp))
                        .testTag("auto_model_summary_card")
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Choix Automatique du Meilleur Modèle",
                                style = MaterialTheme.typography.titleMedium,
                                color = TextPrimary
                            )
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (isAutoActive) ScoreGreenBg else SlateSurfaceBright)
                                    .clickable { onSelectModelOverride(null) }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = if (isAutoActive) "Mode Auto Actif" else "Réactiver Mode Auto",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isAutoActive) ScoreGreen else CyanPrecip
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(SlateSurfaceElevated)
                                    .padding(10.dp)
                            ) {
                                Text(
                                    text = "Court terme (0 – 48 h)",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = GoldApparentTemp
                                )
                                Text(
                                    text = "Température : ${auto.tempModel0to48h.shortBadge}",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = AmberTemp
                                )
                                Text(
                                    text = "Précipitations : ${auto.precipModel0to48h.shortBadge}",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = CyanPrecip
                                )
                            }

                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(SlateSurfaceElevated)
                                    .padding(10.dp)
                            ) {
                                Text(
                                    text = "Moyen terme (> 48 h)",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = GoldApparentTemp
                                )
                                Text(
                                    text = "Température : ${auto.tempModelOver48h.shortBadge}",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = AmberTemp
                                )
                                Text(
                                    text = "Précipitations : ${auto.precipModelOver48h.shortBadge}",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = CyanPrecip
                                )
                            }
                        }
                        Text(
                            text = "Mis en cache localement (recalcul max 1 fois/jour/lieu).",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextMuted
                        )
                    }
                }
            }

            // 3. Sélecteur d'échéance (J+1, J+2, J+3, et J+4..J+7 pour modèles globaux)
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Choisir l'échéance de prévision (J+1 à J+7) :",
                        style = MaterialTheme.typography.labelLarge,
                        color = TextPrimary
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        (1..7).forEach { lead ->
                            FilterChip(
                                selected = selectedLeadDays == lead,
                                onClick = { onSelectLeadDays(lead) },
                                label = {
                                    Text(
                                        text = "J+$lead",
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                },
                                modifier = Modifier.testTag("lead_chip_j$lead")
                            )
                        }
                    }
                }
            }

            // 4. Tableau des métriques par modèle à l'échéance sélectionnée
            val metricsForLead = report.metrics.filter { it.leadDays == selectedLeadDays }
                .sortedBy { it.tempMaeC }

            item {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = SlateSurface),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, SlateBorder, RoundedCornerShape(12.dp))
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Métriques à J+$selectedLeadDays (30 jours glissants)",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Modèle", style = MaterialTheme.typography.labelSmall, color = TextMuted, modifier = Modifier.weight(1.2f))
                            Text("MAE T°", style = MaterialTheme.typography.labelSmall, color = AmberTemp, modifier = Modifier.weight(0.8f))
                            Text("Err. Pluie", style = MaterialTheme.typography.labelSmall, color = CyanPrecip, modifier = Modifier.weight(0.9f))
                            Text("Pluie/Sec (0,2mm)", style = MaterialTheme.typography.labelSmall, color = ScoreGreen, modifier = Modifier.weight(1.1f))
                        }

                        if (metricsForLead.isEmpty()) {
                            Text(
                                text = "Aucun modèle régional ne couvre J+$selectedLeadDays ou historique en cours de synchronisation.",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary,
                                modifier = Modifier.padding(6.dp)
                            )
                        } else {
                            metricsForLead.forEach { m ->
                                val isRowSelected = m.model == selectedModel
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isRowSelected) SlateSurfaceBright else SlateSurfaceElevated)
                                        .border(
                                            1.dp,
                                            if (isRowSelected) CyanPrecip else SlateBorder,
                                            RoundedCornerShape(8.dp)
                                        )
                                        .clickable { onSelectModel(m.model) }
                                        .padding(horizontal = 8.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1.2f)) {
                                        Text(
                                            text = m.model.shortBadge,
                                            style = MaterialTheme.typography.labelLarge,
                                            color = TextPrimary
                                        )
                                        Text(
                                            text = "${m.sampleDaysCount} j.",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = TextMuted
                                        )
                                    }
                                    Text(
                                        text = String.format(Locale.FRANCE, "±%.2f °C", m.tempMaeC),
                                        style = MaterialTheme.typography.labelLarge,
                                        color = AmberTemp,
                                        modifier = Modifier.weight(0.8f)
                                    )
                                    Text(
                                        text = String.format(Locale.FRANCE, "±%.1f mm/j", m.precipDailyMaeMm),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = CyanPrecip,
                                        modifier = Modifier.weight(0.9f)
                                    )
                                    Text(
                                        text = String.format(Locale.FRANCE, "%.0f %% juste", m.rainNoRainAccuracyPercent),
                                        style = MaterialTheme.typography.labelLarge,
                                        color = ScoreGreen,
                                        modifier = Modifier.weight(1.1f)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 5. Graphe « Prévu vs Réel » sur 30 jours avec choix du modèle et de la variable
            val activeMetric: ModelLeadReliability? = metricsForLead.find { it.model == selectedModel }
                ?: metricsForLead.firstOrNull()

            if (activeMetric != null && activeMetric.dailyComparison.isNotEmpty()) {
                item {
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = SlateSurface),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, SlateBorder, RoundedCornerShape(12.dp))
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Graphe Prévu vs Réel · ${activeMetric.model.shortBadge} (J+$selectedLeadDays)",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = TextPrimary,
                                    modifier = Modifier.weight(1f)
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    FilterChip(
                                        selected = !chartShowPrecip,
                                        onClick = { chartShowPrecip = false },
                                        label = { Text("T° Moy", style = MaterialTheme.typography.labelSmall) }
                                    )
                                    FilterChip(
                                        selected = chartShowPrecip,
                                        onClick = { chartShowPrecip = true },
                                        label = { Text("Pluie mm", style = MaterialTheme.typography.labelSmall) }
                                    )
                                }
                            }

                            ForecastVsReal30DayChart(
                                points = activeMetric.dailyComparison,
                                showPrecipitation = chartShowPrecip,
                                modelBadge = activeMetric.model.shortBadge,
                                leadDay = selectedLeadDays
                            )
                        }
                    }
                }
            }

            // 6. Info sur le module remplaçable de la source du « réel »
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(SlateSurface)
                        .border(1.dp, SlateBorder, RoundedCornerShape(10.dp))
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Sensors,
                        contentDescription = null,
                        tint = CyanPrecip,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        val isStation = report.groundTruthSourceName.startsWith(IRM_SOURCE_PREFIX)
                        Text(
                            text = "Référence du « réel » : ${report.groundTruthSourceName}",
                            style = MaterialTheme.typography.labelMedium,
                            color = TextPrimary
                        )
                        Text(
                            text = if (isStation) {
                                "Mesures réelles de la station IRM la plus proche (données ouvertes, sans clé). " +
                                    "Une station reste un point : la distance et le dénivelé peuvent créer un petit écart propre au lieu."
                            } else {
                                "Aucune station IRM à moins de 25 km (ou service indisponible) : comparaison avec la réanalyse, " +
                                    "qui est elle-même un modèle à maille large. Elle avantage ECMWF et pénalise AROME / ICON-D2 : " +
                                    "lis ces scores comme un écart à la réanalyse, pas comme une fiabilité mesurée."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                }
            }
        }
    }
}
