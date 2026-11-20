import SwiftUI
import UIKit

@main
struct TrueFindApp: App {
    @State private var activePayload: IosCapturePayload? = nil

    var body: some Scene {
        WindowGroup {
            MainSettingsView(activePayload: $activePayload)
                .onAppear {
                    consumePendingShortcutCapture()
                }
                .onReceive(NotificationCenter.default.publisher(for: .trueFindPendingShortcutCapture)) { _ in
                    consumePendingShortcutCapture()
                }
                .onOpenURL { url in
                    handleIncomingUrl(url)
                }
        }
    }

    private func consumePendingShortcutCapture() {
        let defaults = TrueFindSharedContainer.shared.defaults
        guard let path = defaults.string(forKey: TrueFindSharedContainer.pendingShortcutImageKey),
              !path.isEmpty else { return }
        defaults.removeObject(forKey: TrueFindSharedContainer.pendingShortcutImageKey)

        let fileUrl = URL(fileURLWithPath: path)
        if let image = UIImage(contentsOfFile: path) {
            activePayload = IosCapturePayload(
                image: image,
                imageUrl: fileUrl,
                text: nil,
                sourceLabel: "已透過 iPhone 捷徑擷取畫面"
            )
        }
    }

    private func handleIncomingUrl(_ url: URL) {
        guard url.scheme == "truefind" else { return }
        let components = URLComponents(url: url, resolvingAgainstBaseURL: false)
        if let text = components?.queryItems?.first(where: { $0.name == "text" })?.value,
           !text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            activePayload = IosCapturePayload(
                image: nil,
                imageUrl: nil,
                text: text,
                sourceLabel: "已接收連結文字"
            )
        } else {
            consumePendingShortcutCapture()
        }
    }
}
