# 三语关键文案对照表（T-UI-10 产出）

供 T-REL-06 验收使用。资源目录：

| 语言 | 资源目录 | `LanguageQualifier` / `RegionQualifier` | Compose Resources 解析结果 |
| --- | --- | --- | --- |
| 简体中文（默认） | `composeResources/values` | `zh` / 空 | 默认 `values` |
| 繁體中文 | `composeResources/values-zh-rTW` | `zh` / `TW` | `values-zh-rTW` |
| English | `composeResources/values-en` | `en` / 空 | `values-en` |

- 首次启动固定简体中文，不跟随系统语言；选择写入 DataStore，重启保持。
- 语言切换通过注入 `ComposeEnvironment` 实现，只重载资源，不修改系统 Locale，不重建导航。
- 三套 `strings.xml` 均为 240 条 `<string>` + 9 组 `<plurals>`（共 249 个 key），key 集合完全一致；统计口径为 2026-11-19 ADR-0008 复核。下表只列关键文案，不是全量清单。
- 2026-11-19 起移除 `pair_init_*` 5 条词条：配对页不再提供手动初始化入口，改为留空配对码自动触发（ADR-0008）。
- 语言名称本身使用自称，不翻译：`简体中文` / `繁體中文` / `English`。
- iOS 分享扩展的 `Localizable.strings` 在 T-IOS-08 补齐，本表只覆盖 Compose 端。

## 品牌与导航

| key | 简体中文 | 繁體中文 | English |
| --- | --- | --- | --- |
| `app_name` | 字匣 | 字匣 | Theca |
| `window_title` | Theca | Theca | Theca |
| `nav_history` | 历史 | 歷史 | History |
| `nav_toolbox` | 工具箱 | 工具箱 | Toolbox |
| `nav_settings` | 设置 | 設定 | Settings |

## 配对

| key | 简体中文 | 繁體中文 | English |
| --- | --- | --- | --- |
| `pair_kicker` | 连接到你的服务器 | 連線到你的伺服器 | Connect to your server |
| `pair_title` | 配对 | 配對 | Pairing |
| `pair_description` | 在同一用户空间内配对多台设备，文本在三端互通。 | 在同一個使用者空間內配對多台裝置，文字在三端互通。 | Pair several devices in one space. Text flows between all of them. |
| `pair_server_label` | 服务器地址 | 伺服器位址 | Server address |
| `pair_code_label` | 6 位配对码 | 6 位配對碼 | 6-digit code |
| `pair_device_label` | 设备名称 | 裝置名稱 | Device name |
| `pair_submit` | 配对 | 配對 | Pair |
| `pair_rate_hint` | 配对请求按来源 IP 限速，5 次／分钟。 | 配對請求依來源 IP 限速，5 次／分鐘。 | Pairing is limited to 5 attempts per minute per IP. |
| `pair_note` | 首次部署可留空配对码：点击「配对」即初始化服务器，本机成为首台设备。已有设备时，请先在任意设备的设置页生成配对码（10 分钟内有效，只能使用一次）。 | 首次部署可留空配對碼：點擊「配對」即初始化伺服器，本機成為首台裝置。已有裝置時，請先在任意裝置的設定頁產生配對碼（10 分鐘內有效，只能使用一次）。 | On a fresh server, leave the code empty and tap Pair to initialize it; this device becomes the first device. If the server already has devices, generate a code in any device's settings first (valid for 10 minutes, single-use). |
| `pair_insecure_warning` | 未加密连接，仅用于学习 | 未加密連線，僅供學習 | Unencrypted connection, for learning only |
| `pair_error_invalid_code` | 配对码无效，请检查后重试 | 配對碼無效，請檢查後重試 | That code is invalid. Check it and try again. |
| `pair_error_code_expired` | 配对码已过期，请重新获取 | 配對碼已過期，請重新取得 | That code has expired. Get a new one. |
| `pair_error_code_used` | 配对码已被使用，请重新获取 | 配對碼已被使用，請重新取得 | That code has already been used. Get a new one. |
| `pair_error_device_name` | 设备名称需为 1–64 个字符 | 裝置名稱需為 1–64 個字元 | Device name must be 1–64 characters. |
| `pair_error_network` | 无法连接服务器，请检查网络 | 無法連線到伺服器，請檢查網路 | Cannot reach the server. Check your network. |
| `pair_error_rate_limited` | 尝试过于频繁，请稍后再试 | 嘗試過於頻繁，請稍後再試 | Too many attempts. Try again later. |
| `pair_error_server_url` | 服务器地址无效，请检查格式 | 伺服器位址無效，請檢查格式 | Invalid server address |
| `pair_error_server` | 服务器暂时不可用，请稍后重试 | 伺服器暫時無法使用，請稍後重試 | The server is unavailable. Try again later. |
| `pair_error_already_initialized` | 服务器已初始化，请输入任意设备生成的配对码 | 伺服器已初始化，請輸入任意裝置產生的配對碼 | The server is already initialized. Enter a pairing code generated from any device. |
| `pair_expired_hint` | 请在任意设备的设置页重新生成配对码后再试 | 請在任意裝置的設定頁重新產生配對碼後再試 | Generate a new code from any device's settings and try again. |

