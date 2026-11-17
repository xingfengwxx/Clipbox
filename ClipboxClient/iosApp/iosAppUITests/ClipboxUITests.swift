//
//  ClipboxUITests.swift
//  iosApp
//
//  在 iOS 27 模拟器上驱动共享 Compose UI，覆盖配对、发送、键盘避让、宽屏导航、主题与三语切换。
//  不进入 CI，不替代 T-IOS-09 的全量验收。
//

import XCTest

final class ClipboxUITests: XCTestCase {

    private let serverBase = "http://127.0.0.1:8080"
    // 本地联调服务端的管理令牌，与服务端启动命令中的 ADMIN_TOKEN 保持一致
    private let adminToken = String(repeating: "a", count: 32)

    override func setUpWithError() throws {
        continueAfterFailure = false
    }

    func testPairingNavigationSendingKeyboard() throws {
        let app = XCUIApplication()
        app.launch()

        // 1. 未配对空态与底部导航往返
        XCTAssertTrue(app.buttons["去配对"].waitForExistence(timeout: 20))
        app.buttons["工具箱"].tap()
        XCTAssertTrue(app.staticTexts["页面占位内容"].waitForExistence(timeout: 5))
        tapBottomTab(app, "设置")
        XCTAssertTrue(app.staticTexts["页面占位内容"].waitForExistence(timeout: 5))
        tapBottomTab(app, "历史")
        XCTAssertTrue(app.buttons["去配对"].waitForExistence(timeout: 5))

        // 2. 配对：输入服务端新生成的 6 位码
        app.buttons["去配对"].tap()
        let codeField = app.textViews.element(boundBy: 0)
        XCTAssertTrue(codeField.waitForExistence(timeout: 10))
        let code = try requestPairingCode()
        codeField.tap()
        codeField.typeText(code)

        let pairButton = app.buttons["配对"]
        expectation(for: NSPredicate(format: "isEnabled == true"), evaluatedWith: pairButton)
        waitForExpectations(timeout: 10)
        pairButton.tap()

        let placeholder = app.staticTexts["输入或粘贴文本，发送到所有设备…"]
        XCTAssertTrue(placeholder.waitForExistence(timeout: 20))

        // 3. 发送与键盘避让：键盘弹出时发送按钮仍需在键盘上方可点
        // 历史页顶部已有搜索输入框，发送器是第二个文本输入
        let composer = app.textViews.element(boundBy: 1)
        composer.tap()
        let keyboard = app.keyboards.firstMatch
        XCTAssertTrue(keyboard.waitForExistence(timeout: 10))

        let message = "xcuitest-\(Int(Date().timeIntervalSince1970))"
        composer.typeText(message)

        let sendButton = app.buttons["发送"]
        XCTAssertTrue(sendButton.waitForExistence(timeout: 5))
        XCTAssertTrue(sendButton.isEnabled)
        XCTAssertTrue(sendButton.isHittable)
        XCTAssertLessThanOrEqual(sendButton.frame.maxY, keyboard.frame.minY + 1)
        addScreenshot(named: "keyboard-avoidance")

        sendButton.tap()

        XCTAssertTrue(app.staticTexts[message].waitForExistence(timeout: 20))
        addScreenshot(named: "sent")
    }

    // 宽屏断点为 600dp：iPad 竖屏使用侧边导航，iPhone 竖屏使用底部导航；旋转后页面状态保持
    func testAdaptiveLayoutAppearanceAndRotation() throws {
        XCUIDevice.shared.orientation = .portrait
        let app = XCUIApplication()
        app.launch()

        let isPad = UIDevice.current.userInterfaceIdiom == .pad
        // 历史页右上角也有“设置”图标；一级导航项宽度远大于图标按钮，用它区分
        let topLevelTab: (String) -> XCUIElement = { label in
            let candidates = app.buttons.matching(NSPredicate(format: "label == %@", label)).allElementsBoundByIndex
            return candidates.first { $0.frame.width > 80 } ?? app.buttons[label]
        }
        let settingsTab = topLevelTab("设置")
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

    private func tapBottomTab(_ app: XCUIApplication, _ label: String) {
        let matches = app.buttons.matching(NSPredicate(format: "label == %@", label))
        let bottom = matches.allElementsBoundByIndex.first { $0.frame.minY > 700 }
        guard let bottom else {
            XCTFail("底部导航项 \(label) 未找到")
            return
        }
        bottom.tap()
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
}
