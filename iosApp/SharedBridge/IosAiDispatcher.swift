import Foundation
import UIKit
import TrueFindShared

struct IosCapturePayload: Identifiable {
    let id = UUID()
    let image: UIImage?
    let imageUrl: URL?
    let text: String?
    let sourceLabel: String
    let frameCount: Int

    init(
        image: UIImage?,
        imageUrl: URL?,
        text: String?,
        sourceLabel: String,
        frameCount: Int = 1
    ) {
        self.image = image
        self.imageUrl = imageUrl
        self.text = text
        self.sourceLabel = sourceLabel
        self.frameCount = frameCount
    }

    var hasImage: Bool { image != nil || imageUrl != nil }

    func resolvedImage() -> UIImage? {
        if let image { return image }
        if let url = imageUrl { return UIImage(contentsOfFile: url.path) }
        return nil
    }
}

enum IosAiDispatcher {

    static func composeAndCopyPrompt(promptBody: String, payload: IosCapturePayload) -> String {
        let finalPrompt = PromptComposer.shared.compose(
            promptBody: promptBody,
            capturedText: payload.text,
            hasImage: payload.hasImage
        )
        if !finalPrompt.isEmpty {
            UIPasteboard.general.string = finalPrompt
        }
        return finalPrompt
    }

    static func buildShareItems(promptBody: String, payload: IosCapturePayload) -> [Any] {
        let finalPrompt = composeAndCopyPrompt(promptBody: promptBody, payload: payload)
        var items: [Any] = []
        if let image = payload.image {
            items.append(image)
        } else if let url = payload.imageUrl, let data = try? Data(contentsOf: url), let img = UIImage(data: data) {
            items.append(img)
        }
        if !finalPrompt.isEmpty {
            items.append(finalPrompt)
        }
        return items
    }

    static func buildDirectUrl(for target: AiTargetApp, finalPrompt: String, hasImage: BooleanLike = false) -> URL? {
        // When an image is attached, native iOS Share Sheet (UIActivityViewController) is required
        // because iOS URL schemes cannot carry image attachments directly.
        if hasImage {
            return URL(string: target.iosUrlScheme)
        }
        if let base = target.webPromptBaseUrl,
           base.contains("?q="),
           let encoded = finalPrompt.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed),
           let webUrl = URL(string: base + encoded) {
            return webUrl
        }
        return URL(string: target.iosUrlScheme)
    }
}

typealias BooleanLike = Bool
