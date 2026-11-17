//
//  ClipboxUITests.swift
//  iosApp
//
//  在 iOS 27 模拟器上驱动共享 Compose UI，覆盖配对、发送、键盘避让、实时接收、复制/粘贴、
//  后台补拉、分享草稿、宽屏导航、主题与三语切换。
//  不进入 CI；分享扩展的系统面板与粘贴权限弹窗属模拟器不稳定场景，由验收脚本另行留证。
//

import XCTest

final class ClipboxUITests: XCTestCase {

    private let serverBase = "http://127.0.0.1:8080"
    // 本地联调服务端的管理令牌，与服务端启动命令中的 ADMIN_TOKEN 保持一致
    private let adminToken = String(repeating: "a", count: 32)
    // 与验收脚本写入 App Group 的 share-draft.json 正文保持一致
    private let seededShareDraft = "ios-share-draft-e2e-1120"

    override func setUpWithError() throws {
        continueAfterFailure = false
    }

    // 前置：全新安装（未配对）。覆盖配对、发送、第二设备接收、复制/粘贴与后台补拉。
    func testPairingSendReceiveCopyPasteBackground() throws {
        XCUIDevice.shared.orientation = .portrait
        let app = XCUIApplication()
        app.launch()

        // 1. 未配对空态进入配对页，默认地址指向本机调试服务端
        let pairEntry = app.buttons["去配对"]
        XCTAssertTrue(pairEntry.waitForExistence(timeout: 20))
        pairEntry.tap()
        XCTAssertTrue(app.staticTexts["6 位配对码"].waitForExistence(timeout: 10))
        // Compose 文本输入框把正文暴露在 label；顺序为服务器地址、配对码、设备名
        let pairingFields = app.textViews
        XCTAssertEqual(pairingFields.element(boundBy: 0).label, "http://127.0.0.1:8080")

        let code = try requestPairingCode()
        let codeField = pairingFields.element(boundBy: 1)
        codeField.tap()
        codeField.typeText(code)

        let pairButton = app.buttons["配对"]
        expectation(for: NSPredicate(format: "isEnabled == true"), evaluatedWith: pairButton)
        waitForExpectations(timeout: 10)
        pairButton.tap()

        // 2. 配对后进入历史页；发送需满足键盘避让，并由第二设备 REST 读取确认落库
        XCTAssertTrue(app.textViews.element(boundBy: 1).waitForExistence(timeout: 20))
        let composer = app.textViews.element(boundBy: 1)
        composer.tap()
        let keyboard = app.keyboards.firstMatch
        XCTAssertTrue(keyboard.waitForExistence(timeout: 10))

        let sentText = "ios-sent-\(Int(Date().timeIntervalSince1970))"
        composer.typeText(sentText)
        let sendButton = app.buttons["发送"]
        XCTAssertTrue(sendButton.waitForExistence(timeout: 5))
        XCTAssertTrue(sendButton.isEnabled)
        XCTAssertTrue(sendButton.isHittable)
        XCTAssertLessThanOrEqual(sendButton.frame.maxY, keyboard.frame.minY + 1)
        addScreenshot(named: "ios-keyboard-avoidance")
        sendButton.tap()
        let secondDevice = try pairSecondDevice(name: "XCUITest 对照设备")
        XCTAssertTrue(try waitForServerClip(token: secondDevice.token, content: sentText))

        // 新记录位于置顶组之后，可能不在视口内；用搜索过滤定位，同时覆盖本地搜索
        search(app, for: "ios-sent-")
        let sentRow = rowElement(app, containing: sentText)
        XCTAssertTrue(sentRow.waitForExistence(timeout: 20))
        addScreenshot(named: "ios-sent")
        clearSearch(app)

        // 3. 第二设备写入后前台实时出现；点击记录复制，再经显式「粘贴」回填输入框
        let incomingText = "ios-received-\(Int(Date().timeIntervalSince1970))"
        try createClip(token: secondDevice.token, content: incomingText)
        search(app, for: "ios-received-")
        let incomingRow = rowElement(app, containing: incomingText)
        XCTAssertTrue(incomingRow.waitForExistence(timeout: 20))
        addScreenshot(named: "ios-received-ws")
        incomingRow.tap()
        XCTAssertTrue(app.staticTexts["已复制到剪贴板"].waitForExistence(timeout: 5))
        clearSearch(app)

        composer.tap()
        let pasteButton = app.buttons["粘贴"]
        XCTAssertTrue(pasteButton.waitForExistence(timeout: 5))
        pasteButton.tap()
        XCTAssertTrue(enteredText(composer).contains(incomingText))
        addScreenshot(named: "ios-copied-pasted")

        // 4. 后台不建立同步：回桌面期间写入的记录在返回前台后经 REST 补拉出现，全程无通知
        XCUIDevice.shared.press(.home)
        Thread.sleep(forTimeInterval: 4)
        let backgroundText = "ios-background-\(Int(Date().timeIntervalSince1970))"
        try createClip(token: secondDevice.token, content: backgroundText)
        let springboard = XCUIApplication(bundleIdentifier: "com.apple.springboard")
        XCTAssertFalse(rowElement(springboard, containing: backgroundText).exists)
        Thread.sleep(forTimeInterval: 2)
        app.activate()
        search(app, for: "ios-background-")
        let backgroundRow = rowElement(app, containing: backgroundText)
        XCTAssertTrue(backgroundRow.waitForExistence(timeout: 25))
        addScreenshot(named: "ios-foreground-catchup")

        // 末尾把后台期间到达的正文留在剪贴板，供验收脚本 simctl pbpaste 复核
        backgroundRow.tap()
        XCTAssertTrue(app.staticTexts["已复制到剪贴板"].waitForExistence(timeout: 5))
    }

