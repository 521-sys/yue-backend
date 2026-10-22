#!/usr/bin/env python3
"""Verify and atomically import the six approved dubbing clips."""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import shutil
import subprocess
from pathlib import Path


MANIFEST = {
    "pendatang-arrival-01.mp4": {
        "sha256": "713c519feedf6f692d6d5ea7d08c436a7846326fa045289b97a8d8f60c4b5116",
        "target": "seed-pendatang-arrival-01-713c519feedf.mp4", "size": 307820, "duration_ms": 9000,
    },
    "pendatang-arrival-02.mp4": {
        "sha256": "6ff8729ea3b25f7275fe94f272bbddd5e8fa785196d840d69db846b5ead319ba",
        "target": "seed-pendatang-arrival-02-6ff8729ea3b2.mp4", "size": 516479, "duration_ms": 12000,
    },
    "pendatang-arrival-03.mp4": {
        "sha256": "7d3c75601d8596774c81eee8b8a842b9f78c655a2475087c94ff752217f44e6f",
        "target": "seed-pendatang-arrival-03-7d3c75601d85.mp4", "size": 913352, "duration_ms": 24250,
    },
    "wash-hands-01.mp4": {
        "sha256": "016ad0ebec3ca0b5c9bb423a59b9e3eef5f8058ce5be7ef85bdb14cf9c021e35",
        "target": "seed-wash-hands-01-016ad0ebec3c.mp4", "size": 1077566, "duration_ms": 17518,
    },
    "wash-hands-02.mp4": {
        "sha256": "3a185ec48b72dd764681e56f1ea800325cddf87fe9ef3d383ea8e7b3ee36ec60",
        "target": "seed-wash-hands-02-3a185ec48b72.mp4", "size": 1274882, "duration_ms": 17751,
    },
    "wash-hands-03.mp4": {
        "sha256": "1c94dad9f60c99cddcf9f98894195e4d72a0e02c4b012b3560d849a4d31a223f",
        "target": "seed-wash-hands-03-1c94dad9f60c.mp4", "size": 1217291, "duration_ms": 22789,
    },
}


class ImportFailure(RuntimeError):
    pass


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as source:
        for chunk in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def probe(path: Path, ffprobe: str) -> dict[str, object]:
    try:
        result = subprocess.run(
            [ffprobe, "-v", "error", "-show_entries",
             "format=format_name,duration,size:stream=codec_type,codec_name", "-of", "json", str(path)],
            check=True, capture_output=True, text=True, timeout=15,
        )
        payload = json.loads(result.stdout)
    except (OSError, subprocess.SubprocessError, json.JSONDecodeError) as error:
        raise ImportFailure(f"FFprobe 无法解析 {path.name}: {error}") from error
    streams = payload.get("streams", [])
    video = next((stream.get("codec_name") for stream in streams if stream.get("codec_type") == "video"), None)
    audio = next((stream.get("codec_name") for stream in streams if stream.get("codec_type") == "audio"), None)
    media = payload.get("format", {})
    try:
        return {
            "container": media.get("format_name", ""), "duration_ms": round(float(media["duration"]) * 1000),
            "size": int(media["size"]), "video": video, "audio": audio,
        }
    except (KeyError, TypeError, ValueError) as error:
        raise ImportFailure(f"FFprobe 元数据不完整: {path.name}") from error


def validate_source(source: Path, entry: dict[str, object], ffprobe: str) -> None:
    if not source.is_file():
        raise ImportFailure(f"缺少源视频: {source.name}")
    actual_hash = sha256(source)
    if actual_hash != entry["sha256"]:
        raise ImportFailure(f"源视频 SHA-256 不匹配: {source.name}")
    metadata = probe(source, ffprobe)
    if ("mp4" not in str(metadata["container"]).lower() or metadata["video"] != "h264"
            or metadata["audio"] != "aac" or metadata["size"] != entry["size"]
            or metadata["duration_ms"] != entry["duration_ms"]):
        raise ImportFailure(f"源视频媒体元数据不匹配: {source.name}")


def import_videos(source_dir: Path | str, target_dir: Path | str, ffprobe: str = "ffprobe") -> list[Path]:
    source_root = Path(source_dir).resolve()
    target_root = Path(target_dir).resolve()

    for name, entry in MANIFEST.items():
        validate_source(source_root / name, entry, ffprobe)

    target_root.mkdir(parents=True, exist_ok=True)
    imported: list[Path] = []
    for name, entry in MANIFEST.items():
        source = source_root / name
        destination = target_root / str(entry["target"])
        if destination.exists():
            if not destination.is_file() or sha256(destination) != entry["sha256"]:
                raise ImportFailure(f"目标文件与清单不匹配，拒绝覆盖: {destination.name}")
            imported.append(destination)
            continue

        temporary = target_root / f".{destination.name}.{os.getpid()}.tmp"
        try:
            with source.open("rb") as input_file, temporary.open("xb") as output_file:
                shutil.copyfileobj(input_file, output_file, length=1024 * 1024)
                output_file.flush()
                os.fsync(output_file.fileno())
            if sha256(temporary) != entry["sha256"]:
                raise ImportFailure(f"复制后 SHA-256 不匹配: {name}")
            if destination.exists():
                if sha256(destination) != entry["sha256"]:
                    raise ImportFailure(f"目标文件与清单不匹配，拒绝覆盖: {destination.name}")
                temporary.unlink()
            else:
                os.replace(temporary, destination)
            imported.append(destination)
        except ImportFailure:
            temporary.unlink(missing_ok=True)
            raise
        except Exception as error:
            temporary.unlink(missing_ok=True)
            raise ImportFailure(f"复制视频失败: {name}: {error}") from error
    return imported


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source-dir", required=True, type=Path)
    parser.add_argument("--target-dir", required=True, type=Path)
    parser.add_argument("--ffprobe", default="ffprobe")
    arguments = parser.parse_args()
    try:
        files = import_videos(arguments.source_dir, arguments.target_dir, arguments.ffprobe)
    except ImportFailure as error:
        parser.error(str(error))
    for path in files:
        print(path.name)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
