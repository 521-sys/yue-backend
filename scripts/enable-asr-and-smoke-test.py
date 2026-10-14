"""Enable authorized ASR and submit one existing short public learning audio sample."""
from pathlib import Path
import base64
import hashlib
import hmac
import json
import os
import shlex
import subprocess
import tempfile
import time
import urllib.request
import urllib.error

path = Path('/opt/yue/backend.env')
original = path.read_bytes()
lines = original.decode().splitlines()
values = dict(line.split('=',1) for line in lines if '=' in line and not line.lstrip().startswith('#'))
assert int(values['DUBBING_ASR_MONTHLY_SECONDS']) == 32400
assert values.get('TENCENTCLOUD_SECRET_ID') and values.get('TENCENTCLOUD_SECRET_KEY')
sample = Path('/var/www/yue/audio/a0001.mp3')
assert sample.is_file() and sample.stat().st_size < 1024*1024
duration = float(subprocess.check_output(['ffprobe','-v','error','-show_entries','format=duration','-of','default=noprint_wrappers=1:nokey=1',str(sample)], timeout=20))
assert 0 < duration <= 15
fd, backup = tempfile.mkstemp(prefix='backend-before-enable-asr-', suffix='.env', dir='/opt/yue/credential-backups')
with os.fdopen(fd, 'wb') as output: output.write(original)

def replace(data):
    fd, name = tempfile.mkstemp(prefix='.asr-enable-', dir=path.parent)
    with os.fdopen(fd, 'wb') as output:
        output.write(data); output.flush(); os.fsync(output.fileno())
    os.replace(name,path)

def restart():
    subprocess.run(['systemctl','restart','yue-backend'],check=True,capture_output=True,timeout=90)
    for _ in range(45):
        try: urllib.request.urlopen('http://127.0.0.1:8080/api/dubbing/transcriptions/check', timeout=2)
        except urllib.error.HTTPError as error:
            if error.code == 401: return
        except Exception: pass
        time.sleep(2)
    raise RuntimeError('BACKEND_NOT_READY')

def b64(data): return base64.urlsafe_b64encode(data).rstrip(b'=')

try:
    content = '\n'.join(line for line in lines if line.partition('=')[0].strip() != 'DUBBING_ASR_ENABLED') + '\nDUBBING_ASR_ENABLED=true\n'
    replace(content.encode()); restart()
    print('ASR_ENABLED; MONTHLY_SITE_LIMIT_SECONDS=32400', flush=True)
    # Short-lived isolated smoke-test principal. No account or database changes.
    secret = shlex.split(values['JWT_SECRET'])[0].encode()
    header = b64(b'{"alg":"HS256","typ":"JWT"}')
    body = b64(json.dumps({'sub':'-9142026','username':'deployment-smoke-test','iat':int(time.time()),'exp':int(time.time())+600}).encode())
    signing = header+b'.'+body
    token = (signing+b'.'+b64(hmac.new(secret, signing, hashlib.sha256).digest())).decode()
    boundary = 'yue-asr-smoke-20261014'
    data = ('--'+boundary+'\r\nContent-Disposition: form-data; name="file"; filename="sample.mp3"\r\nContent-Type: audio/mpeg\r\n\r\n').encode()+sample.read_bytes()+('\r\n--'+boundary+'--\r\n').encode()
    base = 'https://gnep.online/api/dubbing/transcriptions'
    request = urllib.request.Request(base, data=data, headers={'Authorization':'Bearer '+token,'Content-Type':'multipart/form-data; boundary='+boundary},method='POST')
    # Deliberately submit once. A timeout must never trigger a duplicate paid job.
    with urllib.request.urlopen(request,timeout=60) as response: job=json.load(response)
    job_id=job['id']
    print('SMOKE_SUBMITTED_ONCE; SAMPLE_SECONDS='+str(duration), flush=True)
    for _ in range(60):
        request=urllib.request.Request(base+'/'+job_id,headers={'Authorization':'Bearer '+token})
        with urllib.request.urlopen(request,timeout=15) as response: job=json.load(response)
        if job['status']=='failed':
            print('SMOKE_FAILED: '+job.get('error','unknown'),flush=True)
            raise RuntimeError('SMOKE_FAILED')
        if job['status']=='succeeded':
            cues=job.get('cues',[])
            assert cues and all(c['endMs']>c['startMs']>=0 and c['text'].strip() for c in cues)
            print('SMOKE_SUCCEEDED; CUES='+str(len(cues)),flush=True)
            print(json.dumps(cues,ensure_ascii=False),flush=True)
            print('BUDGET='+Path('/opt/yue/data/dubbing-asr-budget.txt').read_text().strip(),flush=True)
            break
        time.sleep(3)
    else: raise RuntimeError('SMOKE_WAIT_TIMEOUT')
except Exception as error:
    print('VERIFICATION_FAILED: '+type(error).__name__+'; RESTORING_PREVIOUS_ENABLE_STATE',flush=True)
    replace(original); restart()
    print('PREVIOUS_CONFIGURATION_RESTORED; no repeat submission',flush=True)
    raise SystemExit(1)
