$ErrorActionPreference = 'Stop'

$html = Get-Content -Raw report-viewer/index.html
$timeFilter = Get-Content -Raw report-viewer/time-filter.js

if ($html -notmatch 'raw-record-time-start') { throw 'Raw record time-range start control absent' }
if ($html -notmatch 'raw-record-time-end') { throw 'Raw record time-range end control absent' }
if ($html -notmatch 'Time bounds are inclusive') { throw 'Inclusive time-range semantics not documented in UI' }
if ($html -notmatch 'time-filter\.js') { throw 'Time-range filter script absent' }
if ($timeFilter -notmatch 'recordTimestamp') { throw 'Time-range filtering does not reuse report timestamp semantics' }
if ($timeFilter -notmatch 'timestamp < start') { throw 'Inclusive lower-bound contract absent' }
if ($timeFilter -notmatch 'timestamp > end') { throw 'Inclusive upper-bound contract absent' }
if ($timeFilter -notmatch 'timestamp === null') { throw 'Untimestamped-record exclusion contract absent' }
if ($timeFilter -notmatch 'start > end') { throw 'Invalid-range handling absent' }
if ($timeFilter -match '\beval\s*\(') { throw 'eval is forbidden in time-range filter' }
if ($timeFilter -match 'innerHTML') { throw 'Unsafe innerHTML is forbidden in time-range filter' }

$fixture = Get-Content report-viewer/sample/madlava-time-range.jsonl | Where-Object { $_.Trim() } | ForEach-Object { $_ | ConvertFrom-Json }
if ($fixture.Count -ne 4) { throw 'Time-range acceptance fixture shape mismatch' }
$start = [DateTimeOffset]::Parse('2026-09-04T10:00:00Z')
$end = [DateTimeOffset]::Parse('2026-09-04T10:10:00Z')
$timestampedInRange = @($fixture | Where-Object {
    $_.PSObject.Properties.Name -contains 'timestamp' -and
    ([DateTimeOffset]::Parse($_.timestamp) -ge $start) -and
    ([DateTimeOffset]::Parse($_.timestamp) -le $end)
})
if ($timestampedInRange.Count -ne 2) { throw 'Inclusive time-range fixture contract mismatch' }
$untimestamped = @($fixture | Where-Object { $_.PSObject.Properties.Name -notcontains 'timestamp' })
if ($untimestamped.Count -ne 1) { throw 'Untimestamped exclusion fixture contract mismatch' }

& node --check report-viewer/time-filter.js
if ($LASTEXITCODE -ne 0) { throw 'Time-range filter JavaScript syntax check failed' }
Write-Output 'Offline viewer inclusive timestamp-range filtering support: PASS'
