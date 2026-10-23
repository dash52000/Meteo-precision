package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.local.RunningScoreSettingsEntity
import com.example.model.ScoreLevel
import com.example.ui.theme.AmberTemp
import com.example.ui.theme.CyanPrecip
import com.example.ui.theme.GoldApparentTemp
import com.example.ui.theme.MagentaGusts
import com.example.ui.theme.ScoreGreen
import com.example.ui.theme.ScoreOrange
import com.example.ui.theme.ScoreRed
import com.example.ui.theme.SlateBackground
import com.example.ui.theme.SlateBorder
import com.example.ui.theme.SlateSurface
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import java.util.Locale

@Composable
fun SettingsScreen(
    currentSettings: RunningScoreSettingsEntity,
    onSaveSettings: (RunningScoreSettingsEntity) -> Unit,
    onBackToDashboard: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBackToDashboard)

    var precipGreenMax by remember(currentSettings) { mutableDoubleStateOf(currentSettings.precipGreenMaxMm) }
    var precipOrangeMax by remember(currentSettings) { mutableDoubleStateOf(currentSettings.precipOrangeMaxMm) }

    var feelsGreenMin by remember(currentSettings) { mutableDoubleStateOf(currentSettings.feelsLikeGreenMinC) }
    var feelsGreenMax by remember(currentSettings) { mutableDoubleStateOf(currentSettings.feelsLikeGreenMaxC) }
    var feelsOrangeMin by remember(currentSettings) { mutableDoubleStateOf(currentSettings.feelsLikeOrangeMinC) }
    var feelsOrangeMax by remember(currentSettings) { mutableDoubleStateOf(currentSettings.feelsLikeOrangeMaxC) }

    var gustsGreenMax by remember(currentSettings) { mutableDoubleStateOf(currentSettings.gustsGreenMaxKmh) }
    var gustsOrangeMax by remember(currentSettings) { mutableDoubleStateOf(currentSettings.gustsOrangeMaxKmh) }

    var nightLevel by remember(currentSettings) { mutableStateOf(currentSettings.nightPenaltyLevel) }

    var trailOrangeMm by remember(currentSettings) { mutableDoubleStateOf(currentSettings.trailRain48hOrangeMm) }
    var trailRedMm by remember(currentSettings) { mutableDoubleStateOf(currentSettings.trailRain48hRedMm) }

    var weekdayS1Start by remember(currentSettings) { mutableIntStateOf(currentSettings.weekdaySlot1StartHour) }
    var weekdayS1End by remember(currentSettings) { mutableIntStateOf(currentSettings.weekdaySlot1EndHour) }
    var weekdayS2Start by remember(currentSettings) { mutableIntStateOf(currentSettings.weekdaySlot2StartHour) }
    var weekdayS2End by remember(currentSettings) { mutableIntStateOf(currentSettings.weekdaySlot2EndHour) }
    var weekendStart by remember(currentSettings) { mutableIntStateOf(currentSettings.weekendSlotStartHour) }
    var weekendEnd by remember(currentSettings) { mutableIntStateOf(currentSettings.weekendSlotEndHour) }

    fun persistNow() {
        onSaveSettings(
            RunningScoreSettingsEntity(
                id = 1,
                precipGreenMaxMm = precipGreenMax,
                precipOrangeMaxMm = precipOrangeMax.coerceAtLeast(precipGreenMax + 0.1),
                feelsLikeGreenMinC = feelsGreenMin,
                feelsLikeGreenMaxC = feelsGreenMax,
                feelsLikeOrangeMinC = feelsOrangeMin.coerceAtMost(feelsGreenMin),
                feelsLikeOrangeMaxC = feelsOrangeMax.coerceAtLeast(feelsGreenMax),
                gustsGreenMaxKmh = gustsGreenMax,
                gustsOrangeMaxKmh = gustsOrangeMax.coerceAtLeast(gustsGreenMax + 5.0),
                nightPenaltyLevel = nightLevel,
                trailRain48hOrangeMm = trailOrangeMm,
                trailRain48hRedMm = trailRedMm.coerceAtLeast(trailOrangeMm + 2.0),
                weekdaySlot1StartHour = weekdayS1Start,
                weekdaySlot1EndHour = weekdayS1End.coerceAtLeast(weekdayS1Start),
                weekdaySlot2StartHour = weekdayS2Start,
                weekdaySlot2EndHour = weekdayS2End.coerceAtLeast(weekdayS2Start),
                weekendSlotStartHour = weekendStart,
                weekendSlotEndHour = weekendEnd.coerceAtLeast(weekendStart)
            )
        )
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(SlateBackground)
            .testTag("settings_screen_list"),
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                text = "Réglages du Score Course & Créneaux",
                style = MaterialTheme.typography.headlineMedium,
                color = TextPrimary
            )
            Text(
                text = "Toute la logique repose sur des règles fixes (le pire critère l'emporte entre 6 h et 21 h). Ajustez vos seuils personnels ci-dessous.",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
        }

        // 1. Précipitations
        item {
            SettingsCard(title = "Précipitations (mm/h)") {
                Text(
                    text = String.format(
                        Locale.FRANCE,
                        "Vert < %.1f mm/h · Orange %.1f–%.1f mm/h · Rouge > %.1f mm/h",
                        precipGreenMax,
                        precipGreenMax,
                        precipOrangeMax,
                        precipOrangeMax
                    ),
                    style = MaterialTheme.typography.labelLarge,
                    color = CyanPrecip
                )
                Text("Seuil Vert max (défaut 0,1 mm/h) :", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                Slider(
                    value = precipGreenMax.toFloat(),
                    onValueChange = { precipGreenMax = (it * 10).toInt() / 10.0 },
                    onValueChangeFinished = { persistNow() },
                    valueRange = 0.0f..1.0f
                )
                Text("Seuil Orange max / Rouge (défaut 1,0 mm/h) :", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                Slider(
                    value = precipOrangeMax.toFloat(),
                    onValueChange = { precipOrangeMax = (it * 10).toInt() / 10.0 },
                    onValueChangeFinished = { persistNow() },
                    valueRange = 0.2f..5.0f
                )
            }
        }

        // 2. Ressenti thermique
        item {
            SettingsCard(title = "Ressenti Thermique (°C)") {
                Text(
                    text = String.format(
                        Locale.FRANCE,
                        "Vert %.0f à %.0f °C · Orange %.0f–%.0f ou %.0f–%.0f °C · Rouge < %.0f ou > %.0f °C",
                        feelsGreenMin,
                        feelsGreenMax,
                        feelsOrangeMin,
                        feelsGreenMin,
                        feelsGreenMax,
                        feelsOrangeMax,
                        feelsOrangeMin,
                        feelsOrangeMax
                    ),
                    style = MaterialTheme.typography.labelLarge,
                    color = AmberTemp
                )
                Text("Plage Verte idéale (Min ${feelsGreenMin.toInt()} °C / Max ${feelsGreenMax.toInt()} °C) :", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                Slider(
                    value = feelsGreenMin.toFloat(),
                    onValueChange = { feelsGreenMin = it.toInt().toDouble() },
                    onValueChangeFinished = { persistNow() },
                    valueRange = -5f..15f
                )
                Slider(
                    value = feelsGreenMax.toFloat(),
                    onValueChange = { feelsGreenMax = it.toInt().toDouble() },
                    onValueChangeFinished = { persistNow() },
                    valueRange = 15f..30f
                )
                Text("Seuils extrêmes Rouge (< ${feelsOrangeMin.toInt()} °C ou > ${feelsOrangeMax.toInt()} °C) :", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                Slider(
                    value = feelsOrangeMin.toFloat(),
                    onValueChange = { feelsOrangeMin = it.toInt().toDouble() },
                    onValueChangeFinished = { persistNow() },
                    valueRange = -15f..5f
                )
                Slider(
                    value = feelsOrangeMax.toFloat(),
                    onValueChange = { feelsOrangeMax = it.toInt().toDouble() },
                    onValueChangeFinished = { persistNow() },
                    valueRange = 20f..38f
                )
            }
        }

        // 3. Rafales de vent
        item {
            SettingsCard(title = "Rafales de Vent (km/h)") {
                Text(
                    text = String.format(
                        Locale.FRANCE,
                        "Vert < %.0f km/h · Orange %.0f–%.0f km/h · Rouge > %.0f km/h",
                        gustsGreenMax,
                        gustsGreenMax,
                        gustsOrangeMax,
                        gustsOrangeMax
                    ),
                    style = MaterialTheme.typography.labelLarge,
                    color = MagentaGusts
                )
                Slider(
                    value = gustsGreenMax.toFloat(),
                    onValueChange = { gustsGreenMax = it.toInt().toDouble() },
                    onValueChangeFinished = { persistNow() },
                    valueRange = 15f..50f
                )
                Slider(
                    value = gustsOrangeMax.toFloat(),
                    onValueChange = { gustsOrangeMax = it.toInt().toDouble() },
                    onValueChangeFinished = { persistNow() },
                    valueRange = 30f..90f
                )
            }
        }

        // 4. Lumière (Nuit) & Orage
        item {
            SettingsCard(title = "Lumière (Nuit) & Orage") {
                Text(
                    text = "Orage = Rouge fixe · Pénalité Nuit (avant lever / après coucher) :",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        ScoreLevel.GREEN.name to "Vert (Ignorer)",
                        ScoreLevel.ORANGE.name to "Orange (Défaut)",
                        ScoreLevel.RED.name to "Rouge (Exclure)"
                    ).forEach { (code, label) ->
                        FilterChip(
                            selected = nightLevel == code,
                            onClick = {
                                nightLevel = code
                                persistNow()
                            },
                            label = { Text(label, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }
            }
        }

        // 5. Spot de trail : cumul de pluie sur 48h
        item {
            SettingsCard(title = "Spot de Trail · Cumul Pluie 48 h") {
                Text(
                    text = String.format(
                        Locale.FRANCE,
                        "Sec ≤ %.0f mm · Gras (Orange) > %.0f mm · Boue (Rouge) > %.0f mm",
                        trailOrangeMm,
                        trailOrangeMm,
                        trailRedMm
                    ),
                    style = MaterialTheme.typography.labelLarge,
                    color = GoldApparentTemp
                )
                Slider(
                    value = trailOrangeMm.toFloat(),
                    onValueChange = { trailOrangeMm = it.toInt().toDouble() },
                    onValueChangeFinished = { persistNow() },
                    valueRange = 2f..30f
                )
                Slider(
                    value = trailRedMm.toFloat(),
                    onValueChange = { trailRedMm = it.toInt().toDouble() },
                    onValueChangeFinished = { persistNow() },
                    valueRange = 10f..60f
                )
            }
        }

        // 6. Créneaux réalistes par jour de semaine
        item {
            SettingsCard(title = "Créneaux Réalistes (Semaine vs Week-end)") {
                Text(
                    text = "Semaine matin : ${weekdayS1Start}h – ${weekdayS1End}h (défaut 6h–8h)",
                    style = MaterialTheme.typography.labelMedium,
                    color = ScoreGreen
                )
                Slider(
                    value = weekdayS1End.toFloat(),
                    onValueChange = {
                        weekdayS1End = it.toInt().coerceIn(weekdayS1Start + 1, 12)
                    },
                    onValueChangeFinished = { persistNow() },
                    valueRange = 7f..12f
                )

                Text(
                    text = "Semaine soir : ${weekdayS2Start}h – ${weekdayS2End}h (défaut 17h–21h)",
                    style = MaterialTheme.typography.labelMedium,
                    color = ScoreGreen
                )
                Slider(
                    value = weekdayS2Start.toFloat(),
                    onValueChange = {
                        weekdayS2Start = it.toInt().coerceIn(12, 20)
                        if (weekdayS2End <= weekdayS2Start) weekdayS2End = 21
                    },
                    onValueChangeFinished = { persistNow() },
                    valueRange = 12f..20f
                )

                Text(
                    text = "Week-end (Samedi & Dimanche) : ${weekendStart}h – ${weekendEnd}h (défaut 6h–21h)",
                    style = MaterialTheme.typography.labelMedium,
                    color = CyanPrecip
                )
                Slider(
                    value = weekendStart.toFloat(),
                    onValueChange = { weekendStart = it.toInt().coerceIn(6, 14) },
                    onValueChangeFinished = { persistNow() },
                    valueRange = 6f..14f
                )
            }
        }

        // Bouton Réinitialiser aux valeurs par défaut
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        val defaults = RunningScoreSettingsEntity()
                        onSaveSettings(defaults)
                    },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("reset_settings_button")
                ) {
                    Text("Réinitialiser seuils par défaut")
                }
                Button(
                    onClick = {
                        persistNow()
                        onBackToDashboard()
                    },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("apply_settings_button")
                ) {
                    Text("Appliquer & Retour")
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SettingsCard(
    title: String,
    content: @Composable () -> Unit
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = SlateSurface),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, SlateBorder, RoundedCornerShape(12.dp))
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary
            )
            content()
        }
    }
}
