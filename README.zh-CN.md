# 服务器运维监控系统 · README（中文）

> 基于 **Spring Boot、Vue 3、InfluxDB** 的轻量级服务器运维监控平台。  
> 模块：`monitor_server/`（后端 API）、`monitor_client/`（采集端/探针）、`monitor-web/`（前端控制台）。

---

## ✨ 特性亮点
- **JWT 多租户**：登录颁发 JWT；按**角色**与**主机分配**进行细粒度授权（含终端权限）。
- **一次性注册令牌**：管理员生成短期一次性 Token；探针完成注册后自动开始上报。
- **实时与历史指标**：OS / CPU / 内存 / 磁盘 / 网络等；实时展示，历史写入 **InfluxDB**。
- **浏览器内 SSH**：后端代理 **WebSocket → SSH**；前端内嵌 xterm.js 交互。
- **治理与可观测性**：Redis 限流、**雪花 ID** 贯穿日志、Swagger/OpenAPI 在线调试。
- **可扩展集成**：RabbitMQ 邮件验证码流程、可选 MinIO 对象存储挂载。

---

## 🧱 架构
```mermaid
flowchart LR
  subgraph Client[monitor_client · Java Probe]
    OSHI --> Metrics
    Metrics -->|HTTP + Quartz| Server[monitor_server · API]
  end
  Web[monitor-web · Vue SPA] -->|REST + JWT| Server
  Web -->|WebSocket| Server
  Server --> MySQL[(MySQL: 关系元数据)]
  Server --> InfluxDB[(InfluxDB: 时序指标)]
  Server --> Redis[(Redis: 限流/验证码/缓存)]
  Server --> RabbitMQ[(RabbitMQ: 邮件队列)]
  Server --> MinIO[(MinIO: 对象存储)]
```

---

## 🗂 目录结构
```
monitor_server/   后端：Controller / Security(JWT) / WebSocket / 集成组件
monitor_client/   探针：OSHI 采集 / Quartz 定时 / 注册 & 上报
monitor-web/      前端：Vue 3 + Vite + Element Plus + Pinia + ECharts + xterm.js
monitor.sql       MySQL DDL：账户、客户端、硬件详情、SSH 凭据
```

---

## ✅ 系统要求（不使用 Docker Compose）
请在目标环境**自行安装并启动**下列组件，并确保后端主机可访问：
- **JDK**：后端使用 JDK **24**；探针使用 JDK **17**（与各模块 `java.version` 一致）。
- **Node.js 与 npm**：用于前端开发与构建。
- **基础服务**：MySQL（数据库名 `monitor`）、Redis、RabbitMQ、MinIO、SMTP、InfluxDB。

快速自检：
- MySQL：`mysql -h <host> -u <user> -p -e "SELECT 1"`  
- Redis：`redis-cli -h <host> -a <password> PING`  
- InfluxDB：浏览器访问 `http://<host>:8086` 并确认组织/桶/Token 已配置

---

## 🚀 快速开始

### 1) 初始化数据库
```sql
-- 在 MySQL 中执行仓库自带的 DDL
SOURCE /path/to/monitor.sql;
```

### 2) 配置并启动后端（`monitor_server/`）
编辑 `src/main/resources/application-dev.yml`：
- MySQL / Redis / RabbitMQ / MinIO / SMTP / InfluxDB 连接与凭据
- `spring.security.jwt.expire`（JWT 过期小时数）
- `spring.web.flow.*`（接口限流阈值）

启动：
```bash
cd monitor_server
./mvnw spring-boot:run
```
关键端点：
- 运维 API：`/api/**`
- 探针入口：`/monitor/**`
- WebSocket SSH：`/terminal/{clientId}`
- 文档：`/swagger-ui/`

### 3) 运行前端（`monitor-web/`）
```bash
cd monitor-web
npm install
# 将 VITE_API_BASE 指向后端地址（例如 http://127.0.0.1:8080）
npm run dev
```
> 若出现 “outside of serving allow list”，仅在**开发环境**放宽 `vite.config.ts` 中的 `server.fs.allow`。

### 4) 运行探针（`monitor_client/`）
```bash
cd monitor_client
mvn spring-boot:run
```
首次运行请输入：后端地址、**一次性注册令牌**、需监控的网络接口名。注册成功后将自动上报。

