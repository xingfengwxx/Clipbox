import SwiftUI

// 宿主只负责窗口容器；导航、主题与业务全部由共享 Compose 层承担
@main
struct iOSApp: App {
    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}
