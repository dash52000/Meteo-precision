package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.example.ui.theme.AmberTemp
import com.example.ui.theme.CyanPrecip
import com.example.ui.theme.GoldApparentTemp
import com.example.ui.theme.ScoreRed
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlin.math.cos
import kotlin.math.sin

enum class WeatherGlyphType {
    CLEAR_SUN,
    CLEAR_NIGHT,
    PARTLY_CLOUDY,
    OVERCAST_CLOUDY,
    FOG,
    RAIN,
    SNOW,
    THUNDERSTORM
}

fun resolveWeatherGlyph(weatherCode: Int, isDay: Boolean = true): WeatherGlyphType {
    return when (weatherCode) {
        0 -> if (isDay) WeatherGlyphType.CLEAR_SUN else WeatherGlyphType.CLEAR_NIGHT
        1, 2 -> WeatherGlyphType.PARTLY_CLOUDY
        3 -> WeatherGlyphType.OVERCAST_CLOUDY
        45, 48 -> WeatherGlyphType.FOG
        51, 53, 55, 56, 57, 61, 63, 65, 66, 67, 80, 81, 82 -> WeatherGlyphType.RAIN
        71, 73, 75, 77, 85, 86 -> WeatherGlyphType.SNOW
        95, 96, 99 -> WeatherGlyphType.THUNDERSTORM
        else -> if (isDay) WeatherGlyphType.PARTLY_CLOUDY else WeatherGlyphType.OVERCAST_CLOUDY
    }
}

fun wmoCodeToLabelFr(weatherCode: Int, isDay: Boolean = true): String {
    return when (weatherCode) {
        0 -> if (isDay) "Ensoleillé" else "Nuit claire"
        1 -> if (isDay) " Soleil dominant" else "Peu nuageux"
        2 -> if (isDay) "Soleil nuageux" else "Éclaircies nocturnes"
        3 -> "Nuageux"
        45 -> "Brouillard"
        48 -> "Brouillard givrant"
        51, 53, 55 -> "Bruine"
        56, 57 -> "Bruine verglaçante"
        61 -> "Pluie faible"
        63 -> "Pluie modérée"
        65 -> "Pluie forte"
        66, 67 -> "Pluie verglaçante"
        71, 73, 75, 77 -> "Neige"
        80, 81, 82 -> "Averses"
        85, 86 -> "Averses de neige"
        95, 96, 99 -> "Orage"
        else -> "Variable"
    }.trim()
}

/**
 * Icône météo vectorielle dessinée sur Canvas :
 * Soleil, Soleil nuageux, Nuageux, Brouillard, Pluie, Neige, Orage.
 */
