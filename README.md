# 掌心窗公开版 v0.3.7

## v0.3.7 日记与守护日历更新

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

> Railway 一键部署按钮需要先在 Railway 控制台生成 Template Code。生成后，把 `docs/railway-one-click.md` 里的 `YOUR_TEMPLATE_CODE` 替换掉，再把按钮复制到这里即可。

掌心窗公开版是一个可自部署的 Android 陪伴窗：手机端负责展示、截图/读屏、通知、日历、门禁与息屏等本机能力；同步后端负责保存状态和下发指令；MCP 服务负责把这些能力暴露给 AI 客户端使用。

公开版对齐私人版的粉白卡片 UI，但不复制私人绑定内容。它适合作为公开模板，让用户自己填写名字、目标 App 和服务器配置。

## 本版内容

- **今日页**：今日窗语、今日专注、天气、下一件事、手机电量、今日轨迹。
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
3. 构建成功后下载 `zhangxinchuang-public-v0.3.7-apk` artifact。

构建产物为：

```text
android/Zhangxinchuang-public-v0.3.7.apk
```

版本名 `0.3.7`，版本码 `30700`。

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

## Railway 一键部署

Railway 的一键部署依赖 **Railway Template**。本仓库已经整理好 server 与 MCP 的双服务配置说明；真正的一键按钮需要你先在 Railway 控制台把双服务项目生成为模板，然后得到 Template Code。

模板生成后，把下面链接中的 `YOUR_TEMPLATE_CODE` 替换成真实模板码，再放到 README 顶部即可：

```markdown
[![Deploy on Railway](https://railway.com/button.svg)](https://railway.com/new/template/YOUR_TEMPLATE_CODE?utm_medium=integration&utm_source=button&utm_campaign=zhangxinchuang)
```

模板内建议使用两个服务：

### server 服务

```text
Service Name：server
Root Directory：server
Build Command：留空或 echo ok
Start Command：python linjian_server.py
Healthcheck Path：/health
Public Networking：开启 HTTP 域名
```

环境变量：

```env
LINJIAN_TOKEN=${{ shared.LINJIAN_TOKEN }}
LINJIAN_HOST=0.0.0.0
LINJIAN_KEEP=3
LINJIAN_DEFAULT_DEVICE=${{ shared.LINJIAN_DEFAULT_DEVICE }}
```

### mcp 服务

```text
Service Name：mcp
Root Directory：mcp
Build Command：npm install
Start Command：npm start
Healthcheck Path：/health
Public Networking：开启 HTTP 域名
```

环境变量：

```env
LINJIAN_URL=https://${{ server.RAILWAY_PUBLIC_DOMAIN }}
LINJIAN_TOKEN=${{ shared.LINJIAN_TOKEN }}
LINJIAN_DEFAULT_DEVICE=${{ shared.LINJIAN_DEFAULT_DEVICE }}
```

### Shared Variables

在 Railway Template 中设置共享变量：

```env
LINJIAN_TOKEN=${{ secret(48) }}
LINJIAN_DEFAULT_DEVICE=android-phone
```

部署完成后：

1. 访问 `https://你的-server-域名/health`，确认后端在线。
2. 访问 `https://你的-mcp-域名/health`，确认 MCP 已连上 server。
3. Android 设置页填写 server 公网地址、同一个 Token 和设备 ID。
4. AI/MCP 客户端填写 `https://你的-mcp-域名/mcp` 或 `https://你的-mcp-域名/sse`。

更完整的 Railway 模板按钮配置见 [docs/railway-one-click.md](docs/railway-one-click.md)。手动双服务部署流程见 [docs/setup.md](docs/setup.md)。

## Railway 手动双服务部署

如果暂时没有 Railway Template Code，也可以手动部署。Railway 建议把后端和 MCP 分成两个服务，它们可以来自同一个 GitHub 仓库，但 Root Directory、启动命令和环境变量不同。

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
Build Command：留空或 echo ok
Start Command：python linjian_server.py
```

环境变量：

```env
LINJIAN_TOKEN=第一步生成的长随机token
LINJIAN_HOST=0.0.0.0
LINJIAN_KEEP=3
LINJIAN_DEFAULT_DEVICE=android-phone
```

注意：Railway 会自动提供 `PORT`，不要强行写死 `LINJIAN_PORT`。后端代码会优先读取 Railway 的 `PORT`。

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
Build Command：npm install
Start Command：npm start
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

看到 `ok: true`、`has_url: true`、`has_token: true` 就说明 MCP 可用。

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

许可条款见 [LICENSE](LICENSE)。

> v0.3.6.6 补充：无障碍状态会在从系统设置返回后延迟复查多次，并兼容不同系统写入无障碍组件名的格式差异；如果侧载 APK 被系统拦截，App 会提示去“应用信息 → 允许受限设置”，再回无障碍开启“掌心窗服务”。