    // 前置：已配对，验收脚本已向 App Group 写入 share-draft.json
    func testShareDraftPrefillWhenPaired() throws {
        let app = XCUIApplication()
        app.launch()
        let composer = app.textViews.element(boundBy: 1)
        XCTAssertTrue(composer.waitForExistence(timeout: 20))
        XCTAssertTrue(enteredText(composer).contains(seededShareDraft))
        addScreenshot(named: "ios-share-draft-prefill")
    }

    // 前置：已配对。为「未配对分享 → 配对后预填」场景清空配对状态。
    func testUnpairForDraftScenario() throws {
        let app = XCUIApplication()
        app.launch()
        let settingsTab = topLevelTab(app, "设置")
        XCTAssertTrue(settingsTab.waitForExistence(timeout: 20))
        settingsTab.tap()

        // 行按钮的无障碍标签把标题与副标题合并成多行文本，只能按前缀定位
        let unbind = app.buttons.matching(NSPredicate(format: "label BEGINSWITH %@", "解除配对")).firstMatch
        for _ in 0..<10 where !unbind.isHittable {
            app.swipeUp()
        }
        XCTAssertTrue(unbind.waitForExistence(timeout: 5))
        unbind.tap()
        let confirm = app.buttons.matching(NSPredicate(format: "label BEGINSWITH %@", "确认")).firstMatch
        XCTAssertTrue(confirm.waitForExistence(timeout: 5))
        confirm.tap()
        XCTAssertTrue(app.staticTexts["6 位配对码"].waitForExistence(timeout: 10))
        addScreenshot(named: "ios-unpaired")
    }

    // 前置：未配对，验收脚本已向 App Group 写入 share-draft.json；配对成功后草稿预填且不自动发送
    func testPairAfterUnpairedShareDraft() throws {
        let app = XCUIApplication()
        app.launch()
        XCTAssertTrue(app.staticTexts["6 位配对码"].waitForExistence(timeout: 20))

        let code = try requestPairingCode()
        let codeField = app.textViews.element(boundBy: 1)
        codeField.tap()
        codeField.typeText(code)
        let pairButton = app.buttons["配对"]
        expectation(for: NSPredicate(format: "isEnabled == true"), evaluatedWith: pairButton)
        waitForExpectations(timeout: 10)
        pairButton.tap()

        let composer = app.textViews.element(boundBy: 1)
        XCTAssertTrue(composer.waitForExistence(timeout: 20))
        XCTAssertTrue(enteredText(composer).contains(seededShareDraft))
        Thread.sleep(forTimeInterval: 3)
        XCTAssertTrue(enteredText(composer).contains(seededShareDraft), "草稿不能被自动发送")
        addScreenshot(named: "ios-unpaired-draft-paired-prefill")
    }

