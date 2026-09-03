$ErrorActionPreference = 'Stop'

$html = Get-Content -Raw report-viewer/index.html
$js = Get-Content -Raw report-viewer/viewer.js
if ($html -notmatch "connect-src 'none'") { throw 'No-network CSP absent' }
if ($html -notmatch 'type="file"') { throw 'File picker absent' }
if ($html -notmatch 'multiple') { throw 'Rotated report multi-file picker absent' }
if ($html -notmatch 'report-directory') { throw 'Run directory picker absent' }
if ($html -notmatch 'webkitdirectory') { throw 'Offline directory discovery absent' }
if ($js -match '\beval\s*\(') { throw 'eval is forbidden' }
if ($js -match 'innerHTML') { throw 'Unsafe innerHTML is forbidden' }
if ($js -notmatch 'orderReportFiles') { throw 'Rotated report ordering absent' }
if ($js -notmatch 'segment-') { throw 'Segment naming contract absent' }
if ($js -notmatch 'parseFiles') { throw 'Multi-segment parsing absent' }
if ($js -notmatch 'discoverRunFiles') { throw 'Manifest-backed run discovery absent' }
if ($js -notmatch 'crypto\.subtle\.digest') { throw 'Per-file SHA-256 verification absent' }
if ($js -notmatch 'Manifest-declared report file is missing') { throw 'Missing-segment integrity diagnostic absent' }

$legacy = Get-Content -Raw report-viewer/sample/madlava-sample.jsonl | ConvertFrom-Json
$current = Get-Content -Raw report-viewer/sample/madlava-v1-sample.jsonl | ConvertFrom-Json
if ($legacy.schemaVersion -ne 3 -or $current.schemaVersion -ne 1) { throw 'Sample schema mismatch' }
if ($js -notmatch '\[1,3,4\]\.includes') { throw 'Viewer does not accept schema-v1' }
if ($js -notmatch 'reportQualityWarnings') { throw 'Data-quality warning helper absent' }
if ($js -notmatch 'dropped snapshot') { throw 'Dropped-snapshot warning absent' }
if ($js -notmatch 'APPROXIMATE') { throw 'Accuracy warning levels absent' }

$fixtureRoot = Join-Path 'report-viewer/sample' 'rotated-run'
$manifestPath = Join-Path $fixtureRoot 'madlava-report-manifest.json'
$manifest = Get-Content -Raw $manifestPath | ConvertFrom-Json
if ($manifest.state -ne 'FINAL') { throw 'Rotated fixture manifest is not FINAL' }
$declaredFiles = @($manifest.files)
if ($manifest.segments -ne $declaredFiles.Count -or $declaredFiles.Count -lt 2) { throw 'Rotated fixture segment inventory mismatch' }
$expectedOrder = @('segments/segment-000001.jsonl', 'segments/segment-000002.jsonl', 'madlava.jsonl')
if (($declaredFiles.path -join '|') -ne ($expectedOrder -join '|')) { throw 'Rotated fixture ordering mismatch' }

$totalBytes = 0L
$totalRecords = 0L
$combined = New-Object System.IO.MemoryStream
foreach ($entry in $declaredFiles) {
    $relative = [string]$entry.path
    $filePath = Join-Path $fixtureRoot ($relative -replace '/', [IO.Path]::DirectorySeparatorChar)
    if (-not (Test-Path -LiteralPath $filePath -PathType Leaf)) { throw "Rotated fixture file missing: $relative" }
    $raw = [IO.File]::ReadAllBytes($filePath)
    $actualBytes = [long]$raw.Length
    $actualHash = ([BitConverter]::ToString(([Security.Cryptography.SHA256]::Create()).ComputeHash($raw))).Replace('-', '').ToLowerInvariant()
    if ($actualBytes -ne [long]$entry.bytes) { throw "Rotated fixture byte mismatch: $relative" }
    if ($actualHash -ne [string]$entry.sha256) { throw "Rotated fixture SHA-256 mismatch: $relative" }
    $totalBytes += $actualBytes
    foreach ($value in $raw) { if ($value -eq 10) { $totalRecords++ } }
    $combined.Write($raw, 0, $raw.Length)
}
$aggregateHash = ([BitConverter]::ToString(([Security.Cryptography.SHA256]::Create()).ComputeHash($combined.ToArray()))).Replace('-', '').ToLowerInvariant()
$combined.Dispose()
if ($totalBytes -ne [long]$manifest.bytes) { throw 'Rotated fixture total byte mismatch' }
if ($totalRecords -ne [long]$manifest.records) { throw 'Rotated fixture record count mismatch' }
if ($aggregateHash -ne [string]$manifest.sha256) { throw 'Rotated fixture aggregate SHA-256 mismatch' }

& node --check report-viewer/viewer.js
if ($LASTEXITCODE -ne 0) { throw 'Viewer JavaScript syntax check failed' }
Write-Output 'Offline viewer schema-v1/mixed-record/rotated-run/manifest-integrity/data-quality support: PASS'
