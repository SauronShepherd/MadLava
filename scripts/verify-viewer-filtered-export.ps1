$ErrorActionPreference = 'Stop'
$html = Get-Content -Raw 'report-viewer/index.html'
$script = Get-Content -Raw 'report-viewer/filtered-export.js'

if ($html -notmatch 'id="export-filtered-records"') { throw 'Filtered export control is missing.' }
if ($html -notmatch 'filtered-export\.js') { throw 'Filtered export script is not loaded.' }
if ($html -notmatch 'reopenable JSONL') { throw 'Filtered export semantics are not documented.' }
if ($script -notmatch 'visibleRecords\.map') { throw 'Export must serialize the active filtered record set.' }
if ($script -notmatch 'JSON\.stringify\(record\.value\)') { throw 'Export must preserve raw record payloads.' }
if ($script -notmatch 'application/x-ndjson') { throw 'Export must use JSONL/NDJSON content type.' }
if ($script -notmatch '-filtered\.jsonl') { throw 'Export filename must identify the filtered subset.' }
if ($script -notmatch 'URL\.revokeObjectURL') { throw 'Export object URLs must be released.' }
if ($script -match '\beval\s*\(' -or $script -match '\.innerHTML\s*=') { throw 'Filtered export must remain CSP-safe.' }

node --check report-viewer/filtered-export.js
if ($LASTEXITCODE -ne 0) { throw 'filtered-export.js failed syntax validation.' }
Write-Host 'Filtered report export contract verified.'
