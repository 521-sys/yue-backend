from pathlib import Path
import subprocess


def render_video(source: Path, background: Path, cues: list[dict], output: Path) -> None:
    """Replace source audio with a ducked background and time-anchored voice takes."""
    if not cues:
        raise ValueError("至少需要一条已录制台词")
    for cue in cues:
        start = cue.get("startMs")
        end = cue.get("endMs")
        if not isinstance(start, (int, float)) or not isinstance(end, (int, float)) or start < 0 or end <= start:
            raise ValueError("结束时间必须晚于开始时间")
    if output.exists():
        raise FileExistsError(f"输出文件已存在：{output}")
    for file in (source, background, *(Path(cue["take"]) for cue in cues)):
        if not file.is_file():
            raise FileNotFoundError(file)

    duration = float(subprocess.check_output([
        "ffprobe", "-v", "error", "-show_entries", "format=duration",
        "-of", "default=noprint_wrappers=1:nokey=1", str(source),
    ], text=True).strip())
    if duration <= 0:
        raise ValueError("无法读取原视频时长")

    filters = [f"[1:a]aresample=48000,aformat=channel_layouts=stereo,apad,atrim=duration={duration:.3f}[bg]"]
    inputs = []
    for index, cue in enumerate(cues):
        delay = round(cue["startMs"])
        length = (cue["endMs"] - cue["startMs"]) / 1000
        filters.append(
            f"[{index + 2}:a]aresample=48000,aformat=channel_layouts=stereo,"
            f"atrim=duration={length:.3f},asetpts=PTS-STARTPTS,"
            f"adelay={delay}|{delay}[voice{index}]"
        )
        inputs.append(f"[voice{index}]")
    if len(inputs) == 1:
        filters.append(f"{inputs[0]}anull[voices]")
    else:
        filters.append(f"{''.join(inputs)}amix=inputs={len(inputs)}:normalize=0:duration=longest[voices]")
    filters.extend([
        "[voices]apad,asplit=2[voicekey][voicemix]",
        "[bg][voicekey]sidechaincompress=threshold=0.02:ratio=4:attack=20:release=300[ducked]",
        "[ducked][voicemix]amix=inputs=2:normalize=0:duration=first,loudnorm=I=-16:TP=-1.5:LRA=11,aresample=48000[out]",
    ])
    command = ["ffmpeg", "-hide_banner", "-loglevel", "error", "-n", "-i", str(source), "-i", str(background)]
    for cue in cues:
        command.extend(["-i", str(cue["take"])])
    command.extend([
        "-filter_complex", ";".join(filters), "-map", "0:v:0", "-map", "[out]",
        "-c:v", "libx264", "-preset", "veryfast", "-pix_fmt", "yuv420p",
        "-c:a", "aac", "-b:a", "192k", "-movflags", "+faststart", "-shortest", str(output),
    ])
    subprocess.run(command, check=True, capture_output=True, text=True)
