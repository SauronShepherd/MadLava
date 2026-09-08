$ErrorActionPreference = 'Stop'

$html = Get-Content -Raw report-viewer/index.html
$timeFilter = Get-Content -Raw report-viewer/time-filter.js

if ($html -notmatch 'raw-record-time-start') { throw 'Raw record time-range start control absent' }
if ($html -notmatch 'raw-record-time-end') { throw 'Raw record time-range end control absent' }
if ($html -notmatch 'clear-raw-record-time') { throw 'Clear time-range control absent' }
if ($html -notmatch 'Time bounds are inclusive') { throw 'Inclusive time-range semantics not documented in UI' }
if ($html -notmatch 'Clear time bounds restores the active type filter') { throw 'Clear-time behavior is not documented in UI' }
if ($html -notmatch 'time-filter\.js') { throw 'Time-range filter script absent' }
if ($html -notmatch 'raw-record-time-start"[^>]*aria-describedby="raw-record-time-help raw-record-context"') { throw 'Start time control is not connected to help and validation feedback' }
if ($html -notmatch 'raw-record-time-end"[^>]*aria-describedby="raw-record-time-help raw-record-context"') { throw 'End time control is not connected to help and validation feedback' }
if ($html -notmatch 'raw-record-context" role="status" aria-live="polite"') { throw 'Time-range validation feedback is not announced as a polite live status' }
if ($timeFilter -notmatch 'recordTimestamp') { throw 'Time-range filtering does not reuse report timestamp semantics' }
if ($timeFilter -notmatch 'timestamp < start') { throw 'Inclusive lower-bound contract absent' }
if ($timeFilter -notmatch 'timestamp > end') { throw 'Inclusive upper-bound contract absent' }
if ($timeFilter -notmatch 'timestamp === null') { throw 'Untimestamped-record exclusion contract absent' }
if ($timeFilter -notmatch 'start > end') { throw 'Invalid-range handling absent' }
if ($timeFilter -notmatch 'rejectInvalidBound') { throw 'Invalid individual time-bound handling absent' }
if ($timeFilter -notmatch 'Invalid .* time bound') { throw 'Invalid individual time-bound feedback absent' }
if ($timeFilter -notmatch 'setAttribute\("aria-invalid", "true"\)') { throw 'Invalid bounds do not expose aria-invalid' }
if ($timeFilter -notmatch 'removeAttribute\("aria-invalid"\)') { throw 'Corrected bounds do not clear aria-invalid' }
if ($timeFilter -notmatch 'clearTimeRangeFilter') { throw 'Clear time-range behavior absent' }
if ($timeFilter -notmatch 'startInput\.value = ""') { throw 'Clear action does not reset start bound' }
if ($timeFilter -notmatch 'endInput\.value = ""') { throw 'Clear action does not reset end bound' }
if ($timeFilter -notmatch 'clearButton\.addEventListener\("click", clearTimeRangeFilter\)') { throw 'Clear action is not keyboard/button invokable' }
if ($timeFilter -notmatch 'startInput\.focus\(\)') { throw 'Clear action does not return focus to the filter controls' }
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
& node scripts/verify-viewer-time-filter-behavior.mjs
if ($LASTEXITCODE -ne 0) { throw 'Time-range filter behavior harness failed' }
Write-Output 'Offline viewer inclusive timestamp-range filtering, accessibility, clear action, and behavior: PASS'
