import SwiftUI
import Shared

/// The Android map marker in SwiftUI: bolts by speed and the operator's short name on a capsule;
/// with live data a tinted end carries free/total, a red ring marks a site out of order.
struct ChargerPill: View {
    let charger: MapCharger

    var body: some View {
        let look = MarkerLook(charger)
        HStack(spacing: 0) {
            HStack(spacing: 5) {
                Bolts(count: look.bolts, color: look.boltColor)
                if let label = look.label {
                    Text(label)
                        .font(.system(size: 13, weight: .medium))
                        .foregroundStyle(.primary)
                        .lineLimit(1)
                }
            }
            .padding(.horizontal, 6)
            .padding(.vertical, 3)
            if let availability = look.availability {
                Group {
                    switch availability {
                    case .live(let free, let total, let level):
                        Text("\(free)/\(total)")
                            .font(.system(size: 12, weight: .semibold))
                            .foregroundStyle(level.color)
                    case .outOfOrder:
                        Image(systemName: "nosign")
                            .font(.system(size: 12, weight: .semibold))
                            .foregroundStyle(.red)
                    }
                }
                .padding(.leading, 6)
                .padding(.trailing, 8)
                .frame(maxHeight: .infinity)
                .background(availability.tint)
            }
        }
        .fixedSize()
        .background(Color(.tertiarySystemBackground))
        .clipShape(Capsule())
        .overlay(Capsule().strokeBorder(look.ringColor, lineWidth: look.outOfOrder ? 2 : 1))
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(look.accessibilityLabel)
    }
}

/// Far zoom: the availability colour when live, otherwise the speed colour; grey in a red ring when out of order.
struct ChargerDot: View {
    let charger: MapCharger

    var body: some View {
        let look = MarkerLook(charger)
        Circle()
            .fill(look.dotColor)
            .padding(look.outOfOrder ? 3 : 2)
            .frame(width: 15, height: 15)
            .background(Color(.tertiarySystemBackground), in: Circle())
            .overlay(Circle().strokeBorder(look.ringColor, lineWidth: look.outOfOrder ? 2 : 1))
            .accessibilityElement(children: .ignore)
            .accessibilityLabel(look.accessibilityLabel)
    }
}

/// Overlapping bolts; each one's halo in the capsule colour cuts an edge into the one before.
private struct Bolts: View {
    let count: Int
    let color: Color

    var body: some View {
        ZStack(alignment: .leading) {
            ForEach(0..<count, id: \.self) { index in
                ZStack {
                    Image(systemName: "bolt.fill")
                        .font(.system(size: 15))
                        .foregroundStyle(Color(.tertiarySystemBackground))
                    Image(systemName: "bolt.fill")
                        .font(.system(size: 12))
                        .foregroundStyle(color)
                }
                .offset(x: CGFloat(index) * 6)
            }
        }
        .frame(width: 12 + CGFloat(max(count - 1, 0)) * 6, alignment: .leading)
    }
}

/// What the shared domain says about a charger, in the terms the marker draws.
private struct MarkerLook {
    enum Availability {
        case live(free: Int32, total: Int32, level: AvailabilityLevel)
        case outOfOrder

        var tint: Color {
            switch self {
            case .live(_, _, let level): return level.color.opacity(0.15)
            case .outOfOrder: return Color.red.opacity(0.15)
            }
        }
    }

    let bolts: Int
    let speedColor: Color
    let label: String?
    let availability: Availability?
    let name: String

    init(_ charger: MapCharger) {
        let speed = ChargeSpeed.companion.of(maxPowerKw: charger.maxPowerKw)
        bolts = Int(speed.bolts)
        speedColor = speed.color
        label = OperatorShortName.shared.of(operator: charger.site.operator)
        name = charger.site.name
        if let siteAvailability = charger.availability {
            switch onEnum(of: siteAvailability) {
            case .live(let live): availability = .live(free: live.free, total: live.total, level: live.level)
            case .outOfOrder: availability = .outOfOrder
            }
        } else {
            availability = nil
        }
    }

    var outOfOrder: Bool {
        if case .outOfOrder = availability { return true }
        return false
    }

    var boltColor: Color { outOfOrder ? .gray : speedColor }
    var ringColor: Color { outOfOrder ? .red : Color(.separator) }

    var dotColor: Color {
        switch availability {
        case .live(_, _, let level): return level.color
        case .outOfOrder: return .gray
        case nil: return speedColor
        }
    }

    var accessibilityLabel: String {
        switch availability {
        case .live(let free, let total, _): return "\(name), \(free)/\(total)"
        case .outOfOrder: return "\(name), \(localized("map_out_of_order"))"
        case nil: return name
        }
    }
}

private extension ChargeSpeed {
    var color: Color {
        switch self {
        case .slow: return .red
        case .medium: return .orange
        case .fast, .ultra, .hyper: return .green
        }
    }
}

private extension AvailabilityLevel {
    var color: Color {
        switch self {
        case .good: return .green
        case .low: return .orange
        case .none: return .red
        }
    }
}
