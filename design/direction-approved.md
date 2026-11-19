# 方向确认记录

## 展示了什么

三方向完整初稿，均为 5 台 Android 屏 + 1 个 Desktop 窗口 + 1 个分享页样本，可点击、可切主题：

| 编号 | 方向 | 来源 | 展示板截图 |
| --- | --- | --- | --- |
| C1 | 仪器清单 | 最佳设计师 A：Dieter Rams / Braun | `design-demos/shots/dir-1-full.png` |
| C4 | 剪贴卡片架 | 现实参照 B：Paste | `design-demos/shots/dir-2-full.png` |
| C5 | 硬件面板 | 秒数轮盘 · App 分区 01：Teenage Engineering / OP-1 | `design-demos/shots/dir-3-full.png` |

六格缩略图总览：`design-demos/shots/thumbs.png`；方向卡全文：`design-demos/directions.md`。

每版交互测试 `pageerror = 0`，底栏切换、搜索、置顶、操作面板、分享两步弹窗、删除二次确认、主题切换均可运行。

## 用户选择

> 「方向一 · 仪器清单」

选择来自 2026-11-13 的方向选择问题：三个方向已摆出，最终全量交付按哪一版展开。用户选定 **方向一 · 仪器清单（C1）**，未选择混合方案。

## 执行决定

- 最终交付按 C1 的视觉系统展开：冷灰仪器台展板 + 暖白手机表面 + 发丝分隔线 + 等宽读数 + 单一信号橙。
- 方向稿的展示缩放导致板面小字号与对比度黄灯；最终交付统一提升到正文 ≥14px、标签 ≥12px、正文对比度 ≥4.5:1，并重新跑 `design_lint.py check`。
- 三版共用技能强制 AndroidFrame，compare 的「同外观家族」黄灯不随选定改变；在 README 中说明。

## 更新功能方向门（2026-11-19）

### 展示了什么

v1.1.0「检查更新 / 下载安装包」的六格候选与三版完整稿。每版覆盖设置页更新区、有新版本弹窗、HTTP 与未签名风险确认、下载中、校验中、安装就绪六个状态；390 与 1440 同板展示，明暗各一张；均未新增导航路由。

| 编号 | 方向 | 完整稿 | 截图 |
| --- | --- | --- | --- |
| D1 | 仪器日志 | `design/updates/directions/D1.html` | `design/updates/shots/D1-light.png`、`D1-dark.png` |
| D2 | 公告头版 | `design/updates/directions/D2.html` | `design/updates/shots/D2-light.png`、`D2-dark.png` |
| D3 | 双栏工作台 | `design/updates/directions/D3.html` | `design/updates/shots/D3-light.png`、`D3-dark.png` |

六格缩略图：`design/updates/shots/candidates.png`；未入选的 D 折叠清单、E 时间线、F 底部抽屉保留为探索记录。三版 `design_lint.py check` 均无红灯，剩余黄灯为既定的等宽元数据与系统无衬线标题。

### 用户选择

> 「选D1方案」

2026-11-19：用户选定 **D1 · 仪器日志**，未选择混合方案。

### 执行决定

- 设置页更新区、更新弹窗、风险确认与下载 / 校验 / 就绪状态全部按 D1 展开：三段读数（当前、最新、日期）+ 等宽元数据 + 发丝分隔线 + 刻度式进度。
- D2 的公告式巨号版本、D3 的双栏工单布局不进入 Compose 实现，只作为后续迭代参考。
- 落地沿用固定色板与 `ClipboxTokens`，不引入方向稿之外的颜色、字体或新导航路由；设置红点仍为 6dp accent 圆点。
- 三语文案进入 `composeResources`；方向稿中的中文只作为版式基准，不作为硬编码文案。
