import SwiftUI
import UIKit
import UniformTypeIdentifiers

final class ShareViewController: UIViewController {

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = .clear
        loadSharedPayload { [weak self] payload in
            guard let self else { return }
            guard let payload else {
                self.closeExtension()
                return
            }
            self.presentChooser(payload: payload)
        }
    }

    private func presentChooser(payload: IosCapturePayload) {
        let chooser = ChooserSheetView(
            payload: payload,
            onOpenUrl: { [weak self] url in
                self?.extensionContext?.open(url, completionHandler: nil)
            },
            onDismiss: { [weak self] in
                self?.closeExtension()
            }
        )

        let hosting = UIHostingController(rootView: chooser)
        addChild(hosting)
        hosting.view.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(hosting.view)
        NSLayoutConstraint.activate([
            hosting.view.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            hosting.view.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            hosting.view.topAnchor.constraint(equalTo: view.topAnchor),
            hosting.view.bottomAnchor.constraint(equalTo: view.bottomAnchor)
        ])
        hosting.didMove(toParent: self)
    }

    private func loadSharedPayload(completion: @escaping (IosCapturePayload?) -> Void) {
        guard let items = extensionContext?.inputItems as? [NSExtensionItem] else {
            completion(nil)
            return
        }
        let providers = items.flatMap { $0.attachments ?? [] }

        let imageProviders = providers.filter {
            $0.hasItemConformingToTypeIdentifier(UTType.image.identifier)
        }
        if !imageProviders.isEmpty {
            let limited = Array(imageProviders.prefix(5))
            let group = DispatchGroup()
            var orderedImages = [UIImage?](repeating: nil, count: limited.count)
            let lock = NSLock()

            for (idx, provider) in limited.enumerated() {
                group.enter()
                provider.loadItem(forTypeIdentifier: UTType.image.identifier, options: nil) { item, _ in
                    let loadedImage: UIImage?
                    if let img = item as? UIImage {
                        loadedImage = img
                    } else if let url = item as? URL, let data = try? Data(contentsOf: url) {
                        loadedImage = UIImage(data: data)
                    } else if let data = item as? Data {
                        loadedImage = UIImage(data: data)
                    } else {
                        loadedImage = nil
                    }
                    lock.lock()
                    orderedImages[idx] = loadedImage
                    lock.unlock()
                    group.leave()
                }
            }

            group.notify(queue: .global(qos: .userInitiated)) {
                let validImages = orderedImages.compactMap { $0 }
                guard !validImages.isEmpty else {
                    DispatchQueue.main.async { completion(nil) }
                    return
                }
                let finalImage = validImages.count > 1
                    ? IosImageStitcher.stitch(frames: validImages)
                    : validImages[0]
                let label = validImages.count > 1
                    ? "已自動拼接長截圖"
                    : "已接收分享圖片／截圖"
                DispatchQueue.main.async {
                    completion(
                        IosCapturePayload(
                            image: finalImage,
                            imageUrl: nil,
                            text: nil,
                            sourceLabel: label,
                            frameCount: validImages.count
                        )
                    )
                }
            }
            return
        }

        if let urlProvider = providers.first(where: { $0.hasItemConformingToTypeIdentifier(UTType.url.identifier) }) {
            urlProvider.loadItem(forTypeIdentifier: UTType.url.identifier, options: nil) { item, _ in
                let text = (item as? URL)?.absoluteString
                DispatchQueue.main.async {
                    completion(
                        text.map {
                            IosCapturePayload(image: nil, imageUrl: nil, text: $0, sourceLabel: "已接收分享連結")
                        }
                    )
                }
            }
            return
        }

        if let textProvider = providers.first(where: { $0.hasItemConformingToTypeIdentifier(UTType.plainText.identifier) }) {
            textProvider.loadItem(forTypeIdentifier: UTType.plainText.identifier, options: nil) { item, _ in
                let text = item as? String
                DispatchQueue.main.async {
                    completion(
                        text.map {
                            IosCapturePayload(image: nil, imageUrl: nil, text: $0, sourceLabel: "已接收分享文字")
                        }
                    )
                }
            }
            return
        }

        completion(nil)
    }

    private func closeExtension() {
        extensionContext?.completeRequest(returningItems: [], completionHandler: nil)
    }
}
