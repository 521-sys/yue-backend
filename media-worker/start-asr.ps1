$ErrorActionPreference = 'Stop'
$workspaceRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$asrPython = Join-Path $workspaceRoot '.asr-probe/venv/Scripts/python.exe'
$env:ASR_MODEL_DIR = Join-Path $workspaceRoot '.asr-probe/models/large-v3'
if (!(Test-Path -LiteralPath (Join-Path $env:ASR_MODEL_DIR 'model.bin'))) {
    throw 'large-v3 model is missing. Download the model before starting this service.'
}
Push-Location $PSScriptRoot
try { & $asrPython -m asr_service }
finally { Pop-Location }
