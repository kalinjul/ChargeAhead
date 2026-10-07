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

    var body: some View {
        Map(position: $camera) {
            UserAnnotation()
            // TODO cluster dense areas (SwiftUI's Map has none; MKMapView's clusteringIdentifier does)
            // TODO tint by availability like the Android markers
            ForEach(chargers, id: \.site.id) { charger in
                Marker(
                    charger.site.name,
                    monogram: Text("\(Int(charger.maxPowerKw.rounded()))"),
                    coordinate: charger.site.position.coordinate
                )
                .tint(Color.accentColor)
                .annotationTitles(.hidden)
            }
        }
        .mapControls {
            MapUserLocationButton()
            MapCompass()
            MapScaleView()
        }
        .onMapCameraChange(frequency: .onEnd) { context in
            onViewportChanged(Self.viewport(of: context.region))
        }
        .onChange(of: position == nil, initial: true) {
            // The first fix centres the map; after that the camera is the driver's.
            guard !followedFirstFix, let fix = position else { return }
            followedFirstFix = true
            camera = .region(MKCoordinateRegion(center: fix.coordinate, latitudinalMeters: 20_000, longitudinalMeters: 20_000))
        }
    }

    /// Roughly Android's zoom 10 on a phone: wider than this, markers stop being useful.
    private static let maxLongitudeSpan = 0.6

    static func viewport(of region: MKCoordinateRegion) -> BoundingBox? {
        let span = region.span
        guard span.longitudeDelta <= maxLongitudeSpan else { return nil }
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
