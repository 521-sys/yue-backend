# 短信验证码登录配置与上线说明

短信登录代码默认关闭（`SMS_ENABLED=false`）。未完成腾讯云审核、数据库迁移和单条验收前，不得在生产开启。

## 腾讯云准备

1. 在腾讯云短信控制台创建国内短信应用，取得 SDK App ID。
2. 提交并通过短信签名和登录验证码模板审核；模板参数顺序必须与“验证码、有效分钟数”一致。
3. 创建仅具短信发送权限的专用子账号密钥，不复用管理员密钥。
4. 设置套餐/费用预算与用量告警，并在控制台每日核对发送量和失败率。

## 环境变量

敏感值只写入服务器受限环境文件，不写入 Git、日志或前端：

- `SMS_ENABLED`：默认 `false`。
- `SMS_CODE_PEPPER`：至少 32 个字符的随机秘密，用于验证码和 IP 的 HMAC；启用后不得随意更换。
- `SMS_EXPIRY_SECONDS`：默认 `300`。
- `SMS_COOLDOWN_SECONDS`：默认 `60`。
- `SMS_PHONE_DAILY_LIMIT`：默认 `10`。
- `SMS_IP_DAILY_LIMIT`：默认 `30`。
- `SMS_MAX_FAILURES`：默认 `5`。
- `SMS_CLEANUP_CRON`：默认每天 03:15（Asia/Shanghai）。
- `SMS_TRUSTED_PROXY_ADDRESSES`：可信反向代理地址列表；只有这些来源的 `X-Forwarded-For` 会被采用。
- `SMS_TENCENT_SECRET_ID`、`SMS_TENCENT_SECRET_KEY`：专用子账号凭据。
- `SMS_TENCENT_SDK_APP_ID`、`SMS_TENCENT_SIGN_NAME`、`SMS_TENCENT_TEMPLATE_ID`。
- `SMS_TENCENT_REGION`：默认 `ap-guangzhou`。

## 安全上线顺序

1. 备份生产数据库，并记录可恢复的备份路径。
2. 保持 `SMS_ENABLED=false` 部署新后端。
3. 使用受限数据库账号执行迁移，例如：

   ```text
   mysql --host=<host> --user=<migration-user> --password <database> < scripts/sms-login-migration.sql
   ```

4. 确认 `sms_login_codes` 表、唯一约束和两个查询索引存在；后端以 Hibernate `validate` 正常启动。
5. 配置全部环境变量，复核代理地址、日限额、腾讯签名和模板；重启后仍保持关闭。
6. 检查应用日志不含手机号、验证码、IP、密钥或腾讯原始错误载荷。正常接口契约：发送成功为 `202`；限流为 `429` 并带 `retryAfterSeconds`；未配置或供应商失败为稳定的 `503`。
7. 经授权后设置 `SMS_ENABLED=true`，只向指定测试手机号发送一条验证码；验证首次登录成功、同一验证码复用失败，并记录腾讯投递状态和消耗条数，不记录手机号或验证码。

## 回滚

优先将 `SMS_ENABLED=false` 并重启后端；密码登录继续可用。若应用版本也需回滚，先停写、恢复已记录的数据库备份，再部署旧版本。不要在有活动验证码时直接删除表。

## 当前验收边界

截至 2026-10-17，自动化测试和构建已通过，但没有获批的腾讯短信配置和用户指定测试手机号，因此尚未发送真实短信、未在生产启用，也未执行生产数据库迁移。
