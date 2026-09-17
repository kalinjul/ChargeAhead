import SwiftUI
import Shared

/// The planned trip: summary, stops, hand-off to Google Maps. The numbers
/// come from the shared planner.
struct TripPlanView: View {
    let plan: TripPlan
    let ownPosition: LatLon?

    var body: some View {
        List {
            Section {
                VStack(alignment: .leading, spacing: 4) {
                    Text("\(Int(plan.route.distanceKm.rounded())) km")
                        .font(.title2.bold())
                    Text(String(
                        format: NSLocalizedString("trip_summary_time_fmt", comment: ""),
                        minutesText(plan.totalMinutes),
                        minutesText(plan.chargeMinutes)
                    ))
                    .font(.subheadline)
                    Text(String(
                        format: NSLocalizedString("trip_summary_arrival_fmt", comment: ""),
                        Int(plan.arrivalSocPercent.rounded())
                    ))
                    .font(.footnote)
                    .foregroundStyle(.secondary)
                }
            }

            Section(NSLocalizedString("trip_stops_heading", comment: "")) {
                ForEach(Array(plan.stops.enumerated()), id: \.element.site.id) { index, stop in
                    NavigationLink {
                        StopDetailView(stop: stop)
                    } label: {
                        VStack(alignment: .leading, spacing: 2) {
                            Text("\(index + 1) · \(stop.site.name)")
                                .font(.headline)
                            Text("\(ChargeStopFormatter.shared.chargeToLabel(stop: stop)) · \(ChargeStopFormatter.shared.powerKwLabel(powerKw: stop.maxPowerKw))")
                                .font(.footnote)
                                .foregroundStyle(.secondary)
                        }
                    }
                }
            }

            Section {
                Button {
                    let waypoints = plan.stops.map { $0.site.position }
                    openInMaps(MapsHandoff.shared.directionsUrl(
                        origin: nil,
                        destination: plan.destination.position,
                        waypoints: waypoints
                    ))
                } label: {
                    Label(NSLocalizedString("trip_send_maps", comment: ""), systemImage: "map.fill")
                }
            }
        }
        .navigationTitle(ChargeStopFormatter.shared.label(destination: plan.destination))
        .navigationBarTitleDisplayMode(.inline)
    }
}

/// One planned stop: what the plan expects here.
struct StopDetailView: View {
    let stop: PlannedStop

    var body: some View {
        List {
            Section {
                Text(stop.site.name).font(.headline)
                if let operatorName = stop.site.operator_ {
                    Text(operatorName).font(.subheadline)
                }
                Text(String(
                    format: NSLocalizedString("detail_plan_fmt", comment: ""),
                    Int(stop.chargeMinutes.rounded()),
                    Int(stop.arrivalSocPercent.rounded()),
                    Int(stop.departureSocPercent.rounded())
                ))
            }

            Section {
                Button {
                    openInMaps(MapsHandoff.shared.navigateUrl(target: stop.site.position))
                } label: {
                    Label(NSLocalizedString("phone_detail_navigate", comment: ""), systemImage: "map.fill")
                }
            }
        }
        .navigationTitle(NSLocalizedString("detail_title", comment: ""))
        .navigationBarTitleDisplayMode(.inline)
    }
}

/// "9 h 16 min" or "42 min".
func minutesText(_ minutes: Double) -> String {
    let total = Int(minutes.rounded())
    let hours = total / 60
    let rest = total % 60
    return hours > 0 ? "\(hours) h \(rest) min" : "\(rest) min"
}
