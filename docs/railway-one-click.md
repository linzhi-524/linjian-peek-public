# Railway 一键部署按钮配置（v0.3.7）

Railway 的一键部署按钮基于 Railway Template。源码包本身不能直接生成 Template Code，需要先在你的 Railway 工作区里创建模板；模板创建后，Railway 会给出 Template URL 和按钮代码。

## 1. 推荐模板结构

在 Railway Template 中放两个服务：

### server

```text
Service Name: server
Source Repo: 你的公开仓库地址
Root Directory: server
Build Command: 留空或 echo ok
Start Command: python linjian_server.py
Healthcheck Path: /health
Public Networking: HTTP 域名开启
```

变量：

```env
LINJIAN_TOKEN=${{ shared.LINJIAN_TOKEN }}
LINJIAN_HOST=0.0.0.0
LINJIAN_KEEP=3
LINJIAN_DEFAULT_DEVICE=${{ shared.LINJIAN_DEFAULT_DEVICE }}
```

不要设置 `LINJIAN_PORT`。Railway 会自动提供 `PORT`，后端会优先读取它。

### mcp

```text
Service Name: mcp
Source Repo: 你的公开仓库地址
Root Directory: mcp
Build Command: npm install
Start Command: npm start
Healthcheck Path: /health
Public Networking: HTTP 域名开启
```

变量：

```env
LINJIAN_URL=https://${{ server.RAILWAY_PUBLIC_DOMAIN }}
LINJIAN_TOKEN=${{ shared.LINJIAN_TOKEN }}
LINJIAN_DEFAULT_DEVICE=${{ shared.LINJIAN_DEFAULT_DEVICE }}
```

`LINJIAN_URL` 指向 server 的公网域名，不要指向 mcp 自己。

## 2. Shared Variables

模板里建议设置共享变量：

```env
LINJIAN_TOKEN=${{ secret(48) }}
LINJIAN_DEFAULT_DEVICE=android-phone
```

这样每次用户部署模板时都会生成自己的 Token，server 与 mcp 使用同一个 Token。

## 3. 生成 Railway Template

1. 先在 Railway 里手动部署并测试 server、mcp 两个服务。
2. 打开 Project Settings。
3. 找到 **Generate Template from Project**。
4. 创建模板，检查两个服务的 Root Directory、Start Command、Healthcheck、Public Networking 和变量。
5. 保存模板后复制 Template URL 或 Template Code。

## 4. README 按钮

得到 Template Code 后，把下面的 `YOUR_TEMPLATE_CODE` 替换成真实模板码，再复制到 README 顶部：

```markdown
[![Deploy on Railway](https://railway.com/button.svg)](https://railway.com/new/template/YOUR_TEMPLATE_CODE?utm_medium=integration&utm_source=button&utm_campaign=zhangxinchuang)
```

按钮图片地址使用 Railway 官方按钮：

```text
https://railway.com/button.svg
```

## 5. 部署后检查

部署完成后：

```text
https://你的-server-域名/health
https://你的-mcp-域名/health
```

server 看到 `ok: true`，mcp 看到 `ok: true`、`has_url: true`、`has_token: true`，就可以在 Android 设置页填写 server 地址、Token 和设备 ID。

MCP 客户端地址：

```text
https://你的-mcp-域名/mcp
https://你的-mcp-域名/sse
```
