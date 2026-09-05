package de.autoapp.shared.domain

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** Earth radius as the WGS84 (IUGG) mean radius, in kilometers. */
private const val EARTH_RADIUS_KM = 6371.0088

/** Length of one degree of latitude in kilometers (EARTH_RADIUS_KM * PI / 180). */
private const val KM_PER_DEGREE_LATITUDE = 111.19492664455873

/**
 * Detour factor from straight-line to road distance for trunk roads, used
 * until a real route is available (empirical, see ARCHITECTURE.md section
 * 5.2). Replaced by `RoutedRouteProvider` from M5 onward.
 */
const val ROUTE_DETOUR_FACTOR = 1.25

/** Great-circle distance between two points (Haversine), in kilometers. */
fun LatLon.distanceKmTo(other: LatLon): Double {
    val lat1Rad = lat.toRadians()
    val lat2Rad = other.lat.toRadians()
    val dLatRad = (other.lat - lat).toRadians()
    val dLonRad = (other.lon - lon).toRadians()

    val a = sin(dLatRad / 2.0).pow(2) + cos(lat1Rad) * cos(lat2Rad) * sin(dLonRad / 2.0).pow(2)
    val c = 2.0 * atan2(sqrt(a), sqrt(1.0 - a))
    return EARTH_RADIUS_KM * c
}

/**
 * Initial bearing from this point to the other, in degrees within [0, 360).
 * The bearing changes along a great circle — irrelevant for the ±35°
 * corridor width over at most 150 km used here.
 */
fun LatLon.bearingDegTo(other: LatLon): Double {
    val lat1Rad = lat.toRadians()
    val lat2Rad = other.lat.toRadians()
    val dLonRad = (other.lon - lon).toRadians()

    val y = sin(dLonRad) * cos(lat2Rad)
    val x = cos(lat1Rad) * sin(lat2Rad) - sin(lat1Rad) * cos(lat2Rad) * cos(dLonRad)
    return normalizeDeg(atan2(y, x).toDegrees())
}

/** Point at distance [distanceKm] along bearing [bearingDeg]. */
fun LatLon.destination(bearingDeg: Double, distanceKm: Double): LatLon {
    val angularDistance = distanceKm / EARTH_RADIUS_KM
    val bearingRad = bearingDeg.toRadians()
    val lat1Rad = lat.toRadians()

    val lat2Rad = asin(
        sin(lat1Rad) * cos(angularDistance) +
            cos(lat1Rad) * sin(angularDistance) * cos(bearingRad),
    )
    val lon2Rad = lon.toRadians() + atan2(
        sin(bearingRad) * sin(angularDistance) * cos(lat1Rad),
        cos(angularDistance) - sin(lat1Rad) * sin(lat2Rad),
    )
    return LatLon(lat2Rad.toDegrees(), normalizeLon(lon2Rad.toDegrees()))
}

/**
 * Smallest angle between two bearings, in degrees within [0, 180].
 * This makes the comparison "more than 45° of heading change" independent of
 * whether the trip happens to cross the 0° mark (359° and 1° are 2° apart).
 */
fun angularDifferenceDeg(fromDeg: Double, toDeg: Double): Double {
    val diff = abs(normalizeDeg(fromDeg) - normalizeDeg(toDeg))
    return if (diff > 180.0) 360.0 - diff else diff
}

/** Normalize a bearing to [0, 360). */
fun normalizeDeg(degrees: Double): Double {
    val wrapped = degrees % 360.0
    return if (wrapped < 0.0) wrapped + 360.0 else wrapped
}

/** Normalize a longitude to [-180, 180). */
private fun normalizeLon(degrees: Double): Double {
    val wrapped = (degrees + 180.0) % 360.0
    return (if (wrapped < 0.0) wrapped + 360.0 else wrapped) - 180.0
}

private fun Double.toRadians(): Double = this * PI / 180.0

private fun Double.toDegrees(): Double = this * 180.0 / PI

/**
 * Axis-aligned rectangle in degrees — the query format every charging-site
 * source understands (see ARCHITECTURE.md section 5.3, step 3).
 *
 * Deliberately unaware of antimeridian crossing: when it would wrap, the box
 * is widened to the full longitude range instead. That over-fetches rather
 * than querying the wrong half — the failure mode is a slower query, not an
 * empty list.
 */
