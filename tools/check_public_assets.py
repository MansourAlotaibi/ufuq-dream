#!/usr/bin/env python3
"""Offline integrity/public-package gate, not a substitute for source-rights review."""
from pathlib import Path
import hashlib
import json
import re
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
def main():
    assets = ROOT / "app/src/main/assets"
    records = json.loads((assets / "photo-provenance.json").read_text())
    ids = [item["id"] for item in records]
    assert len(ids) == len(set(ids)) == 13, "Unexpected or duplicate photo inventory"
    names = {item["fileName"] for item in records}
    assert names == {p.name for p in (assets / "cinematic").glob("*.jpg")}
    assert names == {p.name for p in (assets / "thumbnails").glob("*.jpg")}
    for item in records:
        for key in ["author", "source", "license", "licenseUrl", "changes", "sha256"]:
            assert item.get(key), (item["id"], key)
        assert item["source"].startswith("https://")
        assert item["license"] in {"CC BY-SA 4.0", "CC BY 3.0", "CC BY 3.0 pl"}
        photo = assets / "cinematic" / item["fileName"]
        assert hashlib.sha256(photo.read_bytes()).hexdigest() == item["sha256"], photo.name
        assert (assets / "thumbnails" / item["fileName"]).stat().st_size < 200_000
    manifest = ET.parse(ROOT / "app/src/main/AndroidManifest.xml").getroot()
    assert not manifest.findall("uses-permission"), "Review newly requested app permissions"
    ns = "{http://schemas.android.com/apk/res/android}"
    service = manifest.find("application/service")
    assert service.get(ns + "permission") == "android.permission.BIND_DREAM_SERVICE"
    # Limit to distributable source folders; do not inspect user's home or build caches.
    for folder in [ROOT / "app/src", ROOT / "tools", ROOT / "docs"]:
        for path in folder.rglob("*"):
            if not path.is_file() or "__pycache__" in path.parts: continue
            assert path.suffix.lower() not in {".mp4", ".webm", ".jks", ".keystore", ".p12", ".pem", ".key", ".apk"}, path
            if path.suffix in {".kt", ".py", ".md", ".json", ".xml"}:
                text = path.read_text()
                assert not re.search(r"/" + r"Users/[^/\s]+|192\.168\.\d+\.\d+", text), path
    print("PASS: 13 licensed-record photos and thumbnails; runtime hashes; Dream permission; no bundled private media/local device details")
if __name__ == "__main__": main()