> 2026-11-19（ADR-0008）：`pair_init_action/title/body/confirm/submitting` 5 条词条已随手动初始化入口一并删除。

## 配对码弹层（设置页）

| key | 简体中文 | 繁體中文 | English |
| --- | --- | --- | --- |
| `settings_pair_new_device` | 配对新设备 | 配對新裝置 | Pair a new device |
| `code_dialog_body` | 在另一台设备的配对页输入此码。10 分钟内有效，只能使用一次；重新生成会使旧码立即失效。 | 在另一台裝置的配對頁輸入此碼。10 分鐘內有效，只能使用一次；重新產生會使舊碼立即失效。 | Enter this code on the pairing screen of another device. It stays valid for 10 minutes and works once; generating a new code invalidates the old one immediately. |
| `code_dialog_generating` | 生成中… | 產生中… | Generating… |
| `code_dialog_remaining` | 剩余 %1$s | 剩餘 %1$s | Expires in %1$s |
| `code_dialog_expired` | 已过期，请重新生成 | 已過期，請重新產生 | Expired. Generate a new code. |
| `code_dialog_regenerate` | 重新生成 | 重新產生 | Generate new code |
| `code_dialog_copy` | 复制配对码 | 複製配對碼 | Copy code |
| `code_dialog_copied` | 配对码已复制 | 配對碼已複製 | Code copied |
| `code_dialog_close` | 关闭 | 關閉 | Close |
| `code_error_unauthorized` | 配对已失效，请重新配对 | 配對已失效，請重新配對 | Pairing expired. Pair again. |
| `code_error_network` | 无法连接服务器，请检查网络 | 無法連線到伺服器，請檢查網路 | Cannot reach the server. Check your network. |
| `code_error_server` | 服务器暂时不可用，请稍后重试 | 伺服器暫時無法使用，請稍後重試 | The server is unavailable. Try again later. |
| `code_error_rate_limited` | 尝试过于频繁，请稍后再试 | 嘗試過於頻繁，請稍後再試 | Too many attempts. Try again later. |

## 历史与同步

| key | 简体中文 | 繁體中文 | English |
| --- | --- | --- | --- |
| `history_sync_paired` | 已配对 | 已配對 | Paired |
| `history_sync_unpaired` | 未配对 | 未配對 | Not paired |
| `history_composer_label` | 新记录／UTF-8 | 新紀錄／UTF-8 | New clip / UTF-8 |
| `history_composer_placeholder` | 输入或粘贴文本，发送到所有设备… | 輸入或貼上文字，傳送到所有裝置… | Type or paste text, send to every device… |
| `history_composer_local` | 本地优先，立即落盘 | 本機優先，立即寫入 | Local first, saved to disk |
| `history_send` | 发送 | 傳送 | Send |
| `history_over_limit` | 超出 64 KiB 上限 | 超出 64 KiB 上限 | Over the 64 KiB limit |
| `history_near_limit` | 接近 64 KiB 上限 | 接近 64 KiB 上限 | Approaching the 64 KiB limit |
| `history_paste` | 粘贴 | 貼上 | Paste |
| `history_empty_title` | 还没有记录 | 還沒有紀錄 | Nothing here yet |
| `history_empty_paired_body` | 从手机分享，或在上方输入开始。工具箱离线可用。 | 從手機分享，或在上方輸入開始。工具箱離線可用。 | Share from your phone, or type above. The toolbox works offline. |
| `history_empty_unpaired_body` | 尚未配对，记录只保存在本机 | 尚未配對，紀錄只保存在本機 | Not paired yet. Clips stay on this device only. |
| `history_pair` | 去配对 | 去配對 | Pair a device |
| `history_act_pin` | 置顶 | 置頂 | Pin |
| `history_act_copy` | 复制 | 複製 | Copy |
| `history_act_share` | 生成分享 | 產生分享 | Create share link |
| `history_act_delete` | 删除 | 刪除 | Delete |
| `history_copied` | 已复制到剪贴板 | 已複製到剪貼簿 | Copied to clipboard |
| `history_delete_title` | 删除这条记录？ | 刪除這筆紀錄？ | Delete this clip? |
| `history_delete_body` | 删除后无法恢复，所有设备都会移除。 | 刪除後無法復原，所有裝置都會移除。 | This can't be undone and removes it from every device. |
| `history_sync_syncing` | 同步中 | 同步中 | Syncing |
| `history_sync_synced` | 已同步 | 已同步 | Synced |
| `history_sync_offline` | 离线 | 離線 | Offline |
| `history_search_placeholder` | 搜索本地记录 | 搜尋本機紀錄 | Search local clips |
| `history_search_empty_title` | 没有匹配的记录 | 沒有符合的紀錄 | No matching clips |
| `history_received_new` | 收到新记录 | 收到新紀錄 | New clip received |

