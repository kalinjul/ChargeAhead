package de.autoapp.android.phone

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import de.autoapp.shared.domain.BoundingBox
import de.autoapp.shared.domain.LatLon
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min

/** A pin on the placeholder map. */
data class MapPin(
    val position: LatLon,
    val color: Color,
    /** Short label drawn into the pin — a stop number or nothing. */
    val label: String? = null,
    val emphasized: Boolean = false,
)

/**
 * Map stand-in until the map SDK is chosen (ROADMAP section 2 leaves
 * Google Maps Compose vs. MapLibre deliberately open). Draws a light,
 * maps-like ground with a faint grid, the route, the pins, and the own
 * position — enough to judge the layout and flows, honest enough not to
 * pretend to be a real map: the screen carries a "Kartenplatzhalter" notice.
 *
 * Projection is a local flat plane around the view center — the same
 * approximation the domain uses for segment distances, fine at city and
 * route scale, wrong for continents. Good enough for a placeholder.
 */
@Composable
fun MapCanvas(
    center: LatLon?,
    pins: List<MapPin>,
    modifier: Modifier = Modifier,
    routePoints: List<LatLon>? = null,
    ownPosition: LatLon? = null,
    /** Half the visible height, in kilometers. Ignored when a route dictates the frame. */
    radiusKm: Double = 8.0,
) {
    Canvas(modifier = modifier) {
        drawRect(MapColors.land)
        drawGrid()

        val frame = frameFor(center, routePoints, radiusKm, size.width, size.height) ?: return@Canvas

        routePoints?.takeIf { it.size >= 2 }?.let { points ->
            val path = Path()
            points.forEachIndexed { index, point ->
                val offset = frame.toOffset(point)
                if (index == 0) path.moveTo(offset.x, offset.y) else path.lineTo(offset.x, offset.y)
            }
            drawPath(path, MapColors.route, style = Stroke(width = 10f, cap = StrokeCap.Round))
        }

        pins.forEach { pin ->
            val offset = frame.toOffset(pin.position)
            val radius = if (pin.emphasized) 26f else 18f
            drawCircle(Color.White, radius + 6f, offset)
            drawCircle(pin.color, radius, offset)
        }

        ownPosition?.let { own ->
            val offset = frame.toOffset(own)
            drawCircle(MapColors.position.copy(alpha = 0.15f), 60f, offset)
            drawCircle(Color.White, 26f, offset)
            drawCircle(MapColors.position, 18f, offset)
        }
    }
}

object MapColors {
    val land = Color(0xFFEFEDE4)
    val grid = Color(0xFFE3E0D6)
    val route = Color(0xFF1A73E8)
    val position = Color(0xFF1A73E8)
    // One color for every charging stop: with hundreds of operators in the
    // catalog, a color per operator carries no meaning a driver could read.
    val stop = Color(0xFF188038)
}

/** Faint street-like grid so the surface reads as "map", not as empty state. */
private fun DrawScope.drawGrid() {
    val step = 140f
    var x = step / 2
    while (x < size.width) {
        drawLine(MapColors.grid, Offset(x, 0f), Offset(x, size.height), strokeWidth = 4f)
        x += step
    }
    var y = step / 2
    while (y < size.height) {
        drawLine(MapColors.grid, Offset(0f, y), Offset(size.width, y), strokeWidth = 4f)
        y += step
    }
}

private class MapFrame(
    private val center: LatLon,
    private val pxPerKm: Float,
    private val widthPx: Float,
    private val heightPx: Float,
) {
    private val kmPerDegLat = 111.19f
    private val kmPerDegLon = (111.19 * cos(center.lat * Math.PI / 180.0)).toFloat()

    fun toOffset(point: LatLon): Offset {
        val dxKm = ((point.lon - center.lon) * kmPerDegLon).toFloat()
        val dyKm = ((point.lat - center.lat) * kmPerDegLat).toFloat()
        return Offset(widthPx / 2f + dxKm * pxPerKm, heightPx / 2f - dyKm * pxPerKm)
    }
}

private fun frameFor(
    center: LatLon?,
    routePoints: List<LatLon>?,
    radiusKm: Double,
    widthPx: Float,
    heightPx: Float,
): MapFrame? {
    if (routePoints != null && routePoints.size >= 2) {
        val box = BoundingBox.enclosing(routePoints)
        val mid = LatLon((box.south + box.north) / 2.0, (box.west + box.east) / 2.0)
        val kmPerDegLon = 111.19 * cos(mid.lat * Math.PI / 180.0)
        val extentKmX = (box.east - box.west) * kmPerDegLon
        val extentKmY = (box.north - box.south) * 111.19
        // 15 % margin so start and destination pins don't sit on the edge.
        val extent = max(max(extentKmX, extentKmY), 1.0) * 1.15
        return MapFrame(mid, (min(widthPx, heightPx) / extent).toFloat(), widthPx, heightPx)
    }
    val mid = center ?: return null
    return MapFrame(mid, (min(widthPx, heightPx) / (radiusKm * 2.0)).toFloat(), widthPx, heightPx)
}
