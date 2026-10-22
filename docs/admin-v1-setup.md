# 管理后台第一版运行与发布

核对日期：2026-10-17。生产部署和可回滚验收已完成。

## 生产发布目标与固定路径

本次复用现有 `gnep.online` 服务器、域名和 TLS 证书，不创建新基础设施。用户网页
继续位于 `https://gnep.online/`，管理端入口固定为
`https://gnep.online/admin/`，管理端静态目录为 `/var/www/yue-admin`，后端 JAR
为 `/opt/yue/app.jar`，私密环境配置为 `/opt/yue/backend.env`，Nginx 站点配置为
`/etc/nginx/sites-available/yue`。管理音频持久目录固定为
`/opt/yue/admin-audio`，电影配音视频持久目录固定为
`/opt/yue/dubbing-videos`。

发布顺序固定为：生产备份及迁移演练 → 数据库迁移和种子 → 后端 → 管理端 → 用户
网页。任一阶段的校验失败都停止下一阶段，并按本文回滚；小程序只做兼容性构建，
不在本次服务器发布中提交微信审核。

### 只读预检

变更生产前先检查 `nginx`、`yue-backend`、`mysql` 服务状态、`nginx -t`、磁盘
容量、Java/MySQL/Nginx 版本，以及上述固定路径是否仍与线上一致。只核对
`/opt/yue/backend.env` 中变量名是否存在，禁止输出值；必须包含数据库、JWT、微信、
腾讯识别配置及 `ADMIN_USERNAME`、`ADMIN_PASSWORD_HASH`、`ADMIN_AUDIO_DIR`、
`ADMIN_DUBBING_VIDEO_DIR`、
`SPRING_JPA_HIBERNATE_DDL_AUTO`。管理员密码哈希为空或
`SPRING_JPA_HIBERNATE_DDL_AUTO=validate` 未生效时不得继续。

### 协调备份

暂停内容写入后，在 `/opt/yue/release-backup-<时间戳>/` 同时保存 MySQL 完整转储、
`app.jar`、`backend.env`、Nginx 站点配置、`/var/www/yue`、已有的
`/var/www/yue-admin`（若存在）、`/opt/yue/admin-audio`（若存在）以及
`/opt/yue/dubbing-videos`（若存在）。数据库、管理音频和电影配音视频目录是同一
恢复单元。迁移前必须检查归档可读、压缩包完整且数据库转储包含
建表语句；缺少任一必需备份就停止发布。

## 数据库与运行配置

1. 停止内容写入，备份 MySQL 数据库、现有应用包和音频目录。先在数据库副本演练。
2. 用 MySQL 客户端选择应用数据库，执行 `scripts/admin-v1-migration.sql`。脚本添加发布状态、分类、难度、排序、版本、粤拼，以及音频索引和审计表。首次添加状态列时，已有官方场景标为已发布；非官方仍受所属用户访问限制。
3. 执行 `scripts/admin-v1-seed.sql`，导入缺失的 5 个内置场景、28 句台词。按 code 保留既有数据；重复执行不会覆盖运营修改或重新发布下架内容。导出源更新时从工作区根运行 `node yue-backend/scripts/export-builtin-scenes.mjs`，两端同 code 内容不同会报错。
4. 执行 `scripts/dubbing-material-migration.sql`；在服务器上从后端目录运行
   `python scripts/import-dubbing-material-videos.py --source-dir ../yue-frontend/public/dubbing-clips --target-dir /opt/yue/dubbing-videos`，
   校验并原子导入 6 个视频文件。再执行 `scripts/dubbing-material-seed.sql`，导入缺失的
   2 套素材、6 个片段、25 句台词。两者都可重复执行，不覆盖运营修改；已有目标文件
   内容不符时导入程序会拒绝覆盖并停止发布。
5. 核对 `dubbing_materials`、`dubbing_parts`、`dubbing_cues` 的记录数分别至少为
   2、6、25，六个种子片段均有文件名、SHA-256、H.264 视频和 AAC 音频元数据。
6. 设置 `SPRING_JPA_HIBERNATE_DDL_AUTO=validate`，启动新后端；不要依赖生产自动改表。
7. 按顺序发布后端、后台、网页、小程序。新用户端使用 `/api/public/scenes` 和
   `/api/public/dubbing-materials`，后端未更新前不应单独发布新客户端。

后台使用既有 `ADMIN_USERNAME` 和 `ADMIN_PASSWORD_HASH`（BCrypt），空密码哈希无法登录；沿用安全配置中的 JWT_SECRET。禁止把密码哈希、数据库密码写入仓库。生产管理端默认使用同源 `/api`，运行 `npm run build` 后将 `dist` 部署到 `/var/www/yue-admin`。网页 `VITE_API_BASE` 是服务器根地址（不含 `/api`），与后台配置不同。小程序沿用其 API_BASE，并配置合法 HTTPS request/download 域名。

## Nginx 路由要求

