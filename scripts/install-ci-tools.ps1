# Installer for CI YAML helper tools: yamllint (Python) and actionlint (GitHub Actions linter)
# Usage: powershell -ExecutionPolicy Bypass -File .\scripts\install-ci-tools.ps1

$ErrorActionPreference = 'Stop'
Write-Output '=== install-ci-tools.ps1 started ==='

# Helper: ensure path in session
function Add-ToSessionPath($p) {
  if (-not [string]::IsNullOrEmpty($p) -and (Test-Path $p)) {
    if (-not ($env:PATH -split ';' | ForEach-Object { $_.Trim() } | Where-Object { $_ -ieq (Resolve-Path $p).Path })) {
      $env:PATH = $env:PATH + ';' + (Resolve-Path $p).Path
      Write-Output "Added to session PATH: $p"
    } else {
      Write-Output "Already on PATH: $p"
    }
  }
}

# 1) Install yamllint via pip (user install)
$python = Get-Command python -ErrorAction SilentlyContinue
if ($python) {
  Write-Output "Python found: $($python.Path)"
  try {
    python -m pip --version
  } catch {
    Write-Warning "pip not found for Python; please ensure pip is installed."
  }
  Write-Output 'Installing yamllint and pyyaml (user install)...'
  python -m pip install --user yamllint pyyaml | Out-Null

  # Discover user Scripts dir and add to session PATH
  try {
    $userbase = python - <<'PY'
import site, sys
print(site.getusersitepackages().rsplit('\\\',1)[0])
PY
  } catch {
    $userbase = $null
  }
  if ($userbase) {
    $scripts = Join-Path $userbase '..\Scripts'
    $scripts = [System.IO.Path]::GetFullPath($scripts)
    if (Test-Path $scripts) { Add-ToSessionPath $scripts } else { Write-Output "User scripts dir not found: $scripts" }
  }
} else {
  Write-Warning 'Python not found. Skipping yamllint install. Please install Python 3.8+ and ensure "python" is on PATH.'
}

# 2) Install or download actionlint
$hasChoco = Get-Command choco -ErrorAction SilentlyContinue
$hasScoop = Get-Command scoop -ErrorAction SilentlyContinue
if ($hasChoco) {
  Write-Output 'Chocolatey detected; installing actionlint via choco...'
  choco install actionlint -y
} elseif ($hasScoop) {
  Write-Output 'Scoop detected; installing actionlint via scoop...'
  scoop install actionlint
} else {
  Write-Output 'No choco/scoop detected; downloading actionlint Windows binary to .github\tools'
  $outdir = Join-Path $PSScriptRoot '..\.github\tools' | Resolve-Path -ErrorAction SilentlyContinue
  if (-not $outdir) { $outdir = Join-Path $PSScriptRoot '..\.github\tools' ; New-Item -Path $outdir -ItemType Directory -Force | Out-Null }
  $outdir = (Resolve-Path $outdir).Path
  $zip = Join-Path $outdir 'actionlint_windows_amd64.zip'
  $url = 'https://github.com/rhysd/actionlint/releases/latest/download/actionlint_windows_amd64.zip'
  Write-Output "Downloading actionlint from $url ..."
  try {
    Invoke-WebRequest -Uri $url -OutFile $zip -UseBasicParsing
    Write-Output 'Extracting...'
    Expand-Archive -Path $zip -DestinationPath $outdir -Force
    Remove-Item $zip -Force
    Add-ToSessionPath $outdir
    Write-Output "Downloaded actionlint into: $outdir"
  } catch {
    Write-Warning "Failed to download or extract actionlint: $_"
  }
}

Write-Output '=== Verification ==='
if (Get-Command yamllint -ErrorAction SilentlyContinue) { yamllint --version } else { Write-Warning 'yamllint: MISSING (ensure Python Scripts dir is on PATH)'}
if (Get-Command actionlint -ErrorAction SilentlyContinue) { actionlint --version } else {
  $localActionlint = Join-Path (Join-Path $PSScriptRoot '..\.github\tools') 'actionlint.exe'
  if (Test-Path $localActionlint) { & $localActionlint --version } else { Write-Warning 'actionlint: MISSING (check .github/tools for actionlint.exe)'}
}

Write-Output ''
Write-Output 'Next steps to integrate with IDEA:'
Write-Output ' - Add the Python Scripts dir and/or repository .github/tools to your IDE PATH/environment variables.'
Write-Output ' - In IntelliJ IDEA: Settings > Tools > Terminal > Environment variables (add PATH adjustments) or set in Run/Debug configuration.'
Write-Output ' - Optionally configure External Tools to call yamllint/actionlint for quick checks.'
Write-Output ' === install-ci-tools.ps1 finished ==='
