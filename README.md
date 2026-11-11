# 课见后端

独立的 Spring Boot API 仓库，不包含小程序、网页前端、学生数据或录音文件。`asr-service/` 包含独立的 Python 语音识别服务及 Dockerfile；它不会随 Java Dockerfile 自动部署。

## 运行和构建

- 需要 JDK 21、Maven Wrapper 和 MySQL 8。
- 在运行环境中设置 `DB_URL`、`DB_USERNAME`、`DB_PASSWORD` 和 `ADMIN_ACCESS_TOKEN`。数据库密码和后台管理密钥没有默认值。
- 微信登录需要 `WECHAT_APP_ID`、`WECHAT_APP_SECRET`，并保持 `AUTH_REQUIRE_TEACHER_TOKEN=true`。AI 服务按需配置 `AI_MODEL_PROVIDER`、`AI_MODEL_BASE_URL`、`AI_MODEL_API_KEY`、`AI_MODEL_PRIMARY` 等变量。
- `./mvnw test` 执行测试；`./mvnw package` 构建。集成测试会连接配置的 MySQL，请使用隔离的测试数据库。
- 应用启动时执行 Flyway 数据库迁移；首次部署前确认数据库目标和备份。

## 微信云托管

使用仓库根目录的 `Dockerfile` 构建，服务监听端口为 `8080`。在平台的环境变量或密钥管理中配置以上变量，不要把实际密钥提交到 Git。

目前音频分片暂存在服务本地目录，不能将容器本地存储视为可靠的长期或跨实例存储。上线录音前需接入可靠共享存储并验证恢复和续传；实时识别服务也需独立部署、配置 `ASR_BASE_URL` 并验证 WebSocket 通路。

## 目录

- `src/`：Java API、数据库迁移与测试。
- `asr-service/`：Python 识别服务及 CPU 镜像构建配置，部署参数见该目录的 README。Java API 连接时需设置 `ASR_BASE_URL` 和与识别服务相同的 `ASR_SERVICE_TOKEN`。
- `Dockerfile`：仅构建 Java API。
