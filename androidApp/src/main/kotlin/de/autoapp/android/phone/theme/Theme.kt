package de.autoapp.android.phone.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Colors from the mockup that have no honest slot in the M3 scheme. */
object ChargeAheadColors {
    val faint = Color(0xFF80868B)
    // Reserved for the trip summary's delay badge once traffic data exists.
    val trafficBg = Color(0xFFFEF7E0)
    val trafficText = Color(0xFFB06000)
}

/** The mockup's `.num`: prices, times and distances that don't wobble. */
val TextStyle.tabular: TextStyle get() = copy(fontFeatureSettings = "tnum")

// docs/mockup/index.html :root — the palette, translated slot by slot.
private val MockupScheme = lightColorScheme(
    primary = Color(0xFF1A73E8),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE8F0FE),
    onPrimaryContainer = Color(0xFF174EA6),
    tertiary = Color(0xFF188038),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFE6F4EA),
    onTertiaryContainer = Color(0xFF188038),
    error = Color(0xFFD93025),
    onError = Color.White,
    errorContainer = Color(0xFFFCE8E6),
    onErrorContainer = Color(0xFFD93025),
    background = Color(0xFFF5F7FA),
    onBackground = Color(0xFF202124),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF202124),
    surfaceVariant = Color(0xFFF1F3F4),
    onSurfaceVariant = Color(0xFF5F6368),
    outline = Color(0xFFDADCE0),
    outlineVariant = Color(0xFFDADCE0),
    // M3 components pick container tones on their own (sheets, drawers,
    // dialogs, cards) — the mockup knows only white surfaces.
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFFFFFF),
    surfaceContainer = Color(0xFFFFFFFF),
    surfaceContainerHigh = Color(0xFFFFFFFF),
    surfaceContainerHighest = Color(0xFFF1F3F4),
)

private val Sans = FontFamily.SansSerif

// The mockup's voice: bold and tight for titles, small and quiet for meta.
private val MockupTypography = Typography().run {
    copy(
        displayLarge = displayLarge.copy(fontFamily = Sans),
        displayMedium = displayMedium.copy(fontFamily = Sans),
        displaySmall = displaySmall.copy(fontFamily = Sans),
        headlineLarge = headlineLarge.copy(fontFamily = Sans, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.5).sp),
        headlineMedium = headlineMedium.copy(fontFamily = Sans, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.4).sp),
        headlineSmall = headlineSmall.copy(fontFamily = Sans, fontSize = 19.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.2).sp),
        titleLarge = titleLarge.copy(fontFamily = Sans, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.4).sp),
        titleMedium = titleMedium.copy(fontFamily = Sans, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold),
        titleSmall = titleSmall.copy(fontFamily = Sans, fontSize = 14.sp, fontWeight = FontWeight.Bold),
        bodyLarge = bodyLarge.copy(fontFamily = Sans, fontSize = 15.sp, fontWeight = FontWeight.Medium),
        bodyMedium = bodyMedium.copy(fontFamily = Sans, fontSize = 13.sp),
        bodySmall = bodySmall.copy(fontFamily = Sans, fontSize = 12.sp),
        labelLarge = labelLarge.copy(fontFamily = Sans, fontSize = 14.sp, fontWeight = FontWeight.Bold),
        labelMedium = labelMedium.copy(fontFamily = Sans, fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
        labelSmall = labelSmall.copy(fontFamily = Sans, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
    )
}

private val MockupShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(11.dp),   // gobtn, search field, segments
    medium = RoundedCornerShape(14.dp),  // --radius: cards, CTAs
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(26.dp), // sheet top corners
)

@Composable
fun ChargeAheadTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = MockupScheme,
        typography = MockupTypography,
        shapes = MockupShapes,
        content = content,
    )
}
