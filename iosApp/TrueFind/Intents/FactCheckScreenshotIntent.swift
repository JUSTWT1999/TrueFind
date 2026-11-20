import AppIntents
import Foundation

struct FactCheckScreenshotIntent: AppIntent {
    static var title: LocalizedStringResource = "事實查核截圖"
    static var description = IntentDescription("將拍攝的截圖送進 TrueFind 查核面板，快速派發給 ChatGPT 或 Gemini。")
    static var openAppWhenRun: Bool = true

    @Parameter(
        title: "截圖圖片",
        supportedTypeIdentifiers: ["public.image"]
    )
    var image: IntentFile

    @MainActor
    func perform() async throws -> some IntentResult {
        if let savedUrl = TrueFindSharedContainer.shared.saveTemporaryImageData(image.data, prefix: "shortcut") {
            TrueFindSharedContainer.shared.defaults.set(
                savedUrl.path,
                forKey: TrueFindSharedContainer.pendingShortcutImageKey
            )
            NotificationCenter.default.post(name: .trueFindPendingShortcutCapture, object: savedUrl)
        }
        return .result()
    }
}

extension Notification.Name {
    static let trueFindPendingShortcutCapture = Notification.Name("trueFindPendingShortcutCapture")
}
