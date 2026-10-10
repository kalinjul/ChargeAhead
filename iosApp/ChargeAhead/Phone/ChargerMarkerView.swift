import SwiftUI
import UIKit
import Shared

/// The Android map marker in SwiftUI: bolts by speed and the operator's short name on a capsule;
/// with live data a tinted end carries free/total, a red ring marks a site out of order.
struct ChargerPill: View {
    let charger: MapCharger

    @ScaledMetric(relativeTo: .footnote) private var labelSize = 13
    @ScaledMetric(relativeTo: .footnote) private var countSize = 12

    var body: some View {
        let look = MarkerLook(charger)
        HStack(spacing: 0) {
            HStack(spacing: 5) {
                Bolts(count: look.bolts, color: look.boltTone.strong)
                if let label = look.label {
                    Text(label)
                        .font(.system(size: labelSize, weight: .medium))
                        .foregroundStyle(.primary)
                        .lineLimit(1)
                }
            }
            .padding(.horizontal, 6)
            .padding(.vertical, 3)
            if let availability = look.availability {
                Group {
                    switch availability {
                    case .live(let free, let total, let tone):
                        Text("\(free)/\(total)")
                            .font(.system(size: countSize, weight: .semibold))
                            .foregroundStyle(tone.strong)
                    case .outOfOrder:
                        Image(systemName: "nosign")
                            .font(.system(size: countSize, weight: .semibold))
                            .foregroundStyle(MarkerTone.bad.strong)
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
            .fill(look.dotTone.fill)
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

    @ScaledMetric(relativeTo: .footnote) private var size = 14

    var body: some View {
        HStack(spacing: -size * 0.55) {
            ForEach(0..<count, id: \.self) { _ in
                ZStack {
                    Image(systemName: "bolt.fill")
                        .font(.system(size: size + 3))
                        .foregroundStyle(Color(.tertiarySystemBackground))
                    Image(systemName: "bolt.fill")
                        .font(.system(size: size))
                        .foregroundStyle(color)
                }
            }
        }
    }
}

/// What the shared domain says about a charger, in the terms the marker draws.
private struct MarkerLook {
    enum Availability {
        case live(free: Int32, total: Int32, tone: MarkerTone)
        case outOfOrder

        var tint: Color {
            switch self {
            case .live(_, _, let tone): return tone.fill.opacity(0.15)
            case .outOfOrder: return MarkerTone.bad.fill.opacity(0.15)
            }
        }
    }

    let bolts: Int
    let boltTone: MarkerTone
    let dotTone: MarkerTone
    let label: String?
    let availability: Availability?
    let name: String

    init(_ charger: MapCharger) {
        let speed = ChargeSpeed.companion.of(maxPowerKw: charger.maxPowerKw)
        bolts = Int(speed.bolts)
        boltTone = MarkerTone.companion.bolts(speed: speed, availability: charger.availability)
        dotTone = MarkerTone.companion.dot(speed: speed, availability: charger.availability)
        label = OperatorShortName.shared.of(operator: charger.site.operator)
        name = charger.site.name
        if let siteAvailability = charger.availability {
            switch onEnum(of: siteAvailability) {
            case .live(let live):
                availability = .live(free: live.free, total: live.total, tone: dotTone)
            case .outOfOrder:
                availability = .outOfOrder
            }
        } else {
            availability = nil
        }
    }

    var outOfOrder: Bool {
        if case .outOfOrder = availability { return true }
        return false
    }

    var ringColor: Color { outOfOrder ? MarkerTone.bad.fill : Color(.separator) }

    var accessibilityLabel: String {
        switch availability {
        case .live(let free, let total, _): return "\(name), \(localized("fmt_available_of", free, total))"
        case .outOfOrder: return "\(name), \(localized("map_out_of_order"))"
        case nil: return name
        }
    }
}

private extension MarkerTone {
    var fill: Color {
        switch self {
        case .good: return .green
        case .warn: return .orange
        case .bad: return .red
        case .muted: return .gray
        }
    }

    /// Bolts and counts on the light capsule need more contrast than the system colours give;
    /// the same values as Android's marker palette.
    var strong: Color {
        switch self {
        case .good: return Color(light: 0x188038, dark: 0x81C995)
        case .warn: return Color(light: 0xB06000, dark: 0xFDD663)
        case .bad: return Color(light: 0xD93025, dark: 0xF28B82)
        case .muted: return Color(light: 0x9AA0A6, dark: 0x80868B)
        }
    }
}

private extension Color {
    init(light: UInt32, dark: UInt32) {
        self.init(UIColor { traits in
            UIColor(rgb: traits.userInterfaceStyle == .dark ? dark : light)
        })
    }
}

private extension UIColor {
    convenience init(rgb: UInt32) {
        self.init(
            red: CGFloat((rgb >> 16) & 0xFF) / 255,
            green: CGFloat((rgb >> 8) & 0xFF) / 255,
            blue: CGFloat(rgb & 0xFF) / 255,
            alpha: 1
        )
    }
}
