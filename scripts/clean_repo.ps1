# Clean Gradle + Kotlin + IDE workspace artifacts
# Safe: does NOT touch source files, merged.txt, git metadata

param(
    [switch]$DryRun
)

function Remove-PathSafe($path) {
    if (Test-Path $path) {
        if ($DryRun) {
            Write-Host "WOULD REMOVE: $path"
        } else {
            Remove-Item -Recurse -Force -ErrorAction SilentlyContinue $path
            Write-Host "REMOVED: $path"
        }
    }
}

Write-Host "=== Cleaning workspace artifacts ==="

# --- Gradle build outputs ---
Get-ChildItem -Directory -Recurse -Force -Filter build |
    ForEach-Object { Remove-PathSafe $_.FullName }

# --- Gradle caches ---
Get-ChildItem -Directory -Recurse -Force -Filter .gradle |
    ForEach-Object { Remove-PathSafe $_.FullName }

# --- Kotlin tooling cache ---
Remove-PathSafe ".kotlin"
Remove-PathSafe "build-logic\.kotlin"

# --- IntelliJ local state ---
Remove-PathSafe ".idea\workspace.xml"
Remove-PathSafe ".idea\shelf"
Get-ChildItem ".idea" -Filter "copilot.data.migration.*.xml" -ErrorAction SilentlyContinue |
    ForEach-Object { Remove-PathSafe $_.FullName }

Write-Host "=== Done ==="
if ($DryRun) {
    Write-Host "Dry-run only. Nothing was deleted."
}
