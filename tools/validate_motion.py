#!/usr/bin/env python3
"""Technical gate only: this cannot establish location, authenticity, rights or loop aesthetics.
Requires host ffprobe. It does not install or play media on Android.
"""
import json
import subprocess
from pathlib import Path

def validate(path):
    path = Path(path)
    if not path.is_file():
        raise ValueError('Missing clip')
    probe = subprocess.run(['ffprobe', '-v', 'error', '-show_streams', '-show_format', '-of', 'json', str(path)], capture_output=True, text=True)
    if probe.returncode:
        raise ValueError('Unreadable/corrupt media')
    data = json.loads(probe.stdout)
    streams = data.get('streams', [])
    video = [s for s in streams if s.get('codec_type') == 'video']
    if len(video) != 1 or any(s.get('codec_type') == 'audio' for s in streams):
        raise ValueError('Exactly one video track and no audio required')
    v = video[0]
    w, h = v.get('width', 0), v.get('height', 0)
    num, den = (int(n) for n in v.get('avg_frame_rate', '0/1').split('/'))
    if not den or not 0 < num / den <= 30:
        raise ValueError('Frame rate must be <=30 fps')
    if v.get('codec_name') != 'h264' or (w, h) != (1920, 1080) or v.get('pix_fmt') != 'yuv420p':
        raise ValueError('1080p H.264 yuv420p required')
    if not 8 <= float(data.get('format', {}).get('duration', 0)) <= 180.1:
        raise ValueError('Duration must be 8–180 seconds')
    if 'mp4' not in data.get('format', {}).get('format_name', ''):
        raise ValueError('MP4 container required')
    return dict(resolution=[w, h], fps=num / den, duration=float(data['format']['duration']), audio=False, codec='h264')

if __name__ == '__main__':
    import sys
    print(json.dumps(validate(sys.argv[1]), indent=2))
