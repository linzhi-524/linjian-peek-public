# 掌心窗公开版 v0.3.9.1

## v0.3.9.1 小金库账单管理、通知昵称与 Cloudflare MCP 同步

- 小金库已添加账单支持**左滑或长按**唤出「重新编辑」和「删除」操作。
- 优化账单滑动识别，并在统计页补充当月账单明细，较早记录也可以重新编辑或删除。
- 修复用户修改设置页昵称后，「休息眼睛」提醒仍固定显示“小猫”的问题；现统一读取用户设置的昵称。
- Cloudflare MCP 补齐小金库账单编辑/删除能力，并同步陪伴者小金库申请与结果回写相关工具。
- Cloudflare Worker 同步支持 `edit_wallet_record`、`delete_wallet_record`、`submit_companion_wallet_request`、`list_companion_wallet_requests`、`save_user_wallet_request_result`、`wallet_takeout_action`。
- 同步两份 Cloudflare Worker 源码与 `/health` 版本标识，避免不同部署入口能力不一致。
- 版本号：`0.3.9.1` / `30901`，固定签名保持不变。
- 部署提示：手机 App 需重新构建并安装新 APK；使用 Cloudflare MCP 的用户需重新部署 Worker。

## v0.3.9.0 MCP 兼容与日记批注读取修复

- 修复部分 AI 平台连接 MCP 时因 `params.capabilities.experimental.ovoActivityCards` 触发 `Invalid input` 的兼容问题。
- 修复日记批注读取工具返回 `noop`：server 白名单补齐批注相关 action。
- 保留上一版媒体状态修复。
- 版本号：`0.3.9.0` / `30900`，固定签名保持不变。
- 部署提示：MCP 服务与 server 服务都需要重新部署；手机 App 如需显示新版本号需重新打包安装。


## v0.3.9.0 此刻状态媒体读取修复

- 修复「此刻状态」中媒体权限已开启但仍显示“暂无媒体播放”的问题。
- 媒体状态新增 MediaSession 主动读取，不再只依赖通知回调，兼容更多音乐/视频 App。
- 通知监听扫描继续保留作为兜底；通知回调和活跃媒体会话任一方式读到内容即可显示。
- 保留隐私限制：仅读取媒体标题、歌手、专辑、播放状态和应用名，不读取通知正文。
- 版本号：`0.3.9.0` / `30900`，固定签名保持不变。

## v0.3.8.7 回家模式设置生效修复

- 修复回家模式开关、强制抱回、阈值分钟、冷却分钟、返回目标输入后没有即时保存的问题。
- 配置变化后清空回家模式当前计时与冷却缓存，避免关闭后仍沿用旧状态。
- 只弹窗模式不再要求必须设置返回目标 APP；未设置返回目标时仍会按阈值发悬浮横幅。
- 观察 APP 列表为空时回退到默认观察包名：抖音、小红书。
- 版本号：`0.3.8.7` / `30807`，固定签名保持不变。


## v0.3.8.6 应用门禁 OPPO/ColorOS 修复

- 应用门禁优先级调整为：**全屏锁定页 > 全屏悬浮遮罩 > 回到桌面兜底**。
- 修复部分 OPPO/ColorOS 设备锁定 App 后仍能继续滑动的问题。
- 锁定命令写入后会立即检查当前前台包名；如果被锁 App 已经在前台，会立刻触发门禁。
- 无障碍触发增加窗口变化、内容变化、滑动事件兜底，减少“锁了但没等到窗口切换”的漏拦截。
- 版本号：`0.3.8.6` / `30806`，固定签名保持不变。


## v0.3.8.6 页边纸条批注

- 新增「TA 的日记」页边纸条批注：展开日记后长按段落即可留下批注。
- 不把批注做成评论区，也不在每段固定显示按钮；只有被批注过的段落会出现柔和高亮、纸条数量与便签预览。
- 新增 MCP 批注工具：`add_diary_annotation`、`list_diary_annotations`、`read_diary_entry_with_annotations`、`mark_diary_annotations_seen`、`delete_diary_annotation`。
- 日记备份导入/导出会带上批注；删除日记或日记本会同步清理对应纸条。
- 版本号：`0.3.8.6` / `30806`，固定签名保持不变。

## v0.3.8.6 日记写入兜底修复

