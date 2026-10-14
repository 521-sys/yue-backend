param([switch]$CheckUi)
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Windows.Forms
Add-Type -AssemblyName System.Drawing
[Windows.Forms.Application]::EnableVisualStyles()
$form = New-Object Windows.Forms.Form
$form.Text = '腾讯云密钥配置'
$form.ClientSize = New-Object Drawing.Size(520, 320)
$form.StartPosition = 'CenterScreen'
$form.FormBorderStyle = 'FixedDialog'
$form.MaximizeBox = $false
$form.Font = New-Object Drawing.Font('Microsoft YaHei UI', 10)
$intro = New-Object Windows.Forms.Label
$intro.Text = "分别点击下面两个输入框，用 Ctrl+V 粘贴。`n内容显示为圆点；完成后点击保存。"
$intro.SetBounds(20, 15, 480, 55)
$form.Controls.Add($intro)
$idLabel = New-Object Windows.Forms.Label
$idLabel.Text = 'SecretId'
$idLabel.SetBounds(20, 82, 90, 25)
$form.Controls.Add($idLabel)
$idBox = New-Object Windows.Forms.TextBox
$idBox.SetBounds(115, 80, 375, 30)
$idBox.UseSystemPasswordChar = $true
$form.Controls.Add($idBox)
$keyLabel = New-Object Windows.Forms.Label
$keyLabel.Text = 'SecretKey'
$keyLabel.SetBounds(20, 132, 90, 25)
$form.Controls.Add($keyLabel)
$keyBox = New-Object Windows.Forms.TextBox
$keyBox.SetBounds(115, 130, 375, 30)
$keyBox.UseSystemPasswordChar = $true
$form.Controls.Add($keyBox)
$save = New-Object Windows.Forms.Button
$save.Text = '保存到服务器'
$save.SetBounds(170, 185, 180, 40)
$form.Controls.Add($save)
$status = New-Object Windows.Forms.Label
$status.Text = '仅配置密钥，不启用识别、不重启服务。'
$status.SetBounds(20, 240, 480, 65)
$form.Controls.Add($status)
$save.Add_Click({
    if ($idBox.Text.Trim() -cnotmatch '^AKID[A-Za-z0-9]{8,124}$' -or $keyBox.Text.Trim() -cnotmatch '^[A-Za-z0-9]{16,128}$') {
        $status.Text = '请检查两个值是否粘贴完整，不要包含名称或引号。'
        return
    }
    $save.Enabled = $false
    $status.Text = '正在安全保存，请稍候…'
    $form.Refresh()
    $process = $null
    $payload = $null
    try {
        $info = New-Object Diagnostics.ProcessStartInfo
        $info.FileName = (Get-Command ssh.exe).Source
        $info.Arguments = '-T -o BatchMode=yes -o StrictHostKeyChecking=yes -o ConnectTimeout=10 admin@47.106.190.236 sudo -n python3 /home/admin/yue-release-20261014-cloud-asr/configure-tencent-asr.py --stdin'
        $info.UseShellExecute = $false
        $info.CreateNoWindow = $true
        $info.RedirectStandardInput = $true
        $info.RedirectStandardOutput = $true
        $info.RedirectStandardError = $true
        $process = New-Object Diagnostics.Process
        $process.StartInfo = $info
        [void]$process.Start()
        $outputTask = $process.StandardOutput.ReadToEndAsync()
        $errorTask = $process.StandardError.ReadToEndAsync()
        $payload = @{ secretId = $idBox.Text.Trim(); secretKey = $keyBox.Text.Trim() } | ConvertTo-Json -Compress
        $process.StandardInput.WriteLine($payload)
        $process.StandardInput.Close()
        if (!$process.WaitForExit(45000)) { $process.Kill(); throw 'Timed out' }
        if ($process.ExitCode -ne 0 -or !$outputTask.Result.Contains('SUCCESS: credentials saved.')) { throw 'Save failed' }
        $idBox.Clear()
        $keyBox.Clear()
        $status.ForeColor = [Drawing.Color]::DarkGreen
        $status.Text = '保存成功！可以关闭窗口，回到聊天告诉我“配置完成”。'
    } catch {
        $status.ForeColor = [Drawing.Color]::DarkRed
        $status.Text = '未能确认保存成功，请返回聊天说明情况。不要发送密钥。'
        $save.Enabled = $true
    } finally {
        $payload = $null
        if ($null -ne $process) { $process.Dispose() }
    }
})
if ($CheckUi) {
    if (!$idBox.UseSystemPasswordChar -or !$keyBox.UseSystemPasswordChar) { throw 'Input masking check failed' }
    $form.Dispose()
    Write-Output 'GUI controls and masked input checks passed'
    exit 0
}
$form.Add_Shown({ $form.Activate(); $idBox.Focus() })
[void]$form.ShowDialog()
$form.Dispose()
