import shutil
import subprocess
import tempfile
import unittest
from pathlib import Path
from validate_motion import validate

@unittest.skipUnless(shutil.which("ffmpeg") and shutil.which("ffprobe"), "ffmpeg and ffprobe required")
class MediaGateTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.temp = tempfile.TemporaryDirectory()
        cls.root = Path(cls.temp.name)
        # Synthetic technical fixtures only, generated locally; no stock/personal footage.
        for name, duration, audio in [("silent.mp4", 8, False), ("audio.mp4", 8, True), ("long.mp4", 61, False)]:
            args = ["ffmpeg", "-v", "error", "-f", "lavfi", "-i", "color=c=black:s=1920x1080:r=1"]
            if audio:
                args += ["-f", "lavfi", "-i", "anullsrc=r=8000:cl=mono"]
            args += ["-t", str(duration), "-c:v", "libx264", "-preset", "ultrafast", "-threads", "1", "-pix_fmt", "yuv420p"]
            args += ["-c:a", "aac", "-shortest"] if audio else ["-an"]
            subprocess.run(args + [str(cls.root / name)], check=True, timeout=60)
    @classmethod
    def tearDownClass(cls):
        cls.temp.cleanup()
    def test_missing(self):
        with self.assertRaises(ValueError): validate(self.root / "missing.mp4")
    def test_corrupt(self):
        path = self.root / "corrupt.mp4"; path.write_bytes(b"Technical fixture, not media")
        with self.assertRaises(ValueError): validate(path)
    def test_technical_fixture(self):
        result = validate(self.root / "silent.mp4")
        self.assertEqual([1920,1080], result["resolution"]); self.assertFalse(result["audio"])
    def test_audio_is_rejected(self):
        with self.assertRaises(ValueError): validate(self.root / "audio.mp4")
    def test_native_minute_plus_is_supported(self):
        self.assertGreaterEqual(validate(self.root / "long.mp4")["duration"], 60)

if __name__ == "__main__": unittest.main()