## 分享

| key | 简体中文 | 繁體中文 | English |
| --- | --- | --- | --- |
| `share_sheet_title` | 生成分享链接 | 產生分享連結 | Create share link |
| `share_tier_1h` | 1 小时 | 1 小時 | 1 hour |
| `share_tier_24h` | 24 小时 | 24 小時 | 24 hours |
| `share_tier_7d` | 7 天 | 7 天 | 7 days |
| `share_tier_one_time` | 阅后即焚 | 閱後即焚 | Burn after reading |
| `share_tier_one_time_hint` | 首次打开后失效 | 首次開啟後失效 | Expires after the first open |
| `share_generate` | 生成链接 | 產生連結 | Generate link |
| `share_result_title` | 链接已生成 | 連結已產生 | Link created |
| `share_result_expires` | 过期时间：%1$s | 到期時間：%1$s | Expires: %1$s |
| `share_result_one_time` | 一次性链接，首次打开后失效 | 一次性連結，首次開啟後失效 | One-time link, expires after the first open |
| `share_result_reusable` | 有效期内可重复访问 | 有效期內可重複存取 | Can be opened again until it expires |
| `share_copy_link` | 复制链接 | 複製連結 | Copy link |
| `share_error_offline` | 网络不可用，请检查连接 | 網路不可用，請檢查連線 | Network unavailable, check your connection |
| `share_error_unauthorized` | 配对已失效，请重新配对 | 配對已失效，請重新配對 | Pairing expired, please pair again |

## 剪贴板与启动

| key | 简体中文 | 繁體中文 | English |
| --- | --- | --- | --- |
| `clipboard_read_failed` | 无法读取剪贴板，请重试或长按输入框手动粘贴 | 無法讀取剪貼簿，請重試或長按輸入框手動貼上 | Couldn't read the clipboard. Try again or long-press the input field to paste. |
| `clipboard_write_failed` | 无法写入剪贴板，请重试 | 無法寫入剪貼簿，請重試 | Couldn't write to the clipboard. Try again. |
| `clipboard_empty` | 剪贴板中没有文本 | 剪貼簿中沒有文字 | No text in the clipboard |
| `clipboard_auto_paste_failed` | 自动复制失败，请手动复制 | 自動複製失敗，請手動複製 | Auto-copy failed, please copy manually |
| `clipboard_auto_send_too_large` | 剪贴板内容超过 64 KiB，未自动发送 | 剪貼簿內容超過 64 KiB，未自動傳送 | Clipboard content exceeds 64 KiB and was not sent automatically |
| `startup_loading` | 正在启动… | 正在啟動… | Starting… |
| `startup_failed_title` | 启动失败 | 啟動失敗 | Failed to start |
| `startup_failed_body` | 本机存储初始化失败，请重启应用后重试。 | 本機儲存初始化失敗，請重新啟動應用程式後再試。 | Local storage could not be initialized. Restart the app and try again. |

## 设置

