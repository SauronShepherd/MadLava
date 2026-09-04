$ErrorActionPreference = 'Stop'

$html = Get-Content -Raw report-viewer/index.html
$js = Get-Content -Raw report-viewer/viewer.js
if ($html -notmatch "connect-src 'none'") { throw 'No-network CSP absent' }
if ($html -notmatch 'type="file"') { throw 'File picker absent' }
if ($html -notmatch 'multiple') { throw 'Rotated report multi-file picker absent' }
if ($html -notmatch 'report-directory') { throw 'Run directory picker absent' }
if ($html -notmatch 'webkitdirectory') { throw 'Offline directory discovery absent' }
if ($html -notmatch 'run-warnings') { throw 'Run warning surface absent' }
if ($html -notmatch 'raw-record-select') { throw 'Raw stream record inspection absent' }
if ($html -notmatch 'raw-record-filter') { throw 'Raw stream record filter absent' }
if ($html -notmatch 'raw-record-context') { throw 'Raw stream record context absent' }
if ($js -match '\beval\s*\(') { throw 'eval is forbidden' }
if ($js -match 'innerHTML') { throw 'Unsafe innerHTML is forbidden' }
if ($js -notmatch 'orderReportFiles') { throw 'Rotated report ordering absent' }
if ($js -notmatch 'segment-') { throw 'Segment naming contract absent' }
if ($js -notmatch 'parseFiles') { throw 'Multi-segment parsing absent' }
if ($js -notmatch 'discoverRunFiles') { throw 'Manifest-backed run discovery absent' }
if ($js -notmatch 'crypto\.subtle\.digest') { throw 'Per-file SHA-256 verification absent' }
if ($js -notmatch 'Manifest-declared report file is missing') { throw 'Missing-segment integrity diagnostic absent' }
if ($js -notmatch 'reportQualityWarnings') { throw 'Data-quality warning helper absent' }
if ($js -notmatch 'APPROXIMATE') { throw 'Accuracy warning levels absent' }
if ($js -notmatch 'renderRunWarnings') { throw 'Run warning rendering absent' }
if ($js -notmatch 'configuration-change-rejected') { throw 'Rejected configuration warning absent' }
if ($js -notmatch 'populateRawRecords') { throw 'Raw record index absent' }
if ($js -notmatch 'snapshotSequence') { throw 'Snapshot correlation absent' }
if ($js -notmatch 'recordTimestamp') { throw 'Timestamp parser absent' }
if ($js -notmatch 'timestamp-unmatched') { throw 'Explicit uncorrelated timestamp state absent' }
if ($js -notmatch 'snapshot\.timestamp<=eventTimestamp') { throw 'Causal at-or-before correlation guard absent' }
if ($js -notmatch 'correlationTypes') { throw 'Correlation-type summary absent' }
if ($js -notmatch 'recordTypes') { throw 'Record-type summary absent' }

$legacy = Get-Content -Raw report-viewer/sample/madlava-sample.jsonl | ConvertFrom-Json
$current = Get-Content -Raw report-viewer/sample/madlava-v1-sample.jsonl | ConvertFrom-Json
if ($legacy.schemaVersion -ne 3 -or $current.schemaVersion -ne 1) { throw 'Sample schema mismatch' }
if ($js -notmatch '\[1,3,4\]\.includes') { throw 'Viewer does not accept schema-v1' }

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

$temporal = Get-Content report-viewer/sample/madlava-temporal-correlation.jsonl | Where-Object { $_.Trim() } | ForEach-Object { $_ | ConvertFrom-Json }
if ($temporal.Count -ne 4) { throw 'Temporal acceptance fixture shape mismatch' }
$snapshots = @($temporal | Where-Object { $_.schemaVersion -eq 1 })
$events = @($temporal | Where-Object { $_.recordType -eq 'configuration-change' })
if ($snapshots.Count -ne 2 -or $events.Count -ne 2) { throw 'Temporal acceptance fixture record mix mismatch' }
$firstSnapshot = [DateTimeOffset]::Parse($snapshots[0].timestamp)
$secondSnapshot = [DateTimeOffset]::Parse($snapshots[1].timestamp)
$causalEvent = [DateTimeOffset]::Parse($events[0].timestamp)
$earlyEvent = [DateTimeOffset]::Parse($events[1].timestamp)
if (-not ($firstSnapshot -le $causalEvent -and $causalEvent -lt $secondSnapshot)) { throw 'Temporal fixture does not exercise latest-at-or-before matching' }
if (-not ($earlyEvent -lt $firstSnapshot)) { throw 'Temporal fixture does not exercise explicit uncorrelated event' }

& node --check report-viewer/viewer.js
if ($LASTEXITCODE -ne 0) { throw 'Viewer JavaScript syntax check failed' }
Write-Output 'Offline viewer manifest-integrity/data-quality/raw-record/causal-temporal-correlation support: PASS'