- 修复部分用户更新后“机能写日记正文，但保存时报日记本 id 对不上”的问题。
- `write_diary_entry` 现在支持 `book_id` 留空，旧 ID 对不上时会按 `book_name` 或唯一日记本兜底。
- 如果没有日记本，会自动创建默认「TA 的日记」再写入；如果有多本且无法判断，会返回候选项，不会乱写。
- 其它模块不改动：专注模式、应用门禁、小金库、外卖助手和守护日历保持 v0.3.8.2 行为。

## v0.3.8.6 守护页入口补丁

- 修复普通用户在 App 前台找不到「专注模式」的问题。
- 守护页 → 安心规则新增「专注模式」卡片，位置在「应用门禁」下面。
- 点击后可查看专注状态、保存默认规则、开启 5 分钟测试；专注中可查看锁定页或结束专注。
- 继续保留 MCP 远程工具，AI 仍可通过 `start_focus_mode` 等工具开启全机专注。

## v0.3.8.6 专注模式与日记本重命名修复

- 新增 Focus Mode 专注模式：AI 可通过 MCP 开启/结束/查询全机专注。
- 专注锁定页支持“留言给他”和一次 1 分钟应急放行。
- MCP `/health` 会显示 `focus_tools: true` 和专注工具清单，方便确认接口真的暴露。
- 修复 `rename_diary_book`：缺少 `book_id` 时可通过 `old_name` 或唯一日记本自动重命名，减少模型反复调用 `list_diary_books`。
- Render、Railway、局域网、Cloudflare Worker 相关代码都已随源码同步更新。


## v0.3.8.6 MCP 新工具暴露修复

这版修复部分 AI/MCP 客户端出现“后端 health 已经有新工具，但 ChatGPT 工具面板没有暴露双向申请/外卖助手接口”的问题。

- 普通 `/mcp` 提前注册 `wallet_takeout_action` 统一入口，单独工具没刷出时也能通过 `action` 调用。
- 新增 `/mcp-wallet` 专用端点，只暴露小金库、双向申请和外卖助手工具，减少被客户端工具数量限制截断的概率。
- `/health` 增加 `mcp_wallet_endpoint`、`schema_exposure_fix`、`priority_tool`、`wallet_takeout_tools`，方便确认部署结果。
- 原来的 `/mcp`、`/sse` 和手机端功能不变。

## v0.3.8.6 外卖助手与小金库联动
- 守护页新增「今天吃什么」入口，外卖助手作为单独页面存在，不塞进小金库。
- 整合私人版外卖助手：单餐预算、今日外卖预算、口味偏好、常点外卖库、记住这道饭、帮我挑、打开链接、复制备注、点到付款页。
- 外卖助手只帮用户整理、跳转和点到付款前；真正付款必须由用户本人确认。
- 外卖计划可提交到小金库审批；小金库审批通过后会自动生成一条支出记录，避免“批完还要手动记账”。
- 审批自动入账使用 approval_id/source_key 去重，重复处理同一申请不会重复计入支出。

## v0.3.8.6 小金库双向审批闭环修复

- 补全陪伴者发起申请、查看结果、用户理由写回的 MCP 闭环，不需要控制用户屏幕。
- 用户处理陪伴者申请时，点击通过 / 暂缓 / 驳回后会弹出理由输入框，理由会写入审批记录。
- 普通账单金额不再静默默认为 0；申请标题兼容 `item/title/purpose/content/name/note/reason`。
- 最近消费卡片支持左滑展开操作，可编辑或删除账单；删除前会二次确认。
- 新增 `save_user_wallet_request_result`、`edit_wallet_record`、`delete_wallet_record`，保留旧工具兼容。

## v0.3.8.6 双向申请补全

- MCP 新增 `submit_companion_wallet_request`：陪伴者可以主动提交一条申请，等待用户处理。
- MCP 新增 `list_companion_wallet_requests`：陪伴者可以查看自己提交给用户的申请，以及用户通过、暂缓或驳回后的结果。
- MCP 新增 `list_wallet_request_results`：按发起方和状态筛选申请记录，方便查看双方处理结果。
- App 内「审批详情」支持用户处理陪伴者提交的申请：通过、暂缓、驳回。
- 继续保留 `save_wallet_request_result`，用于更中性地保存处理结果。


## v0.3.8.6 反馈修复

- 应用门禁悬浮窗改为全屏触摸拦截兜底：全屏页受系统限制时，悬浮层会挡住下方 App 点击和滑动。
- 小金库「待审批」统一改为「审批列表」，生活细节摘要改为“待处理/审批记录”，避免把历史记录误认成未处理。
- 审批列表新增「我的申请」和「陪伴者的申请」分组，支持折叠查看，为双向申请做准备。
- MCP 增加更中性的 `save_wallet_request_result` 处理结果工具，保留旧工具兼容。

