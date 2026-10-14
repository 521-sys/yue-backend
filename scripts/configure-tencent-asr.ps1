$ErrorActionPreference = 'Stop'
$Host.UI.RawUI.WindowTitle = 'Yue - Tencent ASR secure configuration'
$localSource = Join-Path $PSScriptRoot 'configure-tencent-asr.py'
$remotePath = '/home/admin/yue-release-20261014-cloud-asr/configure-tencent-asr.py'
Write-Host 'Connect to the Yue server. Paste SecretId and SecretKey only at the hidden prompts.'
Write-Host 'No credentials are accepted in command arguments or saved in this local script.'
& scp.exe -q -o BatchMode=yes -o StrictHostKeyChecking=yes -o ConnectTimeout=10 $localSource ('admin@47.106.190.236:' + $remotePath)
if ($LASTEXITCODE -ne 0) {
    Write-Host 'Could not prepare the secure input program. No configuration was changed.' -ForegroundColor Yellow
    [void](Read-Host 'Press Enter to close')
    exit 1
}
& ssh.exe -tt -o BatchMode=yes -o StrictHostKeyChecking=yes -o ConnectTimeout=10 admin@47.106.190.236 sudo -n python3 $remotePath
if ($LASTEXITCODE -eq 0) {
    Write-Host 'SUCCESS. Return to Codex and say: configuration completed.' -ForegroundColor Green
} else {
    Write-Host 'Configuration did not complete. Return to Codex; do not share the credentials.' -ForegroundColor Yellow
}
[void](Read-Host 'Press Enter to close')
