# 计算渲染模板的 SHA-256，并给出可直接粘贴到 config.yml 的配置行（R28）。
#
# 用途：发布模板时把校验和写进发布说明；服主用它核对拿到的模板确实是发布的那一份。
#
# 用法：
#   pwsh -File scripts/verify-template.ps1 .\index.html
#   pwsh -File scripts/verify-template.ps1 .\index.html -Expected 3f2a...   # 顺便校验
param(
    [Parameter(Mandatory = $true)][string]$Path,
    [string]$Expected = ""
)

if (-not (Test-Path -LiteralPath $Path)) {
    Write-Error "文件不存在: $Path"
    exit 1
}

$hash = (Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash.ToLower()
$size = (Get-Item -LiteralPath $Path).Length

Write-Output "文件    : $Path"
Write-Output "体积    : $size 字节"
Write-Output "SHA-256 : $hash"
Write-Output ""
Write-Output "config.yml 里这样填："
Write-Output "    template_sha256: `"$hash`""

if ($Expected) {
    $normalized = $Expected.Trim().ToLower().Replace("sha256:", "").Trim()
    Write-Output ""
    if ($normalized -eq $hash) {
        Write-Output "校验结果: 匹配 ✓"
        exit 0
    }
    Write-Output "校验结果: 不匹配 ✗（期望 $normalized）"
    exit 2
}
