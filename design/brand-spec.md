# 字匣 Theca 品牌与资产规范

## 命名与工程代号

- 对外品牌：中文「字匣」（简繁同字），英文「Theca」（拉丁语「匣、藏所」）。
- `Clipbox` 保留为工程代号：包名 `com.wangxingxing.clipbox`、Bundle ID、URL Scheme `clipbox`、目录名、`Theme.Clipbox` 均不改；技术文档按工程代号书写，不当作对外品牌。
- 命名口径、淘汰名单与可用性核查（2026-11-16）：`brand/naming.md`；三方向展示与用户选择记录：`brand/direction-approved.md`。
- 本规范中的字标、图形、启动图标均为本次自建方案，资产母版在 `brand/`，导出位图在 `brand/exports/`。

## 产品一句话

离线优先的跨设备文本中转与本地工具箱。Android、iPhone / iPad 与电脑之间传文本，不必发消息给自己，也不必登录网页。

## 显示规则

| 位置 | 简体中文 / 繁體中文 | English |
| --- | --- | --- |
| Android 桌面名 | 字匣 | Theca |
| iOS 桌面名（T-04 落地） | 字匣 | Theca |
| Desktop 窗口标题 | Theca | Theca |
| 分享页页脚字标 | 字匣 | Theca |

- Android 用 `values/`、`values-zh-rTW/`、`values-en/` 三套 `app_name` 资源，跟随系统语言；不在代码里拼品牌名。
- iOS 在 T-04 建工程时用 `InfoPlist.strings` 提供 `CFBundleDisplayName`，同样跟随系统语言。
- 分享页页脚只放一行内联文本字标：不加载图片、外部字体或脚本，不出现图形母版。

## 字标

### 图形

- 结构：开口的匣（左上缺口是投入口）+ 2×3 字格线 + 右上角被抽出的填充块；填充块是全套品牌里唯一的信号橙。
- 母版（32×32 视图，可无损缩放）：

```svg
<svg viewBox="0 0 32 32" role="img" aria-label="字匣图形">
  <path fill="#191712" d="M15.2 5H6a3 3 0 0 0-3 3v16a3 3 0 0 0 3 3h20a3 3 0 0 0 3-3V15.2h-2V25H5V7h10.2V5Z"/>
  <path fill="none" stroke="#191712" stroke-width="1.6" d="M16 16V25M5 16H27"/>
  <rect fill="#A8430F" x="17" y="1" width="11" height="11" rx="1.5"/>
</svg>
```

- 变体：`brand/logo-symbol-mono.svg`（全黑单色）、`brand/logo-symbol-reverse.svg`（深底用纸白 + 亮橙 `#E8823F`）。
- 用法：浅色底用墨形 + 信号橙块；深色底用纸白形 + 亮橙块；单色印刷全黑或全白（橙块与匣同色）。
- 禁止：旋转、拉伸、加投影或描边、替换橙块颜色、把图形放进第二层容器里。

### 最小尺寸与清空区

- 图形最小 24px（24px 时格线仍可辨，低于此改用横排组合或不出图形）。
- 横排组合最小高度 32px。
- 清空区：图形或组合四周留白不小于图形短边的 20%，区域内不放文字、图标、分隔线。

### 横排组合

- `brand/logo-lockup-zh.svg`：图形在左、中文「字匣」在右，字重 500，字距 +8‰，视觉居中于图形。
- `brand/logo-lockup-en.svg`：图形在左、英文「Theca」在右，字重 500，字距 +0.6‰。
- `brand/logo-mono.svg`：单色中文横排，用于印刷与单色场景。
- 中文用系统中文字体排版（PingFang SC / Noto Sans SC），不手绘汉字；三端渲染有细微字距差异，以截图为准确认后微调字距。

## 启动图标

- 结构：纸底 + 墨色图形 + 单一信号橙动作点；不放文字，不做渐变、阴影、描边。
- 安全区：Android 自适应图标画布 108×108dp，图形主体收在中心 66dp 圆内；圆形、圆角方形、squircle 裁切下均不丢笔画。
- Android 三层自适应图标：background 全铺纸面 `#F3F0E8`；foreground 为图形；monochrome 为全黑单色层（Android 13+ 主题图标）。
- Android `minSdk 26`，全部受支持设备都走 `mipmap-anydpi-v26` 自适应图标，因此不提供 legacy 密度位图；避免旧启动器回退路径与两套资源不一致。
- iOS：`brand/exports/ios-1024.png`，不透明、无圆角、无透明通道，由系统裁切；T-04 建工程时使用。
- macOS：`brand/exports/macos-1024.png` 与 `brand/app-icon-macos.svg`；T-05 配置打包时生成 `.icns`。
- 对比度：信号橙 `#A8430F` 对纸底 `#F3F0E8` 为 5.31:1，深色模式亮橙 `#E8823F` 对深底 `#17150F` 为 6.69:1，满足图形元素 ≥3:1。

