import SwiftUI
import UIKit
import ComposeApp

// 宿主只做全屏透传：刘海、状态栏与 Home Indicator 由共享导航壳的 safeDrawing 避让，背景由 Compose tokens 铺满；
// 键盘区域同样交给 Compose 处理，避免 SwiftUI 与 Compose 重复收缩布局
struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

struct ContentView: View {
    var body: some View {
        ComposeView()
            .ignoresSafeArea()
    }
}
