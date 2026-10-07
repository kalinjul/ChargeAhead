import SwiftUI
import Shared

/// Holds a set of shared ViewModels for as long as the SwiftUI view that owns it.
final class ViewModelOwner: ObservableObject {
    let viewModels: PhoneViewModels

    init(feature: ChargeStopsFeature) {
        viewModels = PhoneViewModels(feature: feature)
    }

    deinit {
        viewModels.clear()
    }
}

/// The phone's location and charge state, and the map's ViewModels over it.
final class PhoneSession: ObservableObject {
    let feature: ChargeStopsFeature
    let viewModels: PhoneViewModels
    let planner: TripPlanner

    init() {
        feature = SharedEntry.newFeature()
        viewModels = PhoneViewModels(feature: feature)
        planner = TripPlanner()
    }

    deinit {
        viewModels.clear()
        feature.close()
    }
}

extension HomeUiState {
    /// Location status for the map's caption; `nil` when there is nothing to say.
    var statusText: String? {
        if locationUnavailable { return localized("phone_status_location_unavailable") }
        if searchingLocation { return localized("phone_status_waiting") }
        if loadingSites { return localized("phone_status_loading") }
        return nil
    }
}

/// The phone home: the map is the screen, actions float on top.
struct HomeMapView: View {

    @StateObject private var session = PhoneSession()
    @State private var showPlanSheet = false
    @State private var showChargeNow = false
    @State private var showGarage = false
    @State private var planFailure: TripPlanOutcome.TripPlanFailure?
    @State private var plan: TripPlan?
    @State private var planFailureText: String?
    @State private var planningInProgress = false