下列规则合并到现有 `gnep.online` HTTPS `server`，不得替换已有 TLS、配音上传或
静态音频配置。`/audio/uploads/` 必须位于通用静态 `/audio/` 规则之前；管理音频
上传的 21 MiB 限制只覆盖 `/api/admin/audio` 及其数字 ID 路径。

```nginx
location = /admin {
    return 301 /admin/;
}

location = /admin/index.html {
    alias /var/www/yue-admin/index.html;
}

location ^~ /admin/assets/ {
    alias /var/www/yue-admin/assets/;
}

location /admin/ {
    rewrite ^ /admin/index.html last;
}

location /audio/uploads/ {
    proxy_pass http://127.0.0.1:8080/audio/uploads/;
    proxy_http_version 1.1;
    proxy_set_header Host $host;
    proxy_set_header X-Real-IP $remote_addr;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    proxy_set_header X-Forwarded-Proto $scheme;
}

location ~ ^/api/admin/audio(?:/[0-9]+)?$ {
    client_max_body_size 21m;
    proxy_pass http://127.0.0.1:8080;
    proxy_http_version 1.1;
    proxy_set_header Host $host;
    proxy_set_header X-Real-IP $remote_addr;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    proxy_set_header X-Forwarded-Proto $scheme;
}

location ~ ^/api/admin/dubbing-materials/[0-9]+/parts/[0-9]+/video$ {
    client_max_body_size 201m;
    proxy_request_buffering off;
    proxy_pass http://127.0.0.1:8080;
    proxy_http_version 1.1;
    proxy_set_header Host $host;
    proxy_set_header X-Real-IP $remote_addr;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    proxy_set_header X-Forwarded-Proto $scheme;
}
```

通用 `/api/` 仍代理到 `127.0.0.1:8080`，并承载公开及后台视频的 Range 流式读取；
不得在代理层移除 `Range`/`If-Range` 请求头或 `Content-Range` 响应头。修改后必须先运行 `nginx -t`，成功后
才可 reload；失败时立即恢复备份配置，不加载有误的文件。

## 音频存储

设置 `ADMIN_AUDIO_DIR` 为持久目录，默认 `./data/admin-audio`，运行用户需有读写权限。数据库索引与该目录必须一起备份/恢复；UUID 文件名由后端管理，不能只备份 jar。既有 static/audio 作为只读素材继续保留。

支持 WAV、MP3，单文件最多 20 MiB。MP3 解析依赖 ffprobe，可通过 DUBBING_FFPROBE 指定路径。反向代理对 `/api/admin/audio` 和 `/api/admin/audio/{数字ID}` 的上传请求允许 21 MiB；保留既有配音路由设置，其余 multipart 仍为 2 MiB。公共读取 `/audio/uploads/{opaque}` 必须代理到后端，不能被静态 `/audio/` 配置截获。后台返回正确 Content-Type，支持 Range；缓存要求重新验证，替换保持音频 key 稳定。

删除前用资源行锁和引用当前读保护所有台词写入入口。替换成功提交后删除旧文件，失败回滚清理新文件；读取期间持共享锁获取完整音频快照，单次读取最多占用 20 MiB 字节数组，部署时按并发量预留内存。

## 电影配音视频存储

设置 `ADMIN_DUBBING_VIDEO_DIR=/opt/yue/dubbing-videos`，运行用户需有读写权限。支持
MP4，单文件最多 200 MiB；上传接口由后端校验 MP4 文件特征，并通过 ffprobe 要求
H.264 视频流和 AAC 音频流。视频文件使用不透明文件名，数据库中的 SHA-256、大小、
时长和编解码信息必须与文件一起备份/恢复。发布素材前必须确保每个 Part 都已有有效
视频且台词时间范围合法；下架后公开接口不再返回素材，后台仍可编辑并重新发布。

## 本地隔离预览

在后端运行：`mvn spring-boot:test-run -Dspring-boot.run.main-class=com.yue.admin.AdminPreviewApplication`。2026-10-16 已重新启动并验证前后端 HTTP 200。
预览仅绑定 127.0.0.1:18081，使用 H2 内存库和测试账号 `preview` / `local-preview-only`，重启丢失测试场景。测试入口不打入生产 jar。
管理端 PowerShell：`$env:VITE_API_BASE='http://127.0.0.1:18081/api'`，再运行 `npm run dev -- --host 127.0.0.1 --port 5175`。

## 回滚

先停止后台写入，恢复同一时间戳备份中的上一版 JAR、`backend.env`、Nginx 配置、
用户网页和管理端目录，依次执行 `nginx -t`、reload Nginx、重启后端并核对上线前
基线。新增列/表默认保留，避免直接删除新内容；若必须恢复数据库，应同时恢复同一
备份时点的 `/opt/yue/admin-audio` 和 `/opt/yue/dubbing-videos`，并评估备份后写入
的数据。禁止仅删除任一上传目录或仅恢复数据库。

本次生产协调备份为 `/opt/yue/release-backup-20261017-admin-IQ40zS`。上线验证已确认
`nginx -t`、后端 active、用户网页与后台资源 200、公开场景 API 200、未登录管理接口
401；生产临时场景和音频的发布、Range、下架与删除流程通过且数据已清理。
