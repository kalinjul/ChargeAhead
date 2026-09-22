package org.julakali.chargeahead.android.phone

import android.util.LruCache
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionContext
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCompositionContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.applyCanvas
import androidx.core.graphics.createBitmap
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMapComposable
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.rememberUpdatedMarkerState
import org.julakali.chargeahead.shared.domain.AvailabilityLevel
import org.julakali.chargeahead.shared.domain.ChargeSpeed
import org.julakali.chargeahead.shared.domain.MapCharger
import org.julakali.chargeahead.shared.domain.OperatorShortName
import org.julakali.chargeahead.shared.domain.SiteAvailability
import org.julakali.chargeahead.android.R

@Composable
@GoogleMapComposable
fun ChargerMarker(
    charger: MapCharger,
    icons: PillIcons,
    /** Far zoom: a dot instead of the pill. */
    compact: Boolean,
    onClick: (MapCharger) -> Unit,
    alpha: Float = 1f,
) {
    val key = PillKey(
        speed = ChargeSpeed.of(charger.maxPowerKw),
        // Dots don't carry the name, so all sites of a kind share one bitmap.
        label = if (compact) null else OperatorShortName.of(charger.site.operator),
        availability = charger.availability,
        compact = compact,
    )
    Marker(
        state = rememberUpdatedMarkerState(position = LatLng(charger.site.position.lat, charger.site.position.lon)),
        icon = remember(icons, key) { icons[key] },
        title = charger.site.name,
        alpha = alpha,
        anchor = Offset(0.5f, 0.5f),
        onClick = { onClick(charger); true },
    )
}

data class PillKey(
    val speed: ChargeSpeed,
    val label: String?,
    val availability: SiteAvailability?,
    val compact: Boolean = false,
)

/** Renders each distinct [ChargerPill] once, so markers that look alike share one bitmap. */
@Stable
class PillIcons internal constructor(
    private val parent: ViewGroup,
    private val compositionContext: CompositionContext,
) {
    private val cache = LruCache<PillKey, BitmapDescriptor>(MAX_CACHED_PILLS)

    operator fun get(key: PillKey): BitmapDescriptor =
        cache[key] ?: renderToBitmapDescriptor(parent, compositionContext) {
            if (key.compact) {
                ChargerDot(speed = key.speed, availability = key.availability)
            } else {
                ChargerPill(speed = key.speed, label = key.label, availability = key.availability)
            }
        }.also { cache.put(key, it) }
}

/** Starts over whenever density, font scale or locale change, since those change the pixels. */
@Composable
fun rememberPillIcons(): PillIcons {
    val parent = LocalView.current as ViewGroup
    val compositionContext = rememberCompositionContext()
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    return remember(parent, compositionContext, density, configuration) {
        PillIcons(parent, compositionContext)
    }
}

/**
 * Draws [content] into a bitmap. The throwaway [ComposeView] has to hang in [parent]
 * briefly so it composes with the caller's theme and resources; must run on the main thread.
 */
private fun renderToBitmapDescriptor(
    parent: ViewGroup,
    compositionContext: CompositionContext,
    content: @Composable () -> Unit,
): BitmapDescriptor {
    val view = ComposeView(parent.context).apply {
        layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        setParentCompositionContext(compositionContext)
        setContent(content)
    }
    parent.addView(view)
    try {
        val unbounded = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        view.measure(unbounded, unbounded)
        view.layout(0, 0, view.measuredWidth, view.measuredHeight)
        val bitmap = createBitmap(view.measuredWidth.coerceAtLeast(1), view.measuredHeight.coerceAtLeast(1))
        bitmap.applyCanvas { view.draw(this) }
        return BitmapDescriptorFactory.fromBitmap(bitmap)
    } finally {
        parent.removeView(view)
        view.disposeComposition()
    }
}

/**
 * A capsule with one-to-three bolts plus the operator's short name. With live
 * data the whole capsule takes a pale availability tint and the free count
 * closes the line in the strong colour.
 */
