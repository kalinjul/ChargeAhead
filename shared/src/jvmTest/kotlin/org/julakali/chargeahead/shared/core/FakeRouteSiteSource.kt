package org.julakali.chargeahead.shared.core

import org.julakali.chargeahead.shared.domain.ChargeSite
import org.julakali.chargeahead.shared.domain.LatLon
import org.julakali.chargeahead.shared.domain.Route
import org.julakali.chargeahead.shared.domain.RouteSite
import org.julakali.chargeahead.shared.domain.RouteSiteSource
import org.julakali.chargeahead.shared.domain.RouteWithSites

/** Answers the way the backend does: the sites within the buffer, placed on the route, in driving order. */
class FakeRouteSiteSource(
    private val route: Route?,
    private val sites: List<ChargeSite>,
) : RouteSiteSource {

    data class Request(val bufferKm: Double, val networkKeys: Set<String>, val minPowerKw: Double)

    val requests = mutableListOf<Request>()

    override suspend fun routeWithSites(
        from: LatLon,
        to: LatLon,
        bufferKm: Double,
        networkKeys: Set<String>,
        minPowerKw: Double,
    ): RouteWithSites? {
        requests += Request(bufferKm, networkKeys, minPowerKw)
        route ?: return null
        val measure = RouteMeasure(route)
        return RouteWithSites(
            route = route,
            sites = sites
                .filter { site -> site.connectors.any { it.maxPowerKw >= minPowerKw } }
                .map { site ->
                    val projection = measure.project(site.position)
                    RouteSite(site, projection.kmFromStart, projection.distanceKm)
                }
                .filter { it.distanceFromRouteKm <= bufferKm }
                .sortedBy { it.kmFromStart },
        )
    }
}