## v0.3.8.6 小金库更新
- 守护页新增“小金库”：本地记账、预算规则、历史月份侧边栏、待审批详情。
- 小金库默认保存在手机本地；MCP/AI 只能在用户配置连接后读取预算摘要、最近记录和待审批。
- 陪伴者名字跟随设置页“陪伴者/AI 名称”，例如会显示“等待陪伴者审批”。
- 开启通知读取权限后，可在本地识别疑似支付通知并生成待确认账单；不读取支付密码，不接管银行卡。


## v0.3.8.6 修复重点

- 修复归电来电页「接通」后没有跳转到归电设置里指定包名的问题：接通后会读取 `guidian_target_package`，关闭来电页后再按包名启动目标 App，失败时写入调试日志并提示原因。
- 修复陪伴页「xx 的行动」不显示 AI/MCP 工具调用记录的问题：MCP 行动会同步写入统一行动日志，手机端同步时会合并本地与远端行动记录，不再被本地旧记录遮挡。
- Railway 文档改为手动双服务部署说明，移除 README 中未生成模板码的一键部署引导，避免用户误把 server 域名当成 MCP 域名。
- 今日页「此刻状态」卡片新增媒体状态：用户开启通知使用权后，可显示正在播放的歌曲/音频标题、歌手、播放状态与来源 App。

## v0.3.8.6 快速修复

- 补充 `server/Dockerfile`、`.dockerignore` 与 `requirements.txt`，避免 Railway 在 server 服务构建阶段无法识别 Python 项目。Railway server 服务现在推荐：Root Directory=`server`，Build Command 留空，Start Command 留空，由 Dockerfile 启动 `python linjian_server.py`。

- 修复网页端 MCP 管理器连接公开版 MCP 时的跨域响应头问题。
- 设置页「连接设置」会自动保存服务器地址、Token、设备 ID 与轮询间隔，杀掉后台再打开也会保留。

## v0.3.8.6 日记与守护日历更新

- 守护日历事件补充稳定 ID，支持在日期详情卡中编辑、确认删除，并兼容没有 ID 的旧数据。
- MCP 完善 `list_guardian_days`、`add_guardian_day`、`update_guardian_day`、`delete_guardian_day`。
- 陪伴页新增本机保存的“TA 的日记”：纸质封面与纸页、左侧日期抽屉、关键词/日期搜索、封面修改和导入导出备份。
- 日记正文默认仅显示四行摘要；点击对应纸页展开全文，再次点击即可收起，切换纸页时会自动折叠上一篇。
- 日记封面增加书脊、纸页厚度、书签和立体阴影，书名信息直接居中落在封面上并按屏幕高度调整视觉位置；日记与守护日历二级页改用精简顶部，日历主体适当下移。
- 日记正文纸张改为浅粉米白渐变，使用淡灰紫横线、粉色页边线和低对比纸纤维，心情以柔和印章显示。
- “更多”菜单支持手动添加日记；MCP 支持创建、读取、搜索、更新和删除日记本及日记。
- 固定公开版签名保持不变，可继续覆盖安装旧版本。

## v0.3.6.6 稳定性修复

- 将默认轮询间隔从旧版的 1.5 秒调整为 3 秒，并对过低旧配置自动回到 3 秒，减少多人公开使用时的请求压力。
- 生活状态上传改为 10 秒限频，不再每轮都向后端上报。
- 无障碍服务改为兜底轮询：前台服务运行时不重复请求 `/api/poll`，降低系统后台压力，也减少触发 429 的概率。
- MCP 遇到 429 会返回清晰限流提示；手机端遇到 429 会短暂退避后重试。
- 无障碍异常提示补充“允许受限设置、关闭电池优化、允许后台运行”等排查步骤。


## v0.3.6.6 修复重点

- 修复 MCP 状态读取容易超过 20 秒的问题：`get_phone_state` 改为快速读取服务器缓存，不再被活动日志记录或长轮询拖慢。
- 缩短 MCP 到后端、命令排队和命令状态查询的默认等待时间，避免 Render 冷启动/网络波动时整条工具链卡死。
- `open_app` / 门禁类工具增加空参数拦截：缺少 App 名称或包名时直接返回明确错误，不再下发到手机端形成 `package_empty`。
- 增加常用 App 名称到包名的 MCP 侧兜底映射，例如小红书、抖音、微信、QQ、QQ音乐。
- 保留 v0.3.6.3 的守护日历保存修复与应用门禁旧版工具名兼容。
- 清理公开包里的 `server/.env` 与 `__pycache__`，只保留 `.env.example`。
- 固定签名保持不变，可覆盖安装上一版公开版。


