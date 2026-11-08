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

  Docker: -Engine auto (default) uses the engine that already holds this project's containers and volumes, else
  Docker Desktop when both its engine and the "docker compose" plugin work, else the Ubuntu WSL engine.
  Force one with -Engine wsl or -Engine desktop.

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
    [string]$WslDistro = 'Ubuntu',
    [ValidateSet('auto', 'wsl', 'desktop')]
    [string]$Engine = 'auto'
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

function Test-Exe([string]$file, [string[]]$arguments, [int]$timeoutMs = 20000) {
    # Runs a native command quietly and reports success (exit code 0 within the timeout).
    try {
        $p = Start-Process -FilePath $file -ArgumentList $arguments -NoNewWindow -PassThru `
            -RedirectStandardOutput (Join-Path $stateDir 'probe.out') -RedirectStandardError (Join-Path $stateDir 'probe.err')
        $null = $p.Handle   # Windows PowerShell only reports ExitCode if the handle was opened before the exit
        if (-not $p.WaitForExit($timeoutMs)) { $p.Kill(); return $false }
        return ($p.ExitCode -eq 0)
    } catch { return $false }
}

function Test-DesktopDocker {
    # Docker Desktop counts only when its engine answers AND the Compose plugin is available to this shell.
    return (Test-Exe 'docker' @('info', '--format', '{{.ServerVersion}}')) -and (Test-Exe 'docker' @('compose', 'version'))
}

function Test-WslDocker {
    return (Get-Command wsl.exe -ErrorAction SilentlyContinue) -and (Test-Exe 'wsl.exe' @('-d', $WslDistro, '-u', 'root', '--', 'docker', 'info') 60000)
}

function Test-WslHasStack {
    # True when the WSL engine already holds this project's containers (and therefore its data volumes).
    try {
        $names = & wsl.exe -d $WslDistro -u root -- docker ps -a --filter 'label=com.docker.compose.project=cyberconnect-diu' --format '{{.Names}}' 2>$null
        return [bool]($names | Where-Object { $_ })
    } catch { return $false }
}

function Start-WslKeepAlive {
    # The WSL distro stops seconds after its last session ends, taking dockerd with it: keep one session open.
    $alive = Get-CimInstance Win32_Process -Filter "Name='wsl.exe'" | Where-Object { $_.CommandLine -like '*sleep 86400*' }
    if (-not $alive) { Start-Process wsl.exe -ArgumentList "-d $WslDistro -u root -- sleep 86400" -WindowStyle Hidden; Start-Sleep -Seconds 3 }
}

# Engine choice: the stack's data lives in Docker volumes of ONE engine. Use the engine that already has the project
# (so testers see the same data and only one stack binds port $Port); otherwise prefer Docker Desktop when it works.
switch ($Engine) {
    'wsl' { $script:useWsl = $true }
    'desktop' {
        if (-not (Test-DesktopDocker)) { throw 'Docker Desktop is not usable from this shell (engine or "docker compose" plugin missing).' }
        $script:useWsl = $false
    }
    default {
        $wslOk = $false
        if (Get-Command wsl.exe -ErrorAction SilentlyContinue) { Start-WslKeepAlive; $wslOk = Test-WslDocker }
        if ($wslOk -and (Test-WslHasStack)) { $script:useWsl = $true }
        elseif (Test-DesktopDocker) { $script:useWsl = $false }
        elseif ($wslOk) { $script:useWsl = $true }
        else { throw 'No usable Docker engine: start Docker Desktop, or the Docker engine in the Ubuntu WSL distro.' }
    }
}
if ($script:useWsl) {
    Start-WslKeepAlive
    $script:wslRoot = (& wsl.exe -d $WslDistro -- wslpath -a "$root").Trim()
    Write-Host "Docker engine: WSL ($WslDistro)" -ForegroundColor DarkGray
} else {
    Write-Host 'Docker engine: Docker Desktop' -ForegroundColor DarkGray
}

function Invoke-Compose([string[]]$composeArgs, [hashtable]$extraEnv = @{}) {
    if ($script:useWsl) {
        # No shell string crosses the Windows/WSL boundary (Windows PowerShell 5.1 strips embedded quotes from native
        # arguments): compose-wsl.sh receives plain arguments, exports VAR=value pairs and picks a working Compose binary.
        $wslArgs = @('-d', $WslDistro, '-u', 'root', '--exec', 'sh', "$($script:wslRoot)/scripts/compose-wsl.sh")
        foreach ($k in $extraEnv.Keys) { $wslArgs += "$k=$($extraEnv[$k])" }
        $wslArgs += '--'
        $wslArgs += $composeArgs
        & wsl.exe @wslArgs
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
Write-Host 'Building changed code and starting the full stack (postgres, redis, storage, mailpit, backend, frontend, nginx) ...' -ForegroundColor Cyan
# --build keeps the containers in step with the checked-out code (cached layers make it quick when nothing changed).
Invoke-Compose @('--profile','full','up','-d','--build')
# nginx resolves the backend/frontend container addresses once at start-up; after a rebuild those containers get new
# addresses, so nginx must restart or it answers 502 Bad Gateway.
Invoke-Compose @('--profile','full','restart','nginx')
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
