import math
from pathlib import Path
from typing import TypedDict

from faster_whisper import WhisperModel


class Cue(TypedDict):
    startMs: int
    endMs: int
    text: str


class LocalTranscriber:
    def __init__(self, model_dir: Path):
        self.model_dir = model_dir
        self._model = None

    def transcribe(self, path: Path) -> list[Cue]:
        if not (self.model_dir / "model.bin").is_file():
            raise RuntimeError("本地模型不可用，请检查 ASR_MODEL_DIR")
        if self._model is None:
            self._model = WhisperModel(str(self.model_dir), device="cpu", compute_type="int8", local_files_only=True)
        segments, _ = self._model.transcribe(str(path), language="zh", beam_size=3, vad_filter=True)
        cues: list[Cue] = []
        for segment in segments:
            text = segment.text.strip()
            if not text or not math.isfinite(segment.start) or not math.isfinite(segment.end):
                continue
            start, end = round(segment.start * 1000), round(segment.end * 1000)
            if 0 <= start < end:
                cues.append({"startMs": start, "endMs": end, "text": text})
        return sorted(cues, key=lambda cue: cue["startMs"])
