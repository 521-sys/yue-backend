# 管理后台第一版运行与发布

核对日期：2026-10-15。本次完成本地实现和验证，未部署生产。

## 数据库与运行配置

1. 停止内容写入，备份 MySQL 数据库、现有应用包和音频目录。先在数据库副本演练。
2. 用 MySQL 客户端选择应用数据库，执行 `scripts/admin-v1-migration.sql`。脚本添加发布状态、分类、难度、排序、版本、粤拼，以及音频索引和审计表。首次添加状态列时，已有官方场景标为已发布；非官方仍受所属用户访问限制。
3. 执行 `scripts/admin-v1-seed.sql`，导入缺失的 5 个内置场景、28 句台词。按 code 保留既有数据；重复执行不会覆盖运营修改或重新发布下架内容。导出源更新时从工作区根运行 `node yue-backend/scripts/export-builtin-scenes.mjs`，两端同 code 内容不同会报错。
4. 设置 `SPRING_JPA_HIBERNATE_DDL_AUTO=validate`，启动新后端；不要依赖生产自动改表。
5. 按顺序发布后端、后台、网页、小程序。新用户端使用 `/api/public/scenes`，后端未更新前不应单独发布新客户端。

后台使用既有 `ADMIN_USERNAME` 和 `ADMIN_PASSWORD_HASH`（BCrypt），空密码哈希无法登录；沿用安全配置中的 JWT_SECRET。禁止把密码哈希、数据库密码写入仓库。配置后台 `VITE_API_BASE=https://目标域名/api` 后运行 `npm run build`，将 dist 放到独立后台站点；相应来源加入后端 CORS_ORIGINS。网页 VITE_API_BASE 是服务器根地址（不含 /api），与后台配置不同。小程序沿用其 API_BASE，并配置合法 HTTPS request/download 域名。

## 音频存储

设置 `ADMIN_AUDIO_DIR` 为持久目录，默认 `./data/admin-audio`，运行用户需有读写权限。数据库索引与该目录必须一起备份/恢复；UUID 文件名由后端管理，不能只备份 jar。既有 static/audio 作为只读素材继续保留。

支持 WAV、MP3，单文件最多 20 MiB。MP3 解析依赖 ffprobe，可通过 DUBBING_FFPROBE 指定路径。反向代理对 `/api/admin/audio` 和 `/api/admin/audio/{数字ID}` 的上传请求允许 21 MiB；保留既有配音路由设置，其余 multipart 仍为 2 MiB。公共读取 `/audio/uploads/{opaque}` 必须代理到后端，不能被静态 `/audio/` 配置截获。后台返回正确 Content-Type，支持 Range；缓存要求重新验证，替换保持音频 key 稳定。

删除前用资源行锁和引用当前读保护所有台词写入入口。替换成功提交后删除旧文件，失败回滚清理新文件；读取期间持共享锁获取完整音频快照，单次读取最多占用 20 MiB 字节数组，部署时按并发量预留内存。

## 本地隔离预览

在后端运行：`mvn spring-boot:test-run -Dspring-boot.test-run.main-class=com.yue.admin.AdminPreviewApplication`。
预览仅绑定 127.0.0.1:18081，使用 H2 内存库和测试账号 `preview` / `local-preview-only`，重启丢失测试场景。测试入口不打入生产 jar。
管理端 PowerShell：`$env:VITE_API_BASE='http://127.0.0.1:18081/api'`，再运行 `npm run dev -- --host 127.0.0.1 --port 5175`。

## 回滚

先停止后台写入，恢复上一版应用及客户端。新增列/表可保留，避免直接删除新内容；若需数据库恢复，应连同同一备份时点的音频目录恢复，并评估备份后写入的数据。禁止仅删除上传目录或仅恢复数据库。生产迁移与回滚尚未实际执行。
