# Creates .env from .env.example with freshly generated secrets. Works in Windows PowerShell 5.1 and PowerShell 7.
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$envPath = Join-Path $root '.env'
$examplePath = Join-Path $root '.env.example'

if (Test-Path $envPath) {
    Write-Host ".env already exists at $envPath - not overwriting. Delete it first to regenerate." -ForegroundColor Yellow
    exit 1
}

function New-RandomBytes([int]$count) {
    $bytes = New-Object byte[] $count
    $rng = New-Object System.Security.Cryptography.RNGCryptoServiceProvider
    $rng.GetBytes($bytes)
    $rng.Dispose()
    return $bytes
}

# Password-safe secret: base64 without + / = so it never needs escaping in URLs or shells.
function New-Secret { return ([Convert]::ToBase64String((New-RandomBytes 24)) -replace '[+/=]', '') }
function New-AesKey { return [Convert]::ToBase64String((New-RandomBytes 32)) }

$content = Get-Content $examplePath -Raw
$content = $content -replace 'DB_MIGRATOR_PASSWORD=CHANGE_ME', "DB_MIGRATOR_PASSWORD=$(New-Secret)"
$content = $content -replace 'DB_APP_PASSWORD=CHANGE_ME', "DB_APP_PASSWORD=$(New-Secret)"
$content = $content -replace 'REDIS_PASSWORD=CHANGE_ME', "REDIS_PASSWORD=$(New-Secret)"
$content = $content -replace 'STORAGE_SECRET_KEY=CHANGE_ME', "STORAGE_SECRET_KEY=$(New-Secret)"
$content = $content -replace 'DOCUMENT_ENCRYPTION_KEY=CHANGE_ME_BASE64_32_BYTES', "DOCUMENT_ENCRYPTION_KEY=$(New-AesKey)"

[System.IO.File]::WriteAllText($envPath, $content, (New-Object System.Text.UTF8Encoding $false))
Write-Host "Wrote $envPath with generated secrets." -ForegroundColor Green
