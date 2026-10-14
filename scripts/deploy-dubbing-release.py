"""Deploy the verified 2026-10-14 candidate with ASR disabled and automatic rollback."""
from pathlib import Path
import hashlib
import os
import shutil
import subprocess
import tarfile
import tempfile
import time
import urllib.request
import urllib.error

stage = Path('/home/admin/yue-release-20261014-cloud-asr')
jar = Path('/opt/yue/app.jar')
env = Path('/opt/yue/backend.env')
nginx = Path('/etc/nginx/sites-available/yue')
web = Path('/var/www/yue')

def command(*args):
    subprocess.run(args, check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, timeout=90)

def atomic(path, data, mode=0o644):
    fd, name = tempfile.mkstemp(prefix='.release-', dir=path.parent)
    try:
        with os.fdopen(fd, 'wb') as output:
            os.fchmod(output.fileno(), mode)
            output.write(data)
            output.flush()
            os.fsync(output.fileno())
        os.replace(name, path)
    finally:
        if os.path.exists(name): os.unlink(name)

def healthy():
    for attempt in range(45):
        try:
            urllib.request.urlopen('http://127.0.0.1:8080/api/dubbing/transcriptions/release-check', timeout=2)
        except urllib.error.HTTPError as error:
            if error.code == 401: return
        except Exception:
            pass
        time.sleep(2)
    raise RuntimeError('Backend health check failed')

assert os.geteuid() == 0
assert hashlib.sha256((stage/'yue-backend-1.0.0.jar').read_bytes()).hexdigest() == '9e9cae4a4ae724dd3d297a36e8535273e3002a362fb83a5ea55ec557c8e0583c'
assert hashlib.sha256((stage/'release-20261014-cloud-asr.tar.gz').read_bytes()).hexdigest() == 'b17ce1f4edf5f7b56355b75dfb4694e1d7d70d7efc141faf9f802960d0782243'
assert hashlib.sha256(jar.read_bytes()).hexdigest() == '10990d1d2ed50c4dd1764374e3687be6deaee1c07e7f2a4e7c9c256b1b075e72', 'Production changed; recheck before deploying'
command('nginx', '-t')
config = nginx.read_text()
marker = '    location /api/ {'
assert config.count(marker) == 1 and 'location = /api/dubbing/transcriptions' not in config
location = '''    location = /api/dubbing/transcriptions {
        client_max_body_size 201m;
        client_body_timeout 60s;
        proxy_request_buffering off;
        proxy_pass http://127.0.0.1:8080;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_read_timeout 120s;
    }

'''
files = {}
with tarfile.open(stage/'release-20261014-cloud-asr.tar.gz') as archive:
    for member in archive.getmembers():
        relative = Path(member.name)
        assert not relative.is_absolute() and '..' not in relative.parts
        assert member.isfile() or member.isdir()
        if member.isfile() and relative.parts[0] != 'audio':
            files[relative] = archive.extractfile(member).read()
assert Path('index.html') in files
backup = Path(tempfile.mkdtemp(prefix='release-backup-20261014-', dir='/opt/yue'))
for source, name in [(jar,'app.jar'), (env,'backend.env'), (nginx,'nginx.conf')]:
    shutil.copy2(source, backup/name)
    (backup/name).chmod(0o600)
with tarfile.open(backup/'frontend.tar.gz', 'w:gz') as archive:
    archive.add(web, arcname='frontend')
print('BACKUP_READY', backup, flush=True)
original_web = {relative: (web/relative).read_bytes() if (web/relative).is_file() else None for relative in files}
try:
    updates = {'DUBBING_ASR_ENABLED':'false', 'DUBBING_ASR_MONTHLY_SECONDS':'600',
               'DUBBING_ASR_BUDGET_FILE':'/opt/yue/data/dubbing-asr-budget.txt',
               'SPRING_JPA_HIBERNATE_DDL_AUTO':'validate'}
    lines = [line for line in env.read_text().splitlines() if line.partition('=')[0].strip() not in updates]
    lines.extend(key+'='+value for key,value in updates.items())
    atomic(env, ('\n'.join(lines)+'\n').encode(), 0o600)
    atomic(jar, (stage/'yue-backend-1.0.0.jar').read_bytes())
    command('systemctl', 'restart', 'yue-backend')
    healthy()
    print('BACKEND_READY_ASR_DISABLED', flush=True)
    atomic(nginx, config.replace(marker, location+marker).encode())
    command('nginx', '-t')
    command('systemctl', 'reload', 'nginx')
    for relative, data in files.items():
        if relative == Path('index.html'): continue
        (web/relative).parent.mkdir(parents=True, exist_ok=True)
        atomic(web/relative, data)
    atomic(web/'index.html', files[Path('index.html')])
    with urllib.request.urlopen('https://gnep.online/?release=20261014', timeout=15) as response:
        assert response.read() == files[Path('index.html')]
    print('DEPLOYED', 'https://gnep.online', 'ASR_DISABLED', flush=True)
except Exception as error:
    print('DEPLOY_FAILED', type(error).__name__, 'ROLLING_BACK', flush=True)
    for target, name in [(jar,'app.jar'), (env,'backend.env'), (nginx,'nginx.conf')]:
        atomic(target, (backup/name).read_bytes(), 0o600 if target == env else 0o644)
    for relative, data in original_web.items():
        if data is not None: atomic(web/relative, data)
    command('nginx','-t')
    command('systemctl','reload','nginx')
    command('systemctl','restart','yue-backend')
    healthy()
    print('ROLLBACK_COMPLETE', flush=True)
    raise SystemExit(1)
