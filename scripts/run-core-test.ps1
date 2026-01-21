<#
Run core-test module tests and open the HTML report when successful.
Usage: .\scripts\run-core-test.ps1
Optional: add -NoOpen to skip opening the report.
#>
param(
    [switch]$NoOpen
)

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Definition
$repoRoot = Resolve-Path (Join-Path $scriptDir "..")
Set-Location $repoRoot

$gradle = Join-Path $repoRoot "gradlew.bat"
if (-not (Test-Path $gradle)) {
    Write-Error "gradlew.bat not found in repository root: $gradle"
    exit 2
}

Write-Host "Running :core-test:test ..."
& $gradle ":core-test:test" "--no-daemon" "--console" "plain" "--info" "--stacktrace"
$rc = $LASTEXITCODE

$report = Join-Path $repoRoot "core-test\build\reports\tests\test\index.html"

if ($rc -eq 0) {
    Write-Host "Tests passed."
    if ((Test-Path $report) -and (-not $NoOpen)) {
        Write-Host "Opening test report: $report"
        try {
            Start-Process $report
        } catch {
            # fallback
            Invoke-Item $report
        }
    } else {
        if (-not (Test-Path $report)) { Write-Host "Report not found at $report" }
    }
} else {
    Write-Error "Tests failed with exit code $rc. See console output and report if available: $report"
}

exit $rc
