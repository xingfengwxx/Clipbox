import SwiftUI
import ComposeApp

// 宿主只负责窗口容器；导航、主题与业务全部由共享 Compose 层承担
@main
struct iOSApp: App {
    @Environment(\.scenePhase) private var scenePhase

    var body: some Scene {
        WindowGroup {
            ContentView()
                // 分享扩展写入草稿后经 clipbox://share 唤起；深链只触发本地草稿检查，不携带任何正文或令牌
                .onOpenURL { url in
                    if url.scheme == "clipbox" {
                        IosAppLifecycle.shared.onOpenShareUrl()
                    }
                }
        }
        .onChange(of: scenePhase) { phase in
            switch phase {
            case .active:
                IosAppLifecycle.shared.onForeground()
            case .background:
                IosAppLifecycle.shared.onBackground()
            default:
                // .inactive 不断开：切换控制中心或来电横幅时保持同步连接
                break
            }
        }
    }
}
