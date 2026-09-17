import CarPlay
import Shared

/// CarPlay scene delegate. Every number shown here comes from
/// `ChargeStopFormatter`; the list is refreshed on every state change.
class CarPlaySceneDelegate: NSObject, CPTemplateApplicationSceneDelegate {

    private var interfaceController: CPInterfaceController?
    private var listTemplate: CPListTemplate?
    private let viewModel = ChargeStopsViewModel()

    func templateApplicationScene(
        _ templateApplicationScene: CPTemplateApplicationScene,
        didConnect interfaceController: CPInterfaceController
    ) {
        self.interfaceController = interfaceController

        let template = makeListTemplate()
        self.listTemplate = template
        interfaceController.setRootTemplate(template, animated: false, completion: nil)

        // Only the sections are swapped, so the scroll position survives.
        viewModel.onStateChange = { [weak self] _ in
            self?.applyState()
        }
        applyState()
    }

    func templateApplicationScene(
        _ templateApplicationScene: CPTemplateApplicationScene,
        didDisconnectInterfaceController interfaceController: CPInterfaceController
    ) {
        viewModel.onStateChange = nil
        self.interfaceController = nil
        self.listTemplate = nil
    }

    private func applyState() {
        guard let template = listTemplate else { return }
        template.updateSections([makeSection()])
        template.emptyViewSubtitleVariants = [viewModel.statusText]
    }

    private func makeListTemplate() -> CPListTemplate {
        let template = CPListTemplate(
            title: NSLocalizedString("car_list_title", comment: ""),
            sections: [makeSection()]
        )
        template.emptyViewTitleVariants = [NSLocalizedString("car_list_title", comment: "")]
        template.emptyViewSubtitleVariants = [viewModel.statusText]
        return template
    }

    private func makeSection() -> CPListSection {
        // CarPlay has no runtime row limit like Android Auto's ConstraintManager.
        let maxRows = 12
        let stops = Array(viewModel.stops.prefix(maxRows))

        let items = stops.map { stop -> CPListItem in
            let title = [stop.site.name, stop.site.operator_]
                .compactMap { $0 }
                .joined(separator: " · ")
            let primaryLine = ChargeStopFormatter.shared.primaryLine(stop: stop)
            let secondaryLine = ChargeStopFormatter.shared.secondaryLine(stop: stop)

            let item = CPListItem(text: title, detailText: primaryLine)
            // CPListItem has no third text field, so both lines go into detailText.
            item.setDetailText("\(primaryLine)\n\(secondaryLine)")
            return item
        }

        return CPListSection(items: items)
    }
}