    // 宽屏断点为 600dp：iPad 竖屏使用侧边导航，iPhone 竖屏使用底部导航；旋转后页面状态保持
    func testAdaptiveLayoutAppearanceAndRotation() throws {
        XCUIDevice.shared.orientation = .portrait
        let app = XCUIApplication()
        app.launch()

        let isPad = UIDevice.current.userInterfaceIdiom == .pad
        let settingsTab = topLevelTab(app, "设置")
        XCTAssertTrue(settingsTab.waitForExistence(timeout: 20))
        if isPad {
            XCTAssertLessThan(settingsTab.frame.minX, 240, "iPad 应使用左侧 NavigationRail")
        } else {
            XCTAssertGreaterThan(settingsTab.frame.width, 100, "iPhone 竖屏应使用底部导航")
            XCTAssertGreaterThan(settingsTab.frame.minY, 300, "iPhone 底部导航应位于内容区下方")
        }

        settingsTab.tap()
        XCTAssertTrue(app.staticTexts["设置"].waitForExistence(timeout: 5))

        let languageRow = app.buttons.matching(NSPredicate(format: "label CONTAINS %@", "语言")).firstMatch
        for _ in 0..<3 where !languageRow.isHittable {
            app.swipeUp()
        }
        XCTAssertTrue(languageRow.waitForExistence(timeout: 5))
        languageRow.tap()

        XCTAssertTrue(app.staticTexts["外观与语言"].waitForExistence(timeout: 5))
        addScreenshot(named: isPad ? "ipad-appearance-zh-hans" : "iphone-appearance-zh-hans")

        let english = app.staticTexts["English"].firstMatch
        XCTAssertTrue(english.waitForExistence(timeout: 5))
        english.tap()
        XCTAssertTrue(app.staticTexts["Appearance and language"].waitForExistence(timeout: 5))
        addScreenshot(named: isPad ? "ipad-appearance-en" : "iphone-appearance-en")

        let dark = app.staticTexts["Dark"].firstMatch
        XCTAssertTrue(dark.waitForExistence(timeout: 5))
        dark.tap()
        addScreenshot(named: isPad ? "ipad-appearance-en-dark" : "iphone-appearance-en-dark")

        let traditional = app.staticTexts["繁體中文"].firstMatch
        XCTAssertTrue(traditional.waitForExistence(timeout: 5))
        traditional.tap()
        XCTAssertTrue(app.staticTexts["外觀與語言"].waitForExistence(timeout: 5))
        addScreenshot(named: isPad ? "ipad-appearance-zh-hant-dark" : "iphone-appearance-zh-hant-dark")

        XCUIDevice.shared.orientation = .landscapeLeft
        XCTAssertTrue(app.staticTexts["外觀與語言"].waitForExistence(timeout: 5))
        waitForRotation()
        addScreenshot(named: isPad ? "ipad-landscape-appearance" : "iphone-landscape-appearance")

        XCUIDevice.shared.orientation = .portrait
        waitForRotation()
        let simplified = app.staticTexts["简体中文"].firstMatch
        XCTAssertTrue(simplified.waitForExistence(timeout: 5))
        simplified.tap()
        let systemTheme = app.staticTexts["跟随系统"].firstMatch
        XCTAssertTrue(systemTheme.waitForExistence(timeout: 5))
        systemTheme.tap()

        // 返回键在旋转与语言重置后偶尔拿不到稳定语义节点；重启后回到起始页取证更可靠
        app.terminate()
        app.launch()
        XCTAssertTrue(app.staticTexts["字匣"].waitForExistence(timeout: 20))
        addScreenshot(named: isPad ? "ipad-portrait-home" : "iphone-portrait-home")
        XCUIDevice.shared.orientation = .landscapeLeft
        waitForRotation()
        addScreenshot(named: isPad ? "ipad-landscape-home" : "iphone-landscape-home")
        XCUIDevice.shared.orientation = .portrait
        waitForRotation()
    }

    // 无头模拟器的窗口像素缓冲不随旋转改变尺寸，无法用 frame 判断完成；等待旋转动画结束后再截图
    private func waitForRotation() {
        Thread.sleep(forTimeInterval: 2)
    }

    // 历史页右上角也有“设置”图标；一级导航项宽度远大于图标按钮，用它区分
    private func topLevelTab(_ app: XCUIApplication, _ label: String) -> XCUIElement {
        let candidates = app.buttons.matching(NSPredicate(format: "label == %@", label)).allElementsBoundByIndex
        return candidates.first { $0.frame.width > 80 } ?? app.buttons[label]
    }

    // Compose 文本输入框把正文放在 label，空内容时才回退到 value
    private func enteredText(_ element: XCUIElement) -> String {
        if !element.label.isEmpty {
            return element.label
        }
        return element.value as? String ?? ""
    }

    // 记录行在 iOS 无障碍树中会把正文、来源与时间合并成一个节点，按 label 包含关系定位
    private func rowElement(_ app: XCUIApplication, containing text: String) -> XCUIElement {
        app.descendants(matching: .any)
            .matching(NSPredicate(format: "label CONTAINS %@", text))
            .firstMatch
    }

    private func search(_ app: XCUIApplication, for text: String) {
        let searchField = app.textViews.element(boundBy: 0)
        XCTAssertTrue(searchField.waitForExistence(timeout: 5))
        searchField.tap()
        searchField.typeText(text)
    }

    private func clearSearch(_ app: XCUIApplication) {
        let clear = app.buttons["清空"].firstMatch
        if clear.exists {
            clear.tap()
        }
    }

    private func waitForServerClip(token: String, content: String) throws -> Bool {
        for _ in 0..<10 {
            if try serverHasClip(token: token, content: content) {
                return true
            }
            Thread.sleep(forTimeInterval: 1)
        }
        return false
    }

