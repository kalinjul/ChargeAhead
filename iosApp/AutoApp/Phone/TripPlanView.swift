import SwiftUI
import Shared

/// The planned trip: summary, stops, hand-off to Google Maps. The numbers all
/// come from the shared planner — this view only arranges them (AGENTS.md:
/// platforms translate, they don't compute).
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
                    HStack {
                        if let cost = plan.estimatedCostEuro {
                            Text(String(format: NSLocalizedString("trip_summary_cost_fmt", comment: ""), cost.doubleValue))
                        }
                        Text(String(
                            format: NSLocalizedString("trip_summary_arrival_fmt", comment: ""),
                            Int(plan.arrivalSocPercent.rounded())
                        ))
                    }
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
                            HStack {
                                Text(String(
                                    format: "%.0f min · %.0f kW",
                                    stop.chargeMinutes,
                                    stop.maxPowerKw
                                ))
                                .font(.footnote)
                                .foregroundStyle(.secondary)
                                Spacer()
                                if let best = stop.quote.best {
                                    Text(String(format: "%.2f €/kWh", best.euroPerKwh))
                                        .font(.subheadline)
                                        .foregroundStyle(.green)
                                }
                            }
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
                Text(NSLocalizedString("trip_estimate_note", comment: ""))
                    .font(.footnote)
                    .foregroundStyle(.secondary)
            }
        }
        .navigationTitle(plan.destination.name)
        .navigationBarTitleDisplayMode(.inline)
    }
}

/// One planned stop: what the plan expects here and what it costs by tariff.
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

            Section(NSLocalizedString("detail_prices", comment: "")) {
                ForEach(Array(stop.quote.prices.enumerated()), id: \.offset) { _, price in
                    HStack {
                        Text(price.label)
                        Spacer()
                        Text(String(format: "%.2f €/kWh", price.euroPerKwh))
                            .foregroundStyle(price == stop.quote.best ? .green : .primary)
                    }
                }
                if stop.quote.isEstimate {
                    Text(NSLocalizedString("trip_estimate_note", comment: ""))
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                }
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

/// "9 h 16 min" or "42 min" — mirrors the Android helper so both phones say the same thing.
func minutesText(_ minutes: Double) -> String {
    let total = Int(minutes.rounded())
    let hours = total / 60
    let rest = total % 60
    return hours > 0 ? "\(hours) h \(rest) min" : "\(rest) min"
}
