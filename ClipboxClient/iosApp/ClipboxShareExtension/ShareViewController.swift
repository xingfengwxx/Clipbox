import UIKit
import UniformTypeIdentifiers

// 分享扩展只负责把 text/plain 草稿写入 App Group 容器；不初始化网络、数据库或同步运行时，也不读取设备令牌。
// 注意：容器文件名与 JSON 字段（id / text / createdAt）是主 App ShareDraftStore 的共享契约，修改必须两端同步。
// 扩展语言跟随主 App 写入 App Group 的 appLanguage；未设置时回退简体中文，不使用系统语言。
final class ShareViewController: UIViewController {

    private let appGroupIdentifier = "group.com.wangxingxing.clipbox"
    private let draftFileName = "share-draft.json"
    private let maxUtf8Bytes = 65_536

    private let statusLabel = UILabel()
    private let doneButton = UIButton(type: .system)

    override func viewDidLoad() {
        super.viewDidLoad()
        configureView()
        loadSharedText()
    }

    private func configureView() {
        view.backgroundColor = .systemBackground

        statusLabel.translatesAutoresizingMaskIntoConstraints = false
        statusLabel.numberOfLines = 0
        statusLabel.textAlignment = .center
        statusLabel.text = localized("share_sending")

        doneButton.translatesAutoresizingMaskIntoConstraints = false
        doneButton.setTitle(localized("share_done"), for: .normal)
        doneButton.isHidden = true
        doneButton.addTarget(self, action: #selector(dismissExtension), for: .touchUpInside)

        view.addSubview(statusLabel)
        view.addSubview(doneButton)
        NSLayoutConstraint.activate([
            statusLabel.centerYAnchor.constraint(equalTo: view.centerYAnchor),
            statusLabel.leadingAnchor.constraint(equalTo: view.leadingAnchor, constant: 24),
            statusLabel.trailingAnchor.constraint(equalTo: view.trailingAnchor, constant: -24),
            doneButton.topAnchor.constraint(equalTo: statusLabel.bottomAnchor, constant: 24),
            doneButton.centerXAnchor.constraint(equalTo: view.centerXAnchor),
        ])
    }

    private func loadSharedText() {
        guard let items = extensionContext?.inputItems as? [NSExtensionItem] else {
            showFailure(localized("share_error_unreadable"))
            return
        }
        let providers = items.flatMap { $0.attachments ?? [] }
        guard let provider = providers.first(where: {
            $0.hasItemConformingToTypeIdentifier(UTType.plainText.identifier)
        }) else {
            showFailure(localized("share_error_plain_text_only"))
            return
        }
        provider.loadItem(forTypeIdentifier: UTType.plainText.identifier, options: nil) { [weak self] item, _ in
            DispatchQueue.main.async {
                guard let self else { return }
                if let text = Self.extractText(item) {
                    self.writeDraft(text)
                } else {
                    self.showFailure(self.localized("share_error_read_retry"))
                }
            }
        }
    }

    private static func extractText(_ item: Any?) -> String? {
        switch item {
        case let text as String:
            return text
        case let attributed as NSAttributedString:
            return attributed.string
        case let data as Data:
            return String(data: data, encoding: .utf8)
        default:
            return nil
        }
    }

    private func writeDraft(_ text: String) {
        guard text.utf8.count <= maxUtf8Bytes else {
            showFailure(localized("share_error_too_large"))
            return
        }
        guard let containerURL = FileManager.default.containerURL(
            forSecurityApplicationGroupIdentifier: appGroupIdentifier
        ) else {
            showFailure(localized("share_error_storage"))
            return
        }
        let draft: [String: Any] = [
            "id": UUID().uuidString,
            "text": text,
            "createdAt": Int(Date().timeIntervalSince1970 * 1000),
        ]
        do {
            let data = try JSONSerialization.data(withJSONObject: draft, options: [])
            let fileURL = containerURL.appendingPathComponent(draftFileName)
            try data.write(to: fileURL, options: .atomic)
            finishWithSuccess()
        } catch {
            showFailure(localized("share_error_write_retry"))
        }
    }

    private func finishWithSuccess() {
        statusLabel.text = localized("share_sent")
        doneButton.isHidden = false
        guard let url = URL(string: "clipbox://share") else { return }
        extensionContext?.open(url) { [weak self] opened in
            // 分享扩展不保证允许打开自定义 URL；打不开时保留提示与「完成」按钮，草稿仍会在主 App 下次启动时消费
            if opened {
                self?.extensionContext?.completeRequest(returningItems: nil)
            }
        }
    }

    private func showFailure(_ reason: String) {
        statusLabel.text = String(format: localized("share_failed_format"), reason)
        doneButton.isHidden = false
    }

    private func localized(_ key: String) -> String {
        let appLanguage = UserDefaults(suiteName: appGroupIdentifier)?.string(forKey: "appLanguage")
        let resource: String
        switch appLanguage {
        case "en":
            resource = "en"
        case "zh-TW", "zh-Hant":
            resource = "zh-Hant"
        default:
            resource = "zh-Hans"
        }
        guard let path = Bundle.main.path(forResource: resource, ofType: "lproj"),
              let bundle = Bundle(path: path) else {
            return Bundle.main.localizedString(forKey: key, value: key, table: nil)
        }
        return bundle.localizedString(forKey: key, value: key, table: nil)
    }

    @objc private func dismissExtension() {
        extensionContext?.completeRequest(returningItems: nil)
    }
}
