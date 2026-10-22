import importlib.util
import shutil
import tempfile
import unittest
from pathlib import Path
from unittest import mock


SCRIPT = Path(__file__).parents[1] / "import-dubbing-material-videos.py"
SPEC = importlib.util.spec_from_file_location("dubbing_video_importer", SCRIPT)
importer = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(importer)


class ImportDubbingMaterialVideosTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.real_source = Path(__file__).parents[3] / "yue-frontend" / "public" / "dubbing-clips"
        if not cls.real_source.is_dir():
            raise unittest.SkipTest("verified six-video source directory is unavailable")

    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(dir=Path.cwd())
        self.root = Path(self.temp.name)
        self.target = self.root / "managed"

    def tearDown(self):
        self.temp.cleanup()

    def test_correct_copy_and_repeat_execution(self):
        first = importer.import_videos(self.real_source, self.target)
        self.assertEqual(6, len(first))
        snapshots = {path.name: (path.stat().st_size, path.stat().st_mtime_ns) for path in self.target.glob("*.mp4")}
        second = importer.import_videos(self.real_source, self.target)
        self.assertEqual(first, second)
        self.assertEqual(snapshots, {path.name: (path.stat().st_size, path.stat().st_mtime_ns)
                                     for path in self.target.glob("*.mp4")})
        self.assertFalse(list(self.target.glob("*.tmp")))

    def test_wrong_source_hash_is_rejected(self):
        source = self.root / "source"
        shutil.copytree(self.real_source, source)
        first_name = next(iter(importer.MANIFEST))
        with (source / first_name).open("ab") as output:
            output.write(b"tampered")
        with self.assertRaisesRegex(importer.ImportFailure, "SHA-256"):
            importer.import_videos(source, self.target)
        self.assertFalse(self.target.exists())

    def test_interrupted_copy_removes_temporary_file(self):
        def interrupted(source, target, length=0):
            target.write(source.read(16))
            raise OSError("simulated interruption")

        with mock.patch.object(importer.shutil, "copyfileobj", side_effect=interrupted):
            with self.assertRaisesRegex(importer.ImportFailure, "复制"):
                importer.import_videos(self.real_source, self.target)
        self.assertFalse(list(self.target.glob("*.tmp")))
        self.assertFalse(list(self.target.glob("*.mp4")))

    def test_existing_mismatched_target_is_never_overwritten(self):
        self.target.mkdir()
        entry = next(iter(importer.MANIFEST.values()))
        destination = self.target / entry["target"]
        destination.write_bytes(b"operator file")
        with self.assertRaisesRegex(importer.ImportFailure, "目标文件"):
            importer.import_videos(self.real_source, self.target)
        self.assertEqual(b"operator file", destination.read_bytes())


if __name__ == "__main__":
    unittest.main()