data class BoundingBox(
    val south: Double,
    val west: Double,
    val north: Double,
    val east: Double,
) {
    operator fun contains(point: LatLon): Boolean =
        point.lat in south..north && point.lon in west..east

    /** Does this box fully contain the other? Basis of the cache-hit decision. */
    fun contains(other: BoundingBox): Boolean =
        other.south >= south && other.north <= north &&
            other.west >= west && other.east <= east

    /** Grow the box by [marginKm] in every direction. */
    fun expandedBy(marginKm: Double): BoundingBox {
        val latMargin = marginKm / KM_PER_DEGREE_LATITUDE
        // Compute using the widest edge, so the box has at least the
        // required margin at both corners.
        val widestLat = max(abs(south), abs(north))
        val lonMargin = marginKm / kmPerDegreeLongitudeAt(widestLat)
        return of(
            south = south - latMargin,
            west = west - lonMargin,
            north = north + latMargin,
            east = east + lonMargin,
        )
    }

    companion object {
        /** Creates a box, keeping it within valid degree ranges. */
        fun of(south: Double, west: Double, north: Double, east: Double): BoundingBox {
            val clampedSouth = south.coerceIn(-90.0, 90.0)
            val clampedNorth = north.coerceIn(-90.0, 90.0)
            return if (west < -180.0 || east > 180.0 || west > east) {
                BoundingBox(clampedSouth, -180.0, clampedNorth, 180.0)
            } else {
                BoundingBox(clampedSouth, west, clampedNorth, east)
            }
        }

        /** Smallest box enclosing all [points]. */
        fun enclosing(points: List<LatLon>): BoundingBox {
            require(points.isNotEmpty()) { "BoundingBox needs at least one point" }
            return of(
                south = points.minOf { it.lat },
                west = points.minOf { it.lon },
                north = points.maxOf { it.lat },
                east = points.maxOf { it.lon },
            )
        }
    }
}

/**
 * Length of one degree of longitude at latitude [latitudeDeg], in kilometers.
 * Clamped to a minimum near the poles, so the division doesn't blow the box
 * up toward infinity.
 */
private fun kmPerDegreeLongitudeAt(latitudeDeg: Double): Double =
    max(KM_PER_DEGREE_LATITUDE * cos(latitudeDeg.toRadians()), 0.1)

/**
 * Shortest distance to the segment from [start] to [end], in kilometers.
 *
 * Computed with a local flat-plane approximation rather than on the great
 * circle: over the length of a route segment — on the order of ten
 * kilometers — the error is far below GPS accuracy, and the formula stays
 * readable without a trigonometric iteration.
 */
fun LatLon.distanceKmToSegment(start: LatLon, end: LatLon): Double {
    // Reference latitude at the segment's midpoint, so longitude is
    // compressed equally on both sides.
    val referenceLatRad = ((start.lat + end.lat) / 2.0).toRadians()
    val lonScale = cos(referenceLatRad)

    fun x(point: LatLon) = (point.lon - start.lon) * KM_PER_DEGREE_LATITUDE * lonScale
    fun y(point: LatLon) = (point.lat - start.lat) * KM_PER_DEGREE_LATITUDE

    val endX = x(end)
    val endY = y(end)
    val pointX = x(this)
    val pointY = y(this)

    val segmentLengthSquared = endX * endX + endY * endY
    // Degenerate segment (duplicate waypoint): distance to a single point.
    if (segmentLengthSquared < 1e-12) return distanceKmTo(start)

    // Foot of the perpendicular, clamped to the segment — otherwise the
    // distance would be measured to an extension of the route that doesn't exist.
    val t = ((pointX * endX + pointY * endY) / segmentLengthSquared).coerceIn(0.0, 1.0)
    val dx = pointX - t * endX
    val dy = pointY - t * endY
    return sqrt(dx * dx + dy * dy)
}

/** Fraction of the segment where the perpendicular foot lands — 0 at the start, 1 at the end. */
fun LatLon.projectionOnSegment(start: LatLon, end: LatLon): Double {
    val referenceLatRad = ((start.lat + end.lat) / 2.0).toRadians()
    val lonScale = cos(referenceLatRad)

    val endX = (end.lon - start.lon) * KM_PER_DEGREE_LATITUDE * lonScale
    val endY = (end.lat - start.lat) * KM_PER_DEGREE_LATITUDE
    val pointX = (lon - start.lon) * KM_PER_DEGREE_LATITUDE * lonScale
    val pointY = (lat - start.lat) * KM_PER_DEGREE_LATITUDE

    val segmentLengthSquared = endX * endX + endY * endY
    if (segmentLengthSquared < 1e-12) return 0.0
    return ((pointX * endX + pointY * endY) / segmentLengthSquared).coerceIn(0.0, 1.0)
}

/** Point at [fraction] between two waypoints (0 = [start], 1 = [end]). */
fun interpolate(start: LatLon, end: LatLon, fraction: Double): LatLon = LatLon(
    lat = start.lat + (end.lat - start.lat) * fraction,
    lon = start.lon + (end.lon - start.lon) * fraction,
)
