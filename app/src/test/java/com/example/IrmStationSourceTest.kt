package com.example

import com.example.data.remote.IRM_SOURCE_PREFIX
import com.example.data.remote.IrmStationObservationSource
import com.example.domain.ReliabilityAndAutoModelEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class IrmStationSourceTest {

    private val brussels = ZoneId.of("Europe/Brussels")

    @Test
    fun `stations - garde les stations actives, lit lon puis lat, embellit le nom`() {
        val json = """
            {"type":"FeatureCollection","features":[
              {"type":"Feature","geometry":{"type":"Point","coordinates":[5.587,49.62]},
               "properties":{"code":6484,"name":"BUZENOL","date_begin":"2003-03-27T12:20:00Z","date_end":null,"altitude":323.9}},
              {"type":"Feature","geometry":{"type":"Point","coordinates":[4.525,51.075]},
               "properties":{"code":6439,"name":"SINT-KATELIJNE-WAVER","date_begin":"2003-01-01T00:00:00Z","date_end":null,"altitude":8.3}},
              {"type":"Feature","geometry":{"type":"Point","coordinates":[4.0,50.0]},
               "properties":{"code":9999,"name":"FERMEE","date_begin":"2000-01-01T00:00:00Z","date_end":"2010-01-01T00:00:00Z","altitude":10}}
            ]}
        """.trimIndent()

        val stations = IrmStationObservationSource.parseIrmStations(json)
        assertEquals(2, stations.size)
        val buzenol = stations.first { it.code == 6484L }
        assertEquals("Buzenol", buzenol.name)
        assertEquals(49.62, buzenol.latitude, 1e-9)
        assertEquals(5.587, buzenol.longitude, 1e-9)
        assertEquals("Sint-Katelijne-Waver", stations.first { it.code == 6439L }.name)
    }

    @Test
    fun `horaire vers journalier - journee locale, valeurs aberrantes ignorees`() {
        // Journée locale du 21/10/2026 à Bruxelles (UTC+2) : heures horodatées (fin d'heure)
        // de 2026-10-20T23:00Z à 2026-10-21T22:00Z.
        val start = Instant.parse("2026-10-20T23:00:00Z")
        val features = StringBuilder()
        for (h in 0 until 24) {
            val ts = start.plusSeconds(3600L * h)
            // une valeur aberrante de capteur (1003 °C) qui doit être ignorée
            val temp = if (h == 5) 1003.0 else 8.0 + h * 0.5
            val precip = if (h in 10..11) 1.5 else 0.0
            if (features.isNotEmpty()) features.append(",")
            features.append(
                """{"type":"Feature","properties":{"code":6484,"timestamp":"$ts",""" +
                    """"temp_dry_shelter_avg":$temp,"precip_quantity":$precip}}"""
            )
        }
        // heure isolée de la veille (journée incomplète, doit être écartée)
        features.append(""",{"type":"Feature","properties":{"code":6484,"timestamp":"2026-10-20T22:00:00Z","temp_dry_shelter_avg":7.0,"precip_quantity":0.0}}""")
        val json = """{"type":"FeatureCollection","features":[$features]}"""

        val daily = IrmStationObservationSource.parseIrmHourlyToDaily(json, brussels)
        assertEquals(setOf("2026-10-21"), daily.keys)
        val day = daily.getValue("2026-10-21")
        assertEquals(8.0, day.tempMinC, 1e-9)
        assertEquals(8.0 + 23 * 0.5, day.tempMaxC, 1e-9)
        assertTrue("la valeur aberrante ne doit pas compter", day.tempMaxC < 50.0)
        assertEquals(3.0, day.precipitationSumMm, 1e-9)
    }

    @Test
    fun `distance et libelle - Saulnes est a portee de Buzenol`() {
        val km = IrmStationObservationSource.distanceKm(49.53, 5.82, 49.62, 5.587)
        assertTrue("distance attendue 15-25 km, obtenue $km", km in 15.0..25.0)
        val station = IrmStationObservationSource.parseIrmStations(
            """{"features":[{"geometry":{"coordinates":[5.587,49.62]},"properties":{"code":6484,"name":"BUZENOL","date_end":null,"altitude":323.9}}]}"""
        ).single()
        val label = IrmStationObservationSource.stationLabel(station, km)
        assertTrue(label, label.startsWith("$IRM_SOURCE_PREFIX Buzenol · à "))
        assertTrue(label, label.endsWith("alt. 324 m"))
    }

    @Test
    fun `cache fiabilite - un rapport de l'ancienne methode est recalcule`() {
        val oldReport = """
            {"locationKey":"k","locationName":"Saulnes","calculatedEpochDay":1,"windowStartIso":"2026-09-20",
             "windowEndIso":"2026-10-20","groundTruthSourceName":"Open-Meteo Historical Weather API (Réanalyse)",
             "verdictSentence":"x","autoRecommendation":{"temp0_48":"icon_d2","precip0_48":"meteofrance_arome_france",
             "tempOver48":"ecmwf_ifs025","precipOver48":"ecmwf_ifs025","summaryText":""},"metrics":[]}
        """.trimIndent()
        assertNull(ReliabilityAndAutoModelEngine.deserializeReportFromJson(oldReport))

        val fresh = ReliabilityAndAutoModelEngine.createFallbackReport(
            locationKey = "k",
            locationName = "Saulnes",
            today = java.time.LocalDate.of(2026, 10, 23),
            sourceName = "$IRM_SOURCE_PREFIX Buzenol · à 19 km"
        )
        val roundTrip = ReliabilityAndAutoModelEngine.deserializeReportFromJson(
            ReliabilityAndAutoModelEngine.serializeReportToJson(fresh)
        )
        assertNotNull(roundTrip)
        assertEquals(fresh.groundTruthSourceName, roundTrip!!.groundTruthSourceName)
    }
}
