import math
import os
import subprocess
import json
import tempfile
import threading
from pathlib import Path
from typing import Callable, TypedDict

from fastapi import FastAPI, Request
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse
from python_multipart import MultipartParser
from python_multipart.multipart import parse_options_header
from starlette.concurrency import run_in_threadpool

from .transcriber import Cue, LocalTranscriber, ModelUnavailable

ORIGINS = ["http://localhost:8443", "http://127.0.0.1:8443"]
MAX_UPLOAD_BYTES = 200 * 1024 * 1024


class MediaInfo(TypedDict):
    duration_s: float
    has_audio: bool


class UploadError(Exception):
    def __init__(self, status: int, message: str):
        self.status, self.message = status, message


def probe_media(path: Path) -> MediaInfo:
    result = subprocess.run([
        "ffprobe", "-v", "error", "-show_entries", "format=duration:stream=codec_type",
        "-of", "json", str(path),
    ], capture_output=True, check=True, timeout=30)
    data = json.loads(result.stdout)
    return {"duration_s": float(data["format"]["duration"]),
            "has_audio": any(s.get("codec_type") == "audio" for s in data.get("streams", []))}


async def receive_video(request: Request, path: Path, limit: int):
    """Parse directly from the request stream; never spool an unbounded upload."""
    content_type, options = parse_options_header(request.headers.get("content-type", ""))
    boundary = options.get(b"boundary")
    if content_type != b"multipart/form-data" or not boundary or len(boundary) > 200:
        raise UploadError(400, "请选择一个视频文件")
    size = total = parts = 0
    ended = False
    headers = {}
    field = bytearray()
    value = bytearray()

    def part_begin():
        nonlocal parts
        parts += 1
        if parts > 1:
            raise UploadError(400, "每次只能上传一个视频")

    def header_field(data, start, end):
        field.extend(data[start:end])
        if len(field) > 8192:
            raise UploadError(400, "上传请求头过长")

    def header_value(data, start, end):
        value.extend(data[start:end])
        if len(value) > 8192:
            raise UploadError(400, "上传请求头过长")

    def header_end():
        headers[bytes(field).lower()] = bytes(value)
        field.clear()
        value.clear()

    def headers_finished():
        _, disposition = parse_options_header(headers.get(b"content-disposition", b""))
        if disposition.get(b"name") != b"file" or not disposition.get(b"filename"):
            raise UploadError(400, "请选择一个视频文件")
        if not headers.get(b"content-type", b"").lower().startswith(b"video/"):
            raise UploadError(422, "请选择有效的视频文件")

    def finish():
        nonlocal ended
        ended = True

    with path.open("wb") as output:
        def part_data(data, start, end):
            nonlocal size
            size += end - start
            if size > limit:
                raise UploadError(413, "视频超过 200 MB，请截取片段后重试")
            output.write(data[start:end])

        parser = MultipartParser(boundary, {
            "on_part_begin": part_begin, "on_header_field": header_field,
            "on_header_value": header_value, "on_header_end": header_end,
            "on_headers_finished": headers_finished, "on_part_data": part_data,
            "on_end": finish,
        })
        try:
            async for chunk in request.stream():
                total += len(chunk)
                if total > limit + 65536:
                    raise UploadError(413, "上传内容过大，请截取片段后重试")
                parser.write(chunk)
            parser.finalize()
        except UploadError:
            raise
        except Exception as cause:
            raise UploadError(400, "视频上传不完整，请重试") from cause
    if not ended or parts != 1 or size == 0:
        raise UploadError(400, "请选择非空视频文件并完整上传")


def create_app(transcribe_fn: Callable[[Path], list[Cue]] | None = None,
               probe_fn: Callable[[Path], MediaInfo] = probe_media,
               max_upload_bytes: int = MAX_UPLOAD_BYTES) -> FastAPI:
    app = FastAPI()
    app.add_middleware(CORSMiddleware, allow_origins=ORIGINS, allow_methods=["POST", "GET"], allow_headers=["Content-Type"])
    lock = threading.Lock()
    model_dir = os.environ.get("ASR_MODEL_DIR", "")
    worker = LocalTranscriber(Path(model_dir)) if model_dir else None

    def ready():
        return transcribe_fn is not None or (worker is not None and worker.available())

    @app.get("/health")
    def health():
        available = bool(ready())
        return JSONResponse({"ready": available, "modelAvailable": available}, status_code=200 if available else 503)

    @app.post("/transcribe")
    async def transcribe(request: Request):
        if request.headers.get("origin") not in ORIGINS:
            return JSONResponse({"error": "只允许本机网页调用识别服务"}, status_code=403)
        if not ready():
            return JSONResponse({"error": "本地模型不可用，请检查 ASR_MODEL_DIR"}, status_code=503)
        if not lock.acquire(blocking=False):
            return JSONResponse({"error": "正在处理另一个视频，请稍后重试"}, status_code=409)
        try:
            with tempfile.TemporaryDirectory(prefix="yue-asr-") as directory:
                path = Path(directory) / "video.upload"
                await receive_video(request, path, max_upload_bytes)
                try:
                    info = await run_in_threadpool(probe_fn, path)
                    duration = info["duration_s"]
                    if not math.isfinite(duration) or duration <= 0:
                        raise ValueError("duration")
                except Exception as cause:
                    raise UploadError(422, "视频无法解码，请检查文件格式") from cause
                if duration > 300:
                    raise UploadError(413, "视频超过 5 分钟，请截取片段后重试")
                if not info["has_audio"]:
                    raise UploadError(422, "视频没有音轨，请选择有声音的视频")
                cues = await run_in_threadpool(transcribe_fn or worker.transcribe, path)
                return {"cues": cues}
        except UploadError as cause:
            return JSONResponse({"error": cause.message}, status_code=cause.status)
        except ModelUnavailable as cause:
            return JSONResponse({"error": str(cause)}, status_code=503)
        except Exception:
            return JSONResponse({"error": "识别失败，请检查本地模型和视频后重试"}, status_code=500)
        finally:
            lock.release()

    return app
