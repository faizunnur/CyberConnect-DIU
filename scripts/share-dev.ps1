<#
.SYNOPSIS
  Shares the local full stack (nginx on http://localhost:8088) over a public HTTPS link so other people can test it.

.DESCRIPTION
  Uses a Cloudflare "quick tunnel" (free, no account, random *.trycloudflare.com address) in front of nginx, then
  restarts the backend with APP_BASE_URL set to that address so email links and the WebSocket origin check work.
  The address changes every time the tunnel starts; re-run the script and share the new link.

  Safety rails: refuses to start while E2E_FIXTURES_ENABLED=true in .env (the fixtures endpoint would let anyone
  create verified members), switches Swagger off for the shared session, and warns when email still goes to
  Mailpit (testers would not receive verification emails).

  Docker: uses Docker Desktop when it answers; otherwise falls back to the Ubuntu WSL engine (see CLAUDE.md §15).

.EXAMPLE
  powershell -ExecutionPolicy Bypass -File scripts/share-dev.ps1          # start sharing, print the link
  powershell -ExecutionPolicy Bypass -File scripts/share-dev.ps1 -Status  # show the current link
  powershell -ExecutionPolicy Bypass -File scripts/share-dev.ps1 -Stop    # stop the tunnel, back to localhost only
#>
[CmdletBinding()]
param(
    [int]$Port = 8088,
    [switch]$Stop,
    [switch]$Status,
    [string]$WslDistro = 'Ubuntu'
)
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$stateDir = Join-Path $root '.share'
$pidFile = Join-Path $stateDir 'tunnel.pid'
$urlFile = Join-Path $stateDir 'tunnel.url'
$logFile = Join-Path $stateDir 'cloudflared.log'
$toolDir = Join-Path $env:LOCALAPPDATA 'cloudflared'
$cloudflared = Join-Path $toolDir 'cloudflared.exe'
$downloadUrl = 'https://github.com/cloudflare/cloudflared/releases/latest/download/cloudflared-windows-amd64.exe'
New-Item -ItemType Directory -Force $stateDir | Out-Null

function Read-DotEnv {
    $map = @{}
    if (Test-Path (Join-Path $root '.env')) {
        Get-Content (Join-Path $root '.env') | ForEach-Object {
            if ($_ -match '^\s*([A-Za-z_][A-Za-z0-9_]*)\s*=\s*(.*)$') { $map[$matches[1]] = $matches[2].Trim() }
        }
    }
    return $map
}

function Test-WindowsDocker {
    try {
        $p = Start-Process -FilePath 'docker' -ArgumentList 'info','--format','{{.ServerVersion}}' -NoNewWindow -PassThru `
            -RedirectStandardOutput (Join-Path $stateDir 'docker-info.txt') -RedirectStandardError (Join-Path $stateDir 'docker-info.err')
        if (-not $p.WaitForExit(15000)) { $p.Kill(); return $false }
        return ($p.ExitCode -eq 0)
    } catch { return $false }
}

$script:useWsl = -not (Test-WindowsDocker)
if ($script:useWsl) {
    # The WSL distro stops seconds after its last session ends, taking dockerd with it: keep one session open.
    $alive = Get-CimInstance Win32_Process -Filter "Name='wsl.exe'" | Where-Object { $_.CommandLine -like '*sleep 86400*' }
    if (-not $alive) { Start-Process wsl.exe -ArgumentList "-d $WslDistro -u root -- sleep 86400" -WindowStyle Hidden }
    $script:wslRoot = (& wsl -d $WslDistro -- wslpath -a "$root").Trim()
}

function Invoke-Compose([string[]]$composeArgs, [hashtable]$extraEnv = @{}) {
    if ($script:useWsl) {
        $assign = ($extraEnv.GetEnumerator() | ForEach-Object { "$($_.Key)='$($_.Value)'" }) -join ' '
        $cmd = "cd '$($script:wslRoot)' && $assign docker compose $($composeArgs -join ' ')"
        & wsl -d $WslDistro -u root -- bash -c $cmd
    } else {
        $saved = @{}
        foreach ($k in $extraEnv.Keys) { $saved[$k] = [Environment]::GetEnvironmentVariable($k); [Environment]::SetEnvironmentVariable($k, $extraEnv[$k]) }
        try { Push-Location $root; & docker compose @composeArgs } finally { Pop-Location; foreach ($k in $saved.Keys) { [Environment]::SetEnvironmentVariable($k, $saved[$k]) } }
    }
    if ($LASTEXITCODE -ne 0) { throw "docker compose $($composeArgs -join ' ') failed (exit $LASTEXITCODE)" }
}

function Wait-Backend([int]$seconds) {
    $deadline = (Get-Date).AddSeconds($seconds)
    while ((Get-Date) -lt $deadline) {
        try {
            $r = Invoke-WebRequest -UseBasicParsing -TimeoutSec 5 "http://localhost:$Port/api/v1/health"
            if ($r.StatusCode -eq 200) { return $true }
        } catch { }
        Start-Sleep -Seconds 3
    }
    return $false
}

function Stop-Tunnel {
    if (Test-Path $pidFile) {
        $tunnelPid = Get-Content $pidFile | Select-Object -First 1
        $proc = Get-Process -Id $tunnelPid -ErrorAction SilentlyContinue
        if ($proc -and $proc.ProcessName -like 'cloudflared*') { Stop-Process -Id $tunnelPid -Force }
        Remove-Item $pidFile -Force
    }
    Remove-Item $urlFile -Force -ErrorAction SilentlyContinue
}

if ($Status) {
    if ((Test-Path $urlFile) -and (Test-Path $pidFile) -and (Get-Process -Id (Get-Content $pidFile) -ErrorAction SilentlyContinue)) {
        Write-Host "Shared at: $(Get-Content $urlFile)" -ForegroundColor Green
    } else {
        Write-Host 'Not shared (no running tunnel).' -ForegroundColor Yellow
    }
    exit 0
}

if ($Stop) {
    Stop-Tunnel
    Write-Host "Tunnel stopped. Restoring the backend to http://localhost:$Port ..." -ForegroundColor Cyan
    Invoke-Compose @('--profile','full','up','-d','backend')
    Invoke-Compose @('--profile','full','restart','nginx')
    Write-Host 'Done: the stack is reachable only on this machine again.' -ForegroundColor Green
    exit 0
}

# ---- safety rails ----------------------------------------------------------------------------------------------
$dotenv = Read-DotEnv
if ($dotenv['E2E_FIXTURES_ENABLED'] -eq 'true') {
    throw 'Refusing to share: E2E_FIXTURES_ENABLED=true in .env lets anyone create verified members. Set it to false first.'
}
$mailHost = $dotenv['SMTP_HOST']
if (-not $mailHost -or $mailHost -eq 'mailpit' -or $mailHost -eq 'localhost') {
    Write-Host 'WARNING: email still goes to Mailpit (http://localhost:8025). Testers will NOT receive verification emails;' -ForegroundColor Yellow
    Write-Host '         you must copy their links from Mailpit, or set SMTP_* in .env for a real relay (see .env.example).' -ForegroundColor Yellow
}

# ---- cloudflared -----------------------------------------------------------------------------------------------
if (-not (Test-Path $cloudflared)) {
    New-Item -ItemType Directory -Force $toolDir | Out-Null
    Write-Host "Downloading cloudflared to $cloudflared ..." -ForegroundColor Cyan
    [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
    Invoke-WebRequest -UseBasicParsing -Uri $downloadUrl -OutFile $cloudflared
}

# ---- stack -----------------------------------------------------------------------------------------------------
Write-Host 'Starting the full stack (postgres, redis, storage, mailpit, backend, frontend, nginx) ...' -ForegroundColor Cyan
Invoke-Compose @('--profile','full','up','-d')
if (-not (Wait-Backend 180)) { throw "Backend did not become healthy on http://localhost:$Port within 3 minutes." }

# ---- tunnel ----------------------------------------------------------------------------------------------------
Stop-Tunnel
Remove-Item $logFile -Force -ErrorAction SilentlyContinue
$tunnel = Start-Process -FilePath $cloudflared -ArgumentList "tunnel --url http://localhost:$Port --no-autoupdate" `
    -WindowStyle Hidden -PassThru -RedirectStandardError $logFile -RedirectStandardOutput (Join-Path $stateDir 'cloudflared.out')
Set-Content $pidFile $tunnel.Id
$publicUrl = $null
$deadline = (Get-Date).AddSeconds(60)
while (-not $publicUrl -and (Get-Date) -lt $deadline) {
    Start-Sleep -Seconds 2
    if (Test-Path $logFile) {
        $m = Select-String -Path $logFile -Pattern 'https://[a-z0-9-]+\.trycloudflare\.com' | Select-Object -First 1
        if ($m) { $publicUrl = $m.Matches[0].Value }
    }
    if ($tunnel.HasExited) { throw "cloudflared exited early; see $logFile" }
}
if (-not $publicUrl) { Stop-Tunnel; throw "No tunnel address appeared within 60 s; see $logFile" }
Set-Content $urlFile $publicUrl

# ---- point the backend at the public address ---------------------------------------------------------------------
Write-Host "Tunnel is up at $publicUrl - restarting the backend with that base URL (about a minute) ..." -ForegroundColor Cyan
Invoke-Compose @('--profile','full','up','-d','backend') @{ APP_BASE_URL = $publicUrl; API_DOCS_ENABLED = 'false' }
if (-not (Wait-Backend 180)) { throw 'Backend did not come back after the restart.' }
Invoke-Compose @('--profile','full','restart','nginx')

Write-Host ''
Write-Host "Share this link:  $publicUrl" -ForegroundColor Green
Write-Host 'It stays valid while this computer, the stack and the tunnel are running. The address changes on every start.'
Write-Host "Stop sharing:     powershell -ExecutionPolicy Bypass -File scripts/share-dev.ps1 -Stop"
Write-Host "Current link:     powershell -ExecutionPolicy Bypass -File scripts/share-dev.ps1 -Status"