    var body: some View {
        let home = session.viewModels.home()
        NavigationStack {
            Observing(home.uiState) { state in
                ZStack {
                    ChargerMapView(position: state.position, chargers: state.chargers) { viewport in
                        home.onViewportChanged(viewport: viewport)
                    }
                    .ignoresSafeArea(edges: .bottom)

                    VStack {
                        VStack(spacing: 2) {
                            if state.belowMinZoom {
                                Text(localized("map_zoom_hint"))
                                    .font(.footnote)
                                    .padding(.horizontal, 12)
                                    .padding(.vertical, 6)
                                    .background(.regularMaterial, in: Capsule())
                            }
                            if let status = state.statusText {
                                Text(status)
                                    .font(.footnote)
                                    .foregroundStyle(.secondary)
                            }
                            if planningInProgress {
                                ProgressView(localized("plan_planning"))
                                    .padding(.top, 8)
                            }
                            if let failure = planFailureText {
                                Text(failure)
                                    .font(.footnote)
                                    .foregroundStyle(.red)
                                    .padding(.top, 8)
                            }
                            if planFailure == .noVehicle {
                                Button {
                                    showGarage = true
                                } label: {
                                    Label(localized("garage_add_title"), systemImage: "car")
                                }
                                .buttonStyle(.bordered)
                            }
                        }
                        .padding(.top, 8)

                        Spacer()

                        HStack(spacing: 12) {
                            Button {
                                showPlanSheet = true
                            } label: {
                                Label(
                                    localized("ios_home_pill_plan"),
                                    systemImage: "arrow.triangle.turn.up.right.diamond.fill"
                                )
                                .padding(.horizontal, 4)
                            }
                            .buttonStyle(.borderedProminent)

                            Button {
                                showChargeNow = true
                            } label: {
                                Label(
                                    localized("home_pill_charge_now"),
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
                .navigationDestination(
                    isPresented: Binding(
                        get: { plan != nil },
                        set: { presented in if !presented { plan = nil } },
                    ),
                ) {
                    if let plannedTrip = plan {
                        TripPlanView(plan: plannedTrip, ownPosition: state.position)
                    }
                }
                .sheet(isPresented: $showPlanSheet) {
                    PlanSheetView(feature: session.feature) { destination in
                        showPlanSheet = false
                        startPlanning(to: destination, from: state.position)
                    }
                }
            }
            .navigationTitle(localized("app_name"))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button {
                        showGarage = true
                    } label: {
                        Image(systemName: "car")
                    }
                    .accessibilityLabel(localized("garage_title"))
                    .accessibilityIdentifier("garage")
                }
                ToolbarItem(placement: .topBarTrailing) {
                    NavigationLink(localized("ios_home_diagnostics")) {
                        ContentView()
                    }
                }
            }
            .sheet(isPresented: $showChargeNow) {
                ChargeNowView(feature: session.feature)
            }
            .sheet(isPresented: $showGarage, onDismiss: { planFailure = nil; planFailureText = nil }) {
                GarageView(feature: session.feature)
            }
        }
        .onAppear {
            home.onLocationPermissionGranted()
        }
    }

    private func startPlanning(to destination: Destination, from position: LatLon?) {
        guard let position = position else {
            planFailureText = localized("home_no_position")
            return
        }
        planningInProgress = true
        planFailureText = nil
        planFailure = nil
        Task { @MainActor in
            let outcome = try? await session.planner.planTrip(from: position, destination: destination)
            planningInProgress = false
            if let planned = outcome?.plan {
                plan = planned
            } else {
                planFailure = outcome?.failure
                planFailureText = failureText(outcome?.failure)
            }
        }
    }

    private func failureText(_ failure: TripPlanOutcome.TripPlanFailure?) -> String {
        switch failure {
        case .noVehicle: return localized("ios_plan_vehicle_missing")
        case .noChargerInReach: return localized("ios_plan_failed_no_charger")
        case .noConnection: return localized("plan_failed_no_connection")
        default: return localized("plan_failed_no_route")
        }
    }
}

/// Destination search — the shared geocoder behind a plain text field.
struct PlanSheetView: View {
    let onPlan: (Destination) -> Void

    @StateObject private var owner: ViewModelOwner
    @State private var query = ""

    init(feature: ChargeStopsFeature, onPlan: @escaping (Destination) -> Void) {
        self.onPlan = onPlan
        _owner = StateObject(wrappedValue: ViewModelOwner(feature: feature))
    }

    var body: some View {
        let search = owner.viewModels.search()
        NavigationStack {
            Observing(search.uiState) { state in
                List {
                    ForEach(state.results ?? [], id: \.name) { place in
                        Button(place.name) {
                            onPlan(Destination(name: place.name, position: place.position, address: ChargeStopFormatter.shared.detailLine(place: place)))
                        }
                    }
                }
            }
            .searchable(text: $query, prompt: localized("plan_search_hint"))
            .onChange(of: query) { changed in
                search.onQueryChanged(query: changed)
            }
            .navigationTitle(localized("ios_plan_title"))
            .navigationBarTitleDisplayMode(.inline)
        }
    }
}

/// The best chargers nearby, on the shared ranking.
struct ChargeNowView: View {
    @StateObject private var owner: ViewModelOwner

    init(feature: ChargeStopsFeature) {
        _owner = StateObject(wrappedValue: ViewModelOwner(feature: feature))
    }

    var body: some View {
        let chargeNow = owner.viewModels.chargeNow()
        NavigationStack {
            Observing(chargeNow.uiState) { state in
                List {
                    switch onEnum(of: state) {
                    case .noPosition:
                        Text(localized("home_no_position"))
                    case .loading:
                        ProgressView(localized("cn_loading"))
                    case .ready(let ready):
                        ChargeNowRows(result: ready.result)
                    }
                }
            }
            .navigationTitle(localized("cn_title"))
            .navigationBarTitleDisplayMode(.inline)
            .onAppear {
                chargeNow.onSheetOpened()
            }
        }
    }
}

private struct ChargeNowRows: View {
    let result: ChargeNowResult

    var body: some View {
        if !result.relaxed.isEmpty {
            Text(localized("ios_cn_relaxed_note"))
                .font(.footnote)
                .foregroundStyle(.red)
        }
        ForEach(Array(result.candidates.enumerated()), id: \.element.site.id) { index, candidate in
            Button {
                openInMaps(MapsHandoff.shared.navigateUrl(target: candidate.site.position))
            } label: {
                VStack(alignment: .leading, spacing: 2) {
                    Text("\(index + 1) · \(candidate.site.name)")
                        .font(.headline)
                    Text("\(ChargeStopFormatter.shared.distanceLabel(distanceKm: candidate.distanceKm)) · \(ChargeStopFormatter.shared.powerKwLabel(powerKw: candidate.maxPowerKw))")
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                }
            }
        }
        if result.candidates.isEmpty {
            Text(localized("cn_empty"))
        }
    }
}

func openInMaps(_ url: String) {
    guard let parsed = URL(string: url) else { return }
    UIApplication.shared.open(parsed)
}