| key | 简体中文 | 繁體中文 | English |
| --- | --- | --- | --- |
| `settings_title` | 设置 | 設定 | Settings |
| `settings_section_server` | 服务器 | 伺服器 | Server |
| `settings_server_label` | 服务器地址 | 伺服器位址 | Server URL |
| `settings_section_device` | 设备 | 裝置 | Device |
| `settings_pairing_status` | 配对状态 | 配對狀態 | Status |
| `settings_paired` | 已配对 | 已配對 | Paired |
| `settings_unpaired` | 未配对 | 未配對 | Not paired |
| `settings_last_sync` | 最近同步 | 最近同步 | Last sync |
| `settings_last_sync_never` | 从未同步 | 從未同步 | Never synced |
| `settings_sync_now` | 立即同步 | 立即同步 | Sync now |
| `settings_sync_busy` | 同步中… | 同步中… | Syncing… |
| `settings_sync_success` | 同步完成 | 同步完成 | Sync complete |
| `settings_sync_failed` | 同步失败，请稍后重试 | 同步失敗，請稍後重試 | Sync failed. Try again later. |
| `settings_sync_offline` | 网络不可用，请检查连接 | 網路不可用，請檢查連線 | Network unavailable. Check your connection. |
| `settings_sync_unpaired` | 未配对，无法同步 | 未配對，無法同步 | Not paired. Nothing to sync. |
| `settings_sync_unauthorized` | 配对已失效，请重新配对 | 配對已失效，請重新配對 | Pairing expired. Pair again. |
| `settings_section_clipboard` | 桌面剪贴板 | 桌面剪貼簿 | Desktop clipboard |
| `settings_auto_paste` | 收到文本自动复制 | 收到文字自動複製 | Auto-copy incoming text |
| `settings_auto_send` | 复制时自动发送 | 複製時自動傳送 | Send copied text automatically |
| `settings_auto_send_note` | 每次复制都会创建记录，需确认隐私影响。 | 每次複製都會建立紀錄，需確認隱私影響。 | Every copy creates a clip. Confirm the privacy impact. |
| `settings_privacy_title` | 开启自动发送？ | 開啟自動傳送？ | Turn on auto-send? |
| `settings_privacy_body` | 开启后，任何应用里复制的文本都会在 800 毫秒后发送到你的其他设备。密码、验证码与隐私内容也会被发送。 | 開啟後，任何應用程式裡複製的文字都會在 800 毫秒後傳送到你的其他裝置。密碼、驗證碼與隱私內容也會被傳送。 | When on, any text you copy in any app is sent to your other devices after 800 ms. Passwords, codes and private text will be sent too. |
| `settings_confirm_enable` | 我已了解，开启 | 我已瞭解，開啟 | I understand, turn it on |
| `settings_section_appearance` | 外观 | 外觀 | Appearance |
| `settings_theme` | 主题 | 主題 | Theme |
| `settings_language` | 语言 | 語言 | Language |
| `settings_theme_system` | 跟随系统 | 跟隨系統 | Follow system |
| `settings_theme_light` | 浅色 | 淺色 | Light |
| `settings_theme_dark` | 深色 | 深色 | Dark |
| `settings_language_hans` | 简体中文 | 简体中文 | 简体中文 |
| `settings_language_tw` | 繁體中文 | 繁體中文 | 繁體中文 |
| `settings_language_en` | English | English | English |
| `settings_section_diagnostic` | 诊断 | 診斷 | Diagnostics |
| `settings_diagnostic` | 诊断日志 | 診斷日誌 | Diagnostic logs |
| `settings_diagnostic_note` | 日志会包含正文，令牌已脱敏，仅用于本机排查。 | 日誌會包含內文，權杖已去識別化，僅供本機排查。 | Logs include clip text with tokens redacted. Local troubleshooting only. |
| `settings_diag_confirm_title` | 开启诊断日志？ | 開啟診斷日誌？ | Turn on diagnostic logs? |
| `settings_diag_confirm_body` | 诊断日志会把请求与响应正文输出到本机系统日志，令牌与配对码仍会脱敏，不上传、不落盘。 | 診斷日誌會把請求與回應內文輸出到本機系統日誌，權杖與配對碼仍會去識別化，不上傳、不寫入磁碟。 | Diagnostic logs print request and response bodies to the local system log. Tokens and pairing codes stay redacted. Nothing is uploaded or written to disk. |
| `settings_section_about` | 关于 | 關於 | About |
| `settings_about_storage_body` | 记录与设置只保存在本机；保留最近 200 条非置顶与全部置顶。 | 紀錄與設定只保存在本機；保留最近 200 條非置頂與全部置頂。 | Clips and settings stay on this device. The latest 200 unpinned clips and all pinned clips are kept. |
| `settings_about_transport_body` | 未加密连接，仅用于学习 | 未加密連線，僅供學習 | Unencrypted connection, for learning only |
| `settings_section_danger` | 危险操作 | 危險操作 | Danger zone |
| `settings_change_server` | 更换服务器 | 更換伺服器 | Change server |
| `settings_unpair` | 解除配对 | 解除配對 | Unpair |
| `settings_server_saved` | 服务器地址已保存 | 伺服器位址已儲存 | Server URL saved |
| `settings_server_invalid` | 地址格式无效，请检查 | 位址格式無效，請檢查 | Invalid address format |
| `settings_danger_change_title` | 更换服务器？ | 更換伺服器？ | Change server? |
| `settings_danger_change_body` | 旧令牌对新服务器无效，本地历史、待发队列与游标将被清空。此操作无法撤销。 | 舊權杖對新伺服器無效，本機歷史、待傳佇列與游標將被清空。此操作無法復原。 | The old token is invalid on the new server. Local history, the outbox and the sync cursor will be erased. This cannot be undone. |
| `settings_danger_unpair_title` | 解除配对？ | 解除配對？ | Unpair? |
| `settings_danger_unpair_body` | 本机令牌、历史、待发队列与游标将被清空；服务端与其他设备的数据保留。 | 本機權杖、歷史、待傳佇列與游標將被清空；伺服器與其他裝置的資料保留。 | This device token, history, outbox and cursor will be erased. Data on the server and other devices stays. |

