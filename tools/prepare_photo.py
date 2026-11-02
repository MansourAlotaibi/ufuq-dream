#!/usr/bin/env python3
"""Offline asset preparation. Requires Pillow; not an Android runtime dependency.
Review source license/attribution before use. Does not fabricate provenance.
Usage: python prepare_photo.py original.jpg stable_id output-assets-directory
"""
from pathlib import Path
import sys
from PIL import Image, ImageOps
source, image_id, output = sys.argv[1:]
if not image_id.replace('_', '').isalnum():
    raise SystemExit('Use a simple stable alphanumeric/underscore id')
root = Path(output)
for folder in ['cinematic', 'thumbnails']:
    (root / folder).mkdir(parents=True, exist_ok=True)
with Image.open(source) as original:
    original = ImageOps.exif_transpose(original).convert('RGB')
    if original.width < 1920 or original.height < 1080:
        raise SystemExit('Source below runtime dimensions: choose a higher-resolution original')
    image = ImageOps.fit(original, (1920, 1080), method=Image.Resampling.LANCZOS)
    image.save(root / 'cinematic' / f'{image_id}.jpg', quality=87, optimize=True)
    image.resize((240, 135), Image.Resampling.LANCZOS).save(root / 'thumbnails' / f'{image_id}.jpg', quality=83, optimize=True)
print('Prepared assets; add reviewed provenance and catalog entry before shipping.')
