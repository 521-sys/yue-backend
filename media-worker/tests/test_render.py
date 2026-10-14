import json
import math
import shutil
import subprocess
import tempfile
import unittest
import wave
from pathlib import Path

from dubbing.render import render_video


@unittest.skipUnless(shutil.which("ffmpeg") and shutil.which("ffprobe"), "ffmpeg tools unavailable")
class RenderVideoTests(unittest.TestCase):
    def test_places_two_voice_takes_on_original_video_timeline(self):
        with tempfile.TemporaryDirectory(dir=Path.cwd()) as folder:
            root = Path(folder)
            root.resolve().relative_to(Path.cwd().resolve())
            source = root / "source.mp4"
            background = root / "background.wav"
            take = root / "take.wav"
            output = root / "final.mp4"
            subprocess.run(["ffmpeg", "-hide_banner", "-loglevel", "error", "-y", "-f", "lavfi", "-i", "color=c=black:s=160x90:r=10", "-f", "lavfi", "-i", "anullsrc=r=48000:cl=stereo", "-t", "2", "-c:v", "mpeg4", "-c:a", "aac", str(source)], check=True)
            subprocess.run(["ffmpeg", "-hide_banner", "-loglevel", "error", "-y", "-f", "lavfi", "-i", "sine=frequency=220:sample_rate=48000", "-t", "1.5", str(background)], check=True)
            subprocess.run(["ffmpeg", "-hide_banner", "-loglevel", "error", "-y", "-f", "lavfi", "-i", "sine=frequency=880:sample_rate=48000", "-t", "0.4", str(take)], check=True)
            render_video(source, background, [
                {"startMs": 250, "endMs": 650, "take": str(take)},
                {"startMs": 1250, "endMs": 1650, "take": str(take)},
            ], output)
            info = json.loads(subprocess.check_output(["ffprobe", "-v", "error", "-show_entries", "format=duration:stream=codec_type,sample_rate", "-of", "json", str(output)], text=True))
            self.assertTrue(output.exists())
            self.assertEqual({stream["codec_type"] for stream in info["streams"]}, {"video", "audio"})
            self.assertEqual(next(stream["sample_rate"] for stream in info["streams"] if stream["codec_type"] == "audio"), "48000")
            self.assertAlmostEqual(float(info["format"]["duration"]), 2.0, delta=0.3)
            decoded = root / "decoded.wav"
            subprocess.run(["ffmpeg", "-hide_banner", "-loglevel", "error", "-y", "-i", str(output), "-ac", "1", "-ar", "48000", str(decoded)], check=True)
            with wave.open(str(decoded), "rb") as audio:
                def voice_energy(at_seconds):
                    audio.setpos(round(at_seconds * 48000))
                    data = audio.readframes(4800)
                    samples = memoryview(data).cast("h")
                    return abs(sum(sample * math.sin(2 * math.pi * 880 * index / 48000) for index, sample in enumerate(samples))) / len(samples)
                self.assertGreater(voice_energy(0.35), voice_energy(0.05) * 5)
                self.assertGreater(voice_energy(1.35), voice_energy(0.05) * 5)

    def test_rejects_invalid_timing_before_running_ffmpeg(self):
        with self.assertRaisesRegex(ValueError, "结束时间"):
            render_video(Path("source.mp4"), Path("background.wav"), [{"startMs": 1000, "endMs": 500, "take": "take.wav"}], Path("final.mp4"))


if __name__ == "__main__":
    unittest.main()
