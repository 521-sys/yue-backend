import tempfile
import unittest
from pathlib import Path
from types import SimpleNamespace
from unittest.mock import patch, MagicMock

from asr_service.transcriber import LocalTranscriber


class TranscriberTests(unittest.TestCase):
    def test_missing_local_model(self):
        with tempfile.TemporaryDirectory() as directory:
            with self.assertRaisesRegex(RuntimeError, "模型"):
                LocalTranscriber(Path(directory)).transcribe(Path("clip.mp4"))

    def test_normalizes_filters_sorts_and_reuses_offline_model(self):
        with tempfile.TemporaryDirectory() as directory:
            Path(directory, "model.bin").touch()
            fake = MagicMock()
            fake.transcribe.return_value = ([
                SimpleNamespace(start=3, end=4, text=" 再见 "),
                SimpleNamespace(start=1.25, end=2.5, text=" 你好 "),
                SimpleNamespace(start=0, end=1, text=" "),
                SimpleNamespace(start=2, end=1, text="错"),
                SimpleNamespace(start=-1, end=1, text="错"),
                SimpleNamespace(start=0, end=float("nan"), text="错"),
            ], None)
            with patch("asr_service.transcriber.WhisperModel", return_value=fake) as factory:
                worker = LocalTranscriber(Path(directory))
                self.assertEqual(worker.transcribe(Path("clip.mp4")), [
                    {"startMs": 1250, "endMs": 2500, "text": "你好"},
                    {"startMs": 3000, "endMs": 4000, "text": "再见"},
                ])
                fake.transcribe.return_value = ([], None)
                self.assertEqual(worker.transcribe(Path("silent.mp4")), [])
                factory.assert_called_once_with(str(Path(directory)), device="cpu", compute_type="int8", local_files_only=True)
                fake.transcribe.assert_called_with("silent.mp4", language="zh", beam_size=3, vad_filter=True)
