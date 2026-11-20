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

        if let imageProvider = providers.first(where: { $0.hasItemConformingToTypeIdentifier(UTType.image.identifier) }) {
            imageProvider.loadItem(forTypeIdentifier: UTType.image.identifier, options: nil) { item, _ in
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

                DispatchQueue.main.async {
                    guard let loadedImage else {
                        completion(nil)
                        return
                    }
                    completion(
                        IosCapturePayload(
                            image: loadedImage,
                            imageUrl: nil,
                            text: nil,
                            sourceLabel: "已接收分享圖片／截圖"
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
