"""Apply the user-approved nine-hour ceiling without enabling recognition."""
from pathlib import Path
import os
import tempfile
import subprocess
import time
import urllib.request
import urllib.error

path = Path('/opt/yue/backend.env')
original = path.read_bytes()
lines = original.decode().splitlines()
values = dict(line.split('=', 1) for line in lines if '=' in line and not line.lstrip().startswith('#'))
enabled = values.get('DUBBING_ASR_ENABLED', 'false').strip().lower()
updated = '\n'.join(line for line in lines if line.partition('=')[0].strip() != 'DUBBING_ASR_MONTHLY_SECONDS')
updated += '\nDUBBING_ASR_MONTHLY_SECONDS=32400\n'
fd, backup = tempfile.mkstemp(prefix='backend-before-nine-hour-limit-', suffix='.env', dir='/opt/yue/credential-backups')
with os.fdopen(fd, 'wb') as output:
    output.write(original)
    output.flush()
    os.fsync(output.fileno())

def replace(content):
    fd, temporary = tempfile.mkstemp(prefix='.asr-limit-', dir=path.parent)
    with os.fdopen(fd, 'wb') as output:
        output.write(content)
        output.flush()
        os.fsync(output.fileno())
    os.replace(temporary, path)

def restart():
    subprocess.run(['systemctl', 'restart', 'yue-backend'], check=True, capture_output=True, timeout=90)
    for _ in range(40):
        try:
            urllib.request.urlopen('http://127.0.0.1:8080/api/dubbing/transcriptions/check', timeout=2)
        except urllib.error.HTTPError as error:
            if error.code == 401: return
        except Exception:
            pass
        time.sleep(2)
    raise RuntimeError('Health check failed')

try:
    replace(updated.encode())
    restart()
    print('MONTHLY_SECONDS=32400; ASR_ENABLED=' + enabled + '; HEALTH=OK')
except Exception:
    replace(original)
    restart()
    print('UPDATE_FAILED; ORIGINAL_CONFIGURATION_RESTORED')
    raise SystemExit(1)
