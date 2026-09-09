import SwiftUI
import Shared

/// The phone home from the design mockup (docs/mockup): the map is the
/// screen, actions float on top. The map itself is a placeholder drawing —
/// the map SDK decision (MapKit vs. MapLibre) is still open in the ROADMAP,
/// and the flows shouldn't wait for it.
struct HomeMapView: View {

    @StateObject private var viewModel = ChargeStopsViewModel()
    @State private var showPlanSheet = false
    @State private var showChargeNow = false
    @State private var plan: TripPlan?
    @State private var planFailureText: String?
    @State private var planningInProgress = false

    private var planningBridge: PlanningBridge {
        PlanningBridge(feature: viewModel.feature)
    }

    var body: some View {
        NavigationStack {
            ZStack {
                MapPlaceholderView(
                    position: viewModel.state.position,
                    stops: viewModel.stops
                )
                .ignoresSafeArea(edges: .bottom)

                VStack {
                    VStack(spacing: 2) {
                        Text(NSLocalizedString("home_map_placeholder", comment: ""))
                            .font(.caption2)
                            .foregroundStyle(.secondary)
                        if viewModel.state.isDemo {
                            // Invented charging sites must say so — AGENTS.md.
                            Text(NSLocalizedString("phone_demo_notice_short", comment: ""))
                                .font(.caption2)
                                .foregroundStyle(.red)
                        }
                        if planningInProgress {
                            ProgressView(NSLocalizedString("plan_planning", comment: ""))
                                .padding(.top, 8)
                        }
                        if let failure = planFailureText {
                            Text(failure)
                                .font(.footnote)
                                .foregroundStyle(.red)
                                .padding(.top, 8)
                        }
                    }
                    .padding(.top, 8)

                    Spacer()

                    HStack(spacing: 12) {
                        Button {
                            showPlanSheet = true
                        } label: {
                            Label(
                                NSLocalizedString("home_pill_plan", comment: ""),
                                systemImage: "arrow.triangle.turn.up.right.diamond.fill"
                            )
                            .padding(.horizontal, 4)
                        }
                        .buttonStyle(.borderedProminent)

                        Button {
                            showChargeNow = true
                        } label: {
                            Label(
                                NSLocalizedString("home_pill_charge_now", comment: ""),
                                systemImage: "bolt.fill"
                            )
                            .padding(.horizontal, 4)
                        }
                        .buttonStyle(.bordered)
                        .tint(.green)
                    }
                    .padding(.bottom, 24)
                }
            }
            .navigationTitle(NSLocalizedString("app_name", comment: ""))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                NavigationLink(NSLocalizedString("home_diagnostics", comment: "")) {
                    ContentView()
                }
            }
            .navigationDestination(
                isPresented: Binding(
                    get: { plan != nil },
                    set: { presented in if !presented { plan = nil } },
                ),
            ) {
                if let plannedTrip = plan {
                    TripPlanView(plan: plannedTrip, ownPosition: viewModel.state.position)
                }
            }
            .sheet(isPresented: $showPlanSheet) {
                PlanSheetView(feature: viewModel.feature) { destination in
                    showPlanSheet = false
                    startPlanning(to: destination)
                }
            }
            .sheet(isPresented: $showChargeNow) {
                ChargeNowView(
                    bridge: planningBridge,
                    position: viewModel.state.position
                )
            }
        }
    }

    private func startPlanning(to destination: Destination) {
        guard let position = viewModel.state.position else {
            planFailureText = NSLocalizedString("home_no_position", comment: "")
            return
        }
        planningInProgress = true
        planFailureText = nil
        planningBridge.planTrip(from: position, destination: destination) { outcome in
            planningInProgress = false
            if let planned = outcome.plan {
                plan = planned
            } else {
                planFailureText = NSLocalizedString(failureKey(outcome.failure), comment: "")
            }
        }
    }

    private func failureKey(_ failure: TripPlanOutcome.TripPlanFailure?) -> String {
        switch failure {
        case .noVehicle: return "plan_vehicle_missing"
        case .noChargerInReach: return "plan_failed_no_charger"
        default: return "plan_failed_no_route"
        }
    }
}

/// Same stand-in as MapCanvas on Android: light ground, faint grid, pins,
/// own position. Honest about being a placeholder, useful for judging flows.
struct MapPlaceholderView: View {
    let position: LatLon?
    let stops: [ChargeStop]

