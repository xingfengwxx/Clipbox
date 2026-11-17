import SwiftUI
import UIKit
import ComposeApp

// Compose 自带安全区与键盘避让，宿主只透传键盘区域，避免 SwiftUI 与 Compose 重复收缩布局
struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

struct ContentView: View {
    var body: some View {
        ComposeView()
            .ignoresSafeArea(.keyboard)
    }
}
