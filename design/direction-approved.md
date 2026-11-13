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