## 外观与语言页

| key | 简体中文 | 繁體中文 | English |
| --- | --- | --- | --- |
| `appearance_title` | 外观与语言 | 外觀與語言 | Appearance and language |
| `appearance_kicker` | 立即生效 | 立即生效 | Applies immediately |
| `appearance_section_theme` | 主题 | 主題 | Theme |
| `appearance_section_language` | 语言 | 語言 | Language |
| `appearance_current` | 当前 | 目前 | Current |
| `appearance_preview_label` | 预览 | 預覽 | Preview |
| `appearance_preview_body` | 把这段文本发到书房那台 Mac。 | 把這段文字傳到書房那台 Mac。 | Send this text to the Mac in the study. |
| `appearance_preview_device` | 本机 | 本機 | This device |
| `appearance_preview_time` | 刚刚 | 剛剛 | Just now |
| `appearance_note` | 首次启动固定为简体中文，之后以你的选择为准。 | 首次啟動固定為簡體中文，之後以你的選擇為準。 | Simplified Chinese on first launch; your choice after that. |

## 复数文案（`<plurals>`）

简中与繁中只有 `other`；English 有 `one` / `other` 两类，由 `pluralStringResource` 按语言规则选择。

| key | 简体中文（other） | 繁體中文（other） | English one | English other |
| --- | --- | --- | --- | --- |
| `pair_cooldown` | %1$d 秒后可重试 | %1$d 秒後可重試 | Retry in %1$d second | Retry in %1$d seconds |
| `history_group_pinned` | 置顶 %1$d | 置頂 %1$d | Pinned %1$d | Pinned %1$d |
| `history_group_all` | 全部 %1$d | 全部 %1$d | All %1$d | All %1$d |
| `history_time_minutes` | %1$d 分钟前 | %1$d 分鐘前 | %1$d minute ago | %1$d minutes ago |
| `history_time_hours` | %1$d 小时前 | %1$d 小時前 | %1$d hour ago | %1$d hours ago |
| `history_search_count` | 找到 %1$d 条 | 找到 %1$d 筆 | %1$d result found | %1$d results found |
| `history_sync_pending_count` | 待同步 %1$d 条 | 待同步 %1$d 筆 | %1$d item pending | %1$d items pending |
| `history_sync_failed_count` | 失败 %1$d 条 | 失敗 %1$d 筆 | %1$d item failed | %1$d items failed |

## 日期时间格式

`shared/util/ClipTime.kt` 的 `formatAbsoluteTime` 与 `clipTimeLabel` 按语言输出：

| 语言 | 绝对时间格式 | 示例 |
| --- | --- | --- |
| 简体中文 | `yyyy-MM-dd HH:mm` | `2025-11-15 11:00` |
| 繁體中文 | `yyyy/MM/dd HH:mm` | `2025/11/15 11:00` |
| English | `MMM d, yyyy HH:mm` | `Nov 15, 2025 11:00` |

相对时间（刚刚 / N 分钟前 / N 小时前）来自 `ClipTimeLabel` + 上表复数资源，超过 24 小时回落为对应语言的绝对格式。分享到期时间（`share_result_expires` 的 `%1$s`）使用同一函数。

## 已知说明

- 英文复数分类与简繁中文不同（`one` / `other` 对 `other`），这是目标语言的语法差异，不是 key 缺失；三套资源的 key 集合仍然完全一致。
- `window_title` 三语均为品牌名 `Theca`，属于有意保留，不参与翻译。
- 系统级弹窗（剪贴板权限、系统分享面板）跟随操作系统语言，不在应用内资源范围内，T-UI-10 不处理。
