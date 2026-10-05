package org.julakali.chargeahead.android.phone.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import org.julakali.chargeahead.shared.domain.ChargeMode
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Colors that have no slot in the M3 scheme, one set per theme. */
@Immutable
data class ChargeAheadExtras(
    val faint: Color,
    /** Dashed outline of a picked section and the "add" tick. */
    val sectionOutline: Color,
    // Reserved for the trip summary's delay badge once traffic data exists.
    val trafficBg: Color,
    val trafficText: Color,
    /** AC mode's signal colour: the glow, the flag, the drawer's chip. */
    val acMode: Color,
    /** Stöbermodus' signal colour. */
    val browseMode: Color,
    /** What reads on [acMode]. */
    val onAcMode: Color,
    /** What reads on [browseMode]. */
    val onBrowseMode: Color,
)

private val LightExtras = ChargeAheadExtras(
    faint = Color(0xFF80868B),
    sectionOutline = Color(0xFFA8C7FA),
    trafficBg = Color(0xFFFEF7E0),
    trafficText = Color(0xFFB06000),
    // The primary's weight and chroma at their own hues, a step darker so white text clears 4.5:1.
    acMode = Color(0xFFBD5A03),
    browseMode = Color(0xFF068475),
    onAcMode = Color(0xFFFFFFFF),
    onBrowseMode = Color(0xFFFFFFFF),
)

private val DarkExtras = ChargeAheadExtras(
    faint = Color(0xFF80868B),
    sectionOutline = Color(0xFF4A6FA5),
    trafficBg = Color(0xFF3D2F0A),
    trafficText = Color(0xFFFDD663),
    // The dark primary's pastel weight; like it, both take ink rather than white.
    acMode = Color(0xFFD7A96C),
    browseMode = Color(0xFF62C6BC),
    onAcMode = Color(0xFF2E1C05),
    onBrowseMode = Color(0xFF05201C),
)

val LocalChargeAheadExtras = staticCompositionLocalOf { LightExtras }

/** The current theme's off-scheme colours. */
object ChargeAheadColors {
    val faint: Color @Composable get() = LocalChargeAheadExtras.current.faint
    val sectionOutline: Color @Composable get() = LocalChargeAheadExtras.current.sectionOutline
    val trafficBg: Color @Composable get() = LocalChargeAheadExtras.current.trafficBg
    val trafficText: Color @Composable get() = LocalChargeAheadExtras.current.trafficText

    /**
     * The signal colour of an active [ChargeMode]. Callers guard on [ChargeMode.NORMAL] before
     * asking; the transparent branch only keeps the `when` exhaustive.
     */
    @Composable
    fun forMode(mode: ChargeMode): Color = when (mode) {
        ChargeMode.NORMAL -> Color.Transparent
        ChargeMode.AC -> LocalChargeAheadExtras.current.acMode
        ChargeMode.BROWSE -> LocalChargeAheadExtras.current.browseMode
    }

    /** What to paint on top of [forMode]. */
    @Composable
    fun onMode(mode: ChargeMode): Color = when (mode) {
        ChargeMode.NORMAL -> Color.Transparent
        ChargeMode.AC -> LocalChargeAheadExtras.current.onAcMode
        ChargeMode.BROWSE -> LocalChargeAheadExtras.current.onBrowseMode
    }
}

/** Material's content alpha for a disabled control. */
const val DISABLED_ALPHA = 0.38f

/** Tabular figures for times and distances. */
val TextStyle.tabular: TextStyle get() = copy(fontFeatureSettings = "tnum")

private val MockupScheme = lightColorScheme(
    // Brand purple, from the powertrip mark.
    primary = Color(0xFF7F77DD),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFECEAFB),
    onPrimaryContainer = Color(0xFF26215C),
    secondary = Color(0xFF5F6368),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFECEAFB),
    onSecondaryContainer = Color(0xFF26215C),
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
    // Snackbars: the dark theme's raised grey and purple, so they belong to the app and not to Material's baseline.
    inverseSurface = Color(0xFF303134),
    inverseOnSurface = Color(0xFFE8EAED),
    inversePrimary = Color(0xFFAFA9EC),
    outline = Color(0xFFDADCE0),
    outlineVariant = Color(0xFFDADCE0),
    // White container surfaces throughout.
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFFFFFF),
    surfaceContainer = Color(0xFFFFFFFF),
    surfaceContainerHigh = Color(0xFFFFFFFF),
    surfaceContainerHighest = Color(0xFFF1F3F4),
)

// Dark grey surfaces, Google's dark-mode accents; not an inversion of the light scheme.
private val DarkScheme = darkColorScheme(
    // The launcher's light purple on the dark ground.
    primary = Color(0xFFAFA9EC),
    onPrimary = Color(0xFF26215C),
    primaryContainer = Color(0xFF3B3684),
    onPrimaryContainer = Color(0xFFE5E2FA),
    secondary = Color(0xFF9AA0A6),
    onSecondary = Color(0xFF202124),
    secondaryContainer = Color(0xFF3B3684),
    onSecondaryContainer = Color(0xFFE5E2FA),
    tertiary = Color(0xFF81C995),
    onTertiary = Color(0xFF0D3B1E),
    tertiaryContainer = Color(0xFF1E3B2A),
    onTertiaryContainer = Color(0xFF81C995),
    error = Color(0xFFF28B82),
    onError = Color(0xFF3C1F1D),
    errorContainer = Color(0xFF3C1F1D),
    onErrorContainer = Color(0xFFF28B82),
    background = Color(0xFF121212),
    onBackground = Color(0xFFE8EAED),
    surface = Color(0xFF1E1F22),
    onSurface = Color(0xFFE8EAED),
    surfaceVariant = Color(0xFF2A2B2F),
    onSurfaceVariant = Color(0xFF9AA0A6),
    // Snackbars: the light theme's surface and purple.
    inverseSurface = Color(0xFFE8EAED),
    inverseOnSurface = Color(0xFF202124),
    inversePrimary = Color(0xFF7F77DD),
    outline = Color(0xFF5F6368),
    outlineVariant = Color(0xFF3C4043),
    surfaceContainerLowest = Color(0xFF121212),
    surfaceContainerLow = Color(0xFF1E1F22),
    surfaceContainer = Color(0xFF232428),
    surfaceContainerHigh = Color(0xFF2A2B2F),
    surfaceContainerHighest = Color(0xFF303134),
)

private val Sans = FontFamily.SansSerif

// Bold and tight for titles, small and quiet for meta.
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
fun ChargeAheadTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalChargeAheadExtras provides if (darkTheme) DarkExtras else LightExtras) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkScheme else MockupScheme,
            typography = MockupTypography,
            shapes = MockupShapes,
            content = content,
        )
    }
}
