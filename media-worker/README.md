# 粤语配音媒体处理核心（开发中）

当前仅实现 FFmpeg 成片渲染函数 `dubbing.render.render_video(source, background, cues, output)`。输入为原视频、**不含原台词**的背景音，以及 `[{"startMs": 1000, "endMs": 2200, "take": "...wav"}]` 形式的逐句录音清单。函数按原片时间轴放置录音、用人声控制背景音压低，并生成 H.264/AAC MP4；输出路径已存在时拒绝覆盖。

这还不是可从网页调用的服务。视频上传、登录隔离、任务队列、工程保存、分轨与粤语识别尚未接入。请勿将它直接暴露到公网。

本地验证要求 `ffmpeg` 与 `ffprobe` 在 PATH 中。测试使用 Python 标准库和由 FFmpeg 生成的临时媒体，不需要真实电影素材：

```powershell
python -m unittest discover -s tests -v
```

## 本机离线自动生成台词

个人本机测试服务与上述渲染核心独立，绑定 `127.0.0.1:8765`，不得公网部署。仅允许 `http://localhost:8443` 和 `http://127.0.0.1:8443` 网页调用；每次一个视频，最长 5 分钟、最大 200 MiB。临时上传文件在处理结束后删除；取消网页请求不保证 CPU 计算立即停止。

在 `yue-backend/media-worker` 中执行（模型必须预先下载，服务不会联网下载）：

```powershell
& ../../.asr-probe/venv/Scripts/python.exe -m pip install -r requirements-asr.txt
$env:ASR_MODEL_DIR = (Resolve-Path ../../.asr-probe/models/large-v3).Path
& ../../.asr-probe/venv/Scripts/python.exe -m asr_service
# 另开终端检查状态
Invoke-RestMethod http://127.0.0.1:8765/health
```

可用 `ASR_PORT` 改端口；同时设置网页 `VITE_LOCAL_ASR_URL` 并重启 Vite。Python 3.12、CPU int8；要求 FFprobe 在 PATH 中，PyAV 保持 `>=18,<19`。503 请检查 `ASR_MODEL_DIR/model.bin`，422 请检查视频格式和音轨，413 请截短视频。浏览器提示连接失败时检查服务是否启动，以及网页地址是否为上述允许来源。

接口：`POST /transcribe`，multipart 字段 `file`，请求需携带允许的 Origin；成功为 `{ "cues": [{ "startMs": 1000, "endMs": 2000, "text": "你好" }] }`。无语音返回空数组。错误为 `{ "error": "原因" }`。识别初稿需要校对，不包含背景音分离或成片导出。

2026-10-12 起使用 Whisper large-v3，明确指定 `language="yue"`、`task="transcribe"`，以粤语原话转写为目标。不再使用 small + zh 作为本功能配置。已下载模型时可执行 `./start-asr.ps1`，脚本固定指向本工作区的 large-v3 模型。模型来源为 `Systran/faster-whisper-large-v3`，固定版本 `edaa852ec7e145841d8ffdb056a99866b5f0a478`，约 3.1 GB。CPU 识别时间需实测，仍需人工校对。
