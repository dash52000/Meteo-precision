package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.model.ActiveLocation
import com.example.model.LocationType

@Entity(tableName = "saved_locations")
data class SavedLocationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val adminRegion: String,
    val latitude: Double,
    val longitude: Double,
    val locationType: String, // HOME, WORK, TRAIL, BIKE, OTHER
    val commuteMorningStartHour: Int = 7,
    val commuteMorningEndHour: Int = 9,
    val commuteEveningStartHour: Int = 17,
    val commuteEveningEndHour: Int = 19,
    val forcedModelApiId: String? = null,
    val cachedCurrentTempC: Double? = null,
    val cachedTodayMinC: Double? = null,
    val cachedTodayMaxC: Double? = null,
    val cachedTodayRainMm: Double? = null,
    val cachedWindKmh: Double? = null,
    val cachedSpecialBadge: String? = null,
    val cachedModelBadge: String? = null,
    val sortOrder: Int = 0
) {
    fun toActiveLocation(): ActiveLocation {
        val parsedType = runCatching { LocationType.valueOf(locationType) }.getOrDefault(LocationType.OTHER)
        return ActiveLocation(
            id = id,
            name = name,
            adminRegion = adminRegion,
            latitude = latitude,
            longitude = longitude,
            type = parsedType,
            isCurrentGps = false,
            isSaved = true,
            commuteMorningStartHour = commuteMorningStartHour,
            commuteMorningEndHour = commuteMorningEndHour,
            commuteEveningStartHour = commuteEveningStartHour,
            commuteEveningEndHour = commuteEveningEndHour,
            forcedModelApiId = forcedModelApiId
        )
    }
}

@Entity(tableName = "running_score_settings")
data class RunningScoreSettingsEntity(
    @PrimaryKey val id: Int = 1,
    val precipGreenMaxMm: Double = 0.1,
    val precipOrangeMaxMm: Double = 1.0,
    val feelsLikeGreenMinC: Double = 5.0,
    val feelsLikeGreenMaxC: Double = 20.0,
    val feelsLikeOrangeMinC: Double = 0.0,
    val feelsLikeOrangeMaxC: Double = 25.0,
    val gustsGreenMaxKmh: Double = 30.0,
    val gustsOrangeMaxKmh: Double = 50.0,
    val nightPenaltyLevel: String = "ORANGE", // GREEN, ORANGE, RED
    val trailRain48hOrangeMm: Double = 10.0,
    val trailRain48hRedMm: Double = 25.0,
    val weekdaySlot1StartHour: Int = 6,
    val weekdaySlot1EndHour: Int = 8,
    val weekdaySlot2StartHour: Int = 17,
    val weekdaySlot2EndHour: Int = 21,
    val weekendSlotStartHour: Int = 6,
    val weekendSlotEndHour: Int = 21
)

@Entity(tableName = "reliability_cache")
data class ReliabilityCacheEntity(
    @PrimaryKey val coordKey: String,
    val locationName: String,
    val calculatedEpochDay: Long,
    val reportJson: String
)

@Entity(tableName = "weather_forecast_cache")
data class WeatherForecastCacheEntity(
    @PrimaryKey val cacheKey: String,
    val updatedAtMillis: Long,
    val rawForecastJson: String,
    val rawAirQualityJson: String?
)