[![Deploy to Render](https://render.com/images/deploy-to-render-button.svg)](https://render.com/deploy?repo=https://github.com/linzhi-524/linjian-peek-public)


掌心窗公开版是一个可自部署的 Android 陪伴窗：手机端负责展示、截图/读屏、通知、日历、门禁与息屏等本机能力；同步后端负责保存状态和下发指令；MCP 服务负责把这些能力暴露给 AI 客户端使用。

公开版对齐私人版的粉白卡片 UI，但不复制私人绑定内容。它适合作为公开模板，让用户自己填写名字、目标 App 和服务器配置。

## 本版内容

- **今日页**：今日窗语、今日专注、天气、下一件事、手机电量、此刻状态（姿态/光线/当前 App/授权后定位与媒体播放）、今日轨迹。
- **陪伴页**：陪伴对象卡片、最近一句话、陪伴天数、下个纪念日、行动记录、归电入口，以及本机保存的“TA 的日记”。
- **守护页**：守护日历、目标 App 设置、应用门禁、屏幕休息、提醒、天气、息屏。
- **设置页**：服务器连接、Token、设备 ID、用户名称、陪伴对象名称、目标 App、权限、主动提醒、周期、主题、调试、版本更新、许可。
- **MCP**：截图、读屏、点击、输入、通知、天气、日历、归电、门禁、屏幕休息、提醒、到访记录、关心策略等通用工具。

公开版只保留适合公开发布和自部署的通用能力，不包含私人绑定内容、私人 Token、私人服务地址、固定人物关系或不可公开的专属接口。

## 个性化配置

在 Android 设置页填写：

- `userName`：用户自己的名字，留空时默认“宝宝”。
- `companionName`：AI 或陪伴对象名字，留空时默认“陪伴者”。
- `targetApps`：目标 App 列表，每行一个，格式为 `名称|Android包名`，例如 `ChatGPT|com.openai.chatgpt`。

姓名会用于主要界面文案、归电页、提醒、门禁和行动记录。目标 App 会用于归电目标、守护统计、门禁/屏幕休息、回家模式和 MCP 打开 App。

## 目录结构

```text
android/      Android 客户端和 GitHub Actions 构建脚本
server/       Python 同步后端，零第三方依赖
mcp/          Node.js MCP 服务
docs/         安装、版本和 MCP 工具说明
render.yaml   Render Blueprint 一键部署配置
update.json   版本更新信息
```

## 构建 APK

### GitHub Actions 构建

1. 将源码包解压并覆盖到公开仓库根目录，确保 `.github`、`android`、`server`、`mcp` 位于根目录。
2. 打开 GitHub 仓库 → **Actions** → **Build Android Public APK** → **Run workflow**。
3. 构建成功后下载 `zhangxinchuang-public-v0.3.9.1-apk` artifact。

构建产物为：

```text
android/Zhangxinchuang-public-v0.3.9.1.apk
```

版本名 `0.3.9.1`，版本码 `30901`。

### 固定签名

公开版已经使用固定签名：

```text
android/signing/zhangxinchuang-public-release.p12
```

这份证书与私人版完全分离。后续公开版升级必须保留这份证书，否则已安装用户无法直接覆盖升级。不要把私人版签名混入公开版。

## Render 一键部署

点击 README 顶部的 **Deploy to Render**，或在 Render 中使用本仓库的 `render.yaml` Blueprint。

Blueprint 会创建两个 Web Service：

- `zhangxinchuang-server`：手机端连接的同步后端。
- `zhangxinchuang-mcp`：AI/MCP 客户端连接的 MCP 服务。

Blueprint 会自动生成并共享：

- `LINJIAN_TOKEN`：手机端、后端和 MCP 共同使用的访问令牌。
- `LINJIAN_DEFAULT_DEVICE`：默认设备 ID，默认 `android-phone`。
- `LINJIAN_URL`：MCP 自动引用 `zhangxinchuang-server` 的公网 HTTPS 地址，不需要手动填写。

部署完成后：

1. 打开 `zhangxinchuang-server` 的公网地址，访问 `/health`，看到 `ok: true` 即后端在线。
2. 打开 `zhangxinchuang-mcp` 的公网地址，访问 `/health`，看到 `has_url: true`、`has_token: true` 即 MCP 配置完成。
3. 在 Android 设置页填写：
   - 服务器地址：`zhangxinchuang-server` 的公网地址，不要多余斜杠。
   - Token：Render 自动生成的同一个 `LINJIAN_TOKEN`。
   - 设备 ID：建议与 `LINJIAN_DEFAULT_DEVICE` 一致，例如 `android-phone`。
4. 在 AI/MCP 客户端里填写 MCP 地址：
   - Streamable HTTP：`https://你的-mcp-域名/mcp`
   - SSE：`https://你的-mcp-域名/sse`

如果你是从旧版 0.3.6.3 更新上来，**直接重新部署 MCP 服务即可**。新版 MCP 会兼容旧环境变量：即使 `LINJIAN_URL` 仍然是旧版自动写入的 `http://zhangxinchuang-server-xxxx:10000` 内网地址，也会自动兜底转换为 `https://zhangxinchuang-server-xxxx.onrender.com` 公网地址再连接。

如果你重新同步/刷新 Blueprint，新版会自动把 `LINJIAN_URL` 改为引用 server 的公网 `RENDER_EXTERNAL_URL`；如果你只点 **Redeploy**，也可以依靠新版 MCP 的兜底逻辑修复，不需要用户手动复制 URL。

验证方式：打开 `zhangxinchuang-mcp` 的 `/health`，如果看到 `fallback_linjian_urls` 里出现 `https://...onrender.com`，说明旧内网地址兼容逻辑已经生效。

## Railway 手动双服务部署

Railway 请使用手动双服务部署。后端和 MCP 要分成两个服务，它们可以来自同一个 GitHub 仓库，但 Root Directory、启动命令和环境变量不同。

### 第一步：准备 Token

先生成一个长随机 Token：

```bash
python3 - <<'PY'
import secrets
print(secrets.token_urlsafe(32))
PY
```

后面的 server、mcp、Android 设置页都使用同一个 Token。

### 第二步：部署 server 服务

在 Railway 新建一个 Project，然后从 GitHub 仓库创建第一个服务：

```text
服务名：server
Root Directory：server
Build Command：留空
Start Command：留空
Dockerfile Path：Dockerfile
```

环境变量：

```env
LINJIAN_TOKEN=第一步生成的长随机token
LINJIAN_HOST=0.0.0.0
LINJIAN_KEEP=3
LINJIAN_DEFAULT_DEVICE=android-phone
```

注意：Railway 会自动提供 `PORT`，不要强行写死 `LINJIAN_PORT`。后端代码会优先读取 Railway 的 `PORT`。server 目录已经内置 `Dockerfile`，Root Directory 设为 `server` 后，Railway 不需要再猜 Python 构建方式。

部署完成后，给 server 服务生成公网域名，访问：

```text
https://你的-server-域名/health
```

看到 `ok: true` 就说明 server 可用。

### 第三步：部署 MCP 服务

在同一个 Railway Project 中，从同一 GitHub 仓库再创建第二个服务：

```text
服务名：mcp
Root Directory：mcp
Build Command：留空
Start Command：pnpm start
```

环境变量：

```env
LINJIAN_URL=https://你的-server-域名
LINJIAN_TOKEN=第一步生成的同一个长随机token
LINJIAN_DEFAULT_DEVICE=android-phone
```

`LINJIAN_URL` 必须填写 server 的公网地址，不要填 MCP 自己的地址，也不要在末尾加 `/`。

部署完成后，给 MCP 服务生成公网域名，访问：

```text
https://你的-mcp-域名/health
```

看到 `ok: true`、`has_url: true`、`has_token: true` 就说明 MCP 可用。注意：AI/MCP 客户端只能填写 MCP 服务域名，不能把 server 域名加 `/mcp` 当作 MCP 地址。

AI/MCP 客户端连接：

```text
https://你的-mcp-域名/mcp
```

或：

```text
https://你的-mcp-域名/sse
```

### 第四步：连接 Android

Android 设置页填写：

```text
服务器地址：https://你的-server-域名
Token：第一步生成的同一个 token
设备 ID：android-phone
```

然后打开无障碍服务、通知权限、使用情况访问权限，回到 App 点击启动。用“测试截图上传”或 MCP 的 `linjian_status` 检查是否连通。

如果开启无障碍后回到掌心窗仍显示“已关闭/待开启”，请先安装本版或更新后的 APK；本版已修复无障碍服务声明与状态检测。若系统提示“受限设置/出于安全考虑不可用”，请到系统设置 → 应用 → 掌心窗 → 右上角菜单 → 允许受限设置，然后再回无障碍里开启“掌心窗服务”。

## 局域网部署教程

局域网部署适合只在自己电脑和自己手机之间使用。电脑和手机需要连接同一个 Wi-Fi。

### 启动 server

```bash
cd server
cp .env.example .env
```

修改 `server/.env`：

```env
LINJIAN_TOKEN=你的长随机token
LINJIAN_HOST=0.0.0.0
LINJIAN_PORT=8513
LINJIAN_KEEP=3
LINJIAN_DEFAULT_DEVICE=android-phone
```

启动：

```bash
python3 linjian_server.py
```

查看电脑局域网 IP，例如 `192.168.1.23`。手机端服务器地址填写：

```text
http://192.168.1.23:8513
```

如果手机连不上，优先检查：电脑防火墙、手机和电脑是否同一 Wi-Fi、地址是否多写斜杠、Token 是否一致。

### 启动本地 MCP

```bash
cd mcp
npm install
LINJIAN_URL=http://192.168.1.23:8513 LINJIAN_TOKEN=你的长随机token npm start
```

默认 MCP 监听：

```text
http://127.0.0.1:8787/mcp
http://127.0.0.1:8787/sse
```

如果 AI/MCP 客户端和 MCP 服务不在同一台电脑，需要把 `127.0.0.1` 换成 MCP 所在电脑的局域网 IP，并确认防火墙放行端口 `8787`。

Android 允许连接自建局域网 HTTP 地址；公网部署仍建议使用 HTTPS。

## MCP 工具概览

MCP 详细工具说明见 [docs/mcp.md](docs/mcp.md)。常用工具分组如下：

- **状态与截图**：`linjian_status`、`peek_screen`、`latest_screen`、`get_life_state`、`get_phone_state`、`get_screen_nodes`。
- **点击与输入**：`tap_text`、`input_text`、`send_phone_command`、`run_sequence`、`run_preset`。
- **App 控制**：`open_app`、`phone_home`、`phone_back`、`phone_recents`、`phone_screen_off`、`list_known_apps`、`save_known_app`。
- **通知与提醒**：`send_notification`、`set_alarm`、`get_weather_state`、`send_weather_notification`。
- **日历与窗语**：`get_window_whisper`、`set_window_whisper`、`list_guardian_days`、`add_guardian_day`、`update_guardian_day`、`delete_guardian_day`。
- **TA 的日记**：创建/重命名日记本，写入、读取、搜索、更新和删除本机日记；正文不默认上传云端。
- **归电与关心**：`get_guidian_state`、`set_guidian_config`、`trigger_guidian`、`mark_guidian_returned`、`active_care_check`、`care_action`。
- **门禁/屏幕休息**：`screen_break_app`、`temporary_screen_break_release`、`end_screen_break`、`extend_screen_break`、`get_screen_break_state`、`list_screen_break_apps`、`add_screen_break_app`、`set_screen_break_passphrase`。
- **到访与活动**：`record_visit`、`get_last_visit`、`get_visit_history`、`get_visit_stats`、`get_activity_events`、`add_activity_event`、`get_companion_actions`。

截图、读屏、自动点击、输入、门禁、息屏和自动评论都属于敏感能力，只应在本人设备、本人服务和明确授权的 MCP 客户端中使用。

## 安全边界

- 不要公开 `LINJIAN_TOKEN`。
- 不要把 MCP 服务连接到不可信客户端。
- 不要用掌心窗管理别人的设备。
- 自动发送评论、点击按钮、读屏、截图、门禁、息屏等动作建议默认手动确认。
- 公开版不应写死私人姓名、私人关系、私人服务地址或私人接口。

许可条款见 [LICENSE](LICENSE)。本项目不是 MIT/Apache/GPL 等开放源代码许可证；源码公开仅用于透明、学习、审计、个人自用部署和个人本地修改。未经项目作者明确书面许可，不得改名/换图标/换署名后重新发布衍生版本，也不得分发重新打包的 APK、镜像、压缩包或托管服务。

> v0.3.6.6 补充：无障碍状态会在从系统设置返回后延迟复查多次，并兼容不同系统写入无障碍组件名的格式差异；如果侧载 APK 被系统拦截，App 会提示去“应用信息 → 允许受限设置”，再回无障碍开启“掌心窗服务”。
