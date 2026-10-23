package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface WeatherDao {

    @Query("SELECT * FROM saved_locations ORDER BY sortOrder ASC, id ASC")
    fun observeSavedLocations(): Flow<List<SavedLocationEntity>>

    @Query("SELECT * FROM saved_locations ORDER BY sortOrder ASC, id ASC")
    suspend fun getSavedLocationsOnce(): List<SavedLocationEntity>

    @Query("SELECT * FROM saved_locations WHERE id = :id LIMIT 1")
    suspend fun getLocationById(id: Long): SavedLocationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLocation(location: SavedLocationEntity): Long

    @Update
    suspend fun updateLocation(location: SavedLocationEntity)

    @Query("DELETE FROM saved_locations WHERE id = :id")
    suspend fun deleteLocationById(id: Long)

    @Query("UPDATE saved_locations SET forcedModelApiId = :modelApiId WHERE id = :id")
    suspend fun updateForcedModel(id: Long, modelApiId: String?)

    @Query(
        """
        UPDATE saved_locations 
        SET cachedCurrentTempC = :currentTempC,
            cachedTodayMinC = :minTempC,
            cachedTodayMaxC = :maxTempC,
            cachedTodayRainMm = :rainMm,
            cachedWindKmh = :windKmh,
            cachedSpecialBadge = :specialBadge,
            cachedModelBadge = :modelBadge
        WHERE id = :id
        """
    )
    suspend fun updateLocationSummaryCache(
        id: Long,
        currentTempC: Double?,
        minTempC: Double?,
        maxTempC: Double?,
        rainMm: Double?,
        windKmh: Double?,
        specialBadge: String?,
        modelBadge: String?
    )

    @Query("SELECT * FROM running_score_settings WHERE id = 1 LIMIT 1")
    fun observeRunningSettings(): Flow<RunningScoreSettingsEntity?>

    @Query("SELECT * FROM running_score_settings WHERE id = 1 LIMIT 1")
    suspend fun getRunningSettingsOnce(): RunningScoreSettingsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveRunningSettings(settings: RunningScoreSettingsEntity)

    @Query("SELECT * FROM reliability_cache WHERE coordKey = :coordKey LIMIT 1")
    suspend fun getReliabilityCache(coordKey: String): ReliabilityCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveReliabilityCache(cache: ReliabilityCacheEntity)

    @Query("DELETE FROM reliability_cache WHERE coordKey = :coordKey")
    suspend fun clearReliabilityCacheForLocation(coordKey: String)

    @Query("SELECT * FROM weather_forecast_cache WHERE cacheKey = :cacheKey LIMIT 1")
    suspend fun getForecastCache(cacheKey: String): WeatherForecastCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveForecastCache(cache: WeatherForecastCacheEntity)
}
