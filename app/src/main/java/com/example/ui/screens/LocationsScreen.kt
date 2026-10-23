package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DirectionsBike
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Terrain
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.local.SavedLocationEntity
import com.example.data.remote.GeocodingResultItem
import com.example.model.ActiveLocation
import com.example.model.LocationType
import com.example.ui.theme.AmberTemp
import com.example.ui.theme.CyanPrecip
import com.example.ui.theme.GoldApparentTemp
import com.example.ui.theme.ScoreGreen
import com.example.ui.theme.ScoreRed
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
fun LocationsScreen(
    savedLocations: List<SavedLocationEntity>,
    activeLocation: ActiveLocation,
    searchQuery: String,
    isSearching: Boolean,
    searchResults: List<GeocodingResultItem>,
    onSearchQueryChange: (String) -> Unit,
    onPreviewCityWithoutSaving: (GeocodingResultItem) -> Unit,
    onSelectSavedLocation: (SavedLocationEntity) -> Unit,
    onUseGpsPosition: () -> Unit,
    onSaveOrUpdateLocation: (
        existingId: Long,
        name: String,
        adminRegion: String,
        lat: Double,
        lon: Double,
        type: LocationType,
        morningStart: Int,
        morningEnd: Int,
        eveningStart: Int,
        eveningEnd: Int
    ) -> Unit,
    onDeleteLocation: (Long) -> Unit,
    onBackToDashboard: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBackToDashboard)

    var editingEntity by remember { mutableStateOf<SavedLocationEntity?>(null) }
    var savingFromGeocoding by remember { mutableStateOf<GeocodingResultItem?>(null) }

    if (editingEntity != null) {
        val item = editingEntity!!
        val initialType = runCatching { LocationType.valueOf(item.locationType) }.getOrDefault(LocationType.OTHER)
        LocationEditorDialog(
            title = if (item.id == 0L) "Ajouter un lieu" else "Modifier ${item.name}",
            initialName = item.name,
            regionLabel = item.adminRegion,
            initialType = initialType,
            initialMorningStart = item.commuteMorningStartHour,
            initialMorningEnd = item.commuteMorningEndHour,
            initialEveningStart = item.commuteEveningStartHour,
            initialEveningEnd = item.commuteEveningEndHour,
            onDismiss = { editingEntity = null },
            onConfirm = { newName, newType, mStart, mEnd, eStart, eEnd ->
                onSaveOrUpdateLocation(
                    item.id,
                    newName,
                    item.adminRegion,
                    item.latitude,
                    item.longitude,
                    newType,
                    mStart,
                    mEnd,
                    eStart,
                    eEnd
                )
                editingEntity = null
            }
        )
    }

    if (savingFromGeocoding != null) {
        val geo = savingFromGeocoding!!
        LocationEditorDialog(
            title = "Enregistrer ${geo.name}",
            initialName = geo.name,
            regionLabel = geo.subtitle,
            initialType = LocationType.HOME,
            initialMorningStart = 7,
            initialMorningEnd = 9,
            initialEveningStart = 17,
            initialEveningEnd = 19,
            onDismiss = { savingFromGeocoding = null },
            onConfirm = { customName, chosenType, mStart, mEnd, eStart, eEnd ->
                onSaveOrUpdateLocation(
                    0L,
                    customName,
                    geo.subtitle,
                    geo.latitude,
                    geo.longitude,
                    chosenType,
                    mStart,
                    mEnd,
                    eStart,
                    eEnd
                )
                savingFromGeocoding = null
            }
        )
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(SlateBackground)
            .testTag("locations_screen_list"),
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                text = "Lieux Enregistrés & Recherche Geocoding",
                style = MaterialTheme.typography.headlineMedium,
                color = TextPrimary
            )
            Text(
                text = "Recherchez n'importe quelle ville pour la consulter immédiatement sans l'enregistrer, ou enregistrez vos lieux typés (Maison, Boulot, Spot de trail, Vélo, Autre).",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
        }

        // Barre de recherche libre (Geocoding API Open-Meteo)
        item {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("city_search_input"),
                placeholder = {
                    Text(
                        text = stringResource(R.string.search_city_hint),
                        style = MaterialTheme.typography.bodyMedium
                    )
                },
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = "Rechercher", tint = CyanPrecip)
                },
                trailingIcon = {
                    if (isSearching) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onSearchQueryChange("") }) {
                            Icon(Icons.Default.Clear, contentDescription = "Effacer")
                        }
                    }
                },
                singleLine = true
            )
        }

        // Résultats de recherche Geocoding (consultables sans enregistrer ou enregistrables)
        if (searchResults.isNotEmpty()) {
            item {
                Text(
                    text = "Résultats Open-Meteo Geocoding :",
                    style = MaterialTheme.typography.labelLarge,
                    color = CyanPrecip
                )
            }
            items(searchResults, key = { it.id }) { item ->
                Card(
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = SlateSurfaceElevated),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, CyanPrecip.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = TextPrimary
                                )
                                Text(
                                    text = "${item.subtitle} · ${String.format(Locale.US, "%.2f, %.2f", item.latitude, item.longitude)}" +
                                        (item.elevationMeters?.let { " · Alt. ${it.toInt()} m" } ?: ""),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextSecondary
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = { onPreviewCityWithoutSaving(item) },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("preview_city_${item.id}")
                            ) {
                                Icon(Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Consulter sans enregistrer", style = MaterialTheme.typography.labelSmall)
                            }
                            Button(
                                onClick = { savingFromGeocoding = item },
                                modifier = Modifier.testTag("save_city_${item.id}")
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Enregistrer", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
        }

        // Bouton Position GPS actuelle
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (activeLocation.isCurrentGps) SlateSurfaceBright else SlateSurface)
                    .border(
                        1.dp,
                        if (activeLocation.isCurrentGps) CyanPrecip else SlateBorder,
                        RoundedCornerShape(10.dp)
                    )
                    .clickable(onClick = onUseGpsPosition)
                    .padding(12.dp)
                    .testTag("use_gps_location_card"),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.MyLocation, contentDescription = null, tint = CyanPrecip)
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Ma Position GPS Actuelle",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary
                        )
                        Text(
                            text = "Centrer le tableau de bord sur les coordonnées GPS de l'appareil",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                }
                if (activeLocation.isCurrentGps) {
                    Text("ACTIF", style = MaterialTheme.typography.labelMedium, color = CyanPrecip)
                }
            }
        }

        // En-tête de la liste des lieux enregistrés avec résumé
        item {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Mes Lieux Enregistrés (${savedLocations.size}) · Résumé par lieu",
                style = MaterialTheme.typography.titleLarge,
                color = TextPrimary
            )
        }

        items(savedLocations, key = { it.id }) { loc ->
            val locType = runCatching { LocationType.valueOf(loc.locationType) }.getOrDefault(LocationType.OTHER)
            val isSelected = activeLocation.id == loc.id

            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isSelected) SlateSurfaceBright else SlateSurface
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        width = if (isSelected) 1.5.dp else 1.dp,
                        color = if (isSelected) CyanPrecip else SlateBorder,
                        shape = RoundedCornerShape(12.dp)
                    )
                    .clickable { onSelectSavedLocation(loc) }
                    .testTag("saved_location_card_${loc.id}")
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(
                                imageVector = when (locType) {
                                    LocationType.HOME -> Icons.Default.Home
                                    LocationType.WORK -> Icons.Default.Work
                                    LocationType.TRAIL -> Icons.Default.Terrain
                                    LocationType.BIKE -> Icons.Default.DirectionsBike
                                    LocationType.OTHER -> Icons.Default.Place
                                },
                                contentDescription = locType.labelFr,
                                tint = CyanPrecip,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = loc.name,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = TextPrimary
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(SlateSurfaceElevated)
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = locType.labelFr,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = GoldApparentTemp
                                        )
                                    }
                                }
                                Text(
                                    text = loc.adminRegion,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary
                                )
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (loc.cachedCurrentTempC != null) {
                                Text(
                                    text = String.format(Locale.FRANCE, "%.1f°C", loc.cachedCurrentTempC),
                                    style = MaterialTheme.typography.headlineMedium,
                                    color = AmberTemp
                                )
                            }
                            IconButton(
                                onClick = { editingEntity = loc },
                                modifier = Modifier.testTag("edit_location_${loc.id}")
                            ) {
                                Icon(Icons.Default.Edit, contentDescription = "Modifier", tint = TextSecondary)
                            }
                            IconButton(
                                onClick = { onDeleteLocation(loc.id) },
                                modifier = Modifier.testTag("delete_location_${loc.id}")
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = "Supprimer", tint = ScoreRed)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Bandeau résumé météo et métier du lieu
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
                            text = if (loc.cachedTodayMinC != null && loc.cachedTodayMaxC != null) {
                                String.format(
                                    Locale.FRANCE,
                                    "Min %.0f° / Max %.0f° · Pluie %.1f mm",
                                    loc.cachedTodayMinC,
                                    loc.cachedTodayMaxC,
                                    loc.cachedTodayRainMm ?: 0.0
                                )
                            } else {
                                String.format(Locale.US, "Coord. %.3f, %.3f", loc.latitude, loc.longitude)
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = TextPrimary
                        )
                        Text(
                            text = loc.cachedModelBadge ?: "Auto",
                            style = MaterialTheme.typography.labelSmall,
                            color = CyanPrecip
                        )
                    }

                    if (!loc.cachedSpecialBadge.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "▸ ${loc.cachedSpecialBadge}",
                            style = MaterialTheme.typography.labelMedium,
                            color = ScoreGreen
                        )
                    } else if (locType == LocationType.WORK) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "▸ Trajets configurés : Aller ${loc.commuteMorningStartHour}h–${loc.commuteMorningEndHour}h · Retour ${loc.commuteEveningStartHour}h–${loc.commuteEveningEndHour}h",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextMuted
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LocationEditorDialog(
    title: String,
    initialName: String,
    regionLabel: String,
    initialType: LocationType,
    initialMorningStart: Int,
    initialMorningEnd: Int,
    initialEveningStart: Int,
    initialEveningEnd: Int,
    onDismiss: () -> Unit,
    onConfirm: (
        name: String,
        type: LocationType,
        morningStart: Int,
        morningEnd: Int,
        eveningStart: Int,
        eveningEnd: Int
    ) -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    var selectedType by remember { mutableStateOf(initialType) }
    var morningStart by remember { mutableIntStateOf(initialMorningStart) }
    var morningEnd by remember { mutableIntStateOf(initialMorningEnd) }
    var eveningStart by remember { mutableIntStateOf(initialEveningStart) }
    var eveningEnd by remember { mutableIntStateOf(initialEveningEnd) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = title, style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nom du lieu") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (regionLabel.isNotBlank()) {
                    Text(
                        text = "Zone : $regionLabel",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }

                Text(
                    text = "Type de lieu :",
                    style = MaterialTheme.typography.labelLarge,
                    color = TextPrimary
                )

                LocationType.entries.forEach { t ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (selectedType == t) SlateSurfaceBright else SlateSurfaceElevated)
                            .border(
                                1.dp,
                                if (selectedType == t) CyanPrecip else SlateBorder,
                                RoundedCornerShape(8.dp)
                            )
                            .clickable { selectedType = t }
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = t.labelFr,
                                style = MaterialTheme.typography.labelLarge,
                                color = if (selectedType == t) CyanPrecip else TextPrimary
                            )
                            Text(
                                text = t.descriptionFr,
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                    }
                }

                // Configuration directe des heures de trajet Aller et Retour par boutons - et +
                if (selectedType == LocationType.WORK) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(SlateSurfaceElevated)
                            .border(1.dp, CyanPrecip.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                            .padding(10.dp)
                            .testTag("work_hours_picker_section"),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "Horaires Trajet Aller (${morningStart}h00 → ${morningEnd}h00)",
                            style = MaterialTheme.typography.labelLarge,
                            color = GoldApparentTemp
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            HourStepperControl(
                                label = "Début",
                                hour = morningStart,
                                onDecrement = {
                                    morningStart = (morningStart - 1).coerceAtLeast(0)
                                },
                                onIncrement = {
                                    morningStart = (morningStart + 1).coerceAtMost(22)
                                    if (morningEnd <= morningStart) {
                                        morningEnd = (morningStart + 1).coerceAtMost(23)
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            )
                            HourStepperControl(
                                label = "Fin",
                                hour = morningEnd,
                                onDecrement = {
                                    morningEnd = (morningEnd - 1).coerceAtLeast(morningStart + 1)
                                },
                                onIncrement = {
                                    morningEnd = (morningEnd + 1).coerceAtMost(23)
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Text(
                            text = "Horaires Trajet Retour (${eveningStart}h00 → ${eveningEnd}h00)",
                            style = MaterialTheme.typography.labelLarge,
                            color = CyanPrecip
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            HourStepperControl(
                                label = "Début",
                                hour = eveningStart,
                                onDecrement = {
                                    eveningStart = (eveningStart - 1).coerceAtLeast(0)
                                },
                                onIncrement = {
                                    eveningStart = (eveningStart + 1).coerceAtMost(22)
                                    if (eveningEnd <= eveningStart) {
                                        eveningEnd = (eveningStart + 1).coerceAtMost(23)
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            )
                            HourStepperControl(
                                label = "Fin",
                                hour = eveningEnd,
                                onDecrement = {
                                    eveningEnd = (eveningEnd - 1).coerceAtLeast(eveningStart + 1)
                                },
                                onIncrement = {
                                    eveningEnd = (eveningEnd + 1).coerceAtMost(23)
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm(
                        name,
                        selectedType,
                        morningStart,
                        morningEnd,
                        eveningStart,
                        eveningEnd
                    )
                }
            ) {
                Text("Enregistrer")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Annuler")
            }
        }
    )
}

@Composable
private fun HourStepperControl(
    label: String,
    hour: Int,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(SlateSurface)
            .border(1.dp, SlateBorder, RoundedCornerShape(8.dp))
            .padding(horizontal = 6.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondary
        )
        Spacer(modifier = Modifier.height(2.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(SlateSurfaceBright)
                    .clickable(onClick = onDecrement),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Remove,
                    contentDescription = "Diminuer l'heure $label",
                    tint = TextPrimary,
                    modifier = Modifier.size(16.dp)
                )
            }

            Text(
                text = String.format(Locale.FRANCE, "%02dh", hour),
                style = MaterialTheme.typography.titleMedium,
                color = CyanPrecip
            )

            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(SlateSurfaceBright)
                    .clickable(onClick = onIncrement),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Augmenter l'heure $label",
                    tint = TextPrimary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}
