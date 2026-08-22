# MCP 工具清单（v0.3.7-public）

掌心窗 MCP 服务把手机端能力暴露给支持 MCP 的客户端。所有工具都需要你自己的 `LINJIAN_TOKEN`，并且手机端需要保持服务启动。公开版工具只保留通用能力，不包含私人绑定接口、私人 Token、私人服务地址或固定私人关系。

## 连接方式

MCP 服务默认监听 `PORT` 环境变量，未设置时使用 `8787`。

常用地址：

```text
http://127.0.0.1:8787/mcp
http://127.0.0.1:8787/sse
```

公网部署后将域名替换为自己的 MCP 服务域名，例如：

```text
https://你的-mcp-域名/mcp
https://你的-mcp-域名/sse
```

必需环境变量：

```env
LINJIAN_URL=https://你的-server-域名
LINJIAN_TOKEN=你的长随机token
LINJIAN_DEFAULT_DEVICE=android-phone
```

Render 一键部署时，`LINJIAN_URL` 会由 Blueprint 自动引用 server 的公网 `RENDER_EXTERNAL_URL`；旧版部署只重新部署 MCP 时，新版代码也会把旧内网 `hostport` 自动兜底为公网地址。手动部署或 Railway 部署时再按上面格式填写。

## 状态与截图

- `linjian_status()`：检查 MCP 与后端连接状态，确认 `LINJIAN_URL` 和 `LINJIAN_TOKEN` 是否配置，并返回后端健康信息。
- `peek_screen(wait_seconds)`：请求手机端截一张新截图，并等待上传后返回图片。
- `latest_screen()`：不触发手机截图，直接读取服务器最近一张截图。
- `get_life_state(device_id)`：读取生活状态：电量、充电、网络、当前 App、今日屏幕时间、解锁次数、天气地区、门禁/屏幕休息、归电等。
- `get_senses_state(device_id)`：读取公开版轻量聚合状态，主要包括生活状态和归电状态。
- `get_phone_state(device_id)`：读取当前包名、当前 App、无障碍状态和屏幕文字摘要。
- `get_screen_nodes(device_id, wait_seconds)`：读取当前屏幕无障碍节点，包括文字、控件类型、是否可点击和坐标。

## 今日窗语与陪伴行动

- `get_window_whisper()`：读取当前共同窗语、最后修改者、修改时间和版本。
- `set_window_whisper(content, author)`：更新共同窗语。内容应简短，适合显示在手机卡片中。
- `get_companion_actions(limit)`：读取最近的陪伴对象行动摘要，例如查看天气、设置提醒、执行守护动作等。
- `get_activity_events(device_id, date, source, limit)`：读取统一活动事件，可按设备、日期和来源筛选。
- `add_activity_event(device_id, source, type, title, subtitle, app_name, package_name, action, status, metadata_json)`：手动写入一条活动事件。

## 点击、输入与自动操作

- `tap_text(target_text, match, index, device_id, wait_seconds)`：按当前屏幕文字精准点击。
- `input_text(text, append, device_id, wait_seconds)`：向当前已聚焦或第一个可编辑输入框输入文本。
- `send_phone_command(...)`：底层通用手机控制命令。可执行打开 App、回桌面、返回、截图、点击、滑动、输入、通知、闹钟、息屏、屏幕休息等动作。
- `run_sequence(steps, device_id, stop_on_error, wait_seconds)`：一次执行多步动作，适合打开 App、等待、点击、输入、通知、截图等连招。
- `run_preset(preset, device_id, x, y, wait_seconds)`：执行预设连招，例如回到目标 App、打开小红书、睡前回家等。

## App 与系统控制

- `open_app(app, package, device_id)`：打开指定 App。`app` 可填已保存昵称，`package` 可直接填 Android 包名。
- `phone_home(device_id)`：回到桌面。
- `phone_back(device_id)`：执行返回。
- `phone_recents(device_id)`：打开最近任务。
- `phone_screen_off(device_id, wait_seconds)`：立即息屏/锁屏，需要手机端无障碍服务可用。
- `list_known_apps()`：列出预置 App 包名和用户手动保存的包名。
- `save_known_app(alias, package, device_id, wait_seconds)`：保存应用昵称与包名，之后可用昵称打开或守护。

