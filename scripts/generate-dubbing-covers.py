"""Generate approved material covers, or verify and import the checked-in covers."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parent
SOURCES = {
    'pendatang-arrival': ('pendatang-arrival-01.mp4', '713c519feedf6f692d6d5ea7d08c436a7846326fa045289b97a8d8f60c4b5116'),
    'sfdph-wash-hands-cantonese': ('wash-hands-01.mp4', '016ad0ebec3ca0b5c9bb423a59b9e3eef5f8058ce5be7ef85bdb14cf9c021e35'),
}

def digest(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()

def atomic_import(source, target, expected):
    source, target = Path(source), Path(target)
    if digest(source) != expected:
        raise ValueError('Source cover hash mismatch: ' + source.name)
    if target.exists():
        if digest(target) != expected:
            raise ValueError('Refusing to overwrite a different cover: ' + target.name)
        return
    target.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.NamedTemporaryFile(dir=target.parent, suffix='.tmp', delete=False) as stream:
        temporary = Path(stream.name)
        stream.write(source.read_bytes())
        stream.flush()
        os.fsync(stream.fileno())
    try:
        # Hard link publishes atomically without overwriting a concurrently created target.
        try:
            os.link(temporary, target)
        except FileExistsError:
            if digest(target) != expected:
                raise ValueError('Concurrent cover mismatch: ' + target.name)
    finally:
        temporary.unlink(missing_ok=True)

def generate(source_dir, target_dir, ffmpeg='ffmpeg'):
    target_dir = Path(target_dir)
    target_dir.mkdir(parents=True, exist_ok=True)
    for code, (name, expected) in SOURCES.items():
        source = Path(source_dir) / name
        if digest(source) != expected:
            raise ValueError('Approved source video hash mismatch: ' + name)
        with tempfile.TemporaryDirectory(dir=target_dir) as temp:
            output = Path(temp) / (code + '.jpg')
            subprocess.run([ffmpeg, '-v', 'error', '-ss', '0.5', '-i', str(source), '-frames:v', '1',
                '-vf', 'scale=1280:720:force_original_aspect_ratio=decrease,pad=1280:720:(ow-iw)/2:(oh-ih)/2',
                '-q:v', '3', '-threads', '1', str(output)], check=True, timeout=30)
            atomic_import(output, target_dir / output.name, digest(output))

def import_covers(target_dir):
    manifest = json.loads((ROOT / 'dubbing-covers' / 'manifest.json').read_text(encoding='utf-8'))
    for entry in manifest:
        atomic_import(ROOT / 'dubbing-covers' / entry['file'], Path(target_dir) / entry['file'], entry['sha256'])

if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--source-dir', type=Path, help='Generate from verified original clips instead of importing')
    parser.add_argument('--target-dir', type=Path, required=True)
    parser.add_argument('--ffmpeg', default='ffmpeg')
    args = parser.parse_args()
    if args.source_dir:
        generate(args.source_dir, args.target_dir, args.ffmpeg)
    else:
        import_covers(args.target_dir)