    var body: some View {
        Canvas { context, size in
            context.fill(Path(CGRect(origin: .zero, size: size)), with: .color(Color(red: 0.94, green: 0.93, blue: 0.89)))

            let grid = Color(red: 0.89, green: 0.88, blue: 0.84)
            var x: CGFloat = 40
            while x < size.width {
                context.stroke(Path { $0.move(to: CGPoint(x: x, y: 0)); $0.addLine(to: CGPoint(x: x, y: size.height)) }, with: .color(grid), lineWidth: 1.5)
                x += 56
            }
            var y: CGFloat = 40
            while y < size.height {
                context.stroke(Path { $0.move(to: CGPoint(x: 0, y: y)); $0.addLine(to: CGPoint(x: size.width, y: y)) }, with: .color(grid), lineWidth: 1.5)
                y += 56
            }

            guard let center = position else { return }
            let radiusKm = 25.0
            let pxPerKm = min(size.width, size.height) / (radiusKm * 2)
            let kmPerDegLat = 111.19
            let kmPerDegLon = kmPerDegLat * cos(center.lat * .pi / 180)

            func project(_ point: LatLon) -> CGPoint {
                let dx = (point.lon - center.lon) * kmPerDegLon * pxPerKm
                let dy = (point.lat - center.lat) * kmPerDegLat * pxPerKm
                return CGPoint(x: size.width / 2 + dx, y: size.height / 2 - dy)
            }

            for stop in stops {
                let at = project(stop.site.position)
                context.fill(Path(ellipseIn: CGRect(x: at.x - 8, y: at.y - 8, width: 16, height: 16)), with: .color(.white))
                context.fill(Path(ellipseIn: CGRect(x: at.x - 6, y: at.y - 6, width: 12, height: 12)), with: .color(.blue))
            }

            let own = project(center)
            context.fill(Path(ellipseIn: CGRect(x: own.x - 18, y: own.y - 18, width: 36, height: 36)), with: .color(.blue.opacity(0.15)))
            context.fill(Path(ellipseIn: CGRect(x: own.x - 9, y: own.y - 9, width: 18, height: 18)), with: .color(.white))
            context.fill(Path(ellipseIn: CGRect(x: own.x - 6, y: own.y - 6, width: 12, height: 12)), with: .color(.blue))
        }
    }
}

/// Destination search — the shared Nominatim geocoder behind a plain text field.
struct PlanSheetView: View {
    let feature: ChargeStopsFeature
    let onPlan: (Destination) -> Void

    @State private var query = ""
    @State private var results: [Place] = []
    @State private var searchTask: Task<Void, Never>?

    var body: some View {
        NavigationStack {
            List {
                ForEach(results, id: \.name) { place in
                    Button(place.name) {
                        onPlan(Destination(name: place.name, position: place.position))
                    }
                }
            }
            .searchable(text: $query, prompt: NSLocalizedString("plan_search_hint", comment: ""))
            .onChange(of: query) { changed in
                searchTask?.cancel()
                let trimmed = changed.trimmingCharacters(in: .whitespaces)
                guard trimmed.count >= 3 else { return }
                // Debounced for the same reason as on Android: Nominatim
                // allows one request per second.
                searchTask = Task {
                    try? await Task.sleep(for: .milliseconds(600))
                    guard !Task.isCancelled else { return }
                    let places = try? await feature.searchDestinations(query: trimmed)
                    if !Task.isCancelled { results = places ?? [] }
                }
            }
            .navigationTitle(NSLocalizedString("plan_title", comment: ""))
            .navigationBarTitleDisplayMode(.inline)
        }
    }
}

/// The best chargers nearby — list form of the Android sheet, same shared ranking.
struct ChargeNowView: View {
    let bridge: PlanningBridge
    let position: LatLon?

    @State private var result: ChargeNowResult?

    var body: some View {
        NavigationStack {
            List {
                if position == nil {
                    Text(NSLocalizedString("home_no_position", comment: ""))
                } else if let outcome = result {
                    if !outcome.relaxed.isEmpty {
                        Text(NSLocalizedString("cn_relaxed_note", comment: ""))
                            .font(.footnote)
                            .foregroundStyle(.red)
                    }
                    ForEach(Array(outcome.candidates.enumerated()), id: \.element.site.id) { index, candidate in
                        Button {
                            openInMaps(MapsHandoff.shared.navigateUrl(target: candidate.site.position))
                        } label: {
                            VStack(alignment: .leading, spacing: 2) {
                                Text("\(index + 1) · \(candidate.site.name)")
                                    .font(.headline)
                                HStack {
                                    Text("\(ChargeStopFormatter.shared.distanceLabel(distanceKm: candidate.distanceKm)) · \(ChargeStopFormatter.shared.powerKwLabel(powerKw: candidate.maxPowerKw))")
                                        .font(.footnote)
                                        .foregroundStyle(.secondary)
                                    Spacer()
                                    if let best = candidate.quote.best {
                                        Text(ChargeStopFormatter.shared.pricePerKwhLabel(euroPerKwh: best.euroPerKwh))
                                            .font(.subheadline)
                                            .foregroundStyle(.green)
                                    }
                                }
                            }
                        }
                    }
                    if outcome.candidates.isEmpty {
                        Text(NSLocalizedString("cn_empty", comment: ""))
                    }
                } else {
                    ProgressView(NSLocalizedString("cn_loading", comment: ""))
                }
            }
            .navigationTitle(NSLocalizedString("cn_title", comment: ""))
            .navigationBarTitleDisplayMode(.inline)
            .onAppear {
                guard let position else { return }
                bridge.chargeNow(position: position) { result = $0 }
            }
        }
    }
}

func openInMaps(_ url: String) {
    guard let parsed = URL(string: url) else { return }
    UIApplication.shared.open(parsed)
}
