//
//  ClipboxUITests.swift
//  iosApp
//
//  仅用于 T-IOS-03 的运行时验证：在 iOS 27 模拟器上驱动共享 Compose UI，覆盖配对、发送、
//  键盘避让与底部导航。不进入 CI，不替代 T-IOS-09 的全量验收。
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
        let composer = app.textViews.element(boundBy: 0)
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
