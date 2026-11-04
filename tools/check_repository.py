#!/usr/bin/env python3
"""Tracked-file disclosure gate, deliberately offline and credential-free. Not a full secret scanner."""
import re
import subprocess
from pathlib import Path
root = Path(__file__).resolve().parents[1]
files = subprocess.check_output(['git', 'ls-files', '-z'], cwd=root).decode().split('\0')
patterns = [r'-----BEGIN ' + r'(?:RSA |EC |OPENSSH )?PRIVATE KEY-----', r'gh' + r'[pousr]_[A-Za-z0-9]{30,}', r'github_' + r'pat_[A-Za-z0-9_]{40,}', r'AKIA' + r'[A-Z0-9]{16}']
for name in filter(None, files):
    path = root / name
    assert path.suffix.lower() not in {'.jks','.keystore','.p12','.pem','.key','.apk','.mp4','.webm'}, name
    assert path.name not in {'local.properties','keystore.properties','.env'}, name
    if path.suffix in {'.py','.kt','.md','.yml','.xml','.json','.properties','.kts','.java'}:
        text = path.read_text()
        assert not any(re.search(pattern,text) for pattern in patterns), name
print('PASS: tracked-file credential and private-binary gate (limited patterns, not a security certification)')
