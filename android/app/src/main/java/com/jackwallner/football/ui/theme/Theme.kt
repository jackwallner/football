package com.jackwallner.football.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jackwallner.football.model.normalizedTeamAbbreviation

private fun rgb(r: Double, g: Double, b: Double) = Color(r.toFloat(), g.toFloat(), b.toFloat())

/** The iOS `GridironPalette`, value for value. The app is light-only, like iOS. */
object GridironPalette {
    val canvas = rgb(0.94, 0.93, 0.89)
    val surface = rgb(0.99, 0.98, 0.94)
    val surfaceAlt = rgb(0.96, 0.95, 0.90)
    val surfaceSunk = rgb(0.90, 0.89, 0.84)
    val hairline = rgb(0.72, 0.71, 0.66)
    val divider = rgb(0.82, 0.81, 0.76)
    val ink = rgb(0.07, 0.09, 0.08)
    val inkSecondary = rgb(0.22, 0.25, 0.23)
    val inkTertiary = rgb(0.39, 0.41, 0.38)
    val inkOnDark = rgb(0.99, 0.98, 0.94)
    val midnight = rgb(0.035, 0.08, 0.07)
    val turf = rgb(0.08, 0.36, 0.20)
    val leather = rgb(0.48, 0.23, 0.10)
    val gold = rgb(0.84, 0.63, 0.19)
    val linkBlue = rgb(0.05, 0.32, 0.45)
    val performanceHigh = rgb(0.02, 0.46, 0.20)
    val performanceMid = rgb(0.40, 0.38, 0.31)
    val performanceLow = rgb(0.70, 0.20, 0.08)
    val up = performanceHigh
    val down = performanceLow
    val flat = inkTertiary
    val sky = rgb(0.30, 0.55, 0.85)

    private val hot = Triple(0.02, 0.46, 0.20)
    private val mid = Triple(0.40, 0.38, 0.31)
    private val cold = Triple(0.70, 0.20, 0.08)
    private val hotText = Triple(0.06, 0.36, 0.19)
    private val midText = Triple(0.24, 0.26, 0.24)
    private val coldText = Triple(0.55, 0.20, 0.10)

    fun color(percentile: Int): Color {
        val t = (percentile / 100.0).coerceIn(0.0, 1.0)
        return if (t < 0.5) lerp(cold, mid, t * 2) else lerp(mid, hot, (t - 0.5) * 2)
    }

    /** Percentile colour for type on a light surface: the middle is pulled dark so every value clears contrast. */
    fun textColor(percentile: Int): Color {
        val t = (percentile / 100.0).coerceIn(0.0, 1.0)
        return if (t < 0.5) lerp(coldText, midText, t * 2) else lerp(midText, hotText, (t - 0.5) * 2)
    }

    private fun lerp(a: Triple<Double, Double, Double>, b: Triple<Double, Double, Double>, t: Double) =
        rgb(a.first + (b.first - a.first) * t, a.second + (b.second - a.second) * t, a.third + (b.third - a.third) * t)
}

/**
 * iOS semantic styles at their default Dynamic Type sizes, in the platform
 * face. Values use tabular digits so columns line up.
 */
object GridironType {
    private val face = FontFamily.Default
    private const val TABULAR = "tnum"

    val playerName = TextStyle(fontFamily = face, fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold)
    val pageTitle = TextStyle(fontFamily = face, fontSize = 20.sp, lineHeight = 25.sp, fontWeight = FontWeight.Bold)
    val sectionTitle = TextStyle(fontFamily = face, fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Bold)
    val cardTitle = TextStyle(fontFamily = face, fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold)
    val body = TextStyle(fontFamily = face, fontSize = 15.sp, lineHeight = 20.sp)
    val bodyBold = TextStyle(fontFamily = face, fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold)
    val small = TextStyle(fontFamily = face, fontSize = 12.sp, lineHeight = 16.sp)
    val smallBold = TextStyle(fontFamily = face, fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.SemiBold)
    val micro = TextStyle(fontFamily = face, fontSize = 11.sp, lineHeight = 13.sp, fontWeight = FontWeight.SemiBold)

