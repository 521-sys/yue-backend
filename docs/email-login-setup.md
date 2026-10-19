# 邮箱验证码登录配置与发布

核对日期：2026-10-19。邮箱登录默认关闭；没有完成邮件推送账户、域名、生产备份和真实投递验收前，不得打开网页入口。

## 阿里云邮件推送准备

复用现有 `gnep.online`，发信子域名使用 `mail.gnep.online`，无需购买新域名。在阿里云邮件推送控制台完成服务开通、发信域名和发信地址创建：

1. 添加 `mail.gnep.online`，按控制台当前显示的值在 DNS 中配置所有权验证、SPF、DKIM、DMARC 和 MX 记录。不要从本文复制账户专属记录值。
2. 等待控制台显示域名验证通过。DNS 变更最长可能需要 48 小时传播，验证未通过时不要继续。
3. 创建触发邮件发信地址，例如 `verify@mail.gnep.online`，设置独立 SMTP 密码。SMTP 密码不是阿里云账户登录密码。
4. 杭州区域默认使用 `smtpdm.aliyun.com:465` 和 SSL。阿里云 ECS 通常限制 25 端口，生产不要改用 25。

官方参考：[发信域名配置](https://help.aliyun.com/zh/direct-mail/user-guide/how-to-configure-sending-domain-names)、[SMTP 服务地址和端口](https://help.aliyun.com/en/direct-mail/smtp-endpoints)。控制台信息和官方文档变化时，以当前控制台为准。

## 服务器环境变量

生产私密配置位于 `/opt/yue/backend.env`，保持属主仅服务账户可读、权限 `0600`。禁止在聊天、日志、Git、构建参数或前端 `VITE_` 变量中暴露真实值。

先写入配置但保持 `EMAIL_ENABLED=false`：

```dotenv
EMAIL_ENABLED=false
EMAIL_CODE_PEPPER=<至少32字符的独立随机值>
EMAIL_EXPIRY_SECONDS=600
EMAIL_COOLDOWN_SECONDS=60
EMAIL_DAILY_LIMIT=10
EMAIL_IP_DAILY_LIMIT=30
EMAIL_MAX_FAILURES=5
EMAIL_CLEANUP_CRON=0 25 3 * * *
EMAIL_SMTP_HOST=smtpdm.aliyun.com
EMAIL_SMTP_PORT=465
EMAIL_SMTP_USERNAME=<控制台创建的SMTP用户名>
EMAIL_SMTP_PASSWORD=<独立SMTP密码>
EMAIL_FROM=verify@mail.gnep.online
EMAIL_FROM_NAME=粤语学习
EMAIL_SMTP_SSL=true
```

`EMAIL_CODE_PEPPER` 可用 `openssl rand -hex 32` 生成。更换 pepper 会使所有未使用验证码失效，不能复用 JWT 密钥、数据库密码或 SMTP 密码。

## 数据库迁移与后端发布

1. 只读检查 `nginx`、`yue-backend`、`mysql`、`nginx -t`、磁盘空间及固定部署路径。只核对变量名是否存在，不输出 `/opt/yue/backend.env` 的值。
2. 暂停写入，在同一时间戳备份目录保存 MySQL 全量转储、`/opt/yue/app.jar` 和 `/opt/yue/backend.env`；检查转储可读且含建表语句。
3. 先在数据库副本重复执行两次 `scripts/email-login-migration.sql`，确认均成功。随后在已备份的生产数据库执行一次并核验 `users.email`、唯一索引 `uk_users_email` 及 `email_login_codes` 表。
4. 保持 `EMAIL_ENABLED=false`，部署新 JAR，并以 `SPRING_JPA_HIBERNATE_DDL_AUTO=validate` 启动。检查服务为 active、日志无配置泄漏、既有密码登录可用。
5. 关闭态门禁：向 `/api/auth/email/code` 提交合法邮箱，应返回 HTTP 503 和固定错误“邮箱服务暂不可用，请稍后重试”；网页此时仍不得显示邮箱登录标签。

## 开启与真实验收

确认域名和发信地址通过后，将 `EMAIL_ENABLED=true`，重启后端并完成以下小流量验收：

1. 向用户指定的测试邮箱仅发送一次验证码，确认发件人、主题、中文内容和到达时间正常；日志不得出现完整邮箱、验证码或 SMTP 响应正文。
2. 使用验证码成功登录，随后重复使用同一验证码应失败；错误验证码应只返回统一的无效或过期提示。
3. 确认新邮箱用户只创建一条记录，刷新页面后 JWT 登录状态与云端学习进度同步正常。
4. 用 `VITE_EMAIL_LOGIN_ENABLED=true` 构建网页，部署静态文件后确认“我的”页只显示密码登录和邮箱登录，不显示短信入口。
5. 再次核对密码登录、公开接口、管理后台、Nginx 和后端服务状态。

记录验收时间、制品校验和、备份路径和服务商投递状态，但不记录测试邮箱、验证码或任何密钥。

## 回滚

网页异常时先恢复上一版静态制品或以 `VITE_EMAIL_LOGIN_ENABLED=false` 重建，后端保持可兼容。邮件投递异常时立即设置 `EMAIL_ENABLED=false` 并重启后端，再检查固定 503 关闭态门禁。

后端需要回滚时，恢复同一备份点的上一版 JAR 和 `backend.env` 后重启并复查密码登录。新增可空字段和验证码表默认保留，避免为回滚直接删表；若必须恢复数据库，使用上线前全量转储并评估上线后产生的用户数据。任何阶段失败都不要继续发布下一阶段。
