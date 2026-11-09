package com.example

import com.example.data.local.RunningScoreSettingsEntity
import com.example.domain.DayDetailEngine
import com.example.domain.ForecastParserEngine
import com.example.model.ActiveLocation
import com.example.model.AirQualitySummary
import com.example.model.AutoModelRecommendation
import com.example.model.ConfidenceLevel
import com.example.model.ForecastStep
import com.example.model.HourlyWeatherPoint
import com.example.model.LocationType
import com.example.model.WeatherModel
import com.example.ui.components.tempRangeLabel
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.LocalDateTime

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DayDetailEngineTest {

    private val now = LocalDateTime.of(2026, 11, 9, 10, 0)

    private fun hour(
        date: LocalDate,
        h: Int,
        model: WeatherModel,
        precipModel: WeatherModel = model,
        temp: Double = 10.0 + h * 0.5,
        precip: Double = 0.0,
        wind: Double = 10.0,
        gusts: Double? = 20.0,
        dir: Int? = 240,
        code: Int? = 1
    ) = HourlyWeatherPoint(
        timeIso = "%sT%02d:00".format(date.toString(), h),
        dateIso = date.toString(),
        hourOfDay = h,
        hourLabel = "${h}h",
        temperatureC = temp,
        apparentTemperatureC = temp - 2.0,
        precipitationMm = precip,
        windSpeedKmh = wind,
        windGustsKmh = gusts,
        windDirectionDeg = dir,
        visibilityMeters = null,
        relativeHumidityPercent = null,
        weatherCode = code,
        isDay = h in 8..16,
        tempModelUsed = model,
        precipModelUsed = precipModel
    )

    private fun day(date: LocalDate, model: WeatherModel, precipModel: WeatherModel = model) =
        (0 until 24).map { hour(date, it, model, precipModel) }

    // --- Pas de temps selon la résolution native des modèles ---

    @Test
    fun `pas horaire pour AROME a courte echeance`() {
        assertEquals(ForecastStep.HOURLY, DayDetailEngine.stepForDay(day(now.toLocalDate(), WeatherModel.AROME), now))
    }

    @Test
    fun `ECMWF IFS 025 est au pas de 3 h des le depart puis 6 h apres 144 h`() {
        assertEquals(
            ForecastStep.THREE_HOURS,
            DayDetailEngine.stepForDay(day(now.toLocalDate().plusDays(1), WeatherModel.ECMWF_IFS), now)
        )
        assertEquals(
            ForecastStep.SIX_HOURS,
            DayDetailEngine.stepForDay(day(now.toLocalDate().plusDays(7), WeatherModel.ECMWF_IFS), now)
        )
    }

    @Test
    fun `ICON-EU passe au pas de 3 h apres 78 h et GFS apres 120 h`() {
        // J+3 : jusqu'à 85 h d'échéance (+ marge d'âge du run) : au-delà de 78 h pour ICON-EU
        assertEquals(
            ForecastStep.THREE_HOURS,
            DayDetailEngine.stepForDay(day(now.toLocalDate().plusDays(3), WeatherModel.ICON_EU), now)
        )
        // J+2 pour GFS : encore horaire
        assertEquals(
            ForecastStep.HOURLY,
            DayDetailEngine.stepForDay(day(now.toLocalDate().plusDays(2), WeatherModel.GFS), now)
        )
        // J+5 pour GFS : au-delà de 120 h
        assertEquals(
            ForecastStep.THREE_HOURS,
            DayDetailEngine.stepForDay(day(now.toLocalDate().plusDays(5), WeatherModel.GFS), now)
        )
    }

    @Test
    fun `le pas le plus grossier entre temperature et pluie l'emporte`() {
        val mixed = day(now.toLocalDate(), WeatherModel.GFS, precipModel = WeatherModel.ECMWF_IFS)
        assertEquals(ForecastStep.THREE_HOURS, DayDetailEngine.stepForDay(mixed, now))
    }

    // --- Regroupement en tranches ---

    @Test
    fun `tranches de 6 h - nuit matin apres-midi soiree avec min max et cumul`() {
        val date = now.toLocalDate().plusDays(8)
        val hours = (0 until 24).map { h ->
            hour(
                date, h, WeatherModel.ECMWF_IFS,
                temp = h.toDouble(),
                precip = if (h in 12..14) 1.0 else 0.0,
                wind = if (h == 15) 30.0 else 10.0,
                gusts = if (h == 15) 55.0 else 25.0,
                code = if (h in 12..14) 61 else 2
            )
        }
        val slots = DayDetailEngine.buildSlots(hours, ForecastStep.SIX_HOURS)
        assertEquals(listOf("Nuit", "Matin", "Après-midi", "Soirée"), slots.map { it.label })

        val afternoon = slots[2]
        assertEquals(12, afternoon.startHour)
        assertEquals(18, afternoon.endHourExclusive)
        assertEquals(12.0, afternoon.tempMinC, 1e-9)
        assertEquals(17.0, afternoon.tempMaxC, 1e-9)
        assertEquals(10.0, afternoon.apparentMinC, 1e-9)
        assertEquals(15.0, afternoon.apparentMaxC, 1e-9)
        assertEquals(3.0, afternoon.precipitationMm, 1e-9)
        assertEquals(30.0, afternoon.windSpeedKmh, 1e-9)
        assertEquals(55.0, afternoon.windGustsKmh!!, 1e-9)
        assertEquals(61, afternoon.weatherCode)

        // La somme des tranches redonne le cumul de la journée
        assertEquals(hours.sumOf { it.precipitationMm }, slots.sumOf { it.precipitationMm }, 1e-9)
    }

    @Test
    fun `tranches de 3 h et pas horaire`() {
        val hours = day(now.toLocalDate().plusDays(4), WeatherModel.ECMWF_IFS)
        val threeH = DayDetailEngine.buildSlots(hours, ForecastStep.THREE_HOURS)
        assertEquals(8, threeH.size)
        assertEquals("0h–3h", threeH.first().label)
        assertEquals("21h–24h", threeH.last().label)

        val hourly = DayDetailEngine.buildSlots(hours, ForecastStep.HOURLY)
        assertEquals(24, hourly.size)
        assertEquals("14h", hourly[14].label)
        assertEquals(hourly[14].tempMinC, hourly[14].tempMaxC, 1e-9)
    }

    @Test
    fun `rafales absentes restent absentes et direction dominante gere le passage par le nord`() {
        val date = now.toLocalDate()
        val hours = listOf(
            hour(date, 0, WeatherModel.AROME, gusts = null, dir = 350),
            hour(date, 1, WeatherModel.AROME, gusts = null, dir = 10)
        )
        val slot = DayDetailEngine.buildSlots(hours, ForecastStep.SIX_HOURS).single()
        assertNull(slot.windGustsKmh)
        assertEquals(0, slot.windDirectionDeg)
    }

    @Test
    fun `icone d'une tranche seche ignore une heure de bruine sans cumul`() {
        val date = now.toLocalDate()
        val dry = listOf(
            hour(date, 6, WeatherModel.AROME, precip = 0.0, code = 3),
            hour(date, 7, WeatherModel.AROME, precip = 0.1, code = 51)
        )
        assertEquals(3, DayDetailEngine.dominantWeatherCode(dry, 0.1))
        assertEquals(51, DayDetailEngine.dominantWeatherCode(dry, 0.5))
    }

    @Test
    fun `format des plages de temperature, y compris negatives`() {
        assertEquals("4–11°", tempRangeLabel(3.6, 10.6, precise = false))
        assertEquals("12°", tempRangeLabel(11.8, 12.2, precise = false))
        assertEquals("\u22122…\u22121°", tempRangeLabel(-2.2, -0.8, precise = false))
        assertEquals("\u22121…3°", tempRangeLabel(-1.0, 3.0, precise = false))
    }

    // --- Indice de confiance ECMWF / GFS ---

    @Test
    fun `indice de confiance bonne moyenne faible`() {
        val good = DayDetailEngine.computeConfidence(3.0, 9.0, 0.2, 4.0, 10.5, 0.6)!!
        assertEquals(ConfidenceLevel.GOOD, good.level)

        // 3 °C d'écart sur la max
        val medium = DayDetailEngine.computeConfidence(3.0, 9.0, 0.0, 4.0, 12.0, 0.0)!!
        assertEquals(ConfidenceLevel.MEDIUM, medium.level)

        // Les deux voient de la pluie mais 2 mm contre 15 mm : pas « bonne »
        val bothRainBigGap = DayDetailEngine.computeConfidence(3.0, 9.0, 2.0, 3.5, 9.5, 15.0)!!
        assertEquals(ConfidenceLevel.MEDIUM, bothRainBigGap.level)

        // Plus de 4 °C d'écart
        val lowTemp = DayDetailEngine.computeConfidence(3.0, 9.0, 0.0, 3.0, 13.5, 0.0)!!
        assertEquals(ConfidenceLevel.LOW, lowTemp.level)

        // Sec pour l'un, 6 mm pour l'autre
        val lowRain = DayDetailEngine.computeConfidence(3.0, 9.0, 0.3, 3.0, 9.0, 6.3)!!
        assertEquals(ConfidenceLevel.LOW, lowRain.level)
        assertEquals(6.0, lowRain.precipGapMm, 1e-9)

        assertNull(DayDetailEngine.computeConfidence(3.0, 9.0, 0.0, null, 9.0, 0.0))
    }

    // --- Parseur : 16 jours, horaire de chaque jour, indice de confiance ---

    @Test
    fun `parseur - 16 jours, heures de chaque jour, confiance et pas adapte`() {
        val json = buildForecastJson()
        val autoRec = AutoModelRecommendation(
            tempModel0to48h = WeatherModel.GFS,
            precipModel0to48h = WeatherModel.GFS,
            tempModelOver48h = WeatherModel.ECMWF_IFS,
            precipModelOver48h = WeatherModel.ECMWF_IFS,
            summaryText = ""
        )
        val dashboard = ForecastParserEngine.buildDashboardFromJson(
            forecastJsonString = json,
            airQuality = AirQualitySummary(null, "N/D", null, null, null, null, null, null, null, null, null),
            location = ActiveLocation(
                id = null,
                name = "Test",
                adminRegion = "",
                latitude = 49.6,
                longitude = 6.1,
                type = LocationType.HOME
            ),
            settings = RunningScoreSettingsEntity(),
            autoRecommendation = autoRec,
            manualModel = null,
            nowDateTime = now
        )

        assertEquals(7, dashboard.daily7Days.size)
        assertEquals(16, dashboard.dailyAllDays.size)
        assertEquals("2026-11-24", dashboard.dailyAllDays.last().dateIso)
        assertEquals(15, dashboard.dailyAllDays.last().leadDays)
        assertEquals(16, dashboard.hourlyByDate.size)
        assertTrue(dashboard.hourlyByDate.values.all { it.size == 24 })
        assertEquals(24, dashboard.hourlyToday24h.size)
        assertTrue(dashboard.hourlyToday24h.all { it.dateIso == "2026-11-09" })

        // Aujourd'hui : écart ECMWF/GFS faible -> bonne confiance
        val today = dashboard.dailyAllDays.first()
        assertEquals(ConfidenceLevel.GOOD, today.confidence!!.level)
        // J+10 : 6 °C d'écart sur la max -> faible
        assertEquals(ConfidenceLevel.LOW, dashboard.dailyAllDays[10].confidence!!.level)

        fun stepOf(offset: Int): ForecastStep {
            val d = dashboard.dailyAllDays[offset]
            return DayDetailEngine.buildDayDetail(d, dashboard.hourlyByDate.getValue(d.dateIso), now).step
        }
        // GFS horaire sur les 48 premières heures, puis ECMWF (3 h, puis 6 h après 144 h)
        assertEquals(ForecastStep.HOURLY, stepOf(0))
        assertEquals(ForecastStep.THREE_HOURS, stepOf(3))
        assertEquals(ForecastStep.SIX_HOURS, stepOf(10))

        val detail10 = DayDetailEngine.buildDayDetail(
            dashboard.dailyAllDays[10],
            dashboard.hourlyByDate.getValue(dashboard.dailyAllDays[10].dateIso),
            now
        )
        assertEquals(4, detail10.slots.size)
        assertNotNull(detail10.sunriseHourFraction)
    }

    /** Réponse Open-Meteo minimale : 2 jours passés + 16 jours, ECMWF et GFS suffixés. */
    private fun buildForecastJson(): String {
        val models = listOf(WeatherModel.ECMWF_IFS, WeatherModel.GFS)
        val firstDay = LocalDate.of(2026, 11, 7)
        val dayCount = 18

        val hourly = JSONObject()
        val times = JSONArray()
        for (d in 0 until dayCount) for (h in 0 until 24) {
            times.put("%sT%02d:00".format(firstDay.plusDays(d.toLong()).toString(), h))
        }
        hourly.put("time", times)
        for (m in models) {
            val temp = JSONArray()
            val app = JSONArray()
            val precip = JSONArray()
            val wind = JSONArray()
            val gusts = JSONArray()
            val dir = JSONArray()
            val code = JSONArray()
            val isDay = JSONArray()
            for (i in 0 until dayCount * 24) {
                val h = i % 24
                temp.put(5.0 + h * 0.3)
                app.put(3.0 + h * 0.3)
                precip.put(if (h == 15) 0.4 else 0.0)
                wind.put(12.0)
                gusts.put(25.0)
                dir.put(230)
                code.put(if (h == 15) 61 else 2)
                isDay.put(if (h in 8..16) 1 else 0)
            }
            hourly.put("temperature_2m_${m.apiId}", temp)
            hourly.put("apparent_temperature_${m.apiId}", app)
            hourly.put("precipitation_${m.apiId}", precip)
            hourly.put("wind_speed_10m_${m.apiId}", wind)
            hourly.put("wind_gusts_10m_${m.apiId}", gusts)
            hourly.put("wind_direction_10m_${m.apiId}", dir)
            hourly.put("weather_code_${m.apiId}", code)
            hourly.put("is_day_${m.apiId}", isDay)
        }

        val daily = JSONObject()
        val dTimes = JSONArray()
        val sunrise = JSONArray()
        val sunset = JSONArray()
        for (d in 0 until dayCount) {
            val iso = firstDay.plusDays(d.toLong()).toString()
            dTimes.put(iso)
            sunrise.put("${iso}T07:58")
            sunset.put("${iso}T16:49")
        }
        daily.put("time", dTimes)
        daily.put("sunrise", sunrise)
        daily.put("sunset", sunset)
        for (m in models) {
            val tMin = JSONArray()
            val tMax = JSONArray()
            val pSum = JSONArray()
            for (d in 0 until dayCount) {
                // Index 12 = J+10 (2 jours passés en tête) : GFS 6 °C plus chaud sur la max
                val gfsOffset = if (m == WeatherModel.GFS && d == 12) 6.0 else if (m == WeatherModel.GFS) 0.5 else 0.0
                tMin.put(5.0)
                tMax.put(11.9 + gfsOffset)
                pSum.put(0.4)
            }
            daily.put("temperature_2m_min_${m.apiId}", tMin)
            daily.put("temperature_2m_max_${m.apiId}", tMax)
            daily.put("precipitation_sum_${m.apiId}", pSum)
        }

        return JSONObject()
            .put("hourly", hourly)
            .put("daily", daily)
            .toString()
    }
}
