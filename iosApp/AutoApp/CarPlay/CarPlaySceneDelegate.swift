import CarPlay
import Shared

/// CarPlay scene delegate.
///
/// Translation only — no computation or formatting happens here. Every
/// number shown here comes from `ChargeStopFormatter` (see AGENTS.md rules
/// for the car UI). New compared to M0: the list is refreshed on every state
/// change, since it changes continuously while driving.
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

        // Only the sections are swapped, not the root template: creating a
        // new template on every location update would reset the driver's
        // scroll position.
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
        // CarPlay has no runtime query for a row limit like Android Auto's
        // ConstraintManager. Apple's guidelines recommend short, scannable
        // lists; 12 rows is the fixed cap.
        let maxRows = 12
        let stops = Array(viewModel.stops.prefix(maxRows))

        let items = stops.map { stop -> CPListItem in
            let title = [stop.site.name, stop.site.operator_]
                .compactMap { $0 }
                .joined(separator: " · ")
            let primaryLine = ChargeStopFormatter.shared.primaryLine(stop: stop)
            let secondaryLine = ChargeStopFormatter.shared.secondaryLine(stop: stop)

            let item = CPListItem(text: title, detailText: primaryLine)
            // CPListItem has no third text field like Android Auto's Row.
            // Both lines therefore go into detailText separated by a line break.
            item.setDetailText("\(primaryLine)\n\(secondaryLine)")
            return item
        }

        return CPListSection(items: items)
    }
}
