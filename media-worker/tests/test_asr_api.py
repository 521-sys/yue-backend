import os
import tempfile
import threading
import unittest
from pathlib import Path
from unittest.mock import patch

from fastapi.testclient import TestClient
from asr_service.api import create_app

ORIGIN = {"Origin": "http://localhost:8443"}
CUES = [{"startMs": 1000, "endMs": 2000, "text": "你好"}]


class ApiTests(unittest.TestCase):
    def setUp(self):
        self.paths = []

    def probe(self, path):
        self.paths.append(path)
        self.assertEqual(path.read_bytes(), b"video")
        return {"duration_s": 3, "has_audio": True}

    def post(self, app, content=b"video", headers=ORIGIN, mime="video/mp4"):
        with TestClient(app) as client:
            return client.post("/transcribe", files={"file": ("clip.mp4", content, mime)}, headers=headers)

    def test_success_and_empty_clean_up(self):
        for result in [CUES, []]:
            response = self.post(create_app(lambda p: result, self.probe, 8))
            self.assertEqual(response.status_code, 200)
            self.assertEqual(response.json(), {"cues": result})
            self.assertTrue(all(not p.exists() for p in self.paths))

    def test_rejects_oversize_before_probe(self):
        response = self.post(create_app(lambda p: CUES, self.probe, 8), b"123456789")
        self.assertEqual(response.status_code, 413)
        self.assertEqual(self.paths, [])

    def test_invalid_media_and_failures_clean_up(self):
        for info, expected in [({"duration_s": 3, "has_audio": False}, 422), ({"duration_s": 301, "has_audio": True}, 413), ({"duration_s": float("nan"), "has_audio": True}, 422)]:
            def probe(path):
                self.paths.append(path)
                return info
            response = self.post(create_app(lambda p: self.fail("must not transcribe"), probe))
            self.assertEqual(response.status_code, expected)
        def broken(path):
            self.paths.append(path)
            raise ValueError("decode failed")
        self.assertEqual(self.post(create_app(lambda p: CUES, broken)).status_code, 422)
        self.assertEqual(self.post(create_app(broken, self.probe)).status_code, 500)
        self.assertTrue(all(not p.exists() for p in self.paths))

    def test_origin_and_preflight(self):
        app = create_app(lambda p: CUES, self.probe)
        for headers in [{"Origin": "https://evil.test"}, {"Origin": "null"}, {}]:
            self.assertEqual(self.post(app, headers=headers).status_code, 403)
        with TestClient(app) as client:
            response = client.options("/transcribe", headers={**ORIGIN, "Access-Control-Request-Method": "POST"})
            self.assertEqual(response.status_code, 200)
            self.assertEqual(response.headers["access-control-allow-origin"], ORIGIN["Origin"])
        self.assertEqual(self.paths, [])

    def test_bad_upload_and_missing_file(self):
        app = create_app(lambda p: CUES, self.probe)
        self.assertEqual(self.post(app, mime="text/plain").status_code, 422)
        with TestClient(app) as client:
            self.assertEqual(client.post("/transcribe", headers=ORIGIN).status_code, 400)

    def test_missing_model_is_unavailable(self):
        with patch.dict(os.environ, {"ASR_MODEL_DIR": ""}):
            app = create_app()
        with TestClient(app) as client:
            self.assertEqual(client.get("/health").status_code, 503)
        self.assertEqual(self.post(app).status_code, 503)

    def test_busy_request_rejected(self):
        entered, release = threading.Event(), threading.Event()
        def transcribe(path):
            entered.set()
            release.wait(5)
            return CUES
        app = create_app(transcribe, self.probe)
        results = []
        thread = threading.Thread(target=lambda: results.append(self.post(app)))
        thread.start()
        try:
            self.assertTrue(entered.wait(5))
            self.assertEqual(self.post(app).status_code, 409)
        finally:
            release.set()
            thread.join(5)
        self.assertEqual(results[0].status_code, 200)
