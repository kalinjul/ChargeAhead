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
                    Text(localized(
                        "ios_trip_summary_time",
                        minutesText(plan.totalMinutes),
                        minutesText(plan.chargeMinutes)
                    ))
                    .font(.subheadline)
                    Text(localized(
                        "ios_trip_summary_arrival",
                        Int(plan.arrivalSocPercent.rounded())
                    ))
                    .font(.footnote)
                    .foregroundStyle(.secondary)
                }
            }

            Section(localized("phone_title_stops")) {
                TripEndRow(
                    title: plan.startAddress ?? localized("trip_start"),
                    time: clockText(0),
                    levelPercent: nil
                )
                ForEach(Array(plan.stops.enumerated()), id: \.element.site.id) { index, stop in
                    TripLegRow(leg: plan.legs[index])
                    NavigationLink {
                        StopDetailView(stop: stop)
                    } label: {
                        TripStopRow(number: index + 1, stop: stop, arrival: clockText(stop.arrivalMinutesFromStart))
                    }
                    .listRowBackground(RoundedRectangle(cornerRadius: 12).fill(Color(.secondarySystemGroupedBackground)))
                }
                TripLegRow(leg: plan.legs[plan.stops.count])
                TripEndRow(
                    title: plan.destination.name,
                    time: clockText(plan.totalMinutes),
                    levelPercent: plan.arrivalSocPercent
                )
            }
            .listRowSeparator(.hidden)
            .listRowBackground(Color.clear)

            Section {
                Button {
                    let waypoints = plan.stops.map { $0.site.position }
                    openInMaps(MapsHandoff.shared.directionsUrl(
                        origin: nil,
                        destination: plan.destination.position,
                        waypoints: waypoints
                    ))
                } label: {
                    Label(localized("trip_send_maps"), systemImage: "map.fill")
                }
            }
        }
        .navigationTitle(ChargeStopFormatter.shared.label(destination: plan.destination))
        .navigationBarTitleDisplayMode(.inline)
    }

    /// Wall-clock time that many minutes from now.
    private func clockText(_ minutesFromStart: Double) -> String {
        Date().addingTimeInterval(minutesFromStart * 60).formatted(date: .omitted, time: .shortened)
    }
}

private let tripMarkerSize: CGFloat = 24
private let tripMarkerGap: CGFloat = 10

/// The trip's start or destination: a pin, the name and one time, the charge level below.
private struct TripEndRow: View {
    let title: String
    let time: String
    let levelPercent: Double?

    var body: some View {
        VStack(alignment: .leading, spacing: 3) {
            HStack(spacing: tripMarkerGap) {
                Image(systemName: "mappin.circle.fill")
                    .foregroundStyle(.tint)
                    .frame(width: tripMarkerSize, height: tripMarkerSize)
                Text(title).font(.subheadline.weight(.medium)).lineLimit(1)
                Spacer()
                Text(time).font(.subheadline.weight(.medium)).monospacedDigit()
            }
            if let levelPercent {
                HStack(spacing: 6) {
                    BatteryLevelIcon(percent: levelPercent)
                    Text(localized("garage_percent", Int(levelPercent.rounded()))).font(.footnote.weight(.medium))
                }
                .padding(.leading, tripMarkerSize + tripMarkerGap)
            }
        }
    }
}

/// One stop as a card: operator, town and arrival, then the levels, the power and the charging time.
private struct TripStopRow: View {
    let number: Int
    let stop: PlannedStop
    let arrival: String

    var body: some View {
        VStack(alignment: .leading, spacing: 3) {
            HStack(spacing: tripMarkerGap) {
                Text("\(number)")
                    .font(.caption2.weight(.heavy))
                    .foregroundStyle(.white)
                    .frame(width: tripMarkerSize, height: tripMarkerSize)
                    .background(Circle().fill(.tint))
                Text(stop.site.`operator` ?? stop.site.name).font(.subheadline.weight(.medium)).lineLimit(1)
                if let town = stop.site.address?.town {
                    Text(town).font(.footnote).foregroundStyle(.secondary).lineLimit(1)
                }
                Spacer()
                Text(arrival).font(.subheadline.weight(.medium)).monospacedDigit()
            }
            HStack(spacing: 6) {
                BatteryLevelIcon(percent: stop.arrivalSocPercent)
                HStack(spacing: 3) {
                    Text(localized("garage_percent", Int(stop.arrivalSocPercent.rounded())))
                    Image(systemName: "arrow.right").imageScale(.small)
                    Text(localized("garage_percent", Int(stop.departureSocPercent.rounded())))
                }
                .font(.footnote.weight(.medium))
                Text("·").foregroundStyle(.secondary)
                Text(localized("garage_kw", Int(stop.maxPowerKw.rounded()))).foregroundStyle(.secondary)
                Spacer()
                Text(minutesText(stop.chargeMinutes)).foregroundStyle(.secondary)
            }
            .font(.footnote)
            .monospacedDigit()
            .padding(.leading, tripMarkerSize + tripMarkerGap)
        }
    }
}

/// The drive to the row below: a dotted line under the markers, time and distance beside it.
private struct TripLegRow: View {
    let leg: TripLeg

    var body: some View {
        HStack(spacing: tripMarkerGap) {
            Rectangle()
                .fill(.clear)
                .frame(width: tripMarkerSize, height: 22)
                .overlay {
                    GeometryReader { box in
                        Path { path in
                            path.move(to: CGPoint(x: box.size.width / 2, y: 1))
                            path.addLine(to: CGPoint(x: box.size.width / 2, y: box.size.height - 1))
                        }
                        .stroke(Color(.separator), style: StrokeStyle(lineWidth: 2, lineCap: .round, dash: [0, 5]))
                    }
                }
            Text("\(minutesText(leg.driveMinutes)) · \(localized("trip_summary_distance", Int(leg.distanceKm.rounded())))")
                .font(.caption)
                .foregroundStyle(.secondary)
                .monospacedDigit()
        }
        .listRowInsets(EdgeInsets(top: 0, leading: 20, bottom: 0, trailing: 20))
    }
}

/// The system battery glyph nearest to the level.
private struct BatteryLevelIcon: View {
    let percent: Double

    var body: some View {
        let step = min(100, max(0, Int((percent / 25).rounded()) * 25))
        Image(systemName: "battery.\(step)percent").foregroundStyle(.secondary)
    }
}

/// One planned stop: what the plan expects here.
struct StopDetailView: View {
    let stop: PlannedStop

    var body: some View {
        List {
            Section {
                Text(stop.site.name).font(.headline)
                if let operatorName = stop.site.`operator` {
                    Text(operatorName).font(.subheadline)
                }
                Text(localized(
                    "detail_kv_charge_value",
                    Int(stop.chargeMinutes.rounded()),
                    Int(stop.arrivalSocPercent.rounded()),
                    Int(stop.departureSocPercent.rounded())
                ))
            }

            Section {
                Button {
                    openInMaps(MapsHandoff.shared.navigateUrl(target: stop.site.position))
                } label: {
                    Label(localized("phone_detail_navigate"), systemImage: "map.fill")
                }
            }
        }
        .navigationTitle(localized("detail_title"))
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