    val statHero = TextStyle(fontFamily = face, fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold, fontFeatureSettings = TABULAR)
    val statLarge = TextStyle(fontFamily = face, fontSize = 20.sp, lineHeight = 25.sp, fontWeight = FontWeight.Bold, fontFeatureSettings = TABULAR)
    val statMed = TextStyle(fontFamily = face, fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold, fontFeatureSettings = TABULAR)
    val statSmall = TextStyle(fontFamily = face, fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium, fontFeatureSettings = TABULAR)
}

object GridironGeo {
    val radiusCard = 4.dp
    val radiusBadge = 2.dp
    val hairline = 0.5.dp
    val barTrack = 4.dp
    val barMarker = 12.dp
    val padInline = 12.dp
    val padCard = 16.dp
    val padPage = 16.dp
    val padSection = 24.dp
    val rowHeight = 44.dp
    val rowHeightHeader = 28.dp
    val controlRowGap = 10.dp
    /** Android's minimum touch target. */
    val touchTarget = 48.dp
}

/** NFL team primary colours, keyed by nflverse abbreviation. */
object NFLTeamColor {
    private val primary = mapOf(
        "ARI" to rgb(0.59, 0.14, 0.25), "ATL" to rgb(0.65, 0.10, 0.19), "BAL" to rgb(0.14, 0.09, 0.45),
        "BUF" to rgb(0.00, 0.20, 0.55), "CAR" to rgb(0.00, 0.52, 0.79), "CHI" to rgb(0.04, 0.09, 0.16),
        "CIN" to rgb(0.98, 0.31, 0.08), "CLE" to rgb(0.34, 0.18, 0.05), "DAL" to rgb(0.02, 0.12, 0.26),
        "DEN" to rgb(0.98, 0.31, 0.08), "DET" to rgb(0.00, 0.46, 0.71), "GB" to rgb(0.13, 0.22, 0.19),
        "HOU" to rgb(0.01, 0.13, 0.18), "IND" to rgb(0.00, 0.17, 0.37), "JAX" to rgb(0.00, 0.40, 0.47),
        "KC" to rgb(0.89, 0.09, 0.22), "LA" to rgb(0.00, 0.21, 0.58), "LAC" to rgb(0.00, 0.50, 0.78),
        "LV" to rgb(0.10, 0.10, 0.11), "MIA" to rgb(0.00, 0.56, 0.59), "MIN" to rgb(0.31, 0.15, 0.51),
        "NE" to rgb(0.00, 0.13, 0.27), "NO" to rgb(0.62, 0.53, 0.36), "NYG" to rgb(0.04, 0.13, 0.40),
        "NYJ" to rgb(0.07, 0.34, 0.25), "PHI" to rgb(0.00, 0.30, 0.33), "PIT" to rgb(0.98, 0.71, 0.07),
        "SEA" to rgb(0.00, 0.13, 0.27), "SF" to rgb(0.67, 0.00, 0.00), "TB" to rgb(0.84, 0.04, 0.04),
        "TEN" to rgb(0.05, 0.14, 0.25), "WAS" to rgb(0.35, 0.08, 0.08),
    )

    fun color(abbr: String): Color = primary[normalizedTeamAbbreviation(abbr)] ?: GridironPalette.inkTertiary
}

@Composable
fun StatScoutTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = GridironPalette.turf,
            onPrimary = Color.White,
            background = GridironPalette.canvas,
            surface = GridironPalette.surface,
            onSurface = GridironPalette.ink,
            onBackground = GridironPalette.ink,
            surfaceVariant = GridironPalette.surfaceAlt,
            outline = GridironPalette.hairline,
            secondary = GridironPalette.turf,
        ),
        content = content,
    )
}
