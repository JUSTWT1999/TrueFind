import Foundation
import UIKit
import TrueFindShared

final class UserDefaultsKeyValueStore: KeyValueStore {
    private let defaults: UserDefaults

    init(defaults: UserDefaults) {
        self.defaults = defaults
    }

    func getString(key: String) -> String? {
        defaults.string(forKey: key)
    }

    func putString(key: String, value: String?) {
        if let value {
            defaults.set(value, forKey: key)
        } else {
            defaults.removeObject(forKey: key)
        }
    }
}

final class TrueFindSharedContainer {
    static let appGroupId = "group.com.truefind.app"
    static let pendingShortcutImageKey = "pending_shortcut_image_path"
    static let lastTemplateIdKey = "last_template_id"

    static let shared = TrueFindSharedContainer()

    let defaults: UserDefaults
    let templateRepository: PromptTemplateRepository

    private init() {
        let groupDefaults = UserDefaults(suiteName: Self.appGroupId) ?? .standard
        self.defaults = groupDefaults
        self.templateRepository = PromptTemplateRepository(
            store: UserDefaultsKeyValueStore(defaults: groupDefaults)
        )
        cleanupExpiredCaptures()
    }

    var lastTemplateId: String? {
        get { defaults.string(forKey: Self.lastTemplateIdKey) }
        set { defaults.set(newValue, forKey: Self.lastTemplateIdKey) }
    }

    var capturesDirectory: URL {
        let base = FileManager.default.containerURL(forSecurityApplicationGroupIdentifier: Self.appGroupId)
            ?? FileManager.default.temporaryDirectory
        let dir = base.appendingPathComponent("captures", isDirectory: true)
        try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        return dir
    }

    func saveTemporaryImageData(_ data: Data, prefix: String = "ios_cap") -> URL? {
        let fileUrl = capturesDirectory.appendingPathComponent("\(prefix)_\(Int(Date().timeIntervalSince1970 * 1000)).png")
        do {
            try data.write(to: fileUrl, options: .atomic)
            cleanupExpiredCaptures()
            return fileUrl
        } catch {
            return nil
        }
    }

    func cleanupExpiredCaptures(ttlSeconds: TimeInterval = 3600, maxKeep: Int = 3) {
        let fm = FileManager.default
        guard let urls = try? fm.contentsOfDirectory(
            at: capturesDirectory,
            includingPropertiesForKeys: [.contentModificationDateKey],
            options: [.skipsHiddenFiles]
        ) else { return }

        let now = Date()
        var surviving: [(URL, Date)] = []
        for url in urls {
            let mod = (try? url.resourceValues(forKeys: [.contentModificationDateKey]).contentModificationDate) ?? .distantPast
            if now.timeIntervalSince(mod) > ttlSeconds {
                try? fm.removeItem(at: url)
            } else {
                surviving.append((url, mod))
            }
        }

        if surviving.count > maxKeep {
            surviving.sort { $0.1 < $1.1 }
            for item in surviving.prefix(surviving.count - maxKeep) {
                try? fm.removeItem(at: item.0)
            }
        }
    }
}
