# 掌心窗公开版安装与部署流程（v0.3.7）

## 1. 选择部署方式

掌心窗公开版由三部分组成：

- Android 手机端
- `server/` Python 同步后端
- `mcp/` Node.js MCP 服务

推荐部署方式：

- 想省事：用 Render Blueprint 一键部署。
- 想做 Railway 一键部署：先按 [railway-one-click.md](railway-one-click.md) 创建 Railway Template，再把按钮放进 README。
- 想用 Railway 手动部署：server 和 mcp 分成两个 Railway 服务。
- 只在同一 Wi-Fi 内自用：局域网部署。

## 2. Render 一键部署

仓库根目录包含 `render.yaml`。在 Render 使用 Blueprint 部署后，会自动创建：

- `zhangxinchuang-server`
- `zhangxinchuang-mcp`

两个服务共用同一个自动生成的 `LINJIAN_TOKEN`。部署完成后：

1. server 访问 `/health`，确认后端在线。
2. mcp 访问 `/health`，确认 `has_url` 和 `has_token` 为 true。
3. Android 设置页填写 server 公网地址、同一个 Token、设备 ID。
4. MCP 的 `LINJIAN_URL` 会自动引用 server 的公网 `RENDER_EXTERNAL_URL`，Render 一键部署不需要手动填写；旧部署只重新部署 MCP 时，新版 MCP 也会把旧的 Render 内网 `hostport` 自动兜底为公网地址。
4. MCP 客户端填写 mcp 的 `/mcp` 或 `/sse` 地址。

## 3. Railway 一键部署按钮

Railway 的一键部署按钮需要先创建 Railway Template。创建步骤见 [railway-one-click.md](railway-one-click.md)。

模板按钮格式：

```markdown
[![Deploy on Railway](https://railway.com/button.svg)](https://railway.com/new/template/YOUR_TEMPLATE_CODE?utm_medium=integration&utm_source=button&utm_campaign=zhangxinchuang)
```

把 `YOUR_TEMPLATE_CODE` 换成你在 Railway 获得的模板码即可。

## 4. Railway 手动双服务部署

### 4.1 生成 Token

```bash
python3 - <<'PY'
import secrets
print(secrets.token_urlsafe(32))
PY
```

### 4.2 部署 server

Railway 服务设置：

```text
Service Name: server
Root Directory: server
Build Command: 留空或 echo ok
Start Command: python linjian_server.py
Healthcheck Path: /health
```

环境变量：

```env
LINJIAN_TOKEN=你的长随机token
LINJIAN_HOST=0.0.0.0
LINJIAN_KEEP=3
LINJIAN_DEFAULT_DEVICE=android-phone
```

不要手动写死 `LINJIAN_PORT`，Railway 会提供 `PORT`。

### 4.3 部署 mcp

Railway 服务设置：

```text
Service Name: mcp
Root Directory: mcp
Build Command: npm install
Start Command: npm start
Healthcheck Path: /health
```

环境变量：

```env
LINJIAN_URL=https://你的-server-域名
LINJIAN_TOKEN=同一个长随机token
LINJIAN_DEFAULT_DEVICE=android-phone
```

MCP 地址：

```text
https://你的-mcp-域名/mcp
https://你的-mcp-域名/sse
```

## 5. 局域网部署

### 5.1 启动 server

```bash
cd server
cp .env.example .env
python3 linjian_server.py
```

`.env` 示例：

```env
LINJIAN_TOKEN=你的长随机token
LINJIAN_HOST=0.0.0.0
LINJIAN_PORT=8513
LINJIAN_KEEP=3
LINJIAN_DEFAULT_DEVICE=android-phone
```

手机端服务器地址填写电脑局域网 IP：

```text
http://192.168.1.23:8513
```

### 5.2 启动 MCP

```bash
cd mcp
npm install
LINJIAN_URL=http://192.168.1.23:8513 LINJIAN_TOKEN=你的长随机token npm start
```

本机 MCP 地址：

```text
http://127.0.0.1:8787/mcp
http://127.0.0.1:8787/sse
```

## 6. 构建 APK

最省心：上传到 GitHub 后跑 Actions → **Build Android Public APK**。

构建产物：

```text
android/Zhangxinchuang-public-v0.3.7.apk
```

公开版使用固定签名 `android/signing/zhangxinchuang-public-release.p12`，不要删除或替换。

## 7. 手机端设置

- 安装 APK。
- 打开掌心窗。
- 填写服务器地址、Token、设备 ID。
- 设置用户名字、陪伴对象名字、目标 App。
- 开启无障碍服务。
- 授予通知权限、使用情况访问权限、必要的后台运行权限。
- 回 App 点击启动。
- 用“测试截图上传”或 MCP 的 `linjian_status` 检查连通。

## 8. 常见问题

- server `/health` 不通：检查部署日志、端口、环境变量和服务是否休眠。
- MCP `/health` 显示 `has_url: false`：检查是否缺少 `LINJIAN_URL`。Render 旧版部署通常不需要手动改，重新部署新版 MCP 后会自动把旧内网 `hostport` 兜底为公网地址。
- MCP `/health` 显示 `has_token: false`：没有设置 `LINJIAN_TOKEN`。
- 手机连不上：检查服务器地址不要多余斜杠，Token 完全一致，公网地址使用 HTTPS。
- 局域网连不上：检查手机和电脑是否同一 Wi-Fi，电脑防火墙是否放行端口。
- 截图失败：检查无障碍服务是否开启，手机端是否点了启动。
- 无障碍开启后回到 App 仍显示“已关闭”：先更新到修复版 APK；修复版把“掌心窗服务”声明为可由系统无障碍绑定，并改为读取系统已启用无障碍列表判断状态。若系统提示受限设置，请到系统设置 → 应用 → 掌心窗 → 右上角菜单 → 允许受限设置，再重新开启无障碍。

> v0.3.6.6 补充：无障碍状态会在从系统设置返回后延迟复查多次，并兼容不同系统写入无障碍组件名的格式差异；如果侧载 APK 被系统拦截，App 会提示去“应用信息 → 允许受限设置”，再回无障碍开启“掌心窗服务”。