## 通知、天气和提醒

- `send_notification(title, message, device_id)`：发送手机系统通知。
- `set_alarm(hour, minute, message, vibrate, skip_ui, device_id)`：设置系统闹钟。
- `get_weather_state(device_id, city)`：按当前天气地区或指定城市查询天气，并生成出门建议。
- `send_weather_notification(device_id, city, title)`：查询天气后给手机发送天气提醒通知。

## 守护日历

- `get_guardian_calendar(device_id)`：读取守护日历、最近纪念日、节日、倒数日和横幅提醒状态。
- `add_guardian_calendar_event(title, date, date_type, repeat_type, group, note, remind_days_before, banner_enabled, device_id, wait_seconds)`：添加或更新重要日期。支持阳历、农历、每年重复、分组、备注和提前提醒。
- `list_guardian_days(device_id, wait_seconds)`：读取手机本机的完整事件列表和稳定 `id`。
- `add_guardian_day(...)`：添加事件并返回事件 `id`。
- `update_guardian_day(id, ...)`：按 `id` 修改事件，不影响同日其他事件。
- `delete_guardian_day(id, confirm, ...)`：按 `id` 删除。`confirm` 必须为 `true`。如果用户只描述“8 月 23 日的生日”，应先调用 `list_guardian_days` 确认唯一事件，再删除。

## TA 的日记

日记本与正文默认只保存在手机本机，不进入生活状态上传。手机端服务需保持启动，MCP 才能按需读写。

- `create_diary_book(name, subtitle, cover_style, ...)`：创建日记本，成功结果包含 `book_id`。
- `list_diary_books(...)`：列出本机日记本。
- `rename_diary_book(book_id, name, subtitle, ...)`：重命名日记本或修改封面小字。
- `update_diary_book_cover(book_id, cover_style, cover_uri, ...)`：更新封面样式；本机图片通常由用户在 App 内选择。
- `write_diary_entry(book_id, title, content, mood, tags, date, time_label, ...)`：写入一篇日记，成功结果包含 `entry_id`、`book_id`、标题和日期。
- `list_diary_entries(book_id, ...)`：按日记本列出日记。
- `read_diary_entry(entry_id, ...)`：读取一篇完整日记。
- `search_diary_entries(book_id, keyword, date_from, date_to, tags, ...)`：按标题、正文、标签、心情和日期范围搜索。
- `update_diary_entry(entry_id, ...)`：只更新传入字段。
- `delete_diary_entry(entry_id, confirm, ...)`：删除单篇日记，`confirm` 必须为 `true`。
- `delete_diary_book(book_id, confirm, ...)`：高风险操作，会连同全部纸页删除；必须先向用户二次确认并传 `confirm=true`。

## 小红书辅助

- `draft_xhs_comment(text, device_id, wait_seconds)`：尝试打开评论输入框并写入评论草稿，不自动发送。
- `xhs_comment(text, mode, author_tag, device_id, wait_seconds)`：评论助手。`manual` 只写草稿；`auto` 会追加署名并尝试发送，只应在用户明确同意时使用。
- `send_visible_comment_after_confirmation(device_id, wait_seconds)`：在用户确认当前屏幕草稿无误后，点击发送按钮。

## 归电

归电用于把用户从目标 App 或手机使用中叫回指定目标应用。公开版目标 App 由用户配置，不写死到私人 App。

- `get_guidian_state(device_id)`：读取归电状态和设置：上次回来、下次最早归电、今日次数、拒绝理由、目标 App、主题等。
- `set_guidian_config(enabled, interval_minutes, cooldown_minutes, daily_max, quiet_enabled, quiet_start, quiet_end, fullscreen, theme, prompts, quick_reasons, device_id, wait_seconds)`：调整归电设置。
- `trigger_guidian(device_id, wait_seconds)`：立刻触发一次归电全屏页，用于测试或主动叫回。
- `mark_guidian_returned(source, device_id, wait_seconds)`：手动标记用户已经回到归电目标 App。

## 主动关心策略