@Composable
fun ChargerPill(
    speed: ChargeSpeed,
    label: String?,
    availability: SiteAvailability?,
    modifier: Modifier = Modifier,
) {
    val outOfOrder = availability is SiteAvailability.OutOfOrder
    val level = (availability as? SiteAvailability.Live)?.level
    val tint = level?.tint ?: if (outOfOrder) RedTint else Color.White
    val border = when {
        outOfOrder -> Red
        level != null -> level.tintBorder
        else -> Outline
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .background(tint, CircleShape)
            .border(width = if (outOfOrder) 2.dp else 1.dp, color = border, shape = CircleShape)
            .padding(horizontal = 6.dp, vertical = 3.dp),
    ) {
        Bolts(count = speed.bolts, color = if (outOfOrder) Grey else speed.color)
        if (!label.isNullOrBlank()) {
            Text(
                text = label,
                color = TextColor,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                modifier = Modifier.padding(start = 5.dp),
            )
        }
        if (availability != null) {
            Box(
                Modifier
                    .padding(horizontal = 5.dp)
                    .size(width = 1.dp, height = 12.dp)
                    .background(border),
            )
            Text(
                text = when (availability) {
                    is SiteAvailability.Live -> "${availability.free}/${availability.total}"
                    SiteAvailability.OutOfOrder -> stringResource(R.string.map_out_of_order_mark)
                },
                color = level?.strong ?: Red,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
        }
    }
}

/**
 * Far-zoom marker: the availability colour when live, otherwise the speed colour.
 * Out of order is grey inside a red ring, so it can't be mistaken for merely full.
 */
@Composable
fun ChargerDot(speed: ChargeSpeed, availability: SiteAvailability?, modifier: Modifier = Modifier) {
    val outOfOrder = availability is SiteAvailability.OutOfOrder
    val fill = when (availability) {
        is SiteAvailability.Live -> availability.level.color
        SiteAvailability.OutOfOrder -> Grey
        null -> speed.color
    }
    Box(
        modifier
            .size(15.dp)
            .background(Color.White, CircleShape)
            .border(if (outOfOrder) 2.dp else 1.dp, if (outOfOrder) Red else Outline, CircleShape)
            .padding(if (outOfOrder) 3.dp else 2.dp)
            .background(fill, CircleShape),
    )
}

/** Overlapping bolts; each one's white halo cuts a visible edge into the one before. */
@Composable
private fun Bolts(count: Int, color: Color) {
    val bolt = painterResource(R.drawable.ic_bolt)
    Box {
        repeat(count) { i ->
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.padding(start = 6.dp * i).size(14.dp),
            ) {
                Icon(bolt, contentDescription = null, tint = Color.White, modifier = Modifier.requiredSize(17.dp))
                Icon(bolt, contentDescription = null, tint = color, modifier = Modifier.size(14.dp))
            }
        }
    }
}

private val ChargeSpeed.color: Color
    get() = when (this) {
        ChargeSpeed.SLOW -> Red
        ChargeSpeed.MEDIUM -> Amber
        ChargeSpeed.FAST, ChargeSpeed.ULTRA, ChargeSpeed.HYPER -> Green
    }

private val AvailabilityLevel.color: Color
    get() = when (this) {
        AvailabilityLevel.GOOD -> Green
        AvailabilityLevel.LOW -> Amber
        AvailabilityLevel.NONE -> Red
    }

private const val MAX_CACHED_PILLS = 128

private val Red = Color(0xFFD93025)
private val Amber = Color(0xFFF9AB00)
private val Green = Color(0xFF188038)
private val Grey = Color(0xFF9AA0A6)
private val Outline = Color(0xFFDADCE0)
private val TextColor = Color(0xFF202124)
private val RedTint = Color(0xFFFCE8E6)

private val AvailabilityLevel.tint: Color
    get() = when (this) {
        AvailabilityLevel.GOOD -> Color(0xFFE6F4EA)
        AvailabilityLevel.LOW -> Color(0xFFFEF7E0)
        AvailabilityLevel.NONE -> RedTint
    }

private val AvailabilityLevel.tintBorder: Color
    get() = when (this) {
        AvailabilityLevel.GOOD -> Color(0xFFA8DAB5)
        AvailabilityLevel.LOW -> Color(0xFFF9D77C)
        AvailabilityLevel.NONE -> Color(0xFFF2B8B2)
    }

/** Text on the tint: green, a darker amber for contrast, red. */
private val AvailabilityLevel.strong: Color
    get() = when (this) {
        AvailabilityLevel.GOOD -> Green
        AvailabilityLevel.LOW -> Color(0xFFB06000)
        AvailabilityLevel.NONE -> Red
    }

@Preview(showBackground = true, backgroundColor = 0xFFE8EAED)
@Composable
private fun ChargerPillPreview() {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(16.dp),
    ) {
        ChargerPill(speed = ChargeSpeed.SLOW, label = null, availability = null)
        ChargerPill(speed = ChargeSpeed.MEDIUM, label = "EnBW", availability = null)
        ChargerPill(speed = ChargeSpeed.HYPER, label = "Ionity", availability = null)
        ChargerPill(speed = ChargeSpeed.ULTRA, label = "Tesla", availability = SiteAvailability.Live(free = 6, total = 8))
        ChargerPill(speed = ChargeSpeed.FAST, label = "Aral pulse", availability = SiteAvailability.Live(free = 1, total = 4))
        ChargerPill(speed = ChargeSpeed.MEDIUM, label = null, availability = SiteAvailability.Live(free = 0, total = 2))
        ChargerPill(speed = ChargeSpeed.FAST, label = "EWE Go", availability = SiteAvailability.OutOfOrder)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ChargerDot(speed = ChargeSpeed.HYPER, availability = null)
            ChargerDot(speed = ChargeSpeed.FAST, availability = SiteAvailability.Live(free = 6, total = 8))
            ChargerDot(speed = ChargeSpeed.FAST, availability = SiteAvailability.Live(free = 1, total = 4))
            ChargerDot(speed = ChargeSpeed.FAST, availability = SiteAvailability.Live(free = 0, total = 2))
            ChargerDot(speed = ChargeSpeed.FAST, availability = SiteAvailability.OutOfOrder)
        }
    }
}
