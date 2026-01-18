param(
  [string]$Root = "."
)
$ErrorActionPreference = "Stop"
$manifestPath = Join-Path $Root "manifest.json"
if (!(Test-Path $manifestPath)) { throw "manifest.json not found in $Root" }
$manifest = Get-Content $manifestPath -Raw | ConvertFrom-Json
$bad = @()
foreach ($f in $manifest.files) {
  $p = Join-Path $Root $f.path
  if (!(Test-Path $p)) { $bad += "MISSING: $($f.path)"; continue }
  $h = (Get-FileHash $p -Algorithm SHA256).Hash.ToLower()
  if ($h -ne $f.sha256) { $bad += "BADHASH: $($f.path)" }
}
if ($bad.Count -gt 0) {
  Write-Host "FAILED" -ForegroundColor Red
  $bad | ForEach-Object { Write-Host $_ }
  exit 1
}
Write-Host "OK - files verified: $($manifest.file_count)" -ForegroundColor Green
exit 0