- `get_care_policy()`：读取主动关心策略，包括关心开关、风格、允许动作、重点 App、安静时段、冷却时间等。
- `set_care_policy(active_care_enabled, consent_mode, care_style, allowed_actions, sensitive_apps_json, quiet_start, quiet_end, timezone_offset, repeat_cooldown_minutes, history_limit, notes, policy_json)`：更新主动关心策略。
- `record_care_event(action, target_app, package, reason, result, tone, device_id)`：记录一次关心动作，避免短时间重复提醒或重复管束。
- `get_care_history(limit)`：读取最近主动关心记录。
- `active_care_check(reason, care_intent, device_id)`：综合手机状态、当前 App、屏幕时间、归电状态、门禁状态和关心策略，给 AI 判断是否需要介入。
- `care_action(action, target_app, package, duration_minutes, title, message, hour, minute, reason, tone, device_id, wait_seconds)`：执行已经判断好的关心动作，例如发送通知、触发归电、屏幕休息、打开 App、设置闹钟等。

## 门禁与屏幕休息

公开版使用屏幕休息/门禁工具管理指定 App，适合测试自己的手机使用节奏。不要用来管理他人设备。

- `screen_break_app(app, package, duration_minutes, mode, reason, message, emergency_passphrase, emergency_unlock_minutes, device_id, wait_seconds)`：让指定 App 暂停一段时间。
- `temporary_screen_break_release(app, package, minutes, allow_type, max_window_minutes, device_id, wait_seconds)`：临时放行一个正在休息中的 App。
- `end_screen_break(app, package, device_id, wait_seconds)`：结束某个 App 当前的休息状态。
- `extend_screen_break(app, package, minutes, reason, message, device_id, wait_seconds)`：延长某个 App 的休息时间。
- `deny_screen_break_release_request(app, package, message, device_id, wait_seconds)`：拒绝一次恢复申请。
- `get_screen_break_state(device_id, wait_seconds)`：读取当前休息状态、可管理 App、日志和恢复申请。
- `list_screen_break_apps(max, device_id, wait_seconds)`：让手机列出可作为屏幕休息对象的已安装 App。
- `add_screen_break_app(alias, package, device_id, wait_seconds)`：把一个 App 加到可管理名单。
- `set_screen_break_passphrase(app, package, passphrase, device_id, wait_seconds)`：为某个 App 当前休息状态设置或更新紧急口令。
- `get_screen_break_release_requests()`：查看手机恢复申请。

## 到访记录

到访记录用于记录用户主动回来找 AI 的时间，便于做关系型时间线、日记、归电和主动关心。它不读取手机隐私内容。

- `record_visit(source, event, note, mood, conversation_hint, timezone_offset, duplicate_window_minutes)`：记录一次到访。
- `get_last_visit(source, timezone_offset)`：读取最近一次到访时间。
- `get_visit_history(limit, since_hours, date, source, include_intervals, timezone_offset)`：读取最近若干次到访记录。
- `get_visit_stats(since_hours, away_threshold_hours, source, timezone_offset)`：统计到访节奏，例如今日次数、最近一次、最近 24 小时/7 天次数和平均间隔。

## 安全边界

- 截图、读屏、点击、输入、自动评论、门禁、屏幕休息和息屏都属于敏感能力。
- 只在本人设备、本人服务、本人授权的 MCP 客户端中使用。
- 不要把 Token 发给陌生客户端。
- 自动发送评论、自动点击和输入建议默认手动确认。
- 公开版不包含私人 Token、私人服务地址、固定私人关系和不可公开的专属接口。

## v0.3.6.6 应用门禁兼容工具名

为避免部分 AI 平台读取到 `/health` 动作清单后调用旧动作名时报 `Tool not found`，MCP 额外暴露以下兼容工具：

- `get_lock_state`：等同 `get_screen_break_state`，读取应用门禁状态。
- `lock_app`：等同 `screen_break_app`，开启应用门禁。
- `unlock_app`：等同 `end_screen_break`，解除应用门禁。
- `temporary_unlock_app`：等同 `temporary_screen_break_release`，临时放行。
- `extend_lock`：等同 `extend_screen_break`，延长门禁。
- `deny_unlock_request`：等同 `deny_screen_break_release_request`，拒绝恢复申请。
- `list_lockable_apps`、`add_locked_app`、`remove_locked_app`、`set_emergency_passphrase`：旧版命名兼容。
