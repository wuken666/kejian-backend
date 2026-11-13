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

小程序体验版和正式版使用原生云调用。普通接口使用 `callContainer`，实时字幕使用 `connectContainer` 连接 Java 的 `/asr-stream`，仍需携带老师登录令牌。

录音先通过 `wx.cloud.uploadFile` 上传，再通过 `/api/lesson-sessions/{id}/cloud-audio-segments` 提交临时下载地址。必须设置 `CLOUD_STORAGE_DOWNLOAD_HOST` 为实际临时下载地址的主机名（不含协议、路径或签名）；后台仅接受此 HTTPS 主机和当前老师、课堂对应的文件路径，禁止跳转到其他地址。存储桶保持私有，不要把签名地址或密钥写入日志。

云端录音位于 `recordings/{teacherId}/{sessionId}/`，当前不会自动删除云端副本。上线前需制定并配置保留周期，与隐私指引一致。Java 识别阶段仍会使用容器本地音频副本，不能将其视为可靠的长期或跨实例存储，需验证重启后的恢复。实时识别服务仍需独立部署、配置 `ASR_BASE_URL`。

发布顺序：先发布 Java 后台并配置下载主机，再上传小程序体验版；关闭手机调试后验证登录、学生列表、签到、录音上传、结束整理与实时字幕。不要只凭开发者工具测试认定审核环境可用。

## 目录

- `src/`：Java API、数据库迁移与测试。
- `asr-service/`：Python 识别服务及 CPU 镜像构建配置，部署参数见该目录的 README。Java API 连接时需设置 `ASR_BASE_URL` 和与识别服务相同的 `ASR_SERVICE_TOKEN`。
- `Dockerfile`：仅构建 Java API。
