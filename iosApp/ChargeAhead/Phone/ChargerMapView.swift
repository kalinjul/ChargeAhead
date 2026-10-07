import MapKit
import SwiftUI
import Shared

/// Apple's map with the shared ViewModel's chargers; it reports what is visible, the ViewModel loads for it.
struct ChargerMapView: View {
    let position: LatLon?
    let chargers: [MapCharger]
    let onViewportChanged: (BoundingBox?) -> Void

    @State private var camera: MapCameraPosition = .automatic
    @State private var followedFirstFix = false
    @State private var compact = false

    var body: some View {
        GeometryReader { geometry in
            map(widthPoints: geometry.size.width)
        }
    }

    private func map(widthPoints: CGFloat) -> some View {
        Map(position: $camera) {
            UserAnnotation()
            // TODO cluster dense areas (SwiftUI's Map has none; MKMapView's clusteringIdentifier does)
            ForEach(chargers, id: \.site.id) { charger in
                Annotation(charger.site.name, coordinate: charger.site.position.coordinate, anchor: .center) {
                    if compact {
                        ChargerDot(charger: charger)
                    } else {
                        ChargerPill(charger: charger)
                    }
                }
                .annotationTitles(.hidden)
            }
        }
        .mapControls {
            MapUserLocationButton()
            MapCompass()
            MapScaleView()
        }
        .onMapCameraChange(frequency: .onEnd) { context in
            let degreesPerPoint = context.region.span.longitudeDelta / max(widthPoints, 1)
            compact = degreesPerPoint > Self.degreesPerPoint(atZoom: Self.pillZoom)
            onViewportChanged(degreesPerPoint > Self.degreesPerPoint(atZoom: Self.minChargerZoom) ? nil : Self.viewport(of: context.region))
        }
        .onChange(of: position == nil, initial: true) {
            // The first fix centres the map; after that the camera is the driver's.
            guard !followedFirstFix, let fix = position else { return }
            followedFirstFix = true
            camera = .region(MKCoordinateRegion(center: fix.coordinate, latitudinalMeters: 20_000, longitudinalMeters: 20_000))
        }
    }

    /// Android's MIN_CHARGER_ZOOM and PILL_ZOOM: below the first nothing loads, below the second pills become dots.
    private static let minChargerZoom = 10.0
    private static let pillZoom = 11.0

    /// What a web-mercator zoom level shows per point, so the thresholds hold on any screen width.
    private static func degreesPerPoint(atZoom zoom: Double) -> Double {
        360 / (256 * pow(2, zoom))
    }

    static func viewport(of region: MKCoordinateRegion) -> BoundingBox {
        let span = region.span
        return BoundingBox(
            south: region.center.latitude - span.latitudeDelta / 2,
            west: region.center.longitude - span.longitudeDelta / 2,
            north: region.center.latitude + span.latitudeDelta / 2,
            east: region.center.longitude + span.longitudeDelta / 2
        )
    }
}

extension LatLon {
    var coordinate: CLLocationCoordinate2D { CLLocationCoordinate2D(latitude: lat, longitude: lon) }
}