---

## ⚙️ 关键配置速查
| 键 | 作用 | 建议 |
|---|---|---|
| MySQL url/user/pass | 关系型数据源 | 生产使用最小权限的专用账号 |
| Redis host/password | 限流/验证码/缓存 | 必启认证；区分环境 |
| InfluxDB url/org/bucket/token | 时序指标存储 | 仅授予最小读/写权限 |
| RabbitMQ url/credentials | 邮件验证码队列 | 启用持久化与鉴权 |
| MinIO url/key/secret | 可选对象存储 | 仅开放必要桶与策略 |
| `spring.security.jwt.expire` | JWT 有效期（小时） | 生产 2–12h，并配合黑名单/踢出策略 |
| CORS allowed origins | 前端域名白名单 | 部署前务必收紧 |
| `spring.quartz.*` | 探针上报计划 | 按数据量/成本调优 |

---

## 🛠 常用操作
**生成注册令牌（管理员）**
```bash
curl -H "Authorization: Bearer <JWT>" http://<server>/api/monitor/register
```

**探针相关端点（服务端）**
- 注册：`POST /monitor/register`
- 硬件画像：`POST /monitor/detail`
- 运行时指标：`POST /monitor/runtime`

**浏览器 SSH**
- 保存主机 SSH 凭据：`POST /api/monitor/ssh-save`
- UI 打开“终端”抽屉 → 后端建立 `/terminal/{clientId}` WebSocket → SSH

---

## 🔐 安全最佳实践
- 使用强随机 **JWT Secret**；启用 **HTTPS** 与 **HSTS**。
- 严格 **CORS 白名单**；管理面可叠加 MFA 或 IP 白名单。
- Redis / RabbitMQ / MinIO / InfluxDB 全部开启认证，权限最小化。
- 统一日志与轮转；用 **雪花 ID** 贯穿与关联请求。

---

## 🩺 运维与排错
**无图表/无数据**：核对 InfluxDB org/bucket/token/url；确认探针运行且 Quartz 已启用。  
**注册/上报失败**：检查一次性令牌是否已被使用；查看探针控制台与后端日志，用雪花 ID 关联请求。  
**SSH 连接问题**：校验用户名/密钥/端口与堡垒机连通；确认反向代理未拦截 WebSocket 升级。  
**CORS 报错**：将后端 CORS 允许源调整为前端实际域名；仅在本地开发时临时放宽。

---

## 📈 指标（示例）
- **CPU**：逻辑核数、load、usage %
- **内存**：总量、可用、使用率 %
- **磁盘**：总量、已用、剩余、I/O（可扩展）
- **网络**：上/下行吞吐（Bytes/s）、接口名
- **主机元数据**：OS、版本、IP、位置标签等

> 时序写入 InfluxDB，按 hostId/metric 作为 measurement/标签进行建模，便于聚合与查询。

---

## 🔧 部署说明
- **开发**：后端与前端分开运行；前端通过 `VITE_API_BASE` 指向本地后端。  
- **生产**：Nginx/Caddy 反代后端，开启 TLS 与 WebSocket 透传；前端构建产物由反代托管。  
- **容量规划**：按“上报周期 × 主机数”评估 InfluxDB 存储与保留策略（Retention Policy），必要时分桶。

---

## 📄 许可与致谢
- 许可：见仓库 `LICENSE`（缺省视为保留所有权利）。
- 组件：Spring Boot、OSHI、Quartz、MyBatis-Plus、Redis、RabbitMQ、MinIO、InfluxDB、Swagger/OpenAPI、Vue 3、Element Plus、Pinia、ECharts、xterm.js。

---

## 📚 术语对照表（EN ⇄ ZH）
| English | 中文 |
|---|---|
| Agent / Probe | 采集端 / 探针 |
| Web console | 前端控制台 |
| Registration token (one-time) | 一次性注册令牌 |
| WebSocket → SSH | WebSocket 代理到 SSH |
| Runtime snapshot | 运行时快照 |
| Hardware profile | 硬件画像 |
| Throttling | 限流 |
| Snowflake IDs | 雪花 ID |
| Allow-list | 白名单 |
| Bastion host | 堡垒机 |
| Retention policy (RP) | 保留策略（RP） |