    private func addScreenshot(named name: String) {
        let attachment = XCTAttachment(screenshot: XCUIScreen.main.screenshot())
        attachment.name = name
        attachment.lifetime = .keepAlways
        add(attachment)
    }

    private func requestPairingCode() throws -> String {
        guard let url = URL(string: serverBase + "/api/v1/admin/pairing-codes") else {
            throw NSError(domain: "ClipboxUITests", code: 1)
        }
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue(adminToken, forHTTPHeaderField: "X-Admin-Token")
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.httpBody = Data("{}".utf8)

        let semaphore = DispatchSemaphore(value: 0)
        var result: Result<String, Error> = .failure(NSError(domain: "ClipboxUITests", code: 2))
        URLSession.shared.dataTask(with: request) { data, response, error in
            defer { semaphore.signal() }
            if let error {
                result = .failure(error)
                return
            }
            guard let http = response as? HTTPURLResponse,
                  http.statusCode == 201,
                  let data,
                  let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
                  let code = json["code"] as? String
            else {
                result = .failure(NSError(domain: "ClipboxUITests", code: 3))
                return
            }
            result = .success(code)
        }.resume()
        _ = semaphore.wait(timeout: .now() + 15)
        return try result.get()
    }

    private struct TestDevice {
        let token: String
        let deviceId: String
    }

    // 通过管理接口配对一台对照设备，用于替代 Desktop / Android 观察 iOS 的写入
    private func pairSecondDevice(name: String) throws -> TestDevice {
        let code = try requestPairingCode()
        let body: [String: Any] = ["code": code, "deviceName": name]
        let json = try sendJSON(
            method: "POST",
            path: "/api/v1/pair",
            body: body,
            expected: [201],
        )
        guard let token = json["token"] as? String, let deviceId = json["deviceId"] as? String else {
            throw NSError(domain: "ClipboxUITests", code: 4)
        }
        return TestDevice(token: token, deviceId: deviceId)
    }

    private func createClip(token: String, content: String) throws {
        let body: [String: Any] = [
            "id": makeUlid(),
            "content": content,
            "createdAt": Int(Date().timeIntervalSince1970 * 1000),
        ]
        _ = try sendJSON(
            method: "POST",
            path: "/api/v1/clips",
            body: body,
            expected: [200, 201],
            token: token,
        )
    }

    private func serverHasClip(token: String, content: String) throws -> Bool {
        var since = 0
        for _ in 0..<5 {
            let json = try sendJSON(
                method: "GET",
                path: "/api/v1/clips?since=\(since)&limit=500",
                body: nil,
                expected: [200],
                token: token,
            )
            let items = json["items"] as? [[String: Any]] ?? []
            if items.contains(where: { ($0["content"] as? String) == content }) {
                return true
            }
            guard json["hasMore"] as? Bool == true, let next = json["nextSince"] as? Int else {
                return false
            }
            since = next
        }
        return false
    }

    private func sendJSON(
        method: String,
        path: String,
        body: [String: Any]?,
        expected: Set<Int>,
        token: String? = nil,
    ) throws -> [String: Any] {
        guard let url = URL(string: serverBase + path) else {
            throw NSError(domain: "ClipboxUITests", code: 1)
        }
        var request = URLRequest(url: url)
        request.httpMethod = method
        request.timeoutInterval = 15
        if let token {
            request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        }
        if let body {
            request.setValue("application/json", forHTTPHeaderField: "Content-Type")
            request.httpBody = try JSONSerialization.data(withJSONObject: body)
        }

        let semaphore = DispatchSemaphore(value: 0)
        var result: Result<[String: Any], Error> = .failure(NSError(domain: "ClipboxUITests", code: 2))
        URLSession.shared.dataTask(with: request) { data, response, error in
            defer { semaphore.signal() }
            if let error {
                result = .failure(error)
                return
            }
            guard let http = response as? HTTPURLResponse,
                  expected.contains(http.statusCode),
                  let data,
                  let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any]
            else {
                let status = (response as? HTTPURLResponse)?.statusCode ?? -1
                result = .failure(NSError(domain: "ClipboxUITests", code: status))
                return
            }
            result = .success(json)
        }.resume()
        _ = semaphore.wait(timeout: .now() + 20)
        return try result.get()
    }

    // 服务端校验 ULID 格式；用 48 位毫秒时间 + 80 位随机数生成合法值
    private func makeUlid() -> String {
        let alphabet = Array("0123456789ABCDEFGHJKMNPQRSTVWXYZ")
        var time = UInt64(Date().timeIntervalSince1970 * 1000)
        var chars = [Character](repeating: "0", count: 26)
        for index in stride(from: 9, through: 0, by: -1) {
            chars[index] = alphabet[Int(time % 32)]
            time /= 32
        }
        for index in 10..<26 {
            chars[index] = alphabet[Int.random(in: 0..<32)]
        }
        return String(chars)
    }
}