## 色彩

低饱和中性底 + 单一强调色；强调色只给「当前状态、主操作、需要用户注意的同步状态」。浅色与深色两套 tokens，正文对比度 ≥4.5:1，关键操作 ≥3:1。

| 角色 | 浅色 | 深色 |
| --- | --- | --- |
| 纸面 bg | `#F3F0E8` | `#17150F` |
| 面板 panel | `#FBF9F3` | `#1E1B14` |
| 面板次级 panel2 | `#EAE6DA` | `#262218` |
| 正文 ink | `#191712` | `#EDE9DC` |
| 次文本 ink2 | `#5B5446` | `#B7B0A0` |
| 三级文本 ink3 | `#6B6455` | `#9A9384` |
| 分隔 line | `#D0CABA` | `#3A352B` |
| 信号 accent | `#A8430F` | `#E8823F` |
| 成功 ok | `#2F6B45` | `#7ED6A4` |
| 警告 warn | `#7A4E0C` | `#E0B05A` |
| 危险 danger | `#A03024` | `#F08A75` |

不使用紫渐变、霓虹色、动态取色（Material You）、第三方品牌色。主操作按钮使用信号橙 + `#FFF8F0` 文字；深色模式下信号橙填充配深色文字，保证对比度。

## 字体

- UI：系统无衬线栈 —— `-apple-system, "PingFang SC", "Noto Sans SC", Roboto, sans-serif`；Android、iOS、Desktop 均不引入外部字体。
- 元数据（设备名、时间、UTF-8 字节数、同步状态）：等宽栈 `Menlo, Consolas, "Courier New"`，字号 12px，是全产品的签名细节。
- 内容预览：跟随系统无衬线，15–16px；不为了「编辑感」引入衬线正文，避免牺牲可读性。
- 英文与繁體中文必须验证不截断；按钮与列表项使用弹性布局。

## 字号与间距

- 手机正文 15–16px（不低于 14px），标签不低于 12px；桌面正文 13–14px。
- 间距刻度 4 / 8 / 12 / 16 / 24 / 32；列表走发丝分隔线；弹层 6–10px 圆角；按钮 3px；触控目标不小于 48dp。

## 素材原则

- 产品内容是文本，**文本就是主角素材**；不使用 stock 照片、不画人物插画、不做纯装饰 banner。
- 空态插画用极简线稿 SVG（两台设备 + 一条传递线），不使用 AI 生成图。
- 分享页不加载任何外部字体、图片、脚本；所有图形内联；页脚只保留一行文本字标。

## 资产清单

| 文件 | 用途 |
| --- | --- |
| `brand/logo-symbol.svg` | 图形母版（纸底） |
| `brand/logo-symbol-mono.svg` | 单色图形 |
| `brand/logo-symbol-reverse.svg` | 深底反白图形 |
| `brand/logo-lockup-zh.svg` / `logo-lockup-en.svg` | 中文 / 英文横排组合 |
| `brand/logo-mono.svg` | 单色中文横排 |
| `brand/app-icon-1024.svg` | 启动图标母版 |
| `brand/app-icon-rounded.svg` / `app-icon-round.svg` | 圆角方形 / 圆形图标（展示与旧系统预览） |
| `brand/app-icon-adaptive-foreground.svg` / `app-icon-monochrome.svg` | Android 自适应前景 / 单色层母版 |
| `brand/app-icon-macos.svg` | macOS 图标母版 |
| `brand/exports/icon-1024.png` … `icon-24.png` | 通用 PNG 导出（1024 / 512 / 192 / 48 / 24） |
| `brand/exports/ios-1024.png` | iOS 1024（不透明、无圆角） |
| `brand/exports/macos-1024.png` | macOS 1024 |

## 输出

- 三端：Android 412×915（展示框）、Desktop 1000×700（最小 640×480）、只读分享页（正文最大宽度约 680px）。
- 主题：跟随系统 / 浅色 / 深色；语言：简体中文 / 繁體中文 / English。
- 设计交付文件：`design/Clipbox 三端设计.html`、`design/分享页.html`；规范以 `design/README.md` 的 tokens 表为准。
