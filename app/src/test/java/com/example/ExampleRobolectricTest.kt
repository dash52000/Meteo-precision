package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.RunningScoreSettingsEntity
import com.example.data.remote.GroundTruthDailyObservation
import com.example.data.remote.OpenMeteoHistoricalReanalysisSource
import com.example.data.remote.PreviousRunDailyForecast
import com.example.domain.ReliabilityAndAutoModelEngine
import com.example.domain.RunningScoreEngine
import com.example.model.HourlyWeatherPoint
import com.example.model.LocationType
import com.example.model.MudState
import com.example.model.ScoreLevel
import com.example.model.WeatherModel
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `app_name matches Meteo Precision`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Météo Précision", appName)
    }

    @Test
    fun `running score engine worst criterion wins and top 3 slots selected`() {
        val settings = RunningScoreSettingsEntity()
        val hours = (6..21).map { h ->
            HourlyWeatherPoint(
                timeIso = "2026-10-22T%02d:00".format(h),
                dateIso = "2026-10-22",
                hourOfDay = h,
                hourLabel = "${h}h",
                temperatureC = if (h == 14) 28.0 else 14.0,
                apparentTemperatureC = if (h == 14) 27.0 else 13.0, // 14h -> RED (> 25°C)
                precipitationMm = if (h == 10) 0.5 else 0.0, // 10h -> ORANGE (0.1..1.0 mm)
                windSpeedKmh = 12.0,
                windGustsKmh = if (h == 16) 55.0 else 20.0, // 16h -> RED (> 50 km/h)
                windDirectionDeg = 240,
                visibilityMeters = 10000.0,
                relativeHumidityPercent = 65,
                weatherCode = if (h == 19) 95 else 1, // 19h -> RED (orage)
                isDay = h in 7..18, // 6h, 19h, 20h, 21h -> nuit (ORANGE)
                tempModelUsed = WeatherModel.AROME,
                precipModelUsed = WeatherModel.AROME
            )
        }

        val scores = RunningScoreEngine.computeHourlyScores(
            todayHours = hours,
            settings = settings,
            locationType = LocationType.HOME,
            past48hRainMm = 2.0,
            isWeekendDay = false
        )

        assertEquals(16, scores.size)
        // 7h est de jour, 13°C ressenti, 0 mm pluie, 20 km/h rafales -> VERT
        val score7h = scores.first { it.hour == 7 }
        assertEquals(ScoreLevel.GREEN, score7h.overallLevel)
        assertTrue(score7h.isRealisticSlot)

        // 10h a 0.5 mm de pluie -> ORANGE
        val score10h = scores.first { it.hour == 10 }
        assertEquals(ScoreLevel.ORANGE, score10h.overallLevel)

        // 14h a ressenti 27°C -> ROUGE
        val score14h = scores.first { it.hour == 14 }
        assertEquals(ScoreLevel.RED, score14h.overallLevel)

        // 19h a orage (code 95) -> ROUGE
        val score19h = scores.first { it.hour == 19 }
        assertEquals(ScoreLevel.RED, score19h.overallLevel)

        val top3 = RunningScoreEngine.selectTop3Slots(scores)
        assertEquals(3, top3.size)
        assertTrue(top3.all { it.isRealisticSlot })
    }

    @Test
    fun `trail mud status and reliability engine evaluate correctly`() {
        val settings = RunningScoreSettingsEntity()
        assertEquals(
            MudState.DRY,
            RunningScoreEngine.computeTrailConditionStatus(4.5, settings).mudState
        )
        assertEquals(
            MudState.DAMP,
            RunningScoreEngine.computeTrailConditionStatus(14.0, settings).mudState
        )
        assertEquals(
            MudState.MUDDY,
            RunningScoreEngine.computeTrailConditionStatus(28.0, settings).mudState
        )

        // Vérifier l'évaluation de fiabilité sur un échantillon de 7 jours
        val actualMap = (1..7).associate { d ->
            val date = "2026-10-%02d".format(d)
            date to GroundTruthDailyObservation(
                dateIso = date,
                tempMeanC = 12.0,
                tempMinC = 8.0,
                tempMaxC = 16.0,
                precipitationSumMm = if (d % 2 == 0) 2.5 else 0.0
            )
        }
        val forecastMap = (1..7).associate { d ->
            val date = "2026-10-%02d".format(d)
            date to PreviousRunDailyForecast(
                dateIso = date,
                model = WeatherModel.ICON_D2,
                leadDays = 1,
                tempMeanC = 12.5,
                tempMinC = 8.5,
                tempMaxC = 16.5,
                precipSumMm = if (d % 2 == 0) 2.0 else 0.05
            )
        }

        val metric = ReliabilityAndAutoModelEngine.evaluateModelAtLead(
            model = WeatherModel.ICON_D2,
            leadDays = 1,
            actualByDate = actualMap,
            forecastByDate = forecastMap
        )
        assertNotNull(metric)
        assertEquals(0.5, metric!!.tempMaeC, 0.01)
        assertEquals(100.0, metric.rainNoRainAccuracyPercent, 0.01)

        val source = OpenMeteoHistoricalReanalysisSource(OkHttpClient())
        val parsedReanalysis = source.parseHistoricalDailyJson(
            """
            {
              "daily": {
                "time": ["2026-10-01", "2026-10-02"],
                "temperature_2m_mean": [14.2, null],
                "temperature_2m_max": [18.0, null],
                "temperature_2m_min": [10.4, null],
                "precipitation_sum": [1.2, null]
              }
            }
            """.trimIndent()
        )
        // Le jour avec null (retard de réanalyse) est automatiquement arrêté/filtré
        assertEquals(1, parsedReanalysis.size)
        assertTrue(parsedReanalysis.containsKey("2026-10-01"))
    }
}
