# Docker 部署指南

本文档介绍如何使用 Docker Desktop（或 IDEA 自带的 Docker 插件）快速部署本项目的前后端与依赖服务。默认读者已经安装好 Docker Desktop，并在 IDEA 中启用了 Docker 插件。

## 目录结构概览

- `monitor_server/Dockerfile`：Spring Boot 后端镜像定义。
- `monitor-web/Dockerfile`：Vite 前端镜像定义。
- `.dockerignore`：用于减少上下文大小的忽略配置。
- `docker-compose.yml`：一次性拉起数据库、消息队列、对象存储、时序数据库以及前后端。

## 1. 准备工作

1. **修改必要配置（可选）**
   - `docker-compose.yml` 中默认使用的数据库密码、InfluxDB Token、MinIO 账号等均为示例值。生产环境请务必修改。
   - 如果不需要邮件功能，可删除 `SPRING_MAIL_*` 相关环境变量。
2. **初始化数据库**
   - `mysql` 服务启动后会自动创建名为 `monitor` 的库，但不会导入表结构。首次部署后请登录数据库执行项目提供的建表脚本。
3. **准备前端接口地址**
   - 前端构建阶段会读取 `VITE_API_BASE`，Compose 文件默认填入 `http://localhost:8080`。如需通过域名访问，请调整为对外暴露的后端地址。

## 2. 使用 Docker Compose 部署

### 2.1 命令行方式

```bash
# 在仓库根目录执行
docker compose up -d --build
```

- 该命令会先根据两个 Dockerfile 构建镜像，再启动所有容器。
- 若需要重新构建镜像，可执行 `docker compose build` 或者在 `up` 命令后附加 `--build`。
- 查看日志：`docker compose logs -f backend`、`docker compose logs -f frontend` 等。
- 停止并移除容器：`docker compose down`。若希望同时清理数据卷，可追加 `-v` 参数。

### 2.2 IDEA Docker 插件

1. 在 IDEA 右侧 `Services` 面板中添加 Docker 连接（本地 Docker Desktop 一般自动识别）。
2. 右键项目根目录的 `docker-compose.yml`，选择 **Deploy** 或 **Run**。
3. 如需重新构建镜像，可在弹出的运行配置中勾选 `--build`。
4. 通过 `Services` 面板可以查看各容器状态、日志以及端口映射。

## 3. 访问服务

- 前端：`http://localhost/`
- 后端 Swagger（若未禁用）：`http://localhost:8080/doc.html` 或相应地址。
- RabbitMQ 控制台：`http://localhost:15672/`（账号密码见 compose 文件）。
- MinIO 控制台：`http://localhost:9001/`
- InfluxDB 控制台：`http://localhost:8086/`

## 4. 常见问题

- **前端无法请求后端**：确认 `VITE_API_BASE` 是否指向后端的外部访问地址；若在容器网络内互访，可设置为 `http://backend:8080`。
- **后端连不上数据库/消息队列**：检查对应容器是否已启动，或调整环境变量中的主机名与凭据。Compose 默认的服务名（如 `mysql`、`rabbitmq`）会自动注册为 DNS 主机名。
- **端口冲突**：如宿主机已占用对应端口，可在 `docker-compose.yml` 中修改左侧的宿主机端口，例如 `8081:8080`。
- **是否需要手动打包可执行 JAR？**：不需要。在执行 `docker compose up -d --build` 时，后端镜像的多阶段构建会自动运行 `mvn -DskipTests package`，并将生成的 `app.jar` 复制到运行镜像中。如果希望在本机验证打包结果，可在 `monitor_server` 目录使用 `./mvnw clean package -DskipTests`（或安装了 Maven 的情况下执行 `mvn clean package -DskipTests`）。

部署完成后，即可通过 Docker Desktop 或 IDEA 的 Docker 面板对容器进行启动、停止、查看日志等操作。
