# 腾讯云粤语识别：开通与部署

当前状态（2026-10-14）：前后端及 Nginx 路由已部署，服务器密钥已配置；云识别已启用，1.656 秒真实音频经正式 API 验收成功，返回文字及时间戳。普通引擎 `16k_yue`，SDK `3.2.29`。原 AI 对话的短语音识别接口保持不变。回滚备份位于 `/opt/yue/release-backup-20261014-sbvujjes`。

## 用户开通

1. 登录 https://console.cloud.tencent.com/asr ，完成控制台要求的实名认证并开通语音识别。
2. 查看普通「录音文件识别」免费资源包（官方当前每月 10 小时）；确认资源包已生效、有剩余额度。
3. 确认后付费关闭。先不购买资源包，不开通增值功能。
4. 通过腾讯云 CAM 创建专用 API 子账号，只授予 `asr:CreateRecTask` 和 `asr:DescribeTaskStatus` 必需权限。密钥只在服务器受限配置文件填写，勿发聊天、截图或提交 Git。

依据：https://cloud.tencent.com/document/product/1093/35686 （2026-10-13 核对）。免费额度是识别成功的音频时长；重复任务可能重复计费。应用内预算不能代表腾讯账户剩余额度。

## 服务端配置

没有下载密钥文件时，可在本机运行 `scripts/configure-tencent-asr.ps1`。脚本打开交互 SSH 终端，服务器 Python 通过 getpass 隐藏读取两项密钥，不经聊天、命令行参数或本机明文文件。脚本保留其他环境变量、以 0600 文件原子替换 `/opt/yue/backend.env`，旧配置备份到 root 私密目录 `/opt/yue/credential-backups/`。不启用识别、不重启服务；显示 SUCCESS 后仍需核对配置并继续部署。脚本源码不含密钥。

生产在 `/opt/yue/backend.env` 私密配置，不输出文件内容；权限限制为服务账户可读。不要把 SecretKey 放到 VITE 变量。

```dotenv
DUBBING_ASR_ENABLED=true
TENCENTCLOUD_SECRET_ID=通过安全配置填写
TENCENTCLOUD_SECRET_KEY=通过安全配置填写
DUBBING_ASR_MONTHLY_SECONDS=32400
DUBBING_ASR_BUDGET_FILE=/opt/yue/data/dubbing-asr-budget.txt
DUBBING_FFMPEG=/usr/bin/ffmpeg
DUBBING_FFPROBE=/usr/bin/ffprobe
```

FFmpeg/FFprobe 需安装，服务账户对预算目录和临时目录可读写。2026-10-14 用户确认：生产预算为整个网站全部用户合计每自然月 32400 秒（9 小时，中国时区），不是每用户额度；25200 秒（7 小时）提醒。代码未配置时仍默认试用 600 秒。按实际提取 MP3 时长向上取整，失败/结果不明也保留预占；不自动重发创建请求。不要删除预算文件来恢复额度。此实现是单机、单实例设计。

当前聊天已创建每小时用量检查提醒（automation ID: automation），达到 7 小时提醒、达到 9 小时提醒；每月最后一天前 3 天北京时间 09:00 后发送免费包到期核对提醒。提醒依赖 Codex 自动化能运行并连接服务器，不替代服务端硬上限；网站所有用户共享同一个预算文件。后付费仍开启，预算是本站保守预占量，不是腾讯账单；以后月份需核实免费包是否发放。自动识别已于 2026-10-14 按用户要求启用。

视频上限 200 MiB、5 分钟，单上传/单后台任务；音频 16kHz 单声道 MP3 64kbps，直接上传腾讯，不依赖 COS。视频与提取音频在任务结束后清理，不落业务媒体库。任务结果内存保留最多一小时（有硬数量上限），进程重启丢失，月度预算仍保留。刷新/关闭页面会停止客户端等待，无法取消已提交腾讯的任务，勿立即再次点击识别。

## 前端

开发默认保留本机识别；开发时设置 `VITE_DUBBING_ASR_PROVIDER=tencent` 测试云端。正式构建始终使用后端云端接口，要求用户登录。

点击识别时明确确认上传。新增接口：`POST /api/dubbing/transcriptions`（multipart `file`）→ `{id,status,cues,error}`；`GET /api/dubbing/transcriptions/{id}` 查询本人任务。失败或空结果不覆盖原台词/录音。

## Nginx 与上线顺序

先备份现有 jar、Nginx 配置、前端 index 和 assets。保留 `/var/www/yue/audio` 与旧 assets，不使用清空整个网页目录的旧部署脚本。

仅给该路径提升上传限额，其他接口保持已有 2m：

```nginx
location = /api/dubbing/transcriptions {
    client_max_body_size 201m;
    client_body_timeout 60s;
    proxy_request_buffering off;
    proxy_pass http://127.0.0.1:8080;
    proxy_set_header Host $host;
    proxy_set_header X-Real-IP $remote_addr;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    proxy_read_timeout 120s;
}
```

先以识别关闭状态部署后端并验现有功能；确认账号额度/后付费状态和密钥配置后启用；`nginx -t` 通过再 reload。最后发布前端。仅用短粤语样片完成一次真实验收，核对台词、时间戳、费用用量，再扩大测试。

回滚：关闭 `DUBBING_ASR_ENABLED`，恢复备份 jar/index/Nginx，检查配置后重启/reload。预算文件保留。浏览器作品仍按同源保存，localhost 与正式域名不共享作品。

## 验证

自动测试模拟腾讯云，不产生识别费用。FFmpeg 提取测试生成本地 2 秒声音，不上传。2026-10-14 已通过正式 HTTPS 上传接口提交一次现有公开学习音频（1.656 秒），腾讯云返回“你好。”、0–680 ms 时间戳；本站预算记 2 秒，服务与首页正常。完整视频准确率、真实用户录音体验仍需继续测试。