@Composable
fun WeatherConditionIcon(
    weatherCode: Int,
    isDay: Boolean = true,
    modifier: Modifier = Modifier
) {
    val glyph = resolveWeatherGlyph(weatherCode, isDay)
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        when (glyph) {
            WeatherGlyphType.CLEAR_SUN -> {
                drawSunShape(
                    center = Offset(w * 0.5f, h * 0.5f),
                    radius = w * 0.24f,
                    rayInner = w * 0.31f,
                    rayOuter = w * 0.44f,
                    color = GoldApparentTemp
                )
            }

            WeatherGlyphType.CLEAR_NIGHT -> {
                drawCircle(
                    color = GoldApparentTemp,
                    radius = w * 0.28f,
                    center = Offset(w * 0.48f, h * 0.52f)
                )
                drawCircle(
                    color = Color(0xFF131B26),
                    radius = w * 0.22f,
                    center = Offset(w * 0.60f, h * 0.42f)
                )
            }

            WeatherGlyphType.PARTLY_CLOUDY -> {
                // Soleil en haut à gauche + Nuage en bas à droite (« Soleil nuageux »)
                drawSunShape(
                    center = Offset(w * 0.36f, h * 0.36f),
                    radius = w * 0.18f,
                    rayInner = w * 0.23f,
                    rayOuter = w * 0.33f,
                    color = GoldApparentTemp
                )
                drawCloudShape(
                    left = w * 0.24f,
                    top = h * 0.38f,
                    width = w * 0.68f,
                    height = h * 0.44f,
                    color = TextPrimary
                )
            }

            WeatherGlyphType.OVERCAST_CLOUDY -> {
                drawCloudShape(
                    left = w * 0.10f,
                    top = h * 0.22f,
                    width = w * 0.62f,
                    height = h * 0.40f,
                    color = TextSecondary.copy(alpha = 0.7f)
                )
                drawCloudShape(
                    left = w * 0.22f,
                    top = h * 0.34f,
                    width = w * 0.68f,
                    height = h * 0.44f,
                    color = TextPrimary
                )
            }

            WeatherGlyphType.FOG -> {
                // Nuage haut + 3 bandes horizontales de brouillard
                drawCloudShape(
                    left = w * 0.18f,
                    top = h * 0.14f,
                    width = w * 0.64f,
                    height = h * 0.38f,
                    color = TextSecondary
                )
                val strokeW = (h * 0.075f).coerceAtLeast(2f)
                for ((idx, yFrac) in listOf(0.62f, 0.75f, 0.88f).withIndex()) {
                    val xPadding = if (idx == 1) w * 0.12f else w * 0.18f
                    drawLine(
                        color = CyanPrecip.copy(alpha = 0.85f),
                        start = Offset(xPadding, h * yFrac),
                        end = Offset(w - xPadding, h * yFrac),
                        strokeWidth = strokeW,
                        cap = StrokeCap.Round
                    )
                }
            }

            WeatherGlyphType.RAIN -> {
                drawCloudShape(
                    left = w * 0.16f,
                    top = h * 0.14f,
                    width = w * 0.68f,
                    height = h * 0.42f,
                    color = TextPrimary
                )
                val dropStroke = (w * 0.07f).coerceAtLeast(2f)
                for (xFrac in listOf(0.32f, 0.50f, 0.68f)) {
                    drawLine(
                        color = CyanPrecip,
                        start = Offset(w * xFrac, h * 0.62f),
                        end = Offset(w * (xFrac - 0.07f), h * 0.86f),
                        strokeWidth = dropStroke,
                        cap = StrokeCap.Round
                    )
                }
            }

            WeatherGlyphType.SNOW -> {
                drawCloudShape(
                    left = w * 0.16f,
                    top = h * 0.14f,
                    width = w * 0.68f,
                    height = h * 0.42f,
                    color = TextPrimary
                )
                for (xFrac in listOf(0.32f, 0.50f, 0.68f)) {
                    drawCircle(
                        color = CyanPrecip,
                        radius = (w * 0.055f).coerceAtLeast(2f),
                        center = Offset(w * xFrac, h * 0.74f)
                    )
                }
            }

            WeatherGlyphType.THUNDERSTORM -> {
                drawCloudShape(
                    left = w * 0.14f,
                    top = h * 0.12f,
                    width = w * 0.72f,
                    height = h * 0.42f,
                    color = TextSecondary
                )
                val bolt = Path().apply {
                    moveTo(w * 0.54f, h * 0.48f)
                    lineTo(w * 0.38f, h * 0.70f)
                    lineTo(w * 0.52f, h * 0.70f)
                    lineTo(w * 0.42f, h * 0.92f)
                    lineTo(w * 0.66f, h * 0.64f)
                    lineTo(w * 0.52f, h * 0.64f)
                    close()
                }
                drawPath(path = bolt, color = AmberTemp)
                drawLine(
                    color = ScoreRed,
                    start = Offset(w * 0.28f, h * 0.62f),
                    end = Offset(w * 0.22f, h * 0.82f),
                    strokeWidth = (w * 0.06f).coerceAtLeast(1.8f),
                    cap = StrokeCap.Round
                )
            }
        }
    }
}

private fun DrawScope.drawSunShape(
    center: Offset,
    radius: Float,
    rayInner: Float,
    rayOuter: Float,
    color: Color
) {
    drawCircle(color = color, radius = radius, center = center)
    val strokeW = (size.width * 0.065f).coerceAtLeast(1.8f)
    for (i in 0 until 8) {
        val angleRad = Math.toRadians(i * 45.0)
        val c = cos(angleRad).toFloat()
        val s = sin(angleRad).toFloat()
        drawLine(
            color = color,
            start = Offset(center.x + c * rayInner, center.y + s * rayInner),
            end = Offset(center.x + c * rayOuter, center.y + s * rayOuter),
            strokeWidth = strokeW,
            cap = StrokeCap.Round
        )
    }
}

private fun DrawScope.drawCloudShape(
    left: Float,
    top: Float,
    width: Float,
    height: Float,
    color: Color
) {
    val baseTop = top + height * 0.36f
    val baseH = height * 0.64f
    drawRoundRect(
        color = color,
        topLeft = Offset(left, baseTop),
        size = Size(width, baseH),
        cornerRadius = CornerRadius(baseH / 2f, baseH / 2f)
    )
    drawCircle(
        color = color,
        radius = height * 0.38f,
        center = Offset(left + width * 0.36f, top + height * 0.44f)
    )
    drawCircle(
        color = color,
        radius = height * 0.32f,
        center = Offset(left + width * 0.64f, top + height * 0.50f)
    )
    drawRoundRect(
        color = Color(0xFF0B0F17).copy(alpha = 0.25f),
        topLeft = Offset(left, baseTop),
        size = Size(width, baseH),
        cornerRadius = CornerRadius(baseH / 2f, baseH / 2f),
        style = Stroke(width = 1f)
    )
}
